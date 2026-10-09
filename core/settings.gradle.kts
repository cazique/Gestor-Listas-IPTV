// El núcleo es una compilación independiente (Kotlin puro, sin Android):
// se puede probar con `gradle -p core test` sin Android SDK.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "core"
