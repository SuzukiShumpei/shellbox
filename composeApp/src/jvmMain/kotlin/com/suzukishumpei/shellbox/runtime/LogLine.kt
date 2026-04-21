package com.suzukishumpei.shellbox.runtime

enum class LogStream { Out, Err }

data class LogLine(
    val stream: LogStream,
    val text: String,
)
