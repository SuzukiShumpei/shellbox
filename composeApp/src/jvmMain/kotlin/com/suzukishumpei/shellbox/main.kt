package com.suzukishumpei.shellbox

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.suzukishumpei.shellbox.domain.ScriptEntry
import com.suzukishumpei.shellbox.ui.App
import com.suzukishumpei.shellbox.ui.ShellViewModel
import com.suzukishumpei.shellbox.ui.pickDirectory
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

private const val TrayFrequentTitleMaxLen = 48

private fun trayMenuTitleForScript(title: String): String {
    val t = title.trim().ifEmpty { "（無題）" }
    if (t.length <= TrayFrequentTitleMaxLen) return t
    return t.take(TrayFrequentTitleMaxLen - 1) + "…"
}

/** 実行回数の多い順（同数はタイトル順）。現在のスキャン結果に存在する ID のみ。 */
private fun trayFrequentFive(
    counts: Map<String, Int>,
    scripts: List<ScriptEntry>,
): List<Pair<String, String>> {
    if (counts.isEmpty() || scripts.isEmpty()) return emptyList()
    val titleById = scripts.associate { it.id to it.title }
    return counts.entries
        .asSequence()
        .filter { it.key in titleById && it.value > 0 }
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { titleById[it.key] ?: "" },
        )
        .take(5)
        .map { it.key to trayMenuTitleForScript(titleById.getValue(it.key)) }
        .toList()
}

fun main() = application {
    val vm = remember { ShellViewModel() }
    val settings by vm.settings.collectAsState()
    val scripts by vm.scripts.collectAsState()
    val scope = rememberCoroutineScope()
    var isWindowVisible by remember { mutableStateOf(true) }
    var bringToFrontRequest by remember { mutableStateOf(0L) }
    var awtWindowForPicker by remember { mutableStateOf<java.awt.Window?>(null) }
    val trayState = rememberTrayState()
    val trayIcon = remember { ShellBoxTrayIconPainter }
    val frequentTrayEntries = remember(settings.runCountsByScriptId, scripts) {
        trayFrequentFive(settings.runCountsByScriptId, scripts)
    }

    Tray(
        state = trayState,
        icon = trayIcon,
        tooltip = "Shell Box",
        menu = {
            Item(
                text = "Shell Box を表示",
                onClick = {
                    isWindowVisible = true
                    bringToFrontRequest++
                },
            )
            if (isWindowVisible) {
                Item(
                    text = "Shell Box を非表示",
                    onClick = { isWindowVisible = false },
                )
            }
            frequentTrayEntries.forEach { (scriptId, menuTitle) ->
                Item(
                    text = "🤖 $menuTitle",
                    onClick = {
                        isWindowVisible = true
                        bringToFrontRequest++
                        scope.launch {
                            delay(120)
                            val w = awtWindowForPicker
                            val entry = vm.scripts.value.find { it.id == scriptId } ?: return@launch
                            vm.runScript(entry) { pickDirectory(w) }
                        }
                    },
                )
            }
            Item(
                text = "終了",
                onClick = ::exitApplication,
            )
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
            SideEffect {
                awtWindowForPicker = window
            }
            DisposableEffect(Unit) {
                onDispose {
                    awtWindowForPicker = null
                }
            }
            // 表示メニュー押下ごとに前面化。既に表示中(true)でも request が増えて再実行される。
            LaunchedEffect(bringToFrontRequest) {
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
            App(
                parentWindow = window,
                onExitApplication = { exitApplication() },
                viewModel = vm,
            )
        }
    }
}
