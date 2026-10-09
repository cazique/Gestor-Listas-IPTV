import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// versionCode y versionName los fija GitHub Actions (variables de entorno); en local, valores de desarrollo.
val fase: String = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }.getProperty("fase", "0")
val codigoVersion: Int = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 1
val nombreVersion: String = providers.environmentVariable("VERSION_NAME").orNull ?: "0.$fase.0-dev"
val repoActualizaciones: String = providers.environmentVariable("GITHUB_REPOSITORY").orNull ?: "cazique/Gestor-Listas-IPTV"

android {
    namespace = "es.cazique.iptvgestor"
    compileSdk = 37

    defaultConfig {
        // Identidad estable: NO cambiar nunca (ver DECISIONES.md).
        applicationId = "es.cazique.iptvgestor"
        minSdk = 26
        targetSdk = 37
        versionCode = codigoVersion
        versionName = nombreVersion
        buildConfigField("String", "REPO_ACTUALIZACIONES", "\"$repoActualizaciones\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Sin firma aquí: el APK de release se alinea y firma en GitHub Actions con zipalign y apksigner.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Las versiones están fijadas a propósito y comprobadas a mano (DECISIONES.md).
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
    }
}

// Esquemas de Room versionados en app/schemas (el plugin evita escrituras simultáneas debug/release).
room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation("es.cazique.iptvgestor:core:1.0") {
        exclude(group = "net.sf.kxml") // Android ya trae org.xmlpull
    }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.documentfile)
    implementation(libs.ktor.server.cio)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.room.testing)
}
