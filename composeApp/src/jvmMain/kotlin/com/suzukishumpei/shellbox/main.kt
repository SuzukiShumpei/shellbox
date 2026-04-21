package com.suzukishumpei.shellbox

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.suzukishumpei.shellbox.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Shell Box",
    ) {
        App(window)
    }
}