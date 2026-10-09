# Gestor IPTV

App Android (**un solo APK para móvil y Google TV**) que **gestiona y limpia** la lista de canales de un proveedor IPTV compatible con Xtream Codes: se queda con los canales en español, detecta los repetidos, los reparte en **capas por operadora** (M+, Vodafone, Orange), les pone **guía, orden e iconos** de [dobleM](https://github.com/davidmuma/EPG_dobleM) y **exporta un M3U** para verlo en **TiviMate Premium**. **No reproduce vídeo.**

- Especificación: [`SPEC.md`](SPEC.md) · Decisiones tomadas: [`DECISIONES.md`](DECISIONES.md) · Cambios: [`CHANGELOG.md`](CHANGELOG.md)
- Revisión y asignación manual: [`MANUAL_ASIGNACION.md`](MANUAL_ASIGNACION.md)

Ni el repositorio ni el APK contienen tus credenciales, las del proveedor ni la clave de firma.

---

## 1. Puesta en marcha (una sola vez)

### 1.1 Crear los cuatro secretos de firma

La app se firma siempre con la misma clave, que vive **solo** en los secretos de GitHub Actions. Desde el navegador del móvil:

1. En el repositorio: **Settings → Secrets and variables → Actions → New repository secret**.
2. Crea estos cuatro secretos, con los valores que te dio Claude Code en el chat (una sola vez):

| Nombre | Valor |
|---|---|
| `KEYSTORE_BASE64` | El almacén de claves codificado en base64 (un texto largo) |
| `KEYSTORE_PASSWORD` | La contraseña del almacén |
| `KEY_ALIAS` | `iptvgestor` |
| `KEY_PASSWORD` | La misma contraseña que `KEYSTORE_PASSWORD` (PKCS12 usa una sola) |

Guarda también esos valores **fuera del chat** (por ejemplo, en un gestor de contraseñas). Los secretos de GitHub no se pueden volver a leer.

### 1.2 Llevar el código a la rama principal (publica el primer Release)

El trabajo está en la rama `ccr-dc368e82-x0lki4`. Para publicar:

1. En la portada del repositorio pulsa **Compare & pull request** (o *Pull requests → New pull request*).
2. **Importante, porque el repositorio es un fork:** en *base repository* elige **tu** repositorio (`cazique/…`), no `poundifdef/rclone-web`. Base: `master`; compare: `ccr-dc368e82-x0lki4`.
3. **Create pull request** y después **Merge pull request**.
4. En **Actions → Android** verás la ejecución: el trabajo «Firmar y publicar Release» crea el Release `v0.1.N` con `app-release.apk`, `update.json` y `SHA256SUMS`.

Si falta algún secreto, ese trabajo termina con un aviso («Faltan los secretos de firma») y no publica nada.

### 1.3 Publicar otra versión a mano

**Actions → Android → Run workflow** (rama `master`). Cada ejecución tiene un número mayor, así que produce un `versionCode` mayor. Marca «preliminar» para publicarla solo en el canal de pruebas.

---

## 2. Tu primera prueba (la que importa)

1. En el móvil, abre la página de **Releases** del repositorio y descarga `app-release.apk`.
2. Android te pedirá permiso para **instalar apps desconocidas** desde el navegador: acéptalo. Si Play Protect avisa de una app no reconocida, elige **instalar igualmente**.
3. Abre la app: en **Ajustes → Versión y actualizaciones** verás la versión (por ejemplo, `0.1.4 (código 104)`). Escribe algo en «Nota de prueba» y pulsa **Aceptar**.
4. Publica una versión nueva (sección 1.3) y espera a que termine (unos 8 minutos).
5. En la app pulsa **Buscar actualizaciones** → **Descargar e instalar**. La primera vez te llevará a la pantalla de **Instalar apps desconocidas** para esta app: actívalo y vuelve. Android pedirá confirmar la actualización.
6. Al reabrir la app debe decir **«Actualizada a la versión X»**, y la nota y tus decisiones deben seguir ahí.

En **Google TV** es igual, pero el permiso se llama **Fuentes desconocidas** (Ajustes → Apps → Seguridad y restricciones) y todo se maneja con el mando.

---

## 3. Instalación por otras vías

- **Gestor de archivos (TV):** copia el APK a un USB o descárgalo con una app como *Downloader*, y ábrelo desde el gestor de archivos (necesita «Fuentes desconocidas» para esa app).
- **adb** (con depuración USB o por red activada):
  ```
  adb connect IP_DEL_DISPOSITIVO:5555      # solo por red
  adb install -r app-release.apk
  ```
- **APK de depuración:** en cada ejecución de Actions, el artefacto `apk-debug-e-informes` trae `app-debug.apk` (id `es.cazique.iptvgestor.debug`, se instala a la vez que la versión normal y **no** se autoactualiza).

---

## 4. Uso

1. **Ajustes → Cuenta:** servidor, usuario y contraseña → **Probar conexión y guardar**. Muestra el estado de la cuenta (activa, caducidad, conexiones). La app prueba primero **HTTPS**; si el servidor no lo ofrece te pide permiso para usar HTTP (los datos viajarían sin cifrar). Si el certificado HTTPS es inválido, **no** cambia a HTTP.
   Sin red: «Importar archivos» (`live.json` y el JSON de categorías).
2. **Resumen → Sincronizar ahora.** Descarga categorías y canales (solo por `player_api.php`, nunca `get.php`), la guía y calcula paquetes y capas. Después se sincroniza sola cada 6 horas (configurable).
3. **Lista:** la vista previa de lo que se exportará, en orden, con icono, ID de guía y capa. Toca un canal para abrir su ficha.
4. **Revisar:** lo que la app no sabe resolver (ver [`MANUAL_ASIGNACION.md`](MANUAL_ASIGNACION.md)).
5. **Exportar:** todo en un archivo, uno por paquete o uno por capa; guardar en una carpeta (y automáticamente tras cada sincronización) o compartir. **La lista lleva tu usuario y contraseña en cada dirección:** no la publiques.

### En TiviMate Premium

1. Añade la lista como **Lista M3U** (archivo o URL), **no** como Xtream Codes (Xtream no pasaría por la limpieza).
2. Añade la guía como fuente **XMLTV** (Ajustes → Guía → Fuentes de guía) con la dirección que muestra **Exportar → Copiar dirección de la guía**. Por defecto: `https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/guiatv_sincolor.xml.gz`.
3. **Activa esa fuente dentro de la lista** (en Premium hay que activarla por lista).
4. Lo que no se empareje por `tvg-id` solo se asigna en TiviMate a mano, canal a canal; por eso conviene resolverlo antes en «Revisar».
5. Si los canales no abren, fija el **User-Agent** `VLC/3.0.20` en la configuración de la lista dentro de TiviMate.
6. La cuenta permite **una sola conexión**: la vista múltiple o grabar mientras ves otro canal pueden necesitar más.

### Lista de prueba para TiviMate

[`tools/prueba_tivimate.m3u`](tools/prueba_tivimate.m3u) no lleva credenciales ni streams reales (apunta a `127.0.0.1:9`). Sirve para comprobar en tu Google TV, en pocos minutos, cómo trata TiviMate la cabecera `url-tvg`, los `tvg-id` repetidos, las mayúsculas, `tvg-chno` y el User-Agent. Los pasos están en [`docs/PREGUNTAS_IA_RESPONDIDAS.md`](docs/PREGUNTAS_IA_RESPONDIDAS.md), sección 5.

---

## 5. Si se pierde la clave de firma

Android solo instala una actualización si lleva **el mismo `applicationId` (`es.cazique.iptvgestor`) y la misma firma**. Si se perdiera el almacén de claves o su contraseña:
- ninguna versión nueva podrá instalarse encima de la actual;
- habría que crear una clave nueva, **desinstalar** la app (se pierden sus datos) e instalar la nueva;
- antes de desinstalar, exporta las **decisiones manuales** (Ajustes → Decisiones manuales → Exportar) para importarlas después.

Por eso hay que guardar el almacén y la contraseña fuera de GitHub.

## 6. Verificación de desarrolladores de Android

Consultado el 9-10-2026 en [developer.android.com/developer-verification](https://developer.android.com/developer-verification): desde el **30-9-2026** Android exige que las apps de desarrolladores verificados se instalen en dispositivos certificados de **Brasil, Indonesia, Singapur y Tailandia**, y se ampliará a todo el mundo en **2027**. Google mantiene un **«flujo avanzado»** para que los usuarios avanzados instalen apps de desarrolladores no verificados, y una cuenta gratuita de **distribución limitada** (sin documento de identidad) para estudiantes y aficionados, **hasta 20 dispositivos**. España no está en la primera fase. Como el `applicationId` y la clave son estables, la app se podrá registrar más adelante si hace falta.

## 7. Guía e iconos de dobleM

La guía y los iconos se descargan de la dirección remota configurable; **no van dentro del APK ni del repositorio**. No se ha encontrado una licencia explícita en `EPG_dobleM` ni en `picons_dobleM`: conviene **confirmar los permisos con su autor** antes de difundir la app.

## 8. Herramientas

- [`tools/capas_epg.py`](tools/capas_epg.py): implementación de referencia en Python (por si quieres seguir usándola en la DietPi).
- [`tools/comprobar_secretos.sh`](tools/comprobar_secretos.sh): busca claves, contraseñas y direcciones del proveedor en el árbol (se ejecuta en CI y como `pre-commit`).
- El núcleo (`core/`) se prueba sin Android SDK: `gradle -p core test`.

## 9. Compilar

```
./gradlew pruebasNucleo testDebugUnitTest lintDebug assembleDebug assembleRelease
```

JDK 17 o posterior y Android SDK con la plataforma 37. El APK de release sale sin firmar; GitHub Actions lo alinea y firma (`tools/ci_firmar.sh`).

---

## Verificación pendiente

Lo que **no** se ha podido comprobar desde el entorno de desarrollo (sin Android SDK local, sin emulador y sin acceso al proveedor). Todo lo demás está verificado por GitHub Actions (compilación debug y release, pruebas unitarias y de interfaz con Robolectric y lint).

1. **Firma y publicación del Release.** No se pudieron crear los secretos desde aquí. Pasos: sección 1.1 y 1.2. Comprueba en *Actions* que «Firmar y publicar Release» termina en verde y que el Release trae los tres archivos.
2. **Actualización de extremo a extremo** (instalar N, publicar N+1, actualizar desde la app conservando ajustes y decisiones). Pasos: sección 2.
3. **Pantalla «Instalar apps desconocidas» en tu Google TV**: si la app no puede abrirla directamente, abre la de Seguridad; comprueba que puedes activarla con el mando.
4. **Primera sincronización real** con tu proveedor (la API se ha probado con datos simulados y con los fixtures). Comprueba que las cifras del Resumen se parecen a las de referencia: M+ 201 canales (capas 201/80/57/29/15), Vodafone 140 (140/48/1), Orange 146 (146/2); con guía, M+ 136, Vodafone 87 y Orange 109.
5. **HTTPS del proveedor**: si tu servidor ofrece HTTPS en otro puerto, la app lo intenta tras la primera respuesta por HTTP (con tu permiso).
6. **TiviMate**: haz la prueba de `tools/prueba_tivimate.m3u`; después carga la lista completa y comprueba si **todas** las entradas con el mismo `tvg-id` (M+ 1, M+ 2…) muestran la guía. Si no, exporta «un archivo por capa» y carga los archivos como listas separadas.
7. **Selector de carpetas en Google TV** (`ACTION_OPEN_DOCUMENT_TREE`): si no existe, usa «Compartir». Prueba rápida: `adb shell am start -a android.intent.action.OPEN_DOCUMENT_TREE`.
8. **Capturas de pantalla** de móvil y TV: no se han podido generar sin emulador.
9. **Pruebas en emulador o dispositivo** de teléfono y de Android TV.
