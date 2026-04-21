package com.suzukishumpei.shellbox.domain

import java.nio.file.Path

data class ScriptEntry(
    val id: String,
    val title: String,
    val readmePath: Path,
    val readmeFullText: String,
    val scriptPath: Path?,
)
