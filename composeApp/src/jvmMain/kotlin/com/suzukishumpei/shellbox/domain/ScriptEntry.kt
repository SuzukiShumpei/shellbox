package com.suzukishumpei.shellbox.domain

import java.nio.file.Path

/**
 * @param id `scripts` からの相対パス（例: `android/screen-record`）
 * @param category 第1階層ディレクトリ名（例: `android`）
 */
data class ScriptEntry(
    val id: String,
    val category: String,
    val title: String,
    val readmePath: Path,
    val readmeFullText: String,
    val scriptPath: Path?,
)
