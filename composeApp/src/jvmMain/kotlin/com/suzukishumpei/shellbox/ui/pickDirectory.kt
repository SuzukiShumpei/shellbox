package com.suzukishumpei.shellbox.ui

import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume

private fun isMacOs(): Boolean {
    val os = System.getProperty("os.name")?.lowercase() ?: ""
    return "mac" in os || "darwin" in os
}

/**
 * macOS では [FileDialog] + `apple.awt.fileDialogForDirectories` で Finder に近いネイティブのフォルダ選択を出す。
 * それ以外は [JFileChooser]。
 */
suspend fun pickDirectory(parent: Window?): String? = suspendCancellableCoroutine { cont ->
    SwingUtilities.invokeLater {
        if (!cont.isActive) return@invokeLater
        val path = try {
            if (isMacOs()) {
                pickDirectoryMacNative(parent)
            } else {
                pickDirectorySwingChooser(parent)
            }
        } catch (_: Exception) {
            null
        }
        cont.resume(path)
    }
}

private fun pickDirectoryMacNative(parent: Window?): String? {
    val frame = parent as? Frame
    val previous = System.getProperty("apple.awt.fileDialogForDirectories")
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    return try {
        val dialog = FileDialog(frame, "フォルダを選択", FileDialog.LOAD)
        dialog.isMultipleMode = false
        dialog.isVisible = true
        val directory = dialog.directory
        val name = dialog.file
        when {
            directory == null -> null
            name.isNullOrEmpty() -> File(directory).canonicalFile.absolutePath
            else -> File(directory, name).canonicalFile.absolutePath
        }
    } finally {
        if (previous != null) {
            System.setProperty("apple.awt.fileDialogForDirectories", previous)
        } else {
            System.clearProperty("apple.awt.fileDialogForDirectories")
        }
    }
}

private fun pickDirectorySwingChooser(parent: Window?): String? {
    val chooser = JFileChooser()
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = "フォルダを選択"
    return when (chooser.showOpenDialog(parent)) {
        JFileChooser.APPROVE_OPTION -> chooser.selectedFile?.absolutePath
        else -> null
    }
}
