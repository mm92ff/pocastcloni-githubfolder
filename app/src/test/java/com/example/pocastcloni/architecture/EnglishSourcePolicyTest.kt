package com.example.pocastcloni.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EnglishSourcePolicyTest {
    private val repositoryRoot = locateRepositoryRoot()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `application has no localized values directories`() {
        val localizedDirectories =
            listOf(
                repositoryRoot.resolve("app/src/main/res"),
                repositoryRoot.resolve("benchmark/src/main/res")
            ).flatMap(::localizedValuesDirectories)
                .map { file -> file.relativeTo(repositoryRoot).invariantSeparatorsPath }
                .sorted()

        assertTrue(
            "Localized values directories are not allowed: $localizedDirectories",
            localizedDirectories.isEmpty()
        )
    }

    @Test
    fun `production sources and project documentation use English`() {
        val result = applyAllowlist(findLanguageViolations(languagePolicyFiles()), LANGUAGE_ALLOWANCES)

        assertTrue(
            "Unused language allowances:\n${result.unusedAllowances.joinToString("\n")}",
            result.unusedAllowances.isEmpty()
        )
        assertTrue(
            "Non-English first-party text found:\n${result.unexpectedViolations.joinToString("\n")}",
            result.unexpectedViolations.isEmpty()
        )
    }

    @Test
    fun `raw exception messages cannot become application UI copy`() {
        val productionSources = sourceFiles(repositoryRoot.resolve("app/src/main"))
        val violations = findRawExceptionUiViolations(productionSources)

        assertTrue(
            "Raw exception text reaches an application UI boundary:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `application UI primitives use string resources for owned copy`() {
        val productionUiSources =
            sourceFiles(
                repositoryRoot.resolve("app/src/main/java/com/example/pocastcloni/ui")
            )
        val violations =
            productionUiSources.flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (HARDCODED_UI_COPY.containsMatchIn(line)) {
                        "${file.relativeTo(repositoryRoot).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "Application-owned UI copy must use string resources:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `standard missing translation lint remains enabled`() {
        val buildScript = repositoryRoot.resolve("app/build.gradle.kts").readText()

        assertFalse("MissingTranslation must not be disabled", "MissingTranslation" in buildScript)
    }

    @Test
    fun `locale qualifier detection distinguishes API and layout qualifiers`() {
        listOf("values-de", "values-de-rCH", "values-de-v29", "values-b+de+CH")
            .forEach { name -> assertTrue(name, isLocalizedValuesDirectory(name)) }
        listOf("values", "values-v27", "values-v29", "values-night", "values-land")
            .forEach { name -> assertFalse(name, isLocalizedValuesDirectory(name)) }
    }

    @Test
    fun `non-allowlisted German test literal is detected with file and line`() {
        val source =
            temporaryFolder.newFile("Preview.kt").apply {
                writeText("val title = \"KI " + "ver" + "stehen\"")
            }

        val violations = findLanguageViolations(listOf(source))

        assertTrue(
            violations.single().toString()
                .endsWith("Preview.kt:1: val title = \"KI " + "ver" + "stehen\"")
        )
    }

    @Test
    fun `non-allowlisted German script line is detected`() {
        val script =
            temporaryFolder.newFile("verify.ps1").apply {
                writeText("# Stelle " + "sicher that the package is valid.")
            }

        assertTrue(findLanguageViolations(listOf(script)).single().relativePath == "verify.ps1")
    }

    @Test
    fun `German default resource literal is detected`() {
        val resource =
            temporaryFolder.newFile("strings.xml").apply {
                writeText("<string name=\"error\">Podcast " + "konn" + "te nicht geladen werden</string>")
            }

        assertTrue(findLanguageViolations(listOf(resource)).single().relativePath == "strings.xml")
    }

    @Test
    fun `known ASCII German production comments are detected`() {
        val comments =
            listOf(
                "// Home " + "kann hier Edit-Mode aktivieren und deaktivieren.",
                "// Swap-Target " + "im Grid finden.",
                "// PointerInput " + "nutzt IntSize f\u00fcr die Breite.",
                "// pointerInput: " + "size.width ist Int.",
                "// Progress-Bereich: " + "Bar + Time-Labels."
            )
        val source =
            temporaryFolder.newFile("KnownComments.kt").apply {
                writeText(comments.joinToString("\n"))
            }

        assertTrue(findLanguageViolations(listOf(source)).size == comments.size)
    }

    @Test
    fun `indirect multiline exception detail flow is rejected`() {
        val source =
            temporaryFolder.newFile("ImportViewModel.kt").apply {
                writeText(
                    "val benchmarkDetails = t.message.orEmpty()\n" +
                        "val text = UiText.StringResource(R.string.error_unknown, benchmarkDetails)"
                )
            }

        assertTrue(findRawExceptionUiViolations(listOf(source)).isNotEmpty())
    }

    @Test
    fun `documented exact-path allowance accepts an intentional fixture`() {
        val source =
            temporaryFolder.newFile("LocalizedFailureTest.kt").apply {
                writeText("val error = \"Podcast " + "konn" + "te nicht geladen werden\"")
            }
        val allowance =
            LanguageAllowance(
                relativePath = "LocalizedFailureTest.kt",
                expectedText = "Podcast " + "konn" + "te nicht geladen werden",
                reason = "Verifies that localized exception details do not reach the UI."
            )

        val result = applyAllowlist(findLanguageViolations(listOf(source)), listOf(allowance))

        assertTrue(result.unexpectedViolations.isEmpty())
        assertTrue(result.unusedAllowances.isEmpty())
    }

    @Test
    fun `localized resource fixture is rejected while API qualifier is allowed`() {
        val resourceRoot = temporaryFolder.newFolder("res")
        resourceRoot.resolve("values-de").mkdir()
        resourceRoot.resolve("values-v29").mkdir()

        val localizedDirectories = localizedValuesDirectories(resourceRoot)

        assertTrue(localizedDirectories.map(File::getName) == listOf("values-de"))
    }

    private fun languagePolicyFiles(): List<File> {
        val sourceFiles =
            listOf(
                repositoryRoot.resolve("app/src/main"),
                repositoryRoot.resolve("app/src/test/java"),
                repositoryRoot.resolve("app/src/androidTest/java"),
                repositoryRoot.resolve("app/src/debug/java"),
                repositoryRoot.resolve("app/src/benchmark/java"),
                repositoryRoot.resolve("benchmark/src/main"),
                repositoryRoot.resolve("benchmark/src/test/java"),
                repositoryRoot.resolve("benchmark/src/androidTest/java")
            ).flatMap(::sourceFiles)
        val projectFiles =
            listOf(
                "README.md",
                "CHANGELOG.md",
                "AGENTS.md",
                "MEMORY.md",
                "settings.gradle.kts",
                "build.gradle.kts",
                "app/build.gradle.kts",
                "benchmark/build.gradle.kts",
                "gradle.properties",
                "detekt.yml",
                "create-project-backup.bat",
                "sign-release-local.bat",
                "signing.local.properties.example",
                "benchmark/README.md"
            ).map(repositoryRoot::resolve).filter(File::isFile)

        return (sourceFiles + projectFiles + trackedScriptFiles())
            .distinctBy { file -> file.canonicalPath }
            .sortedBy { file -> file.relativeTo(repositoryRoot).invariantSeparatorsPath }
    }

    private fun trackedScriptFiles(): List<File> {
        val rootScripts =
            repositoryRoot.listFiles()
                .orEmpty()
                .filter { file -> file.isFile && file.extension in SCRIPT_EXTENSIONS }
        val benchmarkScripts =
            repositoryRoot.resolve("benchmark/scripts")
                .takeIf(File::isDirectory)
                ?.walkTopDown()
                ?.filter { file -> file.isFile && file.extension in SCRIPT_EXTENSIONS }
                ?.toList()
                .orEmpty()
        return rootScripts + benchmarkScripts
    }

    private fun sourceFiles(root: File): List<File> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { file -> file.isFile && file.extension in SCANNED_EXTENSIONS }
            .toList()
    }

    private fun findLanguageViolations(files: List<File>): List<LanguageViolation> =
        files.flatMap { file ->
            val relativePath =
                file.relativeTo(repositoryRoot).invariantSeparatorsPath
                    .takeUnless { path -> path.startsWith("../") }
                    ?: file.name
            file.readLines().mapIndexedNotNull { index, line ->
                if (GERMAN_DIACRITIC.containsMatchIn(line) || GERMAN_MARKER.containsMatchIn(line)) {
                    LanguageViolation(relativePath, index + 1, line.trim())
                } else {
                    null
                }
            }
        }

    private fun findRawExceptionUiViolations(files: List<File>): List<String> =
        files.flatMap { file ->
            val relativePath =
                file.relativeTo(repositoryRoot).invariantSeparatorsPath
                    .takeUnless { path -> path.startsWith("../") }
                    ?: file.name
            file.readLines().mapIndexedNotNull { index, line ->
                val containsRawExceptionToken = RAW_EXCEPTION_TOKENS.any(line::contains)
                val containsRawExceptionPattern = RAW_EXCEPTION_PATTERNS.any { it.containsMatchIn(line) }
                if (containsRawExceptionToken || containsRawExceptionPattern) {
                    "$relativePath:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }

    private fun applyAllowlist(
        violations: List<LanguageViolation>,
        allowances: List<LanguageAllowance>
    ): LanguagePolicyResult {
        val unexpected = violations.toMutableList()
        val unused = mutableListOf<LanguageAllowance>()
        allowances.forEach { allowance ->
            val matchIndex =
                unexpected.indexOfFirst { violation ->
                    violation.relativePath == allowance.relativePath &&
                        allowance.expectedText in violation.text
                }
            if (matchIndex >= 0) {
                unexpected.removeAt(matchIndex)
            } else {
                unused += allowance
            }
        }
        return LanguagePolicyResult(unexpected, unused)
    }

    private fun localizedValuesDirectories(resourceRoot: File): List<File> =
        resourceRoot.listFiles()
            .orEmpty()
            .filter { file -> file.isDirectory && isLocalizedValuesDirectory(file.name) }
            .sortedBy(File::getName)

    private fun isLocalizedValuesDirectory(name: String): Boolean {
        if (!name.startsWith("values-")) return false
        val qualifiers = name.removePrefix("values-").split('-')
        return qualifiers.any { qualifier ->
            qualifier.matches(Regex("[a-z]{2}")) || qualifier.startsWith("b+")
        }
    }

    private fun locateRepositoryRoot(): File {
        val workingDirectory = System.getProperty("user.dir").orEmpty()
        check(workingDirectory.isNotBlank()) { "The JVM working directory is unavailable" }
        var candidate: File? = File(workingDirectory).canonicalFile
        while (candidate != null) {
            val current = candidate
            if (
                current.resolve("settings.gradle.kts").isFile &&
                current.resolve("app/src/main").isDirectory
            ) {
                return current
            }
            candidate = current.parentFile
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }

    private companion object {
        val SCANNED_EXTENSIONS = setOf("java", "kt", "kts", "md", "xml")
        val SCRIPT_EXTENSIONS = setOf("bat", "cmd", "ps1", "sh")
        val GERMAN_DIACRITIC = Regex("[\u00c4\u00d6\u00dc\u00e4\u00f6\u00fc\u00df]")
        val GERMAN_MARKERS =
            listOf(
                "Abst" + "\u00e4" + "nde",
                "Auf" + "ruf",
                "bew" + "usst",
                "Da" + "tei",
                "Fel" + "der",
                "gegr" + "iffen",
                "Hin" + "weis",
                "hinzu" + "gef\u00fcgt",
                "Klassen" + "name",
                "komp" + "akter",
                "konn" + "te",
                "Original" + "da" + "tei",
                "Sicherheits" + "risiko",
                "Stelle " + "sicher",
                "ver" + "stehen",
                "vert" + "ikal",
                "vor" + "her",
                "Wer" + "te",
                "wurde " + "entfernt",
                "Home " + "kann hier Edit-Mode aktivieren",
                "Swap-Target " + "im Grid finden",
                "PointerInput " + "nutzt IntSize",
                "pointerInput: " + "size.width ist Int",
                "Progress-Bereich: " + "Bar + Time-Labels"
            )
        val GERMAN_MARKER =
            Regex(
                GERMAN_MARKERS.joinToString(prefix = "\\b(?:", postfix = ")\\b", separator = "|") { marker ->
                    Regex.escape(marker)
                },
                RegexOption.IGNORE_CASE
            )
        val LANGUAGE_ALLOWANCES =
            listOf(
                LanguageAllowance(
                    relativePath =
                    "app/src/androidTest/java/com/example/pocastcloni/ui/settings/" +
                        "EnglishUiLanguageAndroidTest.kt",
                    expectedText = "onAllNodesWithText(\"KI " + "ver" + "stehen\")",
                    reason = "Asserts that the former German preview title is absent."
                ),
                LanguageAllowance(
                    relativePath =
                    "app/src/androidTest/java/com/example/pocastcloni/ui/settings/" +
                        "EnglishUiLanguageAndroidTest.kt",
                    expectedText =
                    "onAllNodesWithText(\"KI als Sicherheits" + "risiko\", substring = true)",
                    reason = "Asserts that the former German episode title is absent."
                ),
                LanguageAllowance(
                    relativePath =
                    "app/src/test/java/com/example/pocastcloni/data/worker/DownloadFileNameTest.kt",
                    expectedText = "episodeTitle = \"Fol" + "ge \ud83c\udf99\ufe0f \u00e4\u00f6\u00fc\".repeat(40),",
                    reason = "Verifies Unicode-safe download file names."
                ),
                LanguageAllowance(
                    relativePath =
                    "app/src/test/java/com/example/pocastcloni/ui/home/add/" +
                        "AddPodcastViewModelLanguageTest.kt",
                    expectedText =
                    "IllegalStateException(\"Podcast " + "konn" + "te nicht geladen werden\")",
                    reason = "Verifies that localized exception details do not reach the UI."
                ),
                LanguageAllowance(
                    relativePath =
                    "app/src/test/java/com/example/pocastcloni/ui/settings/" +
                        "SettingsUrlImportViewModelSecurityTest.kt",
                    expectedText =
                    "IllegalStateException(\"Podcast " + "konn" + "te nicht hinzu" +
                        "gef\u00fcgt werden\")",
                    reason = "Verifies that localized exception details do not reach the UI."
                ),
                LanguageAllowance(
                    relativePath =
                    "app/src/test/java/com/example/pocastcloni/data/worker/" +
                        "WorkManagerBackupJobSchedulerTest.kt",
                    expectedText = "putString(\"error_message\", \"Da" + "tei " + "konn" + "te nicht gelesen werden\")",
                    reason = "Verifies that legacy localized worker diagnostics are ignored."
                )
            )
        val RAW_EXCEPTION_UI =
            Regex("UiText\\.(?:StringResource|DynamicString)\\([^\\n]*(?:localizedMessage|\\.message)")
        val RAW_EXCEPTION_ACCESS =
            Regex("\\b(?:t|e|error|exception|throwable|cause)\\.(?:localizedMessage|message)\\b")
        val RAW_EXCEPTION_TOKENS = listOf("localizedMessage", "KEY_ERROR_MESSAGE")
        val RAW_EXCEPTION_PATTERNS = listOf(RAW_EXCEPTION_UI, RAW_EXCEPTION_ACCESS)
        val HARDCODED_UI_COPY =
            Regex(
                "(?:Text|SettingsSectionTitle|showSnackbar)\\(\\s*\"[A-Za-z]" +
                    "|(?:title|subtitle|contentDescription)\\s*=\\s*\"[A-Za-z]"
            )
    }
}

private data class LanguageViolation(
    val relativePath: String,
    val lineNumber: Int,
    val text: String
) {
    override fun toString(): String = "$relativePath:$lineNumber: $text"
}

private data class LanguageAllowance(
    val relativePath: String,
    val expectedText: String,
    val reason: String
) {
    override fun toString(): String = "$relativePath: $expectedText ($reason)"
}

private data class LanguagePolicyResult(
    val unexpectedViolations: List<LanguageViolation>,
    val unusedAllowances: List<LanguageAllowance>
)
