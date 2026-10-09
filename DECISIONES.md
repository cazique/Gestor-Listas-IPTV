# DECISIONES.md

Registro de decisiones tomadas sin preguntar (regla 1 de `SPEC.md`). Formato: decisión · alternativas · motivo.

## Identidad, firma y versiones (no se cambian nunca)

| Decisión | Alternativas | Motivo |
|---|---|---|
| `applicationId = es.cazique.iptvgestor` | Derivarlo del nombre del repositorio | Estable e independiente del repositorio (se llamaba `rclone-web` y ahora `Gestor-Listas-IPTV`; GitHub redirige el nombre antiguo, así que las versiones ya instaladas siguen encontrando las actualizaciones). Android solo actualiza con el mismo id y la misma firma |
| La compilación `debug` usa `es.cazique.iptvgestor.debug` | Mismo id | Así un APK de depuración nunca choca con el de release instalado |
| Clave RSA 4096, PKCS12, alias `iptvgestor`, validez 10.950 días (30 años), creada una sola vez con `keytool` el 9-10-2026 | RSA 2048 | Android recomienda ≥ 2048 bits y ≥ 25 años. Solo vive en los secretos de GitHub Actions y en la copia que guarda Jorge |
| `versionCode = 100 + github.run_number` del flujo `Android` | Número de commits | Crece siempre, aunque se publique dos veces el mismo commit (ejecución manual) |
| `versionName = 0.<fase>.<run_number>` (fase en `version.properties`), etiqueta `v<versionName>` | Semver manual | Lo pide la sección 6.4.2 |
| `minSdk = 26` (Android 8.0) | 23–25 | API 26 trae iconos adaptativos, canales de notificación, `canRequestPackageInstalls()` y la pantalla "instalar apps desconocidas" por app; cubre todos los Google TV. Compose, Room y WorkManager admiten 23, pero no merece la pena el código extra para 2017 y antes |
| `compileSdk = targetSdk = 37` (Android 17) | 36 | Último nivel estable admitido por AGP 9.4.0 (consultado 9-10-2026). Si GitHub Actions no lo pudiera descargar, se baja a 36 y se anota |

## Versiones (comprobadas el 9-10-2026)

Fuentes: developer.android.com (AGP, tabla de AndroidX, BOM de Compose), Maven Central (`maven-metadata.xml` y fecha de publicación), services.gradle.org y las etiquetas de los repositorios de las acciones.

| Componente | Versión | Nota |
|---|---|---|
| Gradle | 9.8.0 (24-9-2026) | 9.8.1 salió el 7-10-2026; se evita una versión de dos días. AGP 9.4 exige ≥ 9.6.0 |
| Android Gradle Plugin | 9.4.0 (septiembre de 2026) | Kotlin integrado en AGP ("built-in Kotlin"): no se aplica `org.jetbrains.kotlin.android` |
| Kotlin (plugins Compose y serialización, núcleo JVM) | 2.4.20 (7-9-2026) | 2.4.21 salió el 8-10-2026: demasiado reciente |
| KSP | 2.3.12 (9-9-2026) | Para Room |
| Compose BOM | 2026.09.00 (ui 1.12.1, material3 1.4.0) | |
| Room 2.8.5 · WorkManager 2.12.0 · DataStore 1.2.1 · Activity 1.13.0 · Lifecycle 2.11.0 · Navigation 2.10.2 · Core 1.19.1 | Estables según la tabla de AndroidX (actualizada 7-10-2026) | |
| Compose for TV `tv-material` | 1.1.0 | Estable |
| kotlinx.serialization 1.11.0 · coroutines 1.11.0 · OkHttp 5.5.0 · Coil 3.6.3 · Ktor 3.6.0 | Maven Central | Ktor 3.6.0 coincide con lo indicado en SPEC |
| JDK en CI | Temurin 21 | AGP 9.4 necesita ≥ 17 |
| `actions/checkout` | v7.0.1 (`3d3c42e…`, 17-7-2026) | Fijada por commit |
| `actions/setup-java` | v6.0.1 (`de7274f…`, 9-9-2026) | |
| `gradle/actions/setup-gradle` | v6.4.0 (`3f5f9ad…`, 7-9-2026) | Valida también el `gradle-wrapper.jar` |
| `actions/upload-artifact` | v7.0.1 (`043fb46…`, 10-4-2026) | v7.0.2 es del 7-10-2026 (menos de dos semanas) |

La suma SHA-256 de la distribución de Gradle (`bafd5ce9…`) se calculó sobre el zip descargado de services.gradle.org por HTTPS; el entorno no deja leer el `.sha256` publicado (dominio bloqueado). Si no coincidiera, el wrapper fallaría al descargarla (no hay riesgo silencioso).

## Arquitectura

