plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// Ejecuta también las pruebas del núcleo con `./gradlew test`.
tasks.register("pruebasNucleo") {
    group = "verification"
    dependsOn(gradle.includedBuild("core").task(":test"))
}
