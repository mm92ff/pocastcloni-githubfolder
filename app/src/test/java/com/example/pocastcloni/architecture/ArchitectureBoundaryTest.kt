package com.example.pocastcloni.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchitectureBoundaryTest {
    private val sourceRoot = locateSourceRoot()

    @Test
    fun `domain stays independent from Android and implementation packages`() {
        val violations =
            kotlinFiles(sourceRoot.resolve("domain"))
                .flatMap { file ->
                    file.readLines()
                        .filter { line -> DOMAIN_FORBIDDEN_IMPORT.matches(line) }
                        .map { line -> "${file.relativeTo(sourceRoot).invariantSeparatorsPath}: $line" }
                }

        assertTrue("Forbidden domain imports:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun `repository implementations never depend on use cases`() {
        val repositoryRoot = sourceRoot.resolve("data/repository")
        val violations =
            kotlinFiles(repositoryRoot)
                .flatMap { file ->
                    file.readLines()
                        .filter { line -> line.startsWith("import com.example.pocastcloni.domain.usecase.") }
                        .map { line -> "${file.name}: $line" }
                }

        assertTrue("Repository-to-use-case dependencies:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun `view models depend on player ports rather than controller implementation`() {
        val violations =
            kotlinFiles(sourceRoot.resolve("ui"))
                .filter { file -> file.name.endsWith("ViewModel.kt") }
                .filter { file -> "AudioPlayerController" in file.readText() }
                .map { file -> file.relativeTo(sourceRoot).invariantSeparatorsPath }

        assertTrue("Concrete player controller in ViewModels:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun `backup view model has no WorkManager knowledge`() {
        val viewModel = sourceRoot.resolve("ui/settings/SettingsBackupViewModel.kt")
        val source = viewModel.readText()
        val violations = BACKUP_WORK_TYPES.filter(source::contains)

        assertTrue("WorkManager details in SettingsBackupViewModel: $violations", violations.isEmpty())
    }

    @Test
    fun `legacy repository contract and UI player controller are gone`() {
        val legacyFiles =
            listOf(
                sourceRoot.resolve("domain/repository/PodcastRepository.kt"),
                sourceRoot.resolve("ui/player/AudioPlayerController.kt")
            ).filter(File::exists)

        assertTrue(
            "Legacy architecture files remain: ${legacyFiles.joinToString { it.name }}",
            legacyFiles.isEmpty()
        )
    }

    private fun kotlinFiles(directory: File): List<File> =
        directory.walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList()

    private fun locateSourceRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            val appSource = candidate.resolve("app/src/main/java/com/example/pocastcloni")
            if (appSource.isDirectory) return appSource

            val moduleSource = candidate.resolve("src/main/java/com/example/pocastcloni")
            if (moduleSource.isDirectory) return moduleSource
            candidate = candidate.parentFile
        }
        error("Could not locate app source root from ${System.getProperty("user.dir")}")
    }

    private companion object {
        val DOMAIN_FORBIDDEN_IMPORT =
            Regex(
                "^import (?:android\\.|androidx\\.|com\\.example\\.pocastcloni\\.(?:data|ui|service)\\.).*$"
            )

        val BACKUP_WORK_TYPES =
            listOf(
                "androidx.work.",
                "WorkManager",
                "WorkInfo",
                "WorkRequest",
                "BackupWorker",
                "TAG_BACKUP_JOB"
            )
    }
}
