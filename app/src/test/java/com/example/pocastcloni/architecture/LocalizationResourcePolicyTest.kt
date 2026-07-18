package com.example.pocastcloni.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationResourcePolicyTest {
    private val repositoryRoot = locateRepositoryRoot()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `supported locale directories match the approved allowlist`() {
        val localizedDirectories =
            repositoryRoot.resolve("app/src/main/res")
                .listFiles()
                .orEmpty()
                .filter { directory ->
                    directory.isDirectory && isLocalizedValuesDirectory(directory.name)
                }
                .map(File::getName)
                .sorted()

        assertTrue(
            "Unexpected locale resource directories: $localizedDirectories",
            localizedDirectories == APPROVED_LOCALE_DIRECTORIES
        )
    }

    @Test
    fun `German catalog has complete type-compatible resource parity`() {
        val defaultCatalog = parseCatalog(defaultCatalogFile())
        val germanCatalog = parseCatalog(germanCatalogFile())
        val violations = compareCatalogs(defaultCatalog, germanCatalog)

        assertTrue(
            "German resource catalog does not match the English source catalog:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `German placeholders plurals and arrays match the English catalog`() {
        val defaultCatalog = parseCatalog(defaultCatalogFile())
        val germanCatalog = parseCatalog(germanCatalogFile())
        val violations = compareResourceContents(defaultCatalog, germanCatalog)

        assertTrue(
            "German formatted resources are incompatible with the English source catalog:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `all format resources use positional placeholders`() {
        val violations =
            listOf(defaultCatalogFile(), germanCatalogFile()).flatMap { file ->
                val catalog = parseCatalog(file)
                catalog.allTexts().mapNotNull { (resourceName, text) ->
                    val placeholders = placeholders(text)
                    if (placeholders.any { it.explicitIndex == null }) {
                        "${file.parentFile?.name}/$resourceName: $text"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "Format resources require positional placeholders:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `format placeholders use matching XLIFF wrappers in every catalog`() {
        val sourceWrappers = placeholderWrappers(defaultCatalogFile())
        val germanWrappers = placeholderWrappers(germanCatalogFile())
        val violations = mutableListOf<String>()

        (sourceWrappers.keys + germanWrappers.keys).sorted().forEach { resourceName ->
            val source = sourceWrappers[resourceName]
            val german = germanWrappers[resourceName]
            if (source == null) {
                violations += "Unexpected German XLIFF resource: $resourceName"
            } else if (german == null) {
                violations += "Missing German XLIFF resource: $resourceName"
            } else if (source != german) {
                violations +=
                    "XLIFF wrapper mismatch: $resourceName source=$source translation=$german"
            }
        }

        assertTrue(
            "Format placeholders must have stable matching XLIFF wrappers:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `packaged locale allowlist and debug pseudo locales remain enabled`() {
        val buildScript = repositoryRoot.resolve("app/build.gradle.kts").readText()

        assertTrue(
            "Packaged locales must be restricted to English and German",
            RESOURCE_CONFIGURATION_ALLOWLIST.containsMatchIn(buildScript)
        )
        assertTrue(
            "Debug pseudo locales must remain enabled",
            DEBUG_PSEUDO_LOCALES.containsMatchIn(buildScript)
        )
    }

    @Test
    fun `MissingTranslation lint remains active`() {
        val policyFiles =
            listOf(
                repositoryRoot.resolve("app/build.gradle.kts"),
                repositoryRoot.resolve("app/lint.xml"),
                repositoryRoot.resolve("lint.xml")
            ).filter(File::isFile) +
                repositoryRoot.resolve("app/src/main/res")
                    .walkTopDown()
                    .filter { file -> file.isFile && file.extension == "xml" }
                    .toList()
        val violations =
            policyFiles.filter { file ->
                MISSING_TRANSLATION_SUPPRESSION.containsMatchIn(file.readText())
            }

        assertTrue(
            "MissingTranslation must not be disabled or suppressed: " +
                violations.joinToString { file ->
                    file.relativeTo(repositoryRoot).invariantSeparatorsPath
                },
            violations.isEmpty()
        )
    }

    @Test
    fun `catalog comparison reports missing and extra resources`() {
        val source = catalogFromXml("<resources><string name=\"alpha\">Alpha</string></resources>")
        val translation =
            catalogFromXml("<resources><string name=\"beta\">Beta</string></resources>")

        val violations = compareCatalogs(source, translation)

        assertTrue(violations.any { violation -> "Missing string: alpha" == violation })
        assertTrue(violations.any { violation -> "Unexpected string: beta" == violation })
    }

    @Test
    fun `content comparison reports placeholder plural and array mismatches`() {
        val source =
            catalogFromXml(
                """
                <resources>
                    <string name="message">Loaded %1${'$'}d of %2${'$'}d</string>
                    <plurals name="episodes">
                        <item quantity="one">%d episode</item>
                        <item quantity="other">%d episodes</item>
                    </plurals>
                    <string-array name="units"><item>B</item><item>KB</item></string-array>
                </resources>
                """.trimIndent()
            )
        val translation =
            catalogFromXml(
                """
                <resources>
                    <string name="message">Geladen: %1${'$'}s</string>
                    <plurals name="episodes">
                        <item quantity="other">%s Episoden</item>
                    </plurals>
                    <string-array name="units"><item>B</item></string-array>
                </resources>
                """.trimIndent()
            )

        val violations = compareResourceContents(source, translation)

        assertTrue(violations.any { violation -> violation.startsWith("Placeholder mismatch: message") })
        assertTrue(violations.any { violation -> violation.startsWith("Plural quantities mismatch: episodes") })
        assertTrue(violations.any { violation -> violation.startsWith("Array length mismatch: units") })
    }

    @Test
    fun `non-positional placeholder fixture is rejected`() {
        assertTrue(
            placeholders("Loaded %d episodes").any { placeholder ->
                placeholder.explicitIndex == null
            }
        )
        assertTrue(
            placeholders("Loaded %1${'$'}d episodes").all { placeholder ->
                placeholder.explicitIndex != null
            }
        )
    }

    @Test
    fun `locale directory detector rejects unsupported locales but ignores API qualifiers`() {
        assertTrue(isLocalizedValuesDirectory("values-fr"))
        assertTrue(isLocalizedValuesDirectory("values-b+zh+Hans"))
        assertFalse(isLocalizedValuesDirectory("values-v29"))
        assertFalse(isLocalizedValuesDirectory("values-night"))
    }

    private fun compareCatalogs(
        source: ResourceCatalog,
        translation: ResourceCatalog
    ): List<String> {
        val violations = mutableListOf<String>()
        RESOURCE_TYPES.forEach { type ->
            val sourceNames = source.names(type, includeNonTranslatable = false)
            val translationNames = translation.names(type, includeNonTranslatable = true)
            (sourceNames - translationNames).sorted().forEach { name ->
                violations += "Missing $type: $name"
            }
            (translationNames - sourceNames).sorted().forEach { name ->
                violations += "Unexpected $type: $name"
            }
        }
        val sourceTypes = source.typeByTranslatableName()
        val translationTypes = translation.typeByName()
        sourceTypes.keys.intersect(translationTypes.keys).sorted().forEach { name ->
            if (sourceTypes.getValue(name) != translationTypes.getValue(name)) {
                violations +=
                    "Resource type mismatch: $name is ${sourceTypes.getValue(name)} in source " +
                    "and ${translationTypes.getValue(name)} in translation"
            }
        }
        return violations
    }

    @Suppress("NestedBlockDepth")
    private fun compareResourceContents(
        source: ResourceCatalog,
        translation: ResourceCatalog
    ): List<String> {
        val violations = mutableListOf<String>()
        source.strings.filterValues(ResourceString::translatable).forEach { (name, sourceValue) ->
            translation.strings[name]?.let { translatedValue ->
                val sourceSignature = placeholderSignature(sourceValue.text)
                val translatedSignature = placeholderSignature(translatedValue.text)
                if (sourceSignature != translatedSignature) {
                    violations +=
                        "Placeholder mismatch: $name source=$sourceSignature " +
                        "translation=$translatedSignature"
                }
            }
        }
        source.plurals.forEach { (name, sourceItems) ->
            translation.plurals[name]?.let { translatedItems ->
                if (sourceItems.keys != translatedItems.keys) {
                    violations +=
                        "Plural quantities mismatch: $name source=${sourceItems.keys} " +
                        "translation=${translatedItems.keys}"
                }
                sourceItems.keys.intersect(translatedItems.keys).forEach { quantity ->
                    val sourceSignature = placeholderSignature(sourceItems.getValue(quantity))
                    val translatedSignature = placeholderSignature(translatedItems.getValue(quantity))
                    if (sourceSignature != translatedSignature) {
                        violations +=
                            "Plural placeholder mismatch: $name[$quantity] source=$sourceSignature " +
                            "translation=$translatedSignature"
                    }
                }
            }
        }
        source.arrays.forEach { (name, sourceItems) ->
            translation.arrays[name]?.let { translatedItems ->
                if (sourceItems.size != translatedItems.size) {
                    violations +=
                        "Array length mismatch: $name source=${sourceItems.size} " +
                        "translation=${translatedItems.size}"
                }
                sourceItems.zip(translatedItems).forEachIndexed { index, (sourceItem, translatedItem) ->
                    val sourceSignature = placeholderSignature(sourceItem)
                    val translatedSignature = placeholderSignature(translatedItem)
                    if (sourceSignature != translatedSignature) {
                        violations +=
                            "Array placeholder mismatch: $name[$index] source=$sourceSignature " +
                            "translation=$translatedSignature"
                    }
                }
            }
        }
        return violations
    }

    private fun parseCatalog(file: File): ResourceCatalog {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        val strings = linkedMapOf<String, ResourceString>()
        val plurals = linkedMapOf<String, Map<String, String>>()
        val arrays = linkedMapOf<String, List<String>>()
        for (index in 0 until root.childNodes.length) {
            val element = root.childNodes.item(index) as? Element ?: continue
            val name = element.getAttribute("name")
            when (element.tagName) {
                "string" -> {
                    strings[name] =
                        ResourceString(
                            text = element.textContent,
                            translatable = element.getAttribute("translatable") != "false"
                        )
                }
                "plurals" -> {
                    plurals[name] =
                        childElements(element, "item").associate { item ->
                            item.getAttribute("quantity") to item.textContent
                        }
                }
                "string-array" -> {
                    arrays[name] = childElements(element, "item").map(Element::getTextContent)
                }
            }
        }
        return ResourceCatalog(strings, plurals, arrays)
    }

    private fun placeholderWrappers(file: File): Map<String, Map<String, List<String>>> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        val values = linkedMapOf<String, Element>()
        for (index in 0 until root.childNodes.length) {
            val element = root.childNodes.item(index) as? Element ?: continue
            val name = element.getAttribute("name")
            when (element.tagName) {
                "string" -> values[name] = element
                "plurals" -> childElements(element, "item").forEach { item ->
                    values["$name[${item.getAttribute("quantity")}]"] = item
                }
                "string-array" -> childElements(element, "item").forEachIndexed { itemIndex, item ->
                    values["$name[$itemIndex]"] = item
                }
            }
        }
        return values.mapNotNull { (resourceName, element) ->
            val resourcePlaceholders = placeholderSignature(element.textContent)
            if (resourcePlaceholders.isEmpty()) return@mapNotNull null
            val wrapperElements = element.getElementsByTagNameNS(XLIFF_NAMESPACE, "g")
            val wrappers =
                (0 until wrapperElements.length).map { wrapperIndex ->
                    wrapperElements.item(wrapperIndex) as Element
                }
            val wrappedPlaceholders = wrappers.flatMap { wrapper ->
                placeholderSignature(wrapper.textContent)
            }.sorted()
            check(resourcePlaceholders == wrappedPlaceholders) {
                "$resourceName has a format placeholder outside an XLIFF wrapper"
            }
            val ids = wrappers.map { wrapper -> wrapper.getAttribute("id") }
            check(ids.all(String::isNotBlank) && ids.distinct().size == ids.size) {
                "$resourceName has missing or duplicate XLIFF wrapper IDs"
            }
            resourceName to wrappers.associate { wrapper ->
                wrapper.getAttribute("id") to placeholderSignature(wrapper.textContent)
            }
        }.toMap()
    }

    private fun childElements(
        parent: Element,
        tagName: String
    ): List<Element> =
        (0 until parent.childNodes.length).mapNotNull { index ->
            (parent.childNodes.item(index) as? Element)?.takeIf { element ->
                element.tagName == tagName
            }
        }

    private fun placeholderSignature(text: String): List<String> {
        var implicitIndex = 1
        return placeholders(text).map { placeholder ->
            val index = placeholder.explicitIndex ?: implicitIndex++
            "$index:${placeholder.type}"
        }.sorted()
    }

    private fun placeholders(text: String): List<FormatPlaceholder> =
        FORMAT_PLACEHOLDER.findAll(text).mapNotNull { match ->
            val conversion = match.groupValues[3]
            if (conversion == "%") {
                null
            } else {
                FormatPlaceholder(
                    explicitIndex = match.groupValues[1].takeIf(String::isNotEmpty)?.toInt(),
                    type = match.groupValues[2] + conversion
                )
            }
        }.toList()

    private fun catalogFromXml(xml: String): ResourceCatalog {
        val file = temporaryFolder.newFile("catalog-${temporaryFolder.root.listFiles().orEmpty().size}.xml")
        file.writeText(xml)
        return parseCatalog(file)
    }

    private fun defaultCatalogFile(): File =
        repositoryRoot.resolve("app/src/main/res/values/strings.xml")

    private fun germanCatalogFile(): File =
        repositoryRoot.resolve("app/src/main/res/values-de/strings.xml")

    private fun isLocalizedValuesDirectory(name: String): Boolean {
        if (!name.startsWith("values-")) return false
        return name.removePrefix("values-").split('-').any { qualifier ->
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
        val APPROVED_LOCALE_DIRECTORIES = listOf("values-de")
        val RESOURCE_TYPES = listOf("string", "plurals", "string-array")
        val RESOURCE_CONFIGURATION_ALLOWLIST =
            Regex("resourceConfigurations\\s*\\+=\\s*listOf\\(\\s*\"en\"\\s*,\\s*\"de\"\\s*\\)")
        val DEBUG_PSEUDO_LOCALES =
            Regex(
                "getByName\\(\"debug\"\\)\\s*\\{[^}]*isPseudoLocalesEnabled\\s*=\\s*true",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
        val MISSING_TRANSLATION_SUPPRESSION =
            Regex(
                "(?:disable|ignore|suppress)[^\\n<]*MissingTranslation|" +
                    "tools:ignore=\"[^\"]*MissingTranslation",
                RegexOption.IGNORE_CASE
            )
        val FORMAT_PLACEHOLDER =
            Regex("%(?:(\\d+)\\$)?[-#+ 0,(<]*(?:\\d+)?(?:\\.\\d+)?([tT]?)([a-zA-Z%])")
        const val XLIFF_NAMESPACE = "urn:oasis:names:tc:xliff:document:1.2"
    }
}

private data class ResourceCatalog(
    val strings: Map<String, ResourceString>,
    val plurals: Map<String, Map<String, String>>,
    val arrays: Map<String, List<String>>
) {
    fun names(
        type: String,
        includeNonTranslatable: Boolean
    ): Set<String> =
        when (type) {
            "string" ->
                strings.filterValues { value -> includeNonTranslatable || value.translatable }.keys
            "plurals" -> plurals.keys
            "string-array" -> arrays.keys
            else -> error("Unsupported resource type: $type")
        }

    fun typeByTranslatableName(): Map<String, String> =
        buildMap {
            strings.filterValues(ResourceString::translatable).keys.forEach { name -> put(name, "string") }
            plurals.keys.forEach { name -> put(name, "plurals") }
            arrays.keys.forEach { name -> put(name, "string-array") }
        }

    fun typeByName(): Map<String, String> =
        buildMap {
            strings.keys.forEach { name -> put(name, "string") }
            plurals.keys.forEach { name -> put(name, "plurals") }
            arrays.keys.forEach { name -> put(name, "string-array") }
        }

    fun allTexts(): List<Pair<String, String>> =
        strings.filterValues(ResourceString::translatable).map { (name, value) -> name to value.text } +
            plurals.flatMap { (name, items) ->
                items.map { (quantity, text) -> "$name[$quantity]" to text }
            } +
            arrays.flatMap { (name, items) ->
                items.mapIndexed { index, text -> "$name[$index]" to text }
            }
}

private data class ResourceString(
    val text: String,
    val translatable: Boolean
)

private data class FormatPlaceholder(
    val explicitIndex: Int?,
    val type: String
)
