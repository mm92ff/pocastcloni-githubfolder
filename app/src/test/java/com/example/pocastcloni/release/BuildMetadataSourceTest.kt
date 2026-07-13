package com.example.pocastcloni.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

class BuildMetadataSourceTest {
    @Test
    fun applicationVersionComesFromCentralGradleProperties() {
        val properties = Properties().apply {
            rootFile("gradle.properties").inputStream().use(::load)
        }
        val appBuild = rootFile("app/build.gradle.kts").readText()

        assertEquals("36100", properties.getProperty("appVersionCode"))
        assertEquals("3.61-dev", properties.getProperty("appVersionName"))
        assertTrue(appBuild.contains("providers.gradleProperty(\"appVersionCode\")"))
        assertTrue(appBuild.contains("providers.gradleProperty(\"appVersionName\")"))
        assertFalse(appBuild.contains("versionCode = 1\n"))
        assertFalse(appBuild.contains("versionName = \"1.0\""))
    }

    @Test
    fun gradleDistributionIsPinnedToItsChecksum() {
        val wrapperProperties = Properties().apply {
            rootFile("gradle/wrapper/gradle-wrapper.properties").inputStream().use(::load)
        }

        assertEquals(
            "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78",
            wrapperProperties.getProperty("distributionSha256Sum")
        )
    }

    private fun rootFile(relativePath: String): File = File("..", relativePath).canonicalFile
}
