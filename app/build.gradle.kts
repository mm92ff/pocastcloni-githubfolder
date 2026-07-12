import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.artifacts.VersionCatalogsExtension

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

android {
    namespace = "com.example.pocastcloni"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.pocastcloni"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("boolean", "BENCHMARK_BUILD", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    composeOptions {
        kotlinCompilerExtensionVersion = libsCatalog.findVersion("compose-compiler").get().requiredVersion
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("hilt.enableAggregatingTask", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core & Lifecycle

    implementation(libs.androidx.core.ktx)
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

    // Jackson XML Parsing Libraries
    implementation(libs.stax)
    implementation(libs.jackson.dataformat.xml)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.woodstox.core)

    // Coil (Image Loading)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Media3 (Audio Player)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.androidx.media)
    implementation(libs.media3.database)

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
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(libs.mockwebserver)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation(libs.leakcanary)
}
