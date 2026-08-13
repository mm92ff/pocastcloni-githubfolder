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

        assertEquals("36900", properties.getProperty("appVersionCode"))
        assertEquals("3.69-beta", properties.getProperty("appVersionName"))
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

    @Test
    fun releaseSmokeInheritsReleaseAndOnlyOverridesSigning() {
        val appBuild = rootFile("app/build.gradle.kts").readText()
        val releaseSmokeBlock =
            appBuild.substringAfter("create(\"releaseSmoke\")")
                .substringBefore("create(\"benchmark\")")

        assertTrue(releaseSmokeBlock.contains("initWith(getByName(\"release\"))"))
        assertTrue(releaseSmokeBlock.contains("signingConfigs.getByName(\"debug\")"))
        assertFalse(releaseSmokeBlock.contains("proguardFiles"))
        assertFalse(releaseSmokeBlock.contains("buildConfigField"))
        assertFalse(releaseSmokeBlock.contains("isMinifyEnabled"))
        assertFalse(releaseSmokeBlock.contains("isShrinkResources"))
        assertTrue(appBuild.contains("instrumentationBuildType"))
        assertTrue(appBuild.contains("testBuildType = instrumentationBuildType"))
        assertTrue(appBuild.contains("androidx.test.uiautomator:uiautomator:2.3.0"))
    }

    @Test
    fun shrinkingRulesAreLimitedToRealReflectedModels() {
        val rules = rootFile("app/proguard-rules.pro").readText()

        assertFalse(rules.contains("com.example.pocastcloni.**"))
        assertFalse(rules.contains("com.fasterxml.jackson.**"))
        assertFalse(rules.contains("JacksonXml"))
        assertFalse(rules.contains("RssFeed"))
        assertTrue(rules.contains("data.remote.ItunesResponse"))
        assertTrue(rules.contains("data.local.BackupData"))
        assertTrue(rules.contains("data.worker.DownloadResumeMetadata"))
        assertTrue(rules.contains("domain.repository.UserSettings"))
        assertFalse(rules.contains("JsonMapperFactory"))

        val mapperFactory =
            rootFile(
                "app/src/main/java/com/example/pocastcloni/data/serialization/JsonMapperFactory.kt"
            ).readText()
        assertTrue(mapperFactory.contains("fun create(): ObjectMapper"))
        assertFalse(mapperFactory.contains("fun readBackup"))
        assertFalse(mapperFactory.contains("fun readSearch"))
    }

    @Test
    fun releaseGatesAndAuthenticSchemasAreVersioned() {
        val appBuild = rootFile("app/build.gradle.kts").readText()
        assertTrue(appBuild.contains("verifyReleaseManifest"))
        assertTrue(appBuild.contains("reportReleaseArtifacts"))
        assertTrue(appBuild.contains("releaseGate"))
        assertTrue(appBuild.contains("inputs.dir(releaseApkDirectory)"))
        assertTrue(appBuild.contains("inputs.file(releaseMappingFile)"))

        val schemaDirectory =
            rootFile("app/schemas/com.example.pocastcloni.data.local.AppDatabase")
        assertEquals(
            (10..17).map { "$it.json" },
            schemaDirectory.listFiles().orEmpty().map { it.name }.sortedBy { it.removeSuffix(".json").toInt() }
        )
        val migrations =
            rootFile(
                "app/src/main/java/com/example/pocastcloni/data/local/AppDatabaseMigrations.kt"
            ).readText()
        assertTrue(migrations.contains("SUPPORTED_SCHEMA_FLOOR = 10"))
    }

    @Test
    fun everyVerifiedDependencyArtifactHasASha256Checksum() {
        val verification = rootFile("gradle/verification-metadata.xml").readText()
        val artifactCount = Regex("<artifact\\s+name=").findAll(verification).count()
        val sha256Count = Regex("<sha256\\s+value=").findAll(verification).count()

        assertTrue(artifactCount > 0)
        assertTrue(sha256Count >= artifactCount)
    }

    private fun rootFile(relativePath: String): File = File("..", relativePath).canonicalFile
}
