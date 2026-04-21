package com.suzukishumpei.shellbox

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.suzukishumpei.shellbox.ui.App

fun main() = application {
    val state = rememberWindowState(
        width = 400.dp,
        height = 800.dp,
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "Shell Box",
    ) {
        App(window)
    }
}