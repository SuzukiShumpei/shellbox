package com.suzukishumpei.shellbox.domain

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.streams.toList

/**
 * `scripts/<カテゴリ>/<…>/` の形のみ採用する。
 * `scripts/<スクリプトID>/` のように直下にスクリプトだけ置いた構成は一覧に出さない。
 */
class ScriptScanner {

    fun scan(projectRoot: Path): Result<List<ScriptEntry>> {
        val scriptsDir = projectRoot.resolve("scripts")
        if (scriptsDir.notExists() || !scriptsDir.isDirectory()) {
            return Result.failure(
                IllegalStateException("scripts ディレクトリが見つかりません: $scriptsDir"),
            )
        }
        val categoryDirs = Files.list(scriptsDir).use { stream ->
            stream.toList()
                .filter { it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
                .filter { !it.name.startsWith(".") }
                .sortedBy { it.name }
        }
        val entries = mutableListOf<ScriptEntry>()
        for (categoryPath in categoryDirs) {
            Files.walk(categoryPath).use { stream ->
                stream.toList()
                    .filter { it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
                    .filter { path ->
                        val rel = scriptsDir.relativize(path)
                        rel.nameCount >= 2
                    }
                    .sorted()
                    .forEach { dir ->
                        val readme = dir.resolve("README.md")
                        if (!readme.isRegularFile()) return@forEach
                        val rel = scriptsDir.relativize(dir)
                        val id = rel.toString().replace(File.separatorChar, '/')
                        val category = rel.getName(0).toString()
                        val readmeText = Files.readString(readme, Charsets.UTF_8)
                        val title = readmeTitleFromContent(readmeText, id)
                        val isImport = (category == "import")
                        val scriptPath = resolveScriptFile(dir)
                        if (scriptPath == null && !isImport) {
                            // import 以外は README だけのディレクトリは採用しない
                            return@forEach
                        }
                        entries.add(
                            ScriptEntry(
                                id = id,
                                category = category,
                                title = title,
                                readmePath = readme,
                                readmeFullText = readmeText,
                                scriptPath = scriptPath,
                                isImported = isImport,
                            ),
                        )
                    }
            }
        }
        return Result.success(entries.distinctBy { it.id }.sortedBy { it.id })
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