| Decisión | Alternativas | Motivo |
|---|---|---|
| Núcleo `core/` en Kotlin puro como compilación independiente (`includeBuild`) | Módulo Android | Se prueba sin Android SDK (`gradle -p core test`), incluso en este entorno sin SDK. Contiene reglas (sección 4), motor, guía, M3U, decisiones, revisión, cambios, cliente Xtream y lógica de actualización |
| Inyección manual (un contenedor en `IptvGestorApp`) | Hilt, Koin | El grafo es pequeño; evita procesadores de anotaciones y reflexión, y un fallo de compilación menos en CI. SPEC permite cambiar el stack si se justifica |
| XML de la guía con `XmlPullParser` aportado por quien llama | StAX, SAX | Android trae `org.xmlpull`; en la JVM las pruebas usan kxml2 (excluido del APK para no duplicar clases del sistema) |
| Release sin R8 (`isMinifyEnabled = false`) | Minificar | Menos riesgo con serialización y reflexión; el tamaño no es un problema en sideload |
| `difflib` portado exactamente (Ratcliff/Obershelp y `get_close_matches`, incluido el desempate por texto) | Levenshtein | Para reproducir las cifras de la sección 11.2 (136/87/109) |
| Expresiones regulares con `(?U)` | ASCII | En Python 3 `\w`, `\b` y `\d` son Unicode; en Java no por defecto |

## Reglas de negocio: diferencias con el script de referencia

| Decisión | Motivo |
|---|---|
| A igual calidad, las variantes `BK` (en el nombre o en el grupo) van después (`respaldoAlFinal`, desactivable) | Sección 4.3. El script de Python no lo hacía; no cambia las cifras por capa |
| Un grupo pertenece al **primer** paquete cuyas reglas cumple | Evita que un canal salga en dos paquetes. Con los fixtures no hay grupos en dos paquetes |
| `M+ LALIGA` tiene **8** variantes (7 del grupo `ES| M+ LALIGA VIP` y `M+: M.LALIGA ᴿᴬᵂ` de `ES| MOVISTAR SPORT ᴿᴬᵂ`), no 7 | Calculado con los fixtures; con 5 capas máximo salen 5 entradas. Las capas 201/80/57/29/15 coinciden |
| "Nombres exactos repetidos: 570 / 1.173" se reproduce comparando nombres **sin distinguir mayúsculas y con espacios colapsados**. Comparación literal: 552 / 1.137 | Investigado con los fixtures (prueba `nombresRepetidos`) |
| Identidad secundaria de una variante: nombre normalizado (sin quitar calidades) + grupo, además de la clave | La clave más el grupo no distingue las 7 variantes de un mismo canal en un grupo |
| Canales de grupos que no son de paquete: se agrupan por clave pero salen todas sus variantes en su grupo (sin capas, salvo el ajuste "capas también en el resto") | Sección 4.4 |
| Nombre mostrado: la clave en los paquetes (como el script), el nombre original en el resto | Igual que el script de referencia |
| Modo "una lista por paquete": si un `tvg-id` se repite dentro del archivo (capa 2, 3…), las repeticiones van sin `tvg-id` | Así ningún archivo repite `tvg-id` (sección 7.4); para tener guía en todas las capas, usar "una lista por capa" |
| El validador de M3U no rechaza U+FFFD | El proveedor lo usa en algunos nombres; el archivo sigue siendo UTF-8 válido. Sí rechaza sustitutos sueltos (no codificables) |

Cifras de la lista final completa con los fixtures y la guía del 9-10-2026: 2.907 entradas conservadas (57 grupos), 85 separadores descartados, **2.816 entradas en la lista final** en **51 grupos** (10 capas de paquetes + resto de grupos `ES|` + `FOR ADULTS`), 2.287 canales lógicos, 690 con guía. Las 6 entradas que faltan son variantes por encima de la capa 5.

## Guía (EPG)

- Comprobado el 9-10-2026: las seis variantes `guiatv_sincolor{,0,1,2,3,4}.xml.gz` tienen **los mismos 640 canales con los mismos IDs y en el mismo orden** (huella MD5 idéntica de la lista de `<channel id>`); 639 con icono.
- La guía no se guarda en el repositorio ni en el APK (licencia no declarada). Las pruebas la descargan; sin red, se omiten esas pruebas (no se dan por buenas).

## Autoactualización

| Decisión | Motivo |
|---|---|
| Repositorio de actualizaciones `cazique/Gestor-Listas-IPTV` (público, comprobado el 9-10-2026), fijado en `BuildConfig` desde `GITHUB_REPOSITORY` | Sin tokens en el APK |
| Dominios permitidos: `api.github.com`, `github.com`, `objects.githubusercontent.com`, `release-assets.githubusercontent.com`, solo HTTPS, también tras redirecciones | Los archivos de los Releases redirigen a `release-assets.githubusercontent.com` (antes `objects.githubusercontent.com`) |
| ETag e `If-None-Match` en la API; comprobación al abrir (≤ 1 vez cada 12 h), diaria con WorkManager y con el botón | Sección 6.4.4 |
| Verificación: `versionCode` mayor, `minSdk`, SHA-256 de `update.json`, nombre de paquete y certificados idénticos a los de la app instalada | Sección 6.4.4 |
| Instalación con `PackageInstaller` (sesión), confirmación abierta por la actividad visible | No hay instalación silenciosa para apps instaladas por sideload; `setRequireUserAction(NOT_REQUIRED)` solo vale si la app es la instaladora de registro (no es el caso: la instala el navegador), así que no se usa |
| Publicación: en push a la rama principal del repositorio, a la rama de trabajo `ccr-dc368e82-x0lki4` (donde está todo el proyecto, porque `master` conserva el código antiguo) o manual (`workflow_dispatch`, con opción de preliminar). Sin secretos, el trabajo avisa y no publica (no falla) | Secretos nunca en pull requests ni forks |
| Release creado con `gh release create` (preinstalado en los runners) | Sin acciones de terceros con permiso de escritura |

