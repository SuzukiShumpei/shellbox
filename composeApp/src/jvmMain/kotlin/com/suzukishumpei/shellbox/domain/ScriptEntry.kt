package com.suzukishumpei.shellbox.domain

import java.nio.file.Path

/**
 * @param id `scripts` からの相対パス（例: `android/screen-record`、`import/my-tool`）
 * @param category 第1階層ディレクトリ名（例: `android`、`import`）
 */
data class ScriptEntry(
    val id: String,
    val category: String,
    val title: String,
    val readmePath: Path,
    val readmeFullText: String,
    val scriptPath: Path?,
    /**
     * `scripts/import/...`（README あり。実行ファイルは多くの場合 [Settings.importedScriptPathById]）。
     */
    val isImported: Boolean = false,
)
