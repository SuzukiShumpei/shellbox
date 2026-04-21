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
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

private fun isMacOs(): Boolean {
    val os = System.getProperty("os.name")?.lowercase() ?: ""
    return "mac" in os || "darwin" in os
}

/**
 * パイプ実行だと bash の `select` や `read -p` のプロンプトが stderr バッファに留まり、
 * かつ TTY でないとメニューが出ないことがある。macOS では `script` で疑似 TTY を付ける。
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
        val pb = ProcessBuilder(shellInvocation(scriptPath))
        pb.directory(java.io.File(workingDirectory.pathString))
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
