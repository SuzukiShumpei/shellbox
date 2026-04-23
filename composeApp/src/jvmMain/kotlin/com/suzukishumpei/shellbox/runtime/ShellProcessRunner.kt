package com.suzukishumpei.shellbox.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

private fun isMacOs(): Boolean {
    val os = System.getProperty("os.name")?.lowercase() ?: ""
    return "mac" in os || "darwin" in os
}

/**
 * Finder や DMG から起動した .app はターミナルと違い [user.home]/.zshrc 相当の PATH が入らない。
 * `adb` / `ffmpeg` 等が [PATH] に無いとスクリプトは「成功」に見えて実際は空出力になる。
 * 一般的な Android SDK / Homebrew の bin を先頭に足す（存在しなくても害はない）。
 */
private fun pathPrependForMacDmgApp(home: String): List<String> = listOf(
    "$home/Library/Android/sdk/platform-tools",
    "$home/Android/Sdk/platform-tools",
    "/opt/homebrew/bin",
    "/opt/homebrew/sbin",
    "/usr/local/bin",
    "/usr/local/sbin",
)

private fun mergePathEnv(current: String?, prepend: List<String>): String {
    val base = (current ?: System.getenv("PATH") ?: "/usr/bin:/bin:/usr/sbin:/sbin")
    val tail = base.split(":")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val head = prepend.map { it.trimEnd('/') }
    return (head + tail)
        .distinct()
        .joinToString(":")
}

/** DMG から起動する GUI .app 向け。Windows の [PATH] は区切りが `;` のため、ここでは手を出さない。 */
private fun applyPathEnvForGuiAppLaunch(env: MutableMap<String, String>) {
    if (!isMacOs()) {
        return
    }
    val home = System.getProperty("user.home") ?: return
    env["PATH"] = mergePathEnv(env["PATH"], pathPrependForMacDmgApp(home))
}

private val PrintenvKeyRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

private fun parsePrintenvOutput(text: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (line in text.lineSequence()) {
        if (line.isEmpty()) continue
        val i = line.indexOf('=')
        if (i <= 0) continue
        val k = line.substring(0, i)
        if (!PrintenvKeyRegex.matches(k)) continue
        out[k] = line.substring(i + 1)
    }
    return out
}

/**
 * [SHELL] でログインシェルから環境を取り込む。Terminal.app は対話＋ログインのため `.zshrc` も読むが、
 * **非対話**の `zsh -lc` だけでは `.zshrc` が読まれず `PATH` / `JAVA_HOME` がターミナルとずれる。
 * まず `-lic`（ログイン＋対話）で `printenv` を実行し、失敗・空なら `-lc` にフォールバックする。
 */
private fun macLoginShellForSubprocess(): String {
    val s = System.getenv("SHELL")?.trim().orEmpty()
    if (s.isNotEmpty()) {
        val p = Path.of(s)
        if (Files.isExecutable(p)) {
            val ok = s.endsWith("/zsh") || s.endsWith("zsh") ||
                    s.endsWith("/bash") || s.endsWith("bash")
            if (ok) return s
        }
    }
    return if (Files.isExecutable(Path.of("/bin/zsh"))) {
        "/bin/zsh"
    } else {
        "/bin/bash"
    }
}

private fun captureMacLoginPrintenv(workDir: File?): Map<String, String>? {
    val scriptBin = Path.of("/usr/bin/script")
    if (!isMacOs() || !Files.isExecutable(scriptBin)) return null
    val login = macLoginShellForSubprocess()
    for (flags in listOf("-lic", "-lc")) {
        val parsed = runMacPrintenvCapture(login, flags, workDir) ?: continue
        if (parsed.isNotEmpty()) return parsed
    }
    return null
}

private fun runMacPrintenvCapture(
    loginShell: String,
    loginFlags: String,
    workDir: File?,
): Map<String, String>? =
    try {
        val pb = ProcessBuilder(
            "/usr/bin/script",
            "-q",
            "/dev/null",
            loginShell,
            loginFlags,
            "/usr/bin/printenv",
        )
        if (workDir != null && workDir.isDirectory) {
            pb.directory(workDir)
        }
        pb.redirectError(ProcessBuilder.Redirect.to(File("/dev/null")))
        val p = pb.start()
        val ok = p.waitFor(28, TimeUnit.SECONDS)
        if (!ok) {
            p.destroyForcibly()
            return null
        }
        if (p.exitValue() != 0) return null
        val text = p.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        parsePrintenvOutput(text)
    } catch (_: Exception) {
        null
    }

/**
 * パイプ実行だと bash の `select` や `read -p` のプロンプトが stderr バッファに留まり、
 * かつ TTY でないとメニューが出ないことがある。macOS では `script` で疑似 TTY を付ける。
 * macOS では起動直前に [captureMacLoginPrintenv] で環境を寄せる。
 */
