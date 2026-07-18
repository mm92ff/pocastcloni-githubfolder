package com.example.pocastcloni.ui.settings

import com.example.pocastcloni.data.local.BackupData
import com.example.pocastcloni.data.serialization.JsonMapperFactory
import com.example.pocastcloni.domain.repository.UserSettings
import com.fasterxml.jackson.databind.JsonNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppLocaleBackupIsolationTest {
    private val objectMapper = JsonMapperFactory.create()

    @Test
    fun `serialized user settings and backup data contain no locale fields`() {
        val userSettingsJson = objectMapper.valueToTree<JsonNode>(UserSettings())
        val backupJson =
            objectMapper.valueToTree<JsonNode>(
                BackupData(settings = UserSettings())
            )

        listOf(userSettingsJson, backupJson).forEach { json ->
            val fieldNames = collectFieldNames(json)
            assertTrue(
                "Locale state must not be serialized: $fieldNames",
                fieldNames.none(::isLocaleField)
            )
        }
    }

    @Test
    fun `backup and import sources cannot access the application locale controller`() {
        val sourceRoot = File("src/main/java/com/example/pocastcloni")
        val backupSources =
            sourceRoot.walkTopDown()
                .filter { file ->
                    file.isFile &&
                        file.extension == "kt" &&
                        (
                            "Backup" in file.name ||
                                file.invariantSeparatorsPath.endsWith("ui/settings/SettingsBackupViewModel.kt")
                            )
                }
                .toList()

        assertTrue("Expected backup production sources", backupSources.isNotEmpty())
        backupSources.forEach { source ->
            assertFalse(
                "${source.invariantSeparatorsPath} must not access AppLocaleController",
                source.readText().contains("AppLocaleController")
            )
        }
    }

    @Test
    fun `settings reset cannot access the application locale controller`() {
        listOf(
            "src/main/java/com/example/pocastcloni/ui/settings/SettingsViewModel.kt",
            "src/main/java/com/example/pocastcloni/domain/usecase/app/ResetAppUseCase.kt"
        ).forEach { path ->
            val source = File(path).readText()
            assertFalse("$path must not access AppLocaleController", source.contains("AppLocaleController"))
        }
    }

    @Test
    fun `legacy backup podcast fallback does not persist localized resource copy`() {
        val path = "src/main/java/com/example/pocastcloni/data/repository/BackupRepositoryImpl.kt"
        val source = File(path).readText()

        assertFalse(source.contains("context.getString(R.string.import_fallback_title)"))
        assertFalse(source.contains("context.getString(R.string.import_fallback_description)"))
        assertTrue(source.contains("title = backupPodcast.title ?: backupPodcast.url"))
        assertTrue(source.contains("description = backupPodcast.description.orEmpty()"))
    }

    private fun collectFieldNames(node: JsonNode): Set<String> {
        val result = mutableSetOf<String>()
        fun visit(current: JsonNode) {
            when {
                current.isObject ->
                    current.fields().forEach { (name, value) ->
                        result += name
                        visit(value)
                    }
                current.isArray -> current.forEach(::visit)
            }
        }
        visit(node)
        return result
    }

    private fun isLocaleField(fieldName: String): Boolean {
        val normalized = fieldName.lowercase()
        return "language" in normalized || "locale" in normalized
    }
}
