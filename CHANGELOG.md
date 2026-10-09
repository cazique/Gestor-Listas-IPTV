# CHANGELOG

## 0.4.19 — Diseño y verificación
- Nuevo diseño: paleta azul noche y ámbar, iconos propios en la navegación, tarjetas con título, cifras grandes en el resumen, etiquetas de capa y estado, estados vacíos y foco más visible en la TV.
- Arreglo del cierre en Android 16 (expresiones regulares compatibles con ICU) y registro de fallos con opción de compartir.
- CI: arranque en emuladores de móvil y Android TV, prueba instrumentada del motor con los fixtures, capturas adjuntas a cada Release; no se publica nada que no arranque.

## Fase 4 — VOD (gestión, sin reproducir)
- Películas (`get_vod_streams`) y series (`get_series`, `get_series_info`), guardadas en archivos privados de la app.
- Filtro por categorías (por defecto, las que empiezan por «ES»), duplicados por `tmdb` y, si falta, por título normalizado y año.
- Adultos ocultos salvo desbloqueo con PIN; orden por valoración, fecha de alta o título.
- M3U de películas independiente del de directos y M3U de episodios por serie; ocultar películas con el mismo sistema de decisiones.

## Fase 3 — Salida y utilidades
- Servidor local opcional (Ktor CIO, servicio en primer plano `specialUse`): `/lista.m3u`, `/lista_<paquete>.m3u`, `/informe.txt`; solo 127.0.0.1 por defecto y ruta secreta en red local.
- Subida a servidor propio por HTTP PUT/WebDAV (manual o tras cada sincronización), con credenciales cifradas.
- Comprobación de enlaces de uno en uno respetando `active_cons`, con tasa de bits medida, opción de ordenar capas por tasa y de ocultar las variantes que fallan.
- Bloqueo parental con PIN (PBKDF2 con sal), activado por defecto para el contenido para adultos.
- Esquema 2 de la base de datos con migración 1→2 y prueba.

## Fase 2 — Asignación y revisión
- Bandeja «Por revisar» (sin guía, dudosos, posibles duplicados, nuevos y desaparecidos) con sugerencias, modo en tanda y atajos del mando.
- Todas las acciones manuales de la ficha (unir, separar, guía, icono, paquete, preferida, ocultar), historial con deshacer y rehacer, exportación e importación (JSON y `alias_epg.txt`).
- (Entregada junto con la Fase 1 en el mismo commit.)

## Fase 1 — Núcleo gestor (directos)
- Cuenta cifrada, HTTPS primero, sincronización con informe de cambios, guía de dobleM, motor de capas, vista previa, exportación M3U validada.

## Fase 0 — Ciclo de compilación, publicación y actualización
- Proyecto Android (teléfono y Google TV, un solo APK) con pantalla de versión y botón «Buscar actualizaciones».
- Autoactualización desde los Releases de GitHub: ETag, SHA-256, misma firma, PackageInstaller, aviso «Actualizada a la versión X», comprobación diaria y canal de pruebas.
- Núcleo en Kotlin puro con las reglas de la sección 4 validadas con los fixtures (sección 11.2).
- GitHub Actions: compilación, pruebas y lint en cada push; firma con zipalign/apksigner y Release con `app-release.apk`, `update.json` y `SHA256SUMS`.
