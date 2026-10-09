# CHANGELOG

## Fase 0 — Ciclo de compilación, publicación y actualización
- Proyecto Android (teléfono y Google TV, un solo APK) con pantalla de versión y botón «Buscar actualizaciones».
- Autoactualización desde los Releases de GitHub: ETag, SHA-256, misma firma, PackageInstaller, aviso «Actualizada a la versión X», comprobación diaria y canal de pruebas.
- Núcleo en Kotlin puro con las reglas de la sección 4 validadas con los fixtures (sección 11.2).
- GitHub Actions: compilación, pruebas y lint en cada push; firma con zipalign/apksigner y Release con `app-release.apk`, `update.json` y `SHA256SUMS`.
