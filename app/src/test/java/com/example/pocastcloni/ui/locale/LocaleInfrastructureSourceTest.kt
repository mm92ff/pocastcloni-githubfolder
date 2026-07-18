package com.example.pocastcloni.ui.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

class LocaleInfrastructureSourceTest {
    @Test
    fun buildConfigurationGeneratesOnlyApprovedApplicationLocales() {
        val versionCatalog = rootFile("gradle/libs.versions.toml").readText()
        val appBuild = rootFile("app/build.gradle.kts").readText()
        val resourceProperties = Properties().apply {
            rootFile("app/src/main/res/resources.properties").inputStream().use(::load)
        }

        assertTrue(versionCatalog.contains("androidx-appcompat = \"1.7.1\""))
        assertTrue(versionCatalog.contains("name = \"appcompat\""))
        assertTrue(appBuild.contains("implementation(libs.androidx.appcompat)"))
        assertTrue(appBuild.contains("generateLocaleConfig = true"))
        assertTrue(appBuild.contains("resourceConfigurations += listOf(\"en\", \"de\")"))
        assertEquals("en-US", resourceProperties.getProperty("unqualifiedResLocale"))
    }

    @Test
    fun manifestEnablesOnlyAppCompatLocaleStorage() {
        val manifest = parseXml(rootFile("app/src/main/AndroidManifest.xml"))
        val services = manifest.getElementsByTagName("service")
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val localeStorageService = (0 until services.length)
            .map { services.item(it) }
            .single { node ->
                node.attributes.getNamedItemNS(androidNamespace, "name").nodeValue ==
                    "androidx.appcompat.app.AppLocalesMetadataHolderService"
            }

        assertEquals(
            "false",
            localeStorageService.attributes.getNamedItemNS(androidNamespace, "enabled").nodeValue
        )
        assertEquals(
            "false",
            localeStorageService.attributes.getNamedItemNS(androidNamespace, "exported").nodeValue
        )
        val metadata = localeStorageService.childNodes
        val localeMetadata = (0 until metadata.length)
            .map { metadata.item(it) }
            .single { node ->
                node.nodeName == "meta-data" &&
                    node.attributes.getNamedItemNS(androidNamespace, "name").nodeValue == "autoStoreLocales"
            }
        assertEquals(
            "true",
            localeMetadata.attributes.getNamedItemNS(androidNamespace, "value").nodeValue
        )
    }

    @Test
    fun activityAndEveryPlatformThemeUseAppCompatWithoutCustomLocalePersistence() {
        val activity = rootFile("app/src/main/java/com/example/pocastcloni/ui/main/MainActivity.kt").readText()
        val controller = rootFile(
            "app/src/main/java/com/example/pocastcloni/ui/locale/AppLocaleController.kt"
        ).readText()
        val activityStartup = rootFile(
            "app/src/main/java/com/example/pocastcloni/ui/locale/AppLocaleStartupSynchronizer.kt"
        ).readText()

        assertTrue(activity.contains("class MainActivity : AppCompatActivity()"))
        listOf("values", "values-v27", "values-v29").forEach { directory ->
            val theme = rootFile("app/src/main/res/$directory/themes.xml").readText()
            assertTrue(theme.contains("parent=\"Theme.AppCompat.Light.NoActionBar\""))
            assertTrue(theme.contains("android:statusBarColor"))
            assertTrue(theme.contains("android:navigationBarColor"))
            assertTrue(theme.contains("android:windowLightStatusBar"))
            if (directory != "values") {
                assertTrue(theme.contains("android:windowLightNavigationBar"))
            }
            if (directory == "values-v29") {
                assertTrue(theme.contains("android:enforceNavigationBarContrast"))
            }
        }
        assertFalse(controller.contains("DataStore"))
        assertFalse(controller.contains("Room"))
        assertFalse(controller.contains("SharedPreferences"))
        assertTrue(controller.contains("LocaleListCompat.getEmptyLocaleList()"))
        assertTrue(activity.contains("awaitAppLocaleStartupAction(this@MainActivity)"))
        assertTrue(activityStartup.contains("AppLocalesMetadataHolderService::class.java"))
        assertFalse(activityStartup.contains("openFileInput"))
        assertFalse(activityStartup.contains("openFileOutput"))
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance().run {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        isExpandEntityReferences = false
        newDocumentBuilder().parse(file)
    }

    private fun rootFile(relativePath: String): File = File("..", relativePath).canonicalFile
}