## Seguridad

| Decisión | Motivo |
|---|---|
| `base-config cleartextTrafficPermitted="true"` | El host lo escribe el usuario; `domain-config` exige dominios fijos. **Riesgo:** la política de Android permite HTTP a cualquier dominio. Mitigación en la app: HTTPS primero, nunca degradar ante error de certificado, HTTP solo con consentimiento guardado por host; la autoactualización exige HTTPS por código |
| `allowBackup=false` y reglas de extracción que lo excluyen todo | Sección 9 |
| `tools/comprobar_secretos.sh` como `pre-commit` y en CI | Sección 1.10 |

## Fase 3: salida y utilidades

| Decisión | Alternativas | Motivo |
|---|---|---|
| Servidor local con **Ktor 3.6.0 (motor CIO)** en un servicio en primer plano **`specialUse`** con `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`, iniciado solo por el usuario, con notificación y botón «Parar» | `dataSync`, `connectedDevice`, NanoHTTPD | `dataSync` está limitado a 6 h cada 24 desde Android 15; `connectedDevice` exige permisos de otros usos (sección 7.2). Ktor está mantenido |
| Puerto 8484; por defecto solo `127.0.0.1`; en red local, ruta con token aleatorio de 144 bits (`/token/lista.m3u`) | Puerto 8080 | 8080 suele estar ocupado en homelabs |
| Subida a servidor propio por **HTTP PUT** (sirve para WebDAV) con autenticación básica opcional, credenciales cifradas como las del proveedor | SFTP | SFTP exige una librería SSH pesada; PUT/WebDAV cubre Nextcloud, nginx, Apache y rclone serve. SFTP queda pendiente |
| Comprobación de enlaces: de uno en uno, consulta de `active_cons` antes de cada prueba (si > 0, se detiene), muestra de 6 s (máx. 8 MB), espera **provisional de 10 s** entre pruebas (configurable: 5–60 s), «lento» si < 1.500 kbps | Paralelo | Conexión única (secciones 3.3 y 7.3). El retraso real de `active_cons` lo medirá Jorge (prueba 6) |
| Los resultados se guardan por `streamId` sin URL (tabla `resultado_enlace`, esquema 2, con migración 1→2 y prueba) | Guardar la URL | Sección 7.3 |
| Opción «Ordenar las capas por tasa de bits medida» (desactivada): las variantes medidas van primero, de mayor a menor kbps; `SOLO EVENTOS`/`HDR` siguen al final | — | Sección 4.3 |
| Bloqueo parental **activado por defecto** sin PIN: el contenido para adultos (`FOR ADULTS` e `is_adult = 1`) se oculta en la app hasta crear un PIN y desbloquear la sesión. PIN con PBKDF2-HMAC-SHA256, 120.000 iteraciones y sal aleatoria | SHA-256 simple | Sección 9 |
| La lista exportada respeta el ajuste «Incluir canales para adultos» (activado por defecto) | — | Sección 6.2.9 |

## Fase 4: VOD

| Decisión | Alternativas | Motivo |
|---|---|---|
| Las respuestas de VOD se guardan como archivos JSON privados (`files/vod/`), escritos a temporales y renombrados al final | Tablas de Room | 67.389 películas no aportan nada a la base de datos de directos; evita otra migración. Todo o nada igualmente |
| Categorías por defecto: las que empiezan por «ES» tras normalizar | Todas | Mismo criterio que los directos. Sin `vod.json` real no se ha podido analizar el formato de los títulos; el prefijo de idioma y el año se detectan con `MotorVod.claveTitulo` y se revisará con datos reales |
| Duplicados: `tmdb` como clave principal; si falta, título normalizado (sin prefijo de idioma, calidades ni corchetes) + año | Solo nombre | Sección 13, Fase 4 |
| Una entrada por título en el M3U de películas (la copia mejor valorada y, a igualdad, la más reciente) | Todas las copias | Lista limpia; las demás copias se ven en la app |
| Series: M3U de episodios por serie, a petición (una llamada a `get_series_info` por serie) | Descargar todas las series | Evita miles de llamadas con una cuenta de conexión única |
| Ocultar películas con la decisión `OCULTAR_PELICULA` (historial y deshacer comunes) | Lista aparte | «El mismo sistema de decisiones manuales» |
