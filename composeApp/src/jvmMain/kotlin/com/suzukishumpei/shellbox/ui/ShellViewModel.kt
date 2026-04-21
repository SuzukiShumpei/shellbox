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
import kotlin.io.path.Path

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
        _settings.update { it.copy(projectRootPath = path) }
        persist()
        refreshScan()
        restartScriptsDirectoryWatcher()
    }

    fun setWorkingDirectoryForScript(scriptId: String, path: String) {
        _settings.update {
            it.copy(workingDirectoryByScriptId = it.workingDirectoryByScriptId + (scriptId to path))
        }
        persist()
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
                _scripts.value = list
                _scanError.value = null
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
            if (entry.scriptPath == null) {
                _runDialog.value = RunDialogState(
                    scriptId = entry.id,
                    title = entry.title,
                    logs = listOf(LogLine(LogStream.Err, "実行できる .sh / .command が見つかりません。")),
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
                runner.start(entry.scriptPath, cwdPath)
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

    private fun persist() {
        settingsStore.save(_settings.value)
    }
}
