import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "es.cazique.iptvgestor"
version = "1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    // Android ya incluye org.xmlpull; en la JVM se usa kxml2 solo para las pruebas.
    compileOnly(libs.xmlpull)
    testImplementation(libs.kxml2)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(kotlin("test"))
}

tasks.test {
    // Las pruebas leen ../fixtures y, si hay red, la guía de dobleM.
    systemProperty("fixtures.dir", rootDir.resolve("../fixtures").absolutePath)
    systemProperty("epg.cache", layout.buildDirectory.dir("epg").get().asFile.absolutePath)
    System.getenv("EPG_LOCAL")?.let { systemProperty("epg.local", it) }
    maxHeapSize = "2g"
    testLogging {
        events("failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
