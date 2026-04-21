package com.suzukishumpei.shellbox.data

import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SettingsStore(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) {
    private val settingsFile: Path
        get() {
            val home = Path.of(System.getProperty("user.home"))
            val dir = home.resolve("Library/Application Support/Shell Box")
            return dir.resolve("settings.json")
        }

    fun load(): Settings {
        val file = settingsFile
        if (file.notExists()) return Settings()
        return try {
            json.decodeFromString(Settings.serializer(), file.readText())
        } catch (_: Exception) {
            Settings()
        }
    }

    fun save(settings: Settings) {
        val file = settingsFile
        file.parent.createDirectories()
        file.writeText(json.encodeToString(Settings.serializer(), settings))
    }
}
