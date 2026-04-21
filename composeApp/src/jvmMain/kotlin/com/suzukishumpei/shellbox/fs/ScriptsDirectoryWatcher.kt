package com.suzukishumpei.shellbox.fs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit

/**
 * [scriptsDir] 直下のエントリの作成・削除・変更を監視し、デバウンス後に [onChanged] を呼ぶ。
 * macOS の WatchService は取りこぼしや遅延があり得るため、手動の再読み込みボタンと併用する想定。
 */
fun CoroutineScope.launchScriptsDirectoryWatcher(
    scriptsDir: Path,
    debounceMs: Long = 450L,
    onChanged: suspend () -> Unit,
): Job = launch(Dispatchers.IO) {
    if (!Files.isDirectory(scriptsDir)) return@launch
    val watchService: WatchService = FileSystems.getDefault().newWatchService()
    scriptsDir.register(
        watchService,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY,
    )
    var debounce: Job? = null
    try {
        while (isActive) {
            val key = watchService.poll(500, TimeUnit.MILLISECONDS) ?: continue
            try {
                key.pollEvents()
            } finally {
                if (!key.reset()) break
            }
            debounce?.cancel()
            debounce = launch {
                delay(debounceMs)
                onChanged()
            }
        }
    } catch (_: java.nio.file.ClosedWatchServiceException) {
    } finally {
        debounce?.cancel()
        watchService.close()
    }
}
