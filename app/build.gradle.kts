import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.artifacts.VersionCatalogsExtension
import java.security.MessageDigest
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = the<LibrariesForLibs>()
val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val appVersionCode = providers.gradleProperty("appVersionCode").get().toInt().also {
    require(it > 0) { "appVersionCode must be positive" }
}
val appVersionName = providers.gradleProperty("appVersionName").get().also {
    require(it.isNotBlank()) { "appVersionName must not be blank" }
}
val instrumentationBuildType =
    providers.gradleProperty("instrumentationBuildType").orElse("debug").get().also {
        require(it == "debug" || it == "releaseSmoke") {
            "instrumentationBuildType must be debug or releaseSmoke"
        }
    }
val releaseSigningStoreFile = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE")
val releaseSigningStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD")
val releaseSigningKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS")
val releaseSigningKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD")
val releaseSigningValues =
    listOf(
        releaseSigningStoreFile,
        releaseSigningStorePassword,
        releaseSigningKeyAlias,
        releaseSigningKeyPassword
    )
val hasReleaseSigning = releaseSigningValues.all { it.isPresent }

check(releaseSigningValues.none { it.isPresent } || hasReleaseSigning) {
    "Release signing is only partially configured. Set all ANDROID_SIGNING_* variables."
}

android {
    namespace = "com.example.pocastcloni"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.pocastcloni"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("boolean", "BENCHMARK_BUILD", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        resourceConfigurations += listOf("en", "de")
    }
    testBuildType = instrumentationBuildType

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseSigningStoreFile.get())
                storePassword = releaseSigningStorePassword.get()
                keyAlias = releaseSigningKeyAlias.get()
                keyPassword = releaseSigningKeyPassword.get()
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("releaseSmoke") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            proguardFiles("benchmark-proguard-rules.pro")
            buildConfigField("boolean", "BENCHMARK_BUILD", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libsCatalog.findVersion("compose-compiler").get().requiredVersion
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("hilt.enableAggregatingTask", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("detekt.yml"))
    baseline = file("detekt-baseline.xml")
}

dependencies {
    // Core & Lifecycle

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Jetpack Compose BOM
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation(libsCatalog.findLibrary("androidx-navigation-navigation-compose").get())

    // Kotlinx Immutable Collections
    implementation(libs.kotlinx.collections.immutable)

    // Logging
    implementation(libs.timber)

    // HILT DEPENDENCIES (Core, Navigation & WorkManager)
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    implementation(libsCatalog.findLibrary("androidx-hilt-navigation-compose").get())
    implementation(libsCatalog.findLibrary("androidx-hilt-hilt-work").get())
    ksp(libsCatalog.findLibrary("androidx-hilt-hilt-compiler").get())

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Paging 3 (required for Repository & DAO)
    implementation(libsCatalog.findLibrary("androidx-paging-paging-runtime-ktx").get())
    implementation(libsCatalog.findLibrary("androidx-paging-paging-compose").get())
    implementation(libsCatalog.findLibrary("androidx-room-room-paging").get())

    // Network (Retrofit & OkHttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.jackson)
    implementation(libs.okhttp)
    implementation(libsCatalog.findLibrary("okhttp-logging-interceptor").get())

    // Jackson JSON support for backups and settings
    implementation(libs.jackson.module.kotlin)

    // Coil (Image Loading)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Media3 (Audio Player)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.androidx.media)
    implementation(libs.media3.database)
    implementation(libs.media3.datasource.okhttp)

    // Guava Support
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.coroutines.android)

    // DataStore (Settings)
    implementation(libs.androidx.datastore.preferences)

    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    add("benchmarkImplementation", platform(libs.compose.bom))
    add("benchmarkImplementation", "androidx.compose.runtime:runtime-tracing:1.0.0-beta01")
    add("benchmarkImplementation", "androidx.tracing:tracing:1.2.0")

    // WorkManager (Downloads)
    implementation(libsCatalog.findLibrary("androidx-work-runtime-ktx").get())

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation(libs.mockwebserver)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation(libs.leakcanary)
}

val releaseManifestFile =
    layout.buildDirectory.file(
        "intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
    )

tasks.register("verifyReleaseManifest") {
    group = "verification"
    description = "Verifies the merged release manifest against the exported-component allowlist."
    dependsOn("processReleaseManifest")
    inputs.file(releaseManifestFile)

    doLast {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val manifest = releaseManifestFile.get().asFile
        check(manifest.isFile) { "Merged release manifest is missing: $manifest" }
        val document = factory.newDocumentBuilder().parse(manifest)
        val actual = linkedMapOf<String, String>()
        val nodes = document.getElementsByTagName("*")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? org.w3c.dom.Element ?: continue
            if (element.getAttributeNS(androidNamespace, "exported") == "true") {
                val componentName = element.getAttributeNS(androidNamespace, "name")
                val permission = element.getAttributeNS(androidNamespace, "permission")
                actual["${element.tagName}:$componentName"] = permission
            }
        }

        val expected = linkedMapOf(
            "activity:com.example.pocastcloni.ui.main.MainActivity" to "",
            "service:com.example.pocastcloni.service.PodcastPlaybackService" to "",
            "receiver:androidx.media.session.MediaButtonReceiver" to "",
            "service:androidx.work.impl.background.systemjob.SystemJobService" to
                "android.permission.BIND_JOB_SERVICE",
            "receiver:androidx.work.impl.diagnostics.DiagnosticsReceiver" to
                "android.permission.DUMP",
            "receiver:androidx.profileinstaller.ProfileInstallReceiver" to
                "android.permission.DUMP"
        )
        check(actual == expected) {
            "Unexpected exported release components. Expected=$expected, actual=$actual"
        }
    }
}

val releaseArtifactReport =
    layout.buildDirectory.file("reports/release-gate/release-artifacts.properties")
val releaseApkDirectory = layout.buildDirectory.dir("outputs/apk/release")
val releaseMappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")

tasks.register("reportReleaseArtifacts") {
    group = "verification"
    description = "Records the release APK hash/size and R8 mapping path/size."
    dependsOn("assembleRelease")
    inputs.dir(releaseApkDirectory)
    inputs.file(releaseMappingFile)
    outputs.file(releaseArtifactReport)

    doLast {
        val apkDirectory = releaseApkDirectory.get().asFile
        val apks = apkDirectory.listFiles { file -> file.isFile && file.extension == "apk" }.orEmpty()
        check(apks.size == 1) { "Expected exactly one release APK in $apkDirectory, found ${apks.size}" }
        val apk = apks.single()
        val mapping = releaseMappingFile.get().asFile
        check(mapping.isFile) { "R8 mapping is missing: $mapping" }

        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        val report = releaseArtifactReport.get().asFile
        report.parentFile.mkdirs()
        report.writeText(
            buildString {
                appendLine("variant=release")
                appendLine("versionCode=$appVersionCode")
                appendLine("versionName=$appVersionName")
                appendLine("apk=${apk.relativeTo(projectDir).invariantSeparatorsPath}")
                appendLine("apkBytes=${apk.length()}")
                appendLine("apkSha256=$sha256")
                appendLine("mapping=${mapping.relativeTo(projectDir).invariantSeparatorsPath}")
                appendLine("mappingBytes=${mapping.length()}")
            }
        )
        logger.lifecycle("Release artifact report: $report")
    }
}

tasks.register("releaseGate") {
    group = "verification"
    description = "Runs unit tests, release lint, manifest verification, assembly and artifact reporting."
    dependsOn("testDebugUnitTest", "lintRelease", "verifyReleaseManifest", "reportReleaseArtifacts")
}
