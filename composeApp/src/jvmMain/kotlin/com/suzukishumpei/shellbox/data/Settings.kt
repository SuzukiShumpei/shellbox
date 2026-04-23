package com.suzukishumpei.shellbox.data

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val projectRootPath: String? = null,
    val workingDirectoryByScriptId: Map<String, String> = emptyMap(),
    /**
     * `import` カテゴリ用。キーはスクリプトID（例: `import/my-tool`）。値は実行する .sh / .command の絶対パス。
     */
    val importedScriptPathById: Map<String, String> = emptyMap(),
    /** null または空: 全カテゴリ表示。非空: 列挙した第1階層名のスクリプトだけ表示 */
    val visibleScriptCategories: List<String>? = null,
)
