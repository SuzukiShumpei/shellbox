package com.suzukishumpei.shellbox.data

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val projectRootPath: String? = null,
    val workingDirectoryByScriptId: Map<String, String> = emptyMap(),
)