private fun shellInvocation(scriptPath: Path): List<String> {
    val path = scriptPath.absolutePathString()
    val scriptBin = Path.of("/usr/bin/script")
    if (isMacOs() && Files.isExecutable(scriptBin)) {
        return listOf("/usr/bin/script", "-q", "/dev/null", "/bin/bash", path)
    }
    return listOf("/bin/bash", path)
}

/**
 * 1 回のスクリプト実行ごとにインスタンスを作る想定。
 */
class ShellProcessRunner(
    private val scope: CoroutineScope,
) {

    private val logChannel = Channel<LogLine>(Channel.UNLIMITED)
    val logLines: Flow<LogLine> = logChannel.receiveAsFlow()

    private var readerJob: Job? = null
    private var process: Process? = null

    suspend fun start(
        scriptPath: Path,
        workingDirectory: Path,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!workingDirectory.isDirectory()) {
            return@withContext Result.failure(
                IllegalArgumentException("作業ディレクトリが存在しません: ${workingDirectory.pathString}"),
            )
        }
        val workDirFile = File(workingDirectory.pathString)
        val pb = ProcessBuilder(shellInvocation(scriptPath))
        pb.directory(workDirFile)
        val env = pb.environment()
        val snap = if (isMacOs()) captureMacLoginPrintenv(workDirFile) else null
        if (snap != null && snap.isNotEmpty()) {
            env.clear()
            env.putAll(snap)
        } else {
            applyPathEnvForGuiAppLaunch(env)
        }
        pb.redirectErrorStream(false)
        try {
            val p = pb.start()
            process = p
            readerJob = scope.launch(Dispatchers.IO) {
                coroutineScope {
                    launch { drainStream(p.inputStream, LogStream.Out) }
                    launch { drainStream(p.errorStream, LogStream.Err) }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logChannel.close()
            Result.failure(e)
        }
    }

    /**
     * bash の `read -p` などはプロンプトを改行なしで stderr に出す。
     * 改行待ちだけだと UI に一切出ないので、入力が止まったあと短時間で溜まった文字をフラッシュする。
     */
    private suspend fun drainStream(stream: java.io.InputStream, streamKind: LogStream) {
        val mutex = Mutex()
        val lineBuf = StringBuilder()
        var partialFlushJob: Job? = null

        suspend fun drainCompleteLinesOnly() {
            mutex.withLock {
                while (true) {
                    val s = lineBuf.toString()
                    val idx = s.indexOf('\n')
                    if (idx < 0) break
                    val line = s.substring(0, idx).removeSuffix("\r")
                    lineBuf.clear()
                    lineBuf.append(s.substring(idx + 1))
                    logChannel.trySend(LogLine(streamKind, line))
                }
            }
        }

        suspend fun flushPartialWithoutTrailingNewline() {
            mutex.withLock {
                while (true) {
                    val s = lineBuf.toString()
                    val idx = s.indexOf('\n')
                    if (idx < 0) break
                    val line = s.substring(0, idx).removeSuffix("\r")
                    lineBuf.clear()
                    lineBuf.append(s.substring(idx + 1))
                    logChannel.trySend(LogLine(streamKind, line))
                }
                if (lineBuf.isEmpty()) return@withLock
                val pending = lineBuf.toString().trimEnd('\r')
                lineBuf.clear()
                if (pending.isNotEmpty()) {
                    logChannel.trySend(LogLine(streamKind, pending))
                }
            }
        }

        fun cancelPartialFlush() {
            partialFlushJob?.cancel()
            partialFlushJob = null
        }

        fun schedulePartialFlush() {
            cancelPartialFlush()
            partialFlushJob = scope.launch {
                delay(90)
                flushPartialWithoutTrailingNewline()
            }
        }

        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            val buffer = CharArray(4096)
            while (scope.isActive) {
                val n = reader.read(buffer)
                if (n == -1) {
                    cancelPartialFlush()
                    flushPartialWithoutTrailingNewline()
                    break
                }
                if (n == 0) continue
                cancelPartialFlush()
                mutex.withLock {
                    lineBuf.appendRange(buffer, 0, n)
                }
                drainCompleteLinesOnly()
                val hasPartial = mutex.withLock { lineBuf.isNotEmpty() }
                if (hasPartial) {
                    schedulePartialFlush()
                }
            }
        }
    }

    fun sendStdinLine(line: String) {
        val p = process ?: return
        val out = p.outputStream
        out.write((line + "\n").toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    suspend fun waitForExit(): Int = withContext(Dispatchers.IO) {
        val p = process ?: return@withContext -1
        val code = try {
            p.waitFor()
        } finally {
            readerJob?.join()
            readerJob = null
            process = null
            logChannel.close()
        }
        code
    }

    fun stop() {
        process?.destroyForcibly()
        readerJob?.cancel()
        readerJob = null
        process = null
        logChannel.close()
    }
}
