package com.suzukishumpei.shellbox.domain

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.notExists

class ScriptScanner {

    fun scan(projectRoot: Path): Result<List<ScriptEntry>> {
        val scriptsDir = projectRoot.resolve("scripts")
        if (scriptsDir.notExists() || !scriptsDir.isDirectory()) {
            return Result.failure(
                IllegalStateException("scripts ディレクトリが見つかりません: $scriptsDir"),
            )
        }
        val dirs = Files.list(scriptsDir).use { stream ->
            stream.toList()
                .filter { it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
                .filter { !it.name.startsWith(".") }
                .sortedBy { it.name }
        }
        val entries = dirs.mapNotNull { dir -> scanScriptDir(dir) }
        return Result.success(entries)
    }

    private fun scanScriptDir(dir: Path): ScriptEntry? {
        val id = dir.name
        val readme = dir.resolve("README.md")
        val readmeText = if (readme.isRegularFile()) {
            Files.readString(readme, Charsets.UTF_8)
        } else {
            ""
        }
        val title = readmeTitleFromContent(readmeText, id)
        val scriptPath = resolveScriptFile(dir)
        return ScriptEntry(
            id = id,
            title = title,
            readmePath = readme,
            readmeFullText = readmeText,
            scriptPath = scriptPath,
        )
    }

    private fun resolveScriptFile(dir: Path): Path? {
        val files = Files.list(dir).use { stream ->
            stream.toList().filter { it.isRegularFile(LinkOption.NOFOLLOW_LINKS) }
        }
        val sh = files.filter { it.name.endsWith(".sh", ignoreCase = true) }.sortedBy { it.name }
        if (sh.isNotEmpty()) return sh.first()
        val command = files.filter { it.name.endsWith(".command", ignoreCase = true) }.sortedBy { it.name }
        if (command.isNotEmpty()) return command.first()
        return null
    }
}
