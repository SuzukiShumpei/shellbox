package com.suzukishumpei.shellbox.domain

internal fun readmeTitleFromContent(content: String, fallbackId: String): String {
    content.lineSequence().forEach { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("# ")) {
            return trimmed.removePrefix("# ").trim()
        }
    }
    return fallbackId
}
