package com.suzukishumpei.shellbox

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.suzukishumpei.shellbox.ui.App
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay

/** メニューバー向けの明るいトーン（Material ベクタの黒塗りをトレイに使わず、再帰も起こさない）。 */
private val TrayIconFill = Color(0xFFF2F2F7)
private val TrayIconStroke = Color(0xFF8E8E93)

private object ShellBoxTrayIconPainter : Painter() {
    override val intrinsicSize: Size get() = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val pad = size.minDimension * 0.18f
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val r = size.minDimension * 0.12f
        drawRoundRect(
            color = TrayIconFill,
            topLeft = Offset(pad, pad),
            size = Size(w, h),
            cornerRadius = CornerRadius(r, r),
        )
        drawRoundRect(
            color = TrayIconStroke,
            topLeft = Offset(pad, pad),
            size = Size(w, h),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = size.minDimension * 0.06f),
        )
        val lineW = w * 0.55f
        val lineH = size.minDimension * 0.07f
        val lx = pad + (w - lineW) / 2
        val y1 = pad + h * 0.38f
        val y2 = pad + h * 0.55f
        drawRect(color = TrayIconStroke, topLeft = Offset(lx, y1), size = Size(lineW, lineH))
        drawRect(
            color = TrayIconStroke,
            topLeft = Offset(lx, y2),
            size = Size(lineW * 0.72f, lineH)
        )
    }
}

fun main() = application {
    var isWindowVisible by remember { mutableStateOf(true) }
    val trayState = rememberTrayState()
    val trayIcon = remember { ShellBoxTrayIconPainter }

    Tray(
        state = trayState,
        icon = trayIcon,
        tooltip = "Shell Box",
        menu = {
            Item(
                text = if (isWindowVisible) "ウィンドウを隠す" else "Shell Box を表示",
                onClick = { isWindowVisible = !isWindowVisible },
            )
            Item("終了", onClick = ::exitApplication)
        },
    )

    if (isWindowVisible) {
        val windowState = rememberWindowState(
            width = 400.dp,
            height = 800.dp,
        )
        Window(
            onCloseRequest = { isWindowVisible = false },
            state = windowState,
            title = "Shell Box",
        ) {
            // トレイ等から再表示のたびに前面へ（マッピング後・EDT で toFront）
            LaunchedEffect(Unit) {
                delay(32)
                val w = window
                SwingUtilities.invokeLater {
                    val prev = w.isAlwaysOnTop
                    w.isAlwaysOnTop = true
                    w.toFront()
                    w.requestFocus()
                    w.isAlwaysOnTop = prev
                }
            }
            App(window)
        }
    }
}
