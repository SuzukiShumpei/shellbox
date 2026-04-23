package com.suzukishumpei.shellbox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suzukishumpei.shellbox.data.Settings
import com.suzukishumpei.shellbox.data.SettingsStore
import com.suzukishumpei.shellbox.domain.ScriptEntry
import com.suzukishumpei.shellbox.domain.ScriptScanner
import com.suzukishumpei.shellbox.fs.launchScriptsDirectoryWatcher
import com.suzukishumpei.shellbox.runtime.LogLine
import com.suzukishumpei.shellbox.runtime.LogStream
import com.suzukishumpei.shellbox.runtime.ShellProcessRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.nio.file.Path as NioPath
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile

sealed interface ShellRoute {
    data object List : ShellRoute
    data class Detail(val entry: ScriptEntry) : ShellRoute
}

data class RunDialogState(
    val scriptId: String,
    val title: String,
    val logs: List<LogLine>,
    val isRunning: Boolean,
    val exitCode: Int?,
)

class ShellViewModel(
    private val settingsStore: SettingsStore = SettingsStore(),
    private val scanner: ScriptScanner = ScriptScanner(),
) : ViewModel() {

    private val importIdSegmentRegex = Regex("""^[a-zA-Z0-9][a-zA-Z0-9._-]*$""")

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _scripts = MutableStateFlow<List<ScriptEntry>>(emptyList())
    val scripts: StateFlow<List<ScriptEntry>> = _scripts.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val _route = MutableStateFlow<ShellRoute>(ShellRoute.List)
    val route: StateFlow<ShellRoute> = _route.asStateFlow()

    private val _runDialog = MutableStateFlow<RunDialogState?>(null)
    val runDialog: StateFlow<RunDialogState?> = _runDialog.asStateFlow()

    private var activeRunner: ShellProcessRunner? = null

    private var scriptsWatchJob: Job? = null

    init {
        refreshScan()
        restartScriptsDirectoryWatcher()
    }

    fun setProjectRoot(path: String) {
        _settings.update {
            it.copy(
                projectRootPath = path,
                visibleScriptCategories = null,
            )
        }
        persist()
        refreshScan()
        restartScriptsDirectoryWatcher()
    }

    /** null = 全カテゴリ表示。非 null = 含めるカテゴリのみ（第1階層名）。 */
    fun clearVisibleScriptCategoryFilter() {
        _settings.update { it.copy(visibleScriptCategories = null) }
        persist()
    }

    /**
     * フィルタ未設定時の初回クリック: そのカテゴリを除外。
     * フィルタ設定済み: カテゴリのオンオフ。結果が全件または空なら null に戻す。
     */
    fun toggleVisibleScriptCategory(category: String) {
        val all = _scripts.value.map { it.category }.toSet()
        if (all.isEmpty()) return
        val cur = _settings.value.visibleScriptCategories?.toSet()
        val next = if (cur == null) {
            all - category
        } else {
            val m = cur.toMutableSet()
            if (category in m) m.remove(category) else m.add(category)
            m
        }
        val normalized = when {
            next.isEmpty() -> null
            next == all -> null
            else -> next.toList().sorted()
        }
        _settings.update { it.copy(visibleScriptCategories = normalized) }
        persist()
    }

    fun setWorkingDirectoryForScript(scriptId: String, path: String) {
        _settings.update {
            it.copy(workingDirectoryByScriptId = it.workingDirectoryByScriptId + (scriptId to path))
        }
        persist()
    }

    fun setImportedScriptPath(scriptId: String, path: String) {
        _settings.update {
            it.copy(importedScriptPathById = it.importedScriptPathById + (scriptId to path))
        }
        persist()
    }

    fun clearImportedScriptPath(scriptId: String) {
        _settings.update { s ->
            s.copy(
                importedScriptPathById = s.importedScriptPathById.filterKeys { it != scriptId },
            )
        }
        persist()
    }

    /**
     * `scripts/import/<idSegment>/README.md` を作成し、外部スクリプト path（と任意で cwd）を登録する。
     * 成功なら [Result.success]，失敗メッセージは [Result.failure]。
     */
    fun registerImportedScript(
        idSegment: String,
        externalScriptPath: String,
        title: String,
        cwd: String?,
    ): Result<Unit> {
        val seg = idSegment.trim()
        if (!importIdSegmentRegex.matches(seg)) {
            return Result.failure(
                IllegalArgumentException("ID は英数字・._- で、先頭は英数字にしてください。"),
            )
        }
        val root = _settings.value.projectRootPath
            ?: return Result.failure(IllegalStateException("プロジェクトを設定してください。"))
        val id = "import/$seg"
        if (_scripts.value.any { it.id == id }) {
            return Result.failure(IllegalStateException("同じ ID のスクリプトが既にあります。"))
        }
        val file = File(externalScriptPath)
        if (!file.isFile) {
            return Result.failure(
                IllegalArgumentException("指定したスクリプトがファイルとして存在しません: $externalScriptPath"),
            )
        }
        val dir = Path(root).resolve("scripts").resolve("import").resolve(seg)
        val readme = dir.resolve("README.md")
        if (Files.isRegularFile(readme)) {
            return Result.failure(
                IllegalStateException(
                    "既に scripts/import/$seg があります。手動で整理するか別の ID を使ってください。",
                ),
            )
        }
        return runCatching {
            Files.createDirectories(dir)
            val t = title.trim().ifEmpty { seg }
            val body =
                """
                |# $t
                |
                |importした外部スクリプトです。スクリプトpathの指定は必須です。詳細画面から指定・変更してください。
                """.trimMargin()
            Files.writeString(readme, body, StandardCharsets.UTF_8)
            _settings.update { s ->
                var next = s.copy(
                    importedScriptPathById = s.importedScriptPathById + (id to file.canonicalPath),
                )
                if (cwd != null) {
                    next = next.copy(
                        workingDirectoryByScriptId = next.workingDirectoryByScriptId + (id to cwd),
                    )
                }
                next
            }
            persist()
            refreshScan()
        }
    }

    /** cwd にこだわらないスクリプト向けに、プロジェクトルートをワンタップで設定する。 */
    fun useProjectRootAsWorkingDirectory(scriptId: String) {
        val root = _settings.value.projectRootPath ?: return
        val canonical = try {
            File(root).canonicalPath
        } catch (_: Exception) {
            root
        }
        setWorkingDirectoryForScript(scriptId, canonical)
    }

    fun navigateToList() {
        _route.value = ShellRoute.List
    }

    fun navigateToDetail(entry: ScriptEntry) {
        _route.value = ShellRoute.Detail(entry)
    }

    fun refreshScan() {
        val root = _settings.value.projectRootPath ?: run {
            _scripts.value = emptyList()
            _scanError.value = null
            return
        }
        val projectPath = Path(root)
        scanner.scan(projectPath).fold(
            onSuccess = { list ->
                val allCats = list.map { it.category }.toSet()
                val validIds = list.map { it.id }.toSet()
                _settings.update { s ->
                    val base = s.copy(
                        workingDirectoryByScriptId = s.workingDirectoryByScriptId.filterKeys { it in validIds },
                        importedScriptPathById = s.importedScriptPathById.filterKeys { it in validIds },
                        runCountsByScriptId = s.runCountsByScriptId.filterKeys { it in validIds },
                    )
                    val f = base.visibleScriptCategories ?: return@update base
                    val pruned = f.filter { it in allCats }
                    when {
                        pruned.isEmpty() || pruned.toSet() == allCats ->
                            base.copy(visibleScriptCategories = null)

                        else ->
                            base.copy(visibleScriptCategories = pruned.sorted())
                    }
                }
                _scripts.value = list
                _scanError.value = null
                persist()
                ensureScriptsDirectoryWatcher()
            },
            onFailure = { e ->
                _scripts.value = emptyList()
                _scanError.value = e.message ?: e.toString()
                stopScriptsDirectoryWatcher()
            },
        )
    }

    private fun stopScriptsDirectoryWatcher() {
        scriptsWatchJob?.cancel()
        scriptsWatchJob = null
    }

    /** プロジェクト変更時など、監視を張り直す。 */
    private fun restartScriptsDirectoryWatcher() {
        stopScriptsDirectoryWatcher()
        startScriptsDirectoryWatcherIfNotRunning()
    }

    /**
     * 監視が止まっているときだけ開始する。
     * （Watch からの [refreshScan] 成功時に stop しないため、自己キャンセルを避ける）
     */
    private fun ensureScriptsDirectoryWatcher() {
        startScriptsDirectoryWatcherIfNotRunning()
    }

    private fun startScriptsDirectoryWatcherIfNotRunning() {
        if (scriptsWatchJob?.isActive == true) return
        val root = _settings.value.projectRootPath ?: return
        val scriptsDir = Path(root).resolve("scripts")
        if (!java.nio.file.Files.isDirectory(scriptsDir)) return
        scriptsWatchJob = viewModelScope.launchScriptsDirectoryWatcher(
            scriptsDir = scriptsDir,
            onChanged = { refreshScan() },
        )
    }

    fun dismissRunDialog() {
        activeRunner?.stop()
        activeRunner = null
        _runDialog.value = null
    }

    fun stopRun() {
        activeRunner?.stop()
        _runDialog.update { state ->
            state?.copy(isRunning = false) ?: state
        }
    }

    fun sendStdinLine(line: String) {
        activeRunner?.sendStdinLine(line)
    }

    /**
     * 実行に必要な cwd が無ければ [pickDirectory] で選ばせる。
     */
    fun runScript(entry: ScriptEntry, pickDirectory: suspend () -> String?) {
        viewModelScope.launch {
            val scriptFile = resolveExecutableForRun(entry)
            if (scriptFile == null || !scriptFile.isRegularFile()) {
                val msg = when {
                    entry.isImported && _settings.value.importedScriptPathById[entry.id] == null &&
                            entry.scriptPath == null ->
                        "外部スクリプトの path を設定してください（詳細で .sh / .command を指定）。"

                    entry.isImported ->
                        "外部スクリプトの path が無効か、ファイルが見つかりません。"

                    else ->
                        "実行できる .sh / .command が見つかりません。"
                }
                _runDialog.value = RunDialogState(
                    scriptId = entry.id,
                    title = entry.title,
                    logs = listOf(LogLine(LogStream.Err, msg)),
                    isRunning = false,
                    exitCode = null,
                )
                return@launch
            }
            var cwd = _settings.value.workingDirectoryByScriptId[entry.id]
            if (cwd == null) {
                cwd = pickDirectory() ?: return@launch
                setWorkingDirectoryForScript(entry.id, cwd)
            }
            val cwdPath = Path(cwd)
            val runner = ShellProcessRunner(viewModelScope)
            activeRunner = runner
            _runDialog.value = RunDialogState(
                scriptId = entry.id,
                title = entry.title,
                logs = emptyList(),
                isRunning = true,
                exitCode = null,
            )
            val collectJob = viewModelScope.launch {
                runner.logLines.collect { line ->
                    _runDialog.update { s ->
                        s?.copy(logs = s.logs + line) ?: s
                    }
                }
            }
            val startResult = withContext(Dispatchers.IO) {
                runner.start(scriptFile, cwdPath)
            }
            if (startResult.isFailure) {
                val msg = startResult.exceptionOrNull()?.message ?: "起動に失敗しました"
                _runDialog.update { s ->
                    s?.copy(
                        logs = s.logs + LogLine(LogStream.Err, msg),
                        isRunning = false,
                    ) ?: s
                }
                collectJob.cancel()
                activeRunner = null
                return@launch
            }
            incrementRunCount(entry.id)
            val code = withContext(Dispatchers.IO) {
                runner.waitForExit()
            }
            collectJob.cancel()
            activeRunner = null
            _runDialog.update { s ->
                s?.copy(
                    logs = s.logs + LogLine(
                        LogStream.Out,
                        "--- 終了コード: $code ---",
                    ),
                    isRunning = false,
                    exitCode = code,
                ) ?: s
            }
        }
    }

    /** 詳細などから cwd だけ先に設定するとき */
    suspend fun pickAndSetWorkingDirectory(scriptId: String, pickDirectory: suspend () -> String?) {
        val path = pickDirectory() ?: return
        setWorkingDirectoryForScript(scriptId, path)
    }

    private fun incrementRunCount(scriptId: String) {
        _settings.update { s ->
            val next = (s.runCountsByScriptId[scriptId] ?: 0) + 1
            s.copy(runCountsByScriptId = s.runCountsByScriptId + (scriptId to next))
        }
        persist()
    }

    private fun persist() {
        settingsStore.save(_settings.value)
    }

    /**
     * import: [Settings.importedScriptPathById] を最優先。未設定 or 無効ならプロジェクト内の [ScriptEntry.scriptPath]。
     */
    private fun resolveExecutableForRun(entry: ScriptEntry): NioPath? {
        _settings.value.importedScriptPathById[entry.id]?.let { o ->
            val p = File(o).toPath()
            if (p.isRegularFile()) return p
        }
        val local = entry.scriptPath
        if (local != null && local.isRegularFile()) return local
        return null
    }
}
