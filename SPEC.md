# SPEC.md — Gestor de listas IPTV para Android (móvil) y Google TV

Especificación completa para que **Claude Code** construya, verifique y entregue la aplicación **sin hacer preguntas**.

**Alcance, en una línea:** la app **gestiona y limpia la lista** (sincroniza, filtra, deduplica, reparte en capas, asigna guía e iconos y exporta). **No es un reproductor**: Jorge verá la tele con **TiviMate Premium** (su reproductor habitual), alimentado con la lista que genere esta app; la sección 7.4 recoge qué debe cumplir la lista para funcionar bien en TiviMate. Primero **canales en directo**; después **VOD** (películas y series).

---

## 0. Para Jorge: cómo usarlo desde el móvil (un solo zip)

Todo viene en un único archivo, `iptv_gestor_inicio.zip`, que incluye este documento, la instrucción inicial (`LEEME_CLAUDE_CODE.md`), los archivos de ejemplo (`fixtures/`, sin credenciales ni datos del proveedor), la documentación de apoyo (`docs/`) y las herramientas (`tools/`).

**Qué necesitas:** un repositorio de GitHub tuyo, vacío o casi vacío, abierto en la sesión de Claude Code con permiso de escritura. **Se recomienda público**, para que la app pueda mirar si hay versión nueva sin guardar ninguna clave (ver 6.4). Ni el APK ni el repositorio contienen tus credenciales ni las del proveedor.

**Qué haces:**
1. Adjunta `iptv_gestor_inicio.zip` a la sesión. Si la app no deja adjuntar archivos, súbelo a la raíz del repositorio desde el navegador del móvil (en GitHub: Add file, Upload files).
2. Escribe: `Descomprime el zip y ejecuta LEEME_CLAUDE_CODE.md tal cual. No me preguntes nada.`

**Tu primera prueba** (Claude Code la repetirá en el `README.md`):
1. Abre la página de Releases del repositorio en el navegador del móvil y descarga el APK.
2. Android te pedirá permiso para instalar apps desde ese navegador ("instalar apps desconocidas"); acéptalo. Si Play Protect avisa de una app no reconocida, elige instalarla igualmente.
3. Abre la app, mira la versión que muestra y pulsa "Buscar actualizaciones".
4. Cuando Claude Code publique una segunda versión, vuelve a pulsar ese botón: debe descargarla e instalarla **conservando tus ajustes**. Esa es la prueba que importa.

Tu usuario y contraseña del proveedor **no van en este documento, ni en el código, ni en el repositorio**: la app los pide una vez en su pantalla de configuración.

---

## 1. Reglas de trabajo para Claude Code (obligatorias)

1. **No hagas preguntas al usuario.** Ante cualquier ambigüedad, elige la opción más razonable, impleméntala y anótala en `DECISIONES.md` (decisión, alternativas y motivo).
2. **Verifica lo actual, no confíes en la memoria.** Antes de fijar versiones de Android Gradle Plugin, Kotlin, Compose, Compose for TV, Room, etc., consulta las últimas versiones **estables** (Google Maven, Maven Central, documentación oficial) y registra en `DECISIONES.md` qué versión usas y la fecha de comprobación. No uses alfa ni beta sin justificarlo.
3. **Verifica todo lo que construyas.** Una tarea solo está terminada cuando:
   - compila (`debug` y `release`) sin errores;
   - pasan los tests unitarios y los de interfaz que puedan ejecutarse;
   - `lint` no tiene errores;
   - se ha probado en emulador de teléfono **y** de Android TV/Google TV, si el entorno lo permite.
4. **Si el entorno no permite verificar algo** (sin emulador por falta de KVM, sin red hacia el proveedor...), no lo des por verificado: usa mocks y fixtures, comprueba lo que se pueda (Robolectric, tests de Compose, capturas) y deja una sección **"Verificación pendiente"** en `README.md` con los pasos exactos para que Jorge lo compruebe en sus dispositivos.
5. **No inventes datos.** Los números de la sección 11 salen de un análisis real de los fixtures. Si tus resultados difieren, investiga la causa antes de tocar los números. Si necesitas cifras que aquí no figuran, calcúlalas tú con los fixtures y documéntalas.
6. **Seguridad:** ninguna credencial en el repositorio, en logs, en capturas ni en mensajes de error (sección 9).
7. **Idioma:** interfaz en español de España (`es-ES`, con `strings.xml`). Documentación en español.
8. **Trabaja por fases** (sección 13), con un commit descriptivo al terminar cada una. **Empieza por la Fase 0** (ciclo completo de compilación, publicación y actualización) antes de construir el resto.
9. **Estás trabajando desde un móvil y quizá en un entorno sin Android SDK, sin emulador y con acceso a red limitado.** No des por hecho que puedes compilar en local. **La compilación y la verificación oficiales las hace GitHub Actions** (sección 6.4): después de cada push, lee el resultado de la ejecución (con `gh run watch` y `gh run view --log-failed`, o con la integración de GitHub de que dispongas) y corrige hasta que quede en verde. Si no puedes leer el resultado, dilo en "Verificación pendiente" y da la dirección de la ejecución para que Jorge la mire. **Nunca declares verificado algo que la integración continua no haya ejecutado.**
10. **Higiene del repositorio:** nunca subas el almacén de claves de firma, contraseñas, usuario o contraseña del proveedor, ni los archivos originales de ejemplo con datos sensibles. Antes de cada commit, busca esas cadenas y aborta si aparecen.

---

## 2. Qué se quiere y por qué

Jorge tiene una suscripción IPTV con un proveedor compatible con **Xtream Codes**. Los datos vienen sucios:

- Decenas de miles de canales y películas en cientos de grupos (313 grupos de directo).
- El **mismo canal está repetido muchas veces** en distintas calidades (RAW, UHD, FHD, HD, HEVC, SD, LOW) y con nombres distintos según la operadora (`ES: MOVISTAR LALIGA ᴴᴰ`, `M+: LA 1 ᴿᴬᵂ`, `VO: LA 1 ᴴᴰ`, `GO: LA 1 ᴿᴬᵂ`...).
- No hay guía de programación (EPG) fiable ni iconos coherentes.
- La cuenta permite **una sola conexión simultánea**.

Lo que quiere conseguir con la app:

1. **Gestionar la lista** desde el teléfono y desde el Google TV (un solo APK).
2. Quedarse solo con los **canales en directo en español** (grupos `ES|`), más `FOR ADULTS` y los PPV de España.
3. **Detectar los canales repetidos y repartirlos en capas** por operadora (Movistar `M+`, `Vodafone`, `Orange`): la capa 1 con la mejor versión de cada canal, la capa 2 con la segunda opción de los canales que la tienen, etc.
4. **Orden, ID de guía e iconos** tomados de la guía de dobleM, válidos para todas las operadoras.
5. **Asignación manual** cuando la app no sepa a qué canal pertenece otro o qué entrada de la guía le corresponde.
6. **Exportar** la lista limpia (M3U) para usarla en su reproductor de siempre, y mantenerla **actualizada** cuando el proveedor cambie algo, con un informe de qué se ha añadido o ha desaparecido.
7. Más adelante, hacer lo mismo con el **VOD**.

### Fuera de alcance (por ahora)

Reproductor de vídeo, mini-guía y parrilla de programación, imagen en imagen, zapeo y respaldo automático durante la reproducción. Tampoco se almacenan las emisiones de la guía: solo interesan los canales de la guía (ID, nombres e icono).

---

## 3. Datos de entrada y proveedor

### 3.1 Fixtures que aporta Jorge (`fixtures/`)

| Archivo | Contenido |
|---|---|
| `live.json` | 24.401 canales en directo, tal como los devuelve la API |
| `vod.json` | 67.389 películas, tal como las devuelve la API |
| `lista.m3u` | M3U de las 91.790 entradas (directos y películas) generado a partir de lo anterior |

Campos de `live.json`: `num`, `name`, `stream_type`, `stream_id`, `stream_icon`, `epg_channel_id`, `added`, `is_adult`, `category_id`, `category_ids`, `custom_sid`, `tv_archive`, `direct_source`, `tv_archive_duration`.

Campos de `vod.json`: `num`, `name`, `stream_type`, `stream_id`, `stream_icon`, `rating`, `rating_5based`, `tmdb`, `trailer`, `added`, `is_adult`, `category_id`, `category_ids`, `container_extension`, `custom_sid`, `direct_source`.

**Importante:** `live.json` trae solo `category_id` (un número), **no el nombre del grupo**. Los nombres salen de la API (`get_live_categories`) o, en los fixtures, del atributo `group-title` de `lista.m3u`, que sigue el mismo orden que `live.json`. Todos los `stream_id` son únicos y `direct_source` viene vacío en todos.

**Para la fase 1 solo se aportan dos archivos** (ya descomprimidos en `fixtures/`), sin credenciales ni dirección del proveedor:
- `live.json`: los 24.401 canales en directo, tal como los devuelve `get_live_streams`.
- `live_categorias.json`: las 313 categorías con su nombre (`category_id`, `category_name`), con el mismo formato que `get_live_categories`. Sustituye a `lista.m3u` para conocer el nombre de cada grupo.

`vod.json` y `lista.m3u` **no se aportan todavía**. Si en una fase posterior Jorge los entrega, antes de guardar nada en el repositorio **sustituye en las copias el usuario, la contraseña y la dirección del proveedor por los marcadores `{USER}`, `{PASS}` y `{HOST}`**, y añade los originales al `.gitignore`. Un repositorio público no debe contener ni credenciales ni el nombre del proveedor. Los números de referencia de la sección 11.2 que dependen de `lista.m3u` (por ejemplo, las 91.790 entradas) no se aplican en la fase 1.

### 3.2 API del proveedor (Xtream Codes)

Base: `{host}/player_api.php?username={user}&password={pass}&action=...`

| Acción | Para qué |
|---|---|
| (sin `action`) | Estado de la cuenta: `status`, `exp_date`, `max_connections`, `active_cons`, `server_info` (con `https_port`) |
| `get_live_categories`, `get_live_streams` | Directos |
| `get_vod_categories`, `get_vod_streams` | Películas |
| `get_series_categories`, `get_series`, `get_series_info&series_id=` | Series |

URL de cada elemento en la lista exportada:
- Directo: `{host}/live/{user}/{pass}/{stream_id}.ts` (también `.m3u8`; la cuenta permite `m3u8`, `ts` y `rtmp`).
- Película: `{host}/movie/{user}/{pass}/{stream_id}.{container_extension}`.

### 3.3 Particularidades descubiertas

- **`get.php` (el que genera el M3U) está bloqueado por el CDN del proveedor:** responde 503 o un código no estándar **884** con 0 bytes, tanto desde servidores como desde el móvil. **`player_api.php` sí funciona.** La app **no debe depender de `get.php`**: lo construye todo desde `player_api.php`.
- El proveedor responde con el `User-Agent` `VLC/3.0.20`. Úsalo en todas las peticiones, configurable en ajustes.
- La cuenta de Jorge tiene `max_connections = 1`. La app no reproduce vídeo, pero su función opcional de comprobar enlaces (sección 7.3) debe respetar ese límite.
- El servidor expone también HTTPS (`https_port` 443). Prueba HTTPS primero y cae a HTTP si falla (sección 9).
- Un `stream_id` puede cambiar si el proveedor reorganiza: las decisiones manuales no deben depender solo del `stream_id` (sección 5.3).

---

## 4. Reglas de negocio

### 4.1 Filtro de directos

Se conservan los grupos cuyo nombre:
- empieza por `ES|`, o
- es exactamente `FOR ADULTS` (lista de extras configurable), o
- contiene `PPV`, solo si el ajuste "PPV de todos los países" está activado (desactivado por defecto; los PPV de `ES|` ya entran por el primer criterio).

Referencia con los fixtures: 56 grupos `ES|` con 2.737 canales; con `FOR ADULTS`, 57 grupos y 2.907 canales. Hay otros 119 grupos PPV de fuera de España (8.281 canales) que solo entran con el ajuste activado.

Los grupos y los extras deben ser editables en ajustes. Las entradas separadoras (nombre que empieza por `#`, como `#### ORANGE ᴿᴬᵂ ####`) se descartan.

### 4.2 Identificar el canal (la "clave")

Cada canal del proveedor se reduce a una **clave** que identifica al canal lógico, quitando lo que varía entre copias. Implementación de referencia en la sección 15 (`clave()`):

1. Normalizar Unicode con NFKD y quitar marcas diacríticas; pasar a mayúsculas. Esto convierte los superíndices (`ᴿᴬᵂ`, `ᴴᴰ`, `ʰᵉᵛᶜ`, `⁶⁰ᶠᵖˢ`, `³⁸⁴⁰ᴾ`) en letras normales.
2. Quitar lo que va entre paréntesis (por ejemplo `(SOLO EVENTOS)`).
3. Quitar prefijos de 1 a 3 caracteres seguidos de dos puntos (`ES:`, `VO:`, `GO:`, `OR:`, `M+:`).
4. Quitar etiquetas de calidad y de grupo: `UHD FHD HD SD RAW HEVC LOW 4K 8K ULTRA HDR 60FPS VIP H265 H264 BK 3840P 2160P 1080P 720P`.
5. Sustituir símbolos por espacios y colapsar espacios.
6. Unificar `MOVISTAR`, `MOVISTAR+`, `MOVISTAR PLUS`, `M` y `M+` iniciales en `M+ `.

Así `ES: MOVISTAR LALIGA ᴴᴰ`, `... SD`, `... ᴿᴬᵂ`, `... ʰᵉᵛᶜ` y `... ᵁᴸᵀᴿᴬ ᴿᴬᵂ` comparten la clave `M+ LALIGA`, y `MOVISTAR LALIGA 2` conserva su número como canal distinto.

### 4.3 Calidades (qué significan y cómo se ordenan)

El proveedor sube el mismo canal varias veces en versiones distintas:

| Etiqueta | Significado |
|---|---|
| RAW | Señal original sin recomprimir. Mejor imagen, pero muy pesada |
| UHD / 4K | Resolución superior a Full HD |
| FHD | Full HD (1080) |
| HD | Calidad normal actual (720) |
| HEVC | Compresión moderna: menos peso con buena imagen; algunos dispositivos antiguos no la manejan bien |
| SD | Calidad antigua |
| LOW | Muy baja, para conexiones malas |

Preferencia por defecto (la primera va a la capa 1): `RAW, UHD, FHD, HD, HEVC, SD, LOW`. **Configurable en ajustes**, con un perfil "ligero" predefinido (`HD, FHD, HEVC, RAW, UHD, SD, LOW`) para reproductores o redes justas. Las variantes `SOLO EVENTOS` o `HDR` van siempre al final. Una variante sin etiqueta se trata como HD.

**Esto es una suposición, no un hecho verificado:** ninguna fuente fiable confirma que `RAW` sea siempre la mejor calidad. Tres IAs consultadas coinciden en que `BK` probablemente significa "backup" (respaldo) y una cuarta advierte de que ninguna de estas etiquetas es un estándar. Por eso: (1) por defecto, a igualdad de calidad, **las variantes de grupos marcados `BK` van en una capa posterior a las que no lo están** (es reversible y poco arriesgado); (2) `VIP` no cambia el orden; (3) cuando se disponga de la tasa de bits medida (sección 7.3), la app debe ofrecer **ordenar por tasa de bits medida** en lugar de por etiqueta.

### 4.4 Capas por operadora ("paquetes")

Un **paquete** agrupa los grupos del proveedor que forman una operadora y se calcula por separado:

| Paquete | Regla por defecto |
|---|---|
| `M+` (Movistar) | Grupo `ES|` cuyo nombre contenga `MOVISTAR` o empiece por `ES| M+` |
| `Vodafone` | Grupo `ES|` cuyo nombre contenga `VODAFONE` |
| `Orange` | Grupo `ES|` cuyo nombre contenga `ORANGE` |

Para cada paquete: agrupa por clave, ordena las variantes de cada canal por calidad y reparte **la mejor en la capa 1, la segunda en la capa 2**, etc. (máximo configurable, 5 por defecto; `M+ LALIGA` tiene 7 variantes). Las capas se llaman `M+ 1`, `M+ 2`... y la capa N solo contiene los canales con al menos N variantes.

El usuario puede **crear paquetes propios** (nombre más reglas por grupo o por texto) y editar los existentes. Los demás grupos `ES|` (TDT, Tivify, DAZN, PPV...) pasan tal cual a la lista final, con guía e icono si se pueden emparejar; se podrá activar el agrupado por capas también para ellos.

### 4.5 Lista final

La lista exportada es **una sola lista** formada por:
1. Las capas de cada paquete (grupos `M+ 1`, `M+ 2`, `Vodafone 1`...).
2. El resto de grupos `ES|` seleccionados, sin duplicar los que ya están dentro de un paquete.
3. Los extras (`FOR ADULTS`).

Cabecera: `#EXTM3U url-tvg="{EPG_URL}" x-tvg-url="{EPG_URL}"`. Cada entrada con `tvg-id`, `tvg-name`, `tvg-logo` y `group-title`.

### 4.6 Guía (EPG), orden e iconos

Fuente: repositorio `davidmuma/EPG_dobleM`.

- **Archivo por defecto (verificado el 9-10-2026): `https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/guiatv_sincolor.xml.gz`**, que es la variante que la propia página de dobleM para TiviMate (`Canales_dobleM/Varios/EPG/Tivimate.md`) recomienda en primer lugar. La misma página indica que **TiviMate no soporta imágenes ni etiquetas de colores**, por eso se usan las variantes `sincolor`. Hay seis, todas `.xml.gz`: `guiatv_sincolor` (con caracteres especiales; año, edad y valoración en el título), `guiatv_sincolor1` (género, año, edad y valoración en la descripción), `guiatv_sincolor0` (subtítulo, año, edad y valoración en la descripción) y `guiatv_sincolor2`, `3` y `4` (los mismos tres tratamientos **sin** caracteres especiales). La app debe dejar elegir entre las seis.
- **Comprobado:** `guiatv.xml`, `guiatv.xml.gz`, `guiatv_sincolor.xml.gz` y `guiatv_sincolor2.xml.gz` tienen **exactamente los mismos 640 canales, con los mismos IDs y en el mismo orden**. Por tanto, los `tvg-id` y el orden obtenidos de una variante valen para las demás. Comprueba tú las otras tres (`sincolor1`, `0`, `3`, `4`) al implementar y registra el resultado.
- Tamaño: `guiatv.xml` unos 33 MB; las variantes comprimidas, unos 5 a 7 MB. Se puede leer **en streaming desde el `.gz`** (envolviendo el flujo con `GZIPInputStream`) y parar en la primera emisión: se obtienen los 640 canales en una fracción de segundo.
- Según su README, se actualiza a las 9:00, 13:00, 17:00 y 21:00. **El README no indica la zona horaria** de esas horas. Dentro del XML, cada emisión lleva su desfase explícito (por ejemplo `20261009075000 +0200`), así que los horarios no son ambiguos; la marca de generación del archivo es coherente con hora de España, pero **no la des por declarada**. No fijes la zona horaria de las actualizaciones: sincroniza cada pocas horas.
- **Licencia:** no se ha localizado una licencia explícita en los repositorios `EPG_dobleM` ni `picons_dobleM`. **No empaquetes la guía ni los iconos dentro del APK**; descárgalos desde la URL remota configurable, y deja en el `README.md` una nota para que Jorge confirme los permisos con el autor.
- Cada `<channel>` trae `id` (por ejemplo `La 1 HD`), varios `<display-name>` (alias: `La 1`, `La 1 FHD`, `La 1 UHD`...) y `<icon src=...>` (639 de 640 con icono).
- **El orden de los `<channel>` en el XML es el orden de canales** que se quiere: La 1, La 2, Antena 3, Cuatro, Telecinco, La Sexta, M+ Estrenos, M+ Hits... No está verificado que coincida con los dorsales oficiales de Movistar Plus+; es el de la guía de dobleM.

Qué hacer con ella:
1. Descargarla **en streaming** (XmlPullParser o SAX sobre el flujo) y **detenerse al llegar a la primera emisión**: los canales van al principio del XML y solo se guardan ellos (ID, nombres, icono y posición). No se guardan emisiones.
2. **Emparejar** cada canal lógico con una entrada de la guía por nombre (algoritmo de referencia en la sección 15, `buscar_epg()`): alias manual del usuario, nombre exacto, sinónimos, nombre compactado sin espacios y coincidencia aproximada (umbral 0,88) **exigiendo que los números coincidan** (para no confundir `LALIGA 2` con `LALIGA 3`).
3. Con la pareja encontrada: su **ID como `tvg-id`**, su **icono como icono del canal** y su **posición como orden**. Los canales sin pareja van al final de su capa, con el icono del proveedor, y aparecen en la bandeja de revisión (sección 5).
4. Como el emparejado es por canal lógico, **la misma guía y el mismo icono sirven a todas las capas y a todas las operadoras**.
5. Guarda copia local de los canales de la guía y úsala si la descarga falla.

Referencia con los fixtures y los alias incorporados: con guía M+ 136 de 201 canales, Vodafone 87 de 140, Orange 109 de 146. El resto son sobre todo regionales, extranjeros, HBO o alquiler, que la guía no cubre.

---

## 5. Asignación manual (requisito clave)

La app **nunca debe obligar a aceptar un emparejado dudoso ni perderlo en la siguiente sincronización**. Las decisiones manuales se guardan y tienen prioridad sobre el algoritmo.

### 5.1 Acciones del usuario

1. **Unir** un canal (o una variante) con otro canal lógico: "este canal pertenece a este otro". Pasa a ser una variante más de ese canal y entra en sus capas.
2. **Separar** una variante de su canal lógico para que sea independiente.
3. **Asignar guía (EPG)** a un canal: buscador sobre los 640 canales de la guía, mostrando nombre e icono.
4. **Quitar la guía** a un canal si el emparejado automático se equivocó.
5. **Cambiar de paquete** (M+, Vodafone, Orange, uno propio) o dejar el canal sin paquete.
6. **Fijar variante preferida**: pasa a la capa 1 aunque no sea la de mayor calidad.
7. **Ocultar** un canal o una variante (no sale en la lista exportada).
8. **Cambiar el icono** (uno de la guía o una URL pegada).
9. **Deshacer** cualquier decisión y ver el **historial**.

### 5.2 Bandeja de revisión ("Por revisar")

Pantalla que lista, ordenada por importancia, lo que la app no sabe resolver, con **sugerencias y puntuación de confianza** y botones de un toque (confirmar, elegir otra, ignorar):

1. Canales **sin guía** (con las tres mejores candidatas aproximadas).
2. Emparejados **con confianza baja**.
3. **Posibles duplicados**: canales hoy distintos pero con nombre muy parecido a otro, por si son el mismo.
4. Canales **nuevos** aparecidos en la última sincronización.
5. Canales **desaparecidos** que tenían decisiones manuales (reasignar o descartar).

Debe usarse con mando en la TV (foco visible, atajos) y con el dedo en el móvil. Incluye un modo **"revisar en tanda"** que pasa al siguiente caso tras cada decisión.

### 5.3 Persistencia robusta

- Cada decisión se guarda con **dos identidades**: el `stream_id` y la **clave normalizada más el grupo de origen**. Si el `stream_id` cambia, se reaplica por clave.
- **Exportar e importar** todas las decisiones en un JSON (copia de seguridad y paso entre móvil y TV). Importar también el formato simple `NOMBRE = NOMBRE EN LA GUÍA` (compatible con el `alias_epg.txt` de los scripts Python).
- Las decisiones sobreviven a actualizaciones de la app y a resincronizaciones.

---

## 6. Requisitos de la aplicación

### 6.1 Plataformas y distribución

- **Un único APK** para teléfono y Google TV, con interfaz adaptada al factor de forma (detectar televisión con `UiModeManager` o `FEATURE_LEANBACK`). **Prioridad al móvil** (donde la gestión es más cómoda); en TV debe poder hacerse todo con mando.
- `minSdk` lo más bajo razonable que admitan las librerías (se espera 23–26; justifícalo); `compileSdk` y `targetSdk` en el último estable (verifícalo).
- Manifiesto para televisión: `uses-feature android.software.leanback` con `required="false"`, `android.hardware.touchscreen` con `required="false"`, categoría `LEANBACK_LAUNCHER`, banner de 320×180 e icono adaptativo.
- Distribución por **APK instalable manualmente** (descarga desde la página de Releases de GitHub, o con `adb`), firmado con una **clave persistente** y con **autoactualización** desde esos mismos Releases. Todo el detalle está en la sección 6.4.

### 6.2 Pantallas y funciones

1. **Configuración inicial**: host, usuario y contraseña; botón "Probar conexión" con `player_api.php` que muestra el estado de la cuenta (activa, caducidad, conexiones máximas y en uso). Importación opcional de `live.json` y `lista.m3u` para arrancar sin red.
2. **Resumen**: estado de la última sincronización, cifras (canales de origen, conservados, por paquete y capa, con y sin guía) y accesos directos.
3. **Vista previa de la lista final**: grupos y canales tal como se exportarán, en su orden, con icono, ID de guía y capa; búsqueda y filtros; marca visual de los canales sin guía y de los decididos a mano. Al tocar un canal se abre su ficha: variantes, calidad de cada una, guía asignada y acciones de la sección 5.1.
4. **Por revisar**: sección 5.2.
5. **Paquetes y filtros**: grupos conservados, extras, PPV, paquetes propios, máximo de capas, perfil de calidad.
6. **Sincronización**: manual y automática, con el **informe de cambios** (añadidos, quitados, cambiados y por revisar), que se puede compartir como texto.
7. **Exportación** (sección 7).
8. **Ajustes**: formato de stream (`ts` o `m3u8`), User-Agent, actualización de guía, bloqueo parental, copia de seguridad y borrado de datos.
9. **Bloqueo parental opcional con PIN**: activado por defecto para el grupo `FOR ADULTS` y las entradas con `is_adult = 1` (se ocultan de la app hasta introducir el PIN; en la exportación se respeta el ajuste elegido). Se puede desactivar.

### 6.3 Móvil y TV

| | Teléfono | Google TV |
|---|---|---|
| Navegación | Barra inferior; gestos | Menú lateral; foco con D-pad |
| Entrada | Táctil | Mando: flechas, centro para aceptar, atrás; atajos para "siguiente caso" en la revisión |
| Texto | Tamaño normal | Textos y zonas de foco grandes; nada que requiera táctil |

Los atajos del mando deben mostrarse en una ayuda en pantalla.


### 6.4 Compilación, publicación y autoactualización con GitHub (requisito crítico)

**La autoactualización es un requisito imprescindible.** Jorge instalará el APK una vez y, a partir de ahí, la app debe detectar, descargar e instalar las versiones nuevas que se publiquen en el repositorio.

#### 6.4.1 Identidad de la app y firma (decisiones que no se pueden cambiar después)
- Elige un `applicationId` estable y **no lo cambies nunca**. Regístralo en `DECISIONES.md`.
- **Android solo instala una actualización si lleva el mismo `applicationId`, la misma firma y un `versionCode` mayor.** Por eso la clave de firma debe ser **la misma en todas las versiones**. Si se perdiera, habría que desinstalar la app y perder sus datos.
- Crea la clave **una sola vez** con `keytool` (RSA de al menos 2048 bits y validez de 25 años o más, verifica las recomendaciones actuales de Android). **La clave nunca va en el repositorio.** Guárdala solo como **secretos de GitHub Actions**: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
  - Si tienes `gh` autenticado con permiso para escribir secretos, créalos con `gh secret set`.
  - Si no, deja el almacén generado y los pasos exactos para que Jorge cree esos cuatro secretos desde el navegador del móvil (Settings, Secrets and variables, Actions).
- **Copia de seguridad:** los secretos de GitHub no se pueden volver a leer. Entrega a Jorge **una sola vez**, en el informe final, el almacén codificado y sus contraseñas, con un aviso claro de que debe guardarlos fuera del chat (por ejemplo, en un gestor de contraseñas) y no compartirlos. Registra en el `README.md` qué pasa si se pierden.
- Ten presente (verificado en la página oficial de Android, consultada el 9-10-2026) que **Android está desplegando la verificación de desarrolladores**: desde el 30-9-2026 aplica en Brasil, Indonesia, Singapur y Tailandia, y se ampliará globalmente en 2027. Google indica que seguirá habiendo sideloading (con ADB y con un "flujo avanzado") y que habrá una cuenta gratuita de distribución limitada, sin documento de identidad, para estudiantes y aficionados, hasta 20 dispositivos. España no está en la primera fase. Mantener estable el `applicationId` y la clave facilitará registrar la app más adelante. Documenta esto en el `README.md` y **verifica el estado vigente en la página oficial (`developer.android.com/developer-verification`) al implementar**.

#### 6.4.2 Versiones
- `versionCode` **estrictamente creciente** (por ejemplo, el número de commits de la rama principal o el número de ejecución del flujo más un desplazamiento). Nunca puede bajar.
- `versionName` con formato `0.<fase>.<contador>`; la etiqueta de la versión es `v` más el nombre de versión.
- La app muestra su versión y su `versionCode` en ajustes.

#### 6.4.3 Flujo de GitHub Actions (`.github/workflows/`)
- **Al hacer push o abrir una pull request:** compilar, pasar los tests unitarios y `lint`. Estas ejecuciones **no tienen acceso a los secretos de firma**.
- **Al hacer push a la rama principal:** además, compilar el APK de `release`, **alinearlo (`zipalign`) y firmarlo (`apksigner`)** con la clave de los secretos, calcular su SHA-256 y **publicar un Release** de GitHub con estos archivos: el APK (con nombre fijo, por ejemplo `app-release.apk`), `update.json` y `SHA256SUMS`. Las notas del Release salen de los mensajes de commit.
- Esquema de `update.json`: `versionCode`, `versionName`, `apkUrl`, `sha256`, `minSdk`, `releaseNotes` y `publishedAt`.
- **Seguridad del flujo:** permisos mínimos (`contents: write` solo en el trabajo de publicación), secretos disponibles únicamente en ese trabajo y nunca en pull requests ni forks, acciones de terceros fijadas por versión o por commit (verifica las versiones vigentes y que no tengan menos de dos semanas), y nada de imprimir secretos en los registros.
- Verifica los nombres de las tareas de Gradle, la versión de Java necesaria para el plugin de Android que elijas y los cambios recientes de `gh` y de las acciones, en lugar de fiarte de la memoria.

#### 6.4.4 Autoactualización dentro de la app
- **Fuente:** `https://api.github.com/repos/<propietario>/<repositorio>/releases/latest` y el `update.json` del Release. El repositorio **público** permite consultarlo sin guardar ninguna clave. **Si el repositorio es privado, no incrustes un token en el APK** (se podría extraer): en ese caso, desactiva la comprobación automática, deja la dirección de actualización como ajuste opcional y explica en el `README.md` cómo actualizar a mano o usar un segundo repositorio público solo para los Releases.
- **Cuándo comprobar:** al abrir la app (como mucho una vez cada 12 horas), cada día con WorkManager, y con el botón **"Buscar actualizaciones"**. Usa ETag o peticiones condicionales para no agotar el límite de la API.
- **Flujo:** si hay un `versionCode` mayor, muestra un aviso con las notas de la versión. Con la confirmación del usuario, **descarga el APK por HTTPS a la caché de la app** (con barra de progreso, reintentos y comprobación de espacio libre), **verifica su SHA-256 con el de `update.json`**, comprueba que el APK lleva **la misma firma que la app instalada** y lo instala con **`PackageInstaller`** (permiso `REQUEST_INSTALL_PACKAGES`).
- **Permiso de instalación:** la primera vez, Android exige que el usuario active "Instalar apps desconocidas" para esta app. La app debe llevarlo a esa pantalla de ajustes y explicar el paso, tanto en el móvil como en Google TV (con el mando).
- **Confirmación de Android:** no hay instalación silenciosa para apps instaladas por sideloading; Android pedirá confirmar en cada actualización. Verifica en la documentación vigente si existen condiciones para actualizar sin confirmación y no cuentes con ellas.
- **Seguridad:** solo HTTPS; solo los dominios de GitHub necesarios para la API y la descarga de los archivos del Release (verifica cuáles son hoy); nunca enviar credenciales; sin telemetría; no instalar versiones con `versionCode` menor o igual.
- **Los datos sobreviven:** la base de datos y los ajustes (incluidas las **decisiones manuales de la sección 5**) deben conservarse entre versiones. **Prohibidas las migraciones destructivas**: cada cambio de esquema lleva su migración de Room y su prueba automática. La actualización cierra la app; al volver a abrirla debe mostrar "Actualizada a la versión X".
- **Canal de pruebas:** ajuste opcional, desactivado, para ver también versiones marcadas como preliminares.

#### 6.4.5 Verificación del ciclo
- La integración continua debe terminar en verde y producir un Release con APK firmado, `update.json` y `SHA256SUMS`.
- Prueba de integración **de la lógica de actualización** (en Kotlin puro, con un servidor simulado): detecta versión mayor, ignora igual o menor, rechaza un SHA-256 incorrecto y una firma distinta.
- **Prueba de extremo a extremo (la hace Jorge, y debe quedar escrita en el `README.md`):** instalar la versión N, esperar a que se publique la N+1 y actualizar desde la app, comprobando que se conservan ajustes y decisiones. Si Claude Code no puede ejecutarla, queda en "Verificación pendiente".

---

## 7. Salida: exportación y utilidades

### 7.1 Exportar

- **Lista final** (sección 4.5) como M3U, con nombre y ubicación configurables.
- Opciones: todo en un archivo, o un archivo por paquete o por capa.
- **Guardar en una carpeta elegida por el usuario** (Storage Access Framework) y **compartir** con el menú de Android.
- **Exportación automática** tras cada sincronización a la carpeta elegida, para que un reproductor que apunte a ese archivo tenga siempre la última versión.
- Exportar también el **informe de cambios** (texto) y las **decisiones manuales** (JSON).
- **Aviso al exportar:** la lista contiene usuario y contraseña en cada URL. Debe mostrarse una advertencia clara y el ajuste "no mostrar más".

### 7.2 Servir la lista en la red (opcional)

Un servidor HTTP integrado y desactivado por defecto, para que el reproductor de la tele lea una dirección en lugar de un archivo:
- `/lista.m3u`, `/lista_<paquete>.m3u` e `/informe.txt`.
- **Por defecto solo escucha en `127.0.0.1`** (útil cuando el reproductor corre en el mismo Google TV). El acceso desde la red local es una opción aparte, protegida por una **ruta con token aleatorio** y con aviso de seguridad, porque la lista lleva credenciales.
- Se ejecuta como servicio en primer plano, con notificación y botón de parada, **iniciado por una acción explícita del usuario**.
- **Tipo de servicio en primer plano (verificado en la documentación de Android, actualizada el 1-10-2026):** desde Android 14 hay que declarar el tipo y su permiso. **No uses `dataSync`**: desde Android 15 está limitado a 6 horas acumuladas por cada 24 horas en segundo plano (el contador se reinicia cuando el usuario trae la app al primer plano) y no puede iniciarse desde `BOOT_COMPLETED`. `connectedDevice` exige declarar permisos concretos (por ejemplo `CHANGE_NETWORK_STATE` o `CHANGE_WIFI_STATE`) y está pensado para interactuar con dispositivos externos. **Usa `specialUse`** con la propiedad `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explicando el uso; la revisión de esa justificación la hace Google Play solo si se publica allí, y esta app se instala por sideload. Documenta la elección en `DECISIONES.md`.
- Como TiviMate solo necesita la lista cuando se actualiza, el servidor no tiene por qué estar siempre encendido. Para disponibilidad permanente, la opción de subir la lista a un servidor propio (sección 7.4) es mejor que un servicio permanente en el móvil o la TV.
- Elige una librería ligera y mantenida. A fecha 9-10-2026, la última versión de **Ktor** publicada es la **3.6.0 (17-9-2026)**; verifícalo de nuevo al implementar (con el motor CIO, por ejemplo) y **comprueba si los reproductores habituales aceptan una dirección local**; anota el resultado en "Verificación pendiente" si no puedes probarlo.

### 7.3 Comprobar enlaces (opcional, fase 3)

Para responder a la duda de qué enlaces funcionan sin abrir un reproductor:
- Prueba **de uno en uno** (nunca en paralelo), con una pausa entre pruebas: abre la conexión, lee unos KB o segundos, mide tiempo hasta los primeros datos y velocidad aproximada, y cierra.
- **Antes de empezar, consulta `active_cons` en `player_api.php`; si es mayor que 0, no empieces** (alguien está viendo con la única conexión). Interrumpe de inmediato si aparece una conexión ajena.
- Guarda el resultado por variante (funciona, falla, lento) y propón reordenar las capas o ocultar las variantes que fallan. **Nunca cambies nada sin confirmación** salvo que el usuario active el ajuste automático.
- **Mide también la tasa de bits aproximada** de cada variante (bytes recibidos dividido entre los segundos de la muestra, por ejemplo 5 a 10 s). Es la forma objetiva de comprobar si `RAW` pesa de verdad más que `FHD` o `HD`, algo que ninguna fuente consultada ha podido confirmar. Muestra el resultado junto a cada variante en la ficha del canal.
- **`active_cons` puede tardar en actualizarse** (retraso de segundos o más, no verificado): tras terminar una prueba, espera antes de comprobarlo de nuevo y antes de empezar otra. La espera debe ser **un ajuste configurable**; Jorge medirá el retraso real de su proveedor (prueba 6 de `docs/PREGUNTAS_IA_RESPONDIDAS.md`) y lo fijará. Hasta entonces, usa un valor prudente y documenta que es provisional.
- No almacenes ni muestres las URLs completas en los resultados.


### 7.4 Compatibilidad con TiviMate Premium (reproductor de destino)

Jorge usará la lista en **TiviMate Premium**. No hay documentación oficial de TiviMate en la que apoyarse del todo, así que lo siguiente se divide en lo que varias fuentes de terceros coinciden en describir y lo que **hay que verificar**. Tú (Claude Code) debes consultar fuentes actuales, documentar lo que confirmes y dejar en "Verificación pendiente" lo que no puedas probar.

**Lo que se da por válido (coincidencia entre fuentes de terceros):**
- TiviMate acepta listas M3U por **URL** o por **archivo**, y también el acceso directo por Xtream Codes. **La lista de esta app debe usarse como M3U**, no con el acceso Xtream, porque el acceso Xtream no pasaría por la limpieza.
- TiviMate empareja la guía por **`tvg-id`**, que debe ser **idéntico, incluidas mayúsculas, a la `id` del `<channel>` del XMLTV**; si no hay coincidencia, recurre a `tvg-name`. Admite además asignar a mano la guía de un canal.
- Premium permite **varias fuentes de guía** y **varias listas**.
- Las listas deben conservar los atributos `tvg-id`, `tvg-name`, `tvg-logo` y `group-title` (formato tipo `m3u_plus`). El orden por defecto de los canales es el de la lista.
- La guía comprimida (`.xml.gz`) carga más rápido que el XML sin comprimir.

**Requisitos para la lista exportada:**
1. Codificación UTF-8; cada `#EXTINF` en una sola línea y la URL en la línea siguiente.
2. **Sanea los valores de atributos:** si un nombre lleva comillas dobles u otros caracteres que rompan el atributo, escápalos o sustitúyelos. Escribe un **validador de M3U** que falle si alguna línea es inválida y úsalo en los tests y antes de cada exportación.
3. `tvg-id` = exactamente el `id` de la guía (por ejemplo `La 1 HD`, con sus espacios y mayúsculas). Si el canal no tiene guía, `tvg-id` va vacío.
4. Los **grupos se escriben en el orden en que deben verse** (capas y paquetes en su orden), y dentro de cada grupo los canales en el orden de la guía.
5. Ajuste opcional **"numerar canales" (`tvg-chno`)**, desactivado por defecto: no está confirmado que TiviMate lo use.
6. Ajuste opcional para añadir a cada entrada `#EXTVLCOPT:http-user-agent=...`, desactivado por defecto: no está confirmado que TiviMate lo respete. En el README, explica que, si los canales no abren, el User-Agent se puede fijar en la configuración de la lista dentro de TiviMate.

**Guía en TiviMate:**
- No des por hecho que TiviMate lea `url-tvg` de la cabecera. La app debe mostrar de forma destacada la **dirección de la guía** (con botón de copiar) y las instrucciones para añadirla en TiviMate como fuente de guía de la lista (ajustes de la lista, fuente de guía, URL XMLTV), con un intervalo de actualización razonable. **Evidencia (hilos de Reddit guardados por Jorge):** en 2021, con TiviMate 4.0, un usuario no consiguió que la guía de `url-tvg` se cargara sola y tuvo que crear la fuente a mano; otro sugirió `x-tvg-url`, que según él funcionaba en OTT Navigator. Un hilo de 2024 indica que, en Premium, la fuente de guía añadida **hay que activarla dentro de la lista** y que la asignación de guía por canal en TiviMate es **manual y de una en una, sin selección múltiple**. La app debe explicar esos dos pasos (añadir la fuente y activarla en la lista) y recordar que lo que no se empareje por `tvg-id` solo se puede asignar a mano en TiviMate canal a canal, de ahí el valor de resolverlo en esta app.
- **Verificado:** la página de dobleM para TiviMate recomienda las variantes `guiatv_sincolor*.xml.gz` y avisa de que TiviMate no soporta imágenes ni colores (sección 4.6). La app usa `guiatv_sincolor.xml.gz` por defecto y deja elegir otra. Esa página no describe los ajustes de menús de TiviMate: no los inventes.

**Riesgo principal a resolver: `tvg-id` repetidos.** El diseño repite el mismo `tvg-id` en varias entradas (la misma cadena en `M+ 1`, `M+ 2`, `Vodafone 1`, `Orange 1`...). Para una guía XMLTV es normal que varios canales de la lista compartan un ID, pero **no está confirmado cómo se comporta TiviMate** con IDs repetidos dentro de una misma lista. Por eso:
- Añade un ajuste de exportación **"una lista por paquete" y "una lista por capa"** (cada archivo con `tvg-id` no repetidos dentro de sí), pensado para cargarlas en TiviMate Premium como listas separadas.
- En `tools/prueba_tivimate.m3u` ya viene una lista de prueba **sin credenciales ni streams reales** (apunta a una dirección local inexistente) para que Jorge resuelva en su Google TV, en pocos minutos, los puntos que ninguna fuente ha podido confirmar. Descríbela en el `README.md` y consérvala en `tools/`.
- Deja en "Verificación pendiente" la prueba exacta que debe hacer Jorge: cargar la lista completa y comprobar si todas las entradas repetidas muestran la guía; si no, usar las listas separadas.

**Publicación estable (URL):** TiviMate actualiza la lista desde una URL según su propio calendario; con un archivo local puede requerir reimportarlo (no verificado). Prefiere ofrecer una **URL estable**:
- el servidor local de la sección 7.2 (en `127.0.0.1` si TiviMate corre en el mismo Google TV; en la red local, con ruta secreta, si corre en otro dispositivo), y
- una **subida opcional a un servidor propio** (HTTP PUT, WebDAV o SFTP, a elección del usuario; pensada para su homelab) para obtener una URL fija accesible desde cualquier dispositivo de casa. Las credenciales de esa subida siguen las reglas de la sección 9.

**Conexión única:** la cuenta permite una sola conexión. En el README, avisa de que funciones de TiviMate como la vista múltiple o grabar mientras se ve otro canal pueden necesitar más de una conexión.

---

## 8. Arquitectura y stack sugeridos (puedes cambiarlos si lo justificas)

- **Lenguaje y UI:** Kotlin, Jetpack Compose y Material 3 para móvil; **Compose for TV** (`androidx.tv`) para televisión. A fecha 9-10-2026, la documentación oficial publica como **estables** `androidx.tv:tv-material:1.1.0` y `androidx.tv:tv-foundation:1.0.0` (6-5-2026); verifícalo de nuevo al implementar. Capa de datos y de dominio compartida.
- **Datos:** Room (canales, canales lógicos, variantes, decisiones manuales, canales de la guía, resultados de comprobación) con índices pensados para ~25.000 canales; DataStore para ajustes.
- **Red:** OkHttp con User-Agent configurable y `kotlinx.serialization`. Descarga de la guía en streaming.
- **Segundo plano:** WorkManager para la sincronización periódica (cada 6 horas por defecto, y al abrir la app si los datos son antiguos).
- **Imágenes:** Coil con caché de disco para los iconos.
- **Inyección:** Hilt o Koin (elige uno).
- **Estructura:** módulos o paquetes separados: `domain` (reglas de la sección 4, sin Android), `data`, `ui-mobile`, `ui-tv`, `export`.
- El motor de normalización, capas y emparejado debe estar en **Kotlin puro**, testeable sin Android.
- No hace falta librería de reproducción de vídeo.

---

## 9. Seguridad y privacidad

- **Credenciales cifradas.** **No uses `EncryptedSharedPreferences`**: la referencia oficial de `androidx.security:security-crypto` la marca como obsoleta desde la versión 1.1.0 (y remite a `SharedPreferences`, que **no** cifra). Tampoco `DataStore` cifra por sí solo. Usa una clave guardada en **Android Keystore** (AES-GCM o Tink) para cifrar los valores sensibles antes de guardarlos en DataStore. Nunca en código, recursos ni repositorio.
- **Las URLs de la lista contienen usuario y contraseña.** Redáctalas (`***`) en logs, informes de fallos, capturas y mensajes de error. Desactiva el logging de URL y cuerpo de OkHttp en `release`.
- **HTTP en claro.** Desde Android 9 el tráfico en claro está desactivado por defecto. La documentación de Network Security Configuration permite habilitarlo **por dominio** (`domain-config`, con los dominios escritos de antemano en un XML fijo) o **para cualquier dominio** (`base-config` con `cleartextTrafficPermitted="true"`, que la propia documentación desaconseja siempre que se pueda evitar). Como el host lo escribe el usuario y no se puede conocer de antemano, `domain-config` no sirve. Decisión: intentar **HTTPS primero** (el servidor expone `https_port`); **no degradar automáticamente a HTTP ante un error de certificado TLS**; cambiar a HTTP solo si HTTPS falla porque el servidor no lo ofrece, con aviso claro y **consentimiento explícito guardado por host**; **no enviar credenciales por HTTP sin ese consentimiento**. Si se habilita HTTP para hosts arbitrarios, la política es global (`base-config`); documenta ese riesgo en `DECISIONES.md`.
- **Sin analíticas, anuncios ni telemetría.**
- **Repositorio y flujo de publicación:** ningún secreto en el repositorio ni en los registros de GitHub Actions; sin tokens incrustados en el APK; el repositorio puede ser público porque no contiene nada sensible (sección 3.1).
- Desactiva la copia de seguridad automática de Android para los datos con credenciales y ofrece solo la exportación manual de decisiones, **sin credenciales**.
- El PIN del bloqueo parental se guarda con hash y sal.
- El servidor local (7.2) sigue las reglas de esa sección.

---

## 10. Sincronización y actualización

1. Descarga categorías y streams. Calcula el conjunto filtrado, las claves, los paquetes y las capas.
2. **Compara con la sincronización anterior** por `stream_id` (y por clave como respaldo): canales **añadidos**, **quitados** y **cambiados** (nombre, grupo, icono). Guarda y muestra el informe.
3. Reaplica las decisiones manuales (5.3) antes de mostrar el resultado.
4. Actualiza la guía cada pocas horas (el README de dobleM publica 9:00, 13:00, 17:00 y 21:00, sin indicar zona horaria), con reintentos de espera creciente. Sin red, usa los datos guardados.
5. Si la sincronización falla a medias, **no dejes la base de datos a medias**: transacciones, todo o nada.
6. La primera sincronización puede partir de los fixtures importados.
7. Tras sincronizar, ejecuta la exportación automática si está activada.

---

## 11. Pruebas y criterios de aceptación

### 11.1 Pruebas obligatorias

- **Unitarias (Kotlin puro)** con los fixtures saneados: normalización de nombres (superíndices, prefijos, números `LALIGA 2` frente a `LALIGA 3`), calidades, reparto en capas, emparejado con la guía (con y sin alias), decisiones manuales, detección de cambios y **generación del M3U** (formato válido, cabecera `url-tvg`, `group-title` de las capas, sin duplicar grupos de paquetes).
- **Interfaz** (Compose test): navegación con D-pad en TV y táctil en móvil; vista previa, ficha de canal y bandeja de revisión.
- **Exportación:** archivo generado legible y que pasa el **validador de M3U** (sección 7.4); ningún dato sensible en informes y logs; modos "una lista por paquete" y "una lista por capa" sin `tvg-id` repetidos dentro de cada archivo.
- **Capturas** de las pantallas principales en móvil y TV para el `README.md`.

### 11.2 Resultados de referencia (con los fixtures entregados)

| Medida | Valor esperado |
|---|---|
| Canales en directo | 24.401 (todos con `stream_id` único) |
| Grupos de directo | 313 |
| Grupos `ES|` / canales | 56 / 2.737 |
| Con `FOR ADULTS` | 57 grupos / 2.907 canales |
| Grupos PPV de fuera de España | 119 grupos / 8.281 canales |
| Nombres exactos repetidos | 570 nombres / 1.173 entradas |
| Paquete `M+` | 201 canales distintos de 388 entradas; capas 201 / 80 / 57 / 29 / 15 |
| Paquete `Vodafone` | 140 distintos de 189 entradas; capas 140 / 48 / 1 |
| Paquete `Orange` | 146 distintos de 148 entradas; capas 146 / 2 |
| Entradas de las capas de los tres paquetes | 719 (382 + 189 + 148) |
| Canales de la guía | 640 (639 con icono) |
| Con guía (con los alias de la sección 15) | M+ 136, Vodafone 87, Orange 109 |
| `M+ LALIGA` | 7 variantes, una por capa |

Con los fixtures **deben coincidir**. Con datos nuevos del proveedor pueden variar ligeramente. Calcula y documenta además las cifras de la lista final completa (secciones 4.5), que aquí no figuran.

### 11.3 Criterios de aceptación finales

1. Un solo APK funciona en teléfono y en Google TV; en TV se maneja todo con mando.
2. Configuración, sincronización, vista previa, revisión manual y exportación funcionan de extremo a extremo (con red real si es posible, o con servidor simulado).
3. La lista exportada es válida según el validador y cumple los requisitos de la sección 7.4 para **TiviMate Premium**; la guía y los iconos se asignan correctamente. Si no se puede probar en TiviMate, queda descrito en "Verificación pendiente" con los pasos exactos.
4. Las decisiones manuales sobreviven a la resincronización y se pueden exportar e importar.
5. Ninguna credencial aparece en el repositorio, los logs ni las capturas (comprobado con una búsqueda automática).
6. `README.md` explica instalación, uso y límites; `DECISIONES.md` recoge las elecciones; "Verificación pendiente" es honesta.
7. **La integración continua está en verde** y publica un Release con APK firmado, `update.json` y `SHA256SUMS`; el `versionCode` crece con cada versión.
8. **La autoactualización funciona:** con dos versiones consecutivas publicadas, la app instalada detecta la nueva, la descarga, verifica y la instala conservando ajustes y decisiones manuales (o queda descrita como prueba pendiente para Jorge, con los pasos exactos).
9. El almacén de claves y las contraseñas **no están** en el repositorio ni en los registros (comprobado con una búsqueda automática).

---

## 12. Entregables

- Proyecto Android completo y reproducible (`./gradlew assembleDebug` y `assembleRelease`).
- APK debug y APK release firmado, con instrucciones para instalarlos en móvil y Google TV por `adb` y desde el gestor de archivos.
- `README.md` (uso, capturas, instalación, límites, verificación pendiente), `DECISIONES.md`, `CHANGELOG.md`.
- Fixtures saneados en `fixtures/` y los tests que los usan.
- `.github/workflows/` con la compilación, las pruebas y la publicación firmada, y el Release inicial con APK, `update.json` y `SHA256SUMS`.
- En el `README.md`: la **primera prueba paso a paso** (sección 0), cómo crear los secretos de firma si no se pudieron crear solos, qué hacer si se pierde la clave y el estado de la verificación de desarrolladores de Android.
- `MANUAL_ASIGNACION.md` con el flujo de revisión y asignación manual.
- Carpeta `tools/` con la implementación de referencia en Python (sección 15), por si Jorge quiere seguir usándola en su DietPi.

---

## 13. Fases

0. **Fase 0 — Ciclo completo de construcción y actualización (primero):** proyecto Android mínimo que muestre su versión y tenga el botón "Buscar actualizaciones"; clave de firma y secretos; flujo de GitHub Actions; primer Release firmado; autoactualización completa (6.4.4). **Publica una segunda versión trivial** para que Jorge pruebe la actualización de extremo a extremo antes de seguir. Hecha cuando se cumplan los criterios 7 y 8 de 11.3.
1. **Fase 1 — Núcleo gestor (directo):** proyecto, ajustes y conexión; sincronización y base de datos; filtro, claves, paquetes y capas; guía, orden e iconos; vista previa de la lista final; exportación a M3U e informe de cambios; tests con los fixtures. Hecha cuando se cumplan 11.2 y 11.3 para directos.
2. **Fase 2 — Asignación y revisión:** bandeja "Por revisar", todas las acciones manuales, exportación e importación de decisiones, historial.
3. **Fase 3 — Salida y utilidades:** exportación automática y compartir, servidor local, comprobación de enlaces, bloqueo parental, pulido en TV.
4. **Fase 4 — VOD (gestión, sin reproducir):** analiza `vod.json` (67.389 películas) y aplica la misma filosofía a la lista de películas:
   - filtrar por categorías (los títulos parecen llevar prefijo de idioma y año, por ejemplo `ES - Título (2026)`; **analízalo tú con los datos reales**);
   - **detectar duplicados**, usando el campo `tmdb` cuando exista como clave principal y el nombre normalizado como respaldo;
   - ocultar contenido adulto con `is_adult` y el bloqueo parental;
   - ordenar por valoración (`rating`) y por fecha de alta (`added`);
   - exportar un M3U de películas independiente del de directos, con el mismo sistema de decisiones manuales.

   Después, **series** mediante `get_series` y `get_series_info`. No hay series en los fixtures: impleméntalas contra la API y prueba con datos simulados.

---

## 14. Historia: qué se hizo y qué NO se hizo

### Hecho (con scripts Python de referencia)
- Descarga de las listas por `player_api.php` tras comprobar que `get.php` está bloqueado.
- Análisis de repetidos (570 nombres, 1.173 entradas; todas las URLs distintas porque cada una usa su `stream_id`).
- Filtro de directos `ES|` (+ `FOR ADULTS`, con opción de PPV de todos los países).
- Capas por operadora (M+, Vodafone, Orange) con orden y preferencia de calidad configurables.
- Cruce con la guía de dobleM para ID de guía, icono y orden.

### NO hecho / límites conocidos (la app debe resolverlos o documentarlos)
- **No se verificó que los enlaces repetidos sean la misma fuente.** Solo se sabe que son el mismo canal por nombre. Con una conexión máxima, probarlos exige hacerlo uno a uno (sección 7.3).
- **El orden oficial de dorsales de Movistar Plus+ no está verificado.** Se usa el de la guía de dobleM.
- **Series:** no incluidas en los fixtures ni en los scripts. **Películas:** descargadas, sin filtrar ni limpiar.
- Los scripts se probaron con los fixtures y con la descarga real de la guía, pero **la API del proveedor se simuló** (estaba bloqueada desde el entorno de pruebas). La primera sincronización real es la prueba definitiva.
- No hay aún ejecución periódica automática en la DietPi (la app la sustituye).
- Los grupos `ES|` que no son de operadora no se deduplican por defecto.
- El paquete Movistar no incluye el grupo `ES| TELEFÓNICA ᴿᴬᵂ` (no está claro que sea Movistar).
- Quedan canales sin guía que necesitan alias o asignación manual (regionales, internacionales, HBO, alquiler...).
- **No está verificado en TiviMate:** si lee `url-tvg` de la cabecera, cómo trata varios canales con el mismo `tvg-id`, si usa `tvg-chno` o `#EXTVLCOPT`, y si actualiza solo una lista local. Las fuentes consultadas eran de terceros, no documentación oficial.

---

## 15. Implementación de referencia (Python)

Es la lógica que funcionó con los fixtures. **Pórtala a Kotlin puro y valida el resultado con los números de la sección 11.2.** Si encuentras un error o una mejora, corrígelo y documéntalo en `DECISIONES.md`. Usuario y contraseña van como marcadores.

```python
import json, os, re, unicodedata, urllib.request, collections, difflib, gzip
import xml.etree.ElementTree as ET

HOST = "http://HOST_DEL_PROVEEDOR"
USER = "TU_USUARIO"
PASS = "TU_CONTRASENA"
UA = "VLC/3.0.20"
DIR = os.path.expanduser("~/iptv")
MAX_CAPAS = 5

# Variante que dobleM recomienda para TiviMate (con caracteres especiales, año | edad | valoración en el título)
EPG_URL = "https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/guiatv_sincolor.xml.gz"

PAQUETES = [
    {"nombre": "M+",       "grupos": lambda g: "MOVISTAR" in g or g.startswith("ES| M+")},
    {"nombre": "Vodafone", "grupos": lambda g: "VODAFONE" in g},
    {"nombre": "Orange",   "grupos": lambda g: "ORANGE" in g},
]

PREFERENCIA = ["RAW", "UHD", "FHD", "HD", "HEVC", "SD", "LOW"]
ETIQUETAS = (r"\b(UHD|FHD|HD|SD|RAW|HEVC|LOW|4K|8K|ULTRA|HDR|60FPS|VIP|H265|H264|BK|"
             r"3840P|2160P|1080P|720P)\b")

# Nombres del proveedor que la guía escribe de otra forma
SINONIMOS = [("LCAMPEONES", "LIGA DE CAMPEONES"), ("LA LIGA", "LALIGA"),
             ("DISNEY JR", "DISNEY JUNIOR"), ("NAT GEOGRAPHIC", "NATIONAL GEOGRAPHIC"),
             ("CLAN TVE", "CLAN"), ("DISCOVERY CHANEL", "DISCOVERY"),
             ("DISCOVERY CHANNEL", "DISCOVERY"), ("HOLLYWOOD", "CANAL HOLLYWOOD")]

EXACTOS = {"VAMOS": "M+ VAMOS", "M+ DEPORTES 1": "M+ DEPORTES", "M+ CINE": "M+ CINE ESPANOL"}

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    return urllib.request.urlopen(req, timeout=120)

def api(action):
    return json.load(get(f"{HOST}/player_api.php?username={USER}&password={PASS}&action={action}"))

def limpio(n):
    n = unicodedata.normalize("NFKD", n)
    return "".join(c for c in n if not unicodedata.combining(c)).upper()

def clave(nombre):
    n = re.sub(r"\(.*?\)", " ", limpio(nombre))
    n = re.sub(r"^\s*[A-Z0-9+]{1,3}\s*:\s*", "", n)
    n = re.sub(ETIQUETAS, " ", n)
    n = re.sub(r"[^\w+ ]", " ", n)
    n = re.sub(r"\s+", " ", n).strip()
    return re.sub(r"^(MOVISTAR PLUS\+?|MOVISTAR\+?|M\+?) ", "M+ ", n)

def puntos(nombre):
    n = limpio(nombre)
    hallados = [i for i, t in enumerate(PREFERENCIA) if re.search(rf"\b{t}\b", n)]
    p = max(hallados) if hallados else PREFERENCIA.index("HD")
    if "ULTRA" in n: p = min(p, 0)
    if "HDR" in n or "SOLO EVENTOS" in n: p += 10
    return p

# ---------- EPG: canales, orden e iconos ----------
def cargar_epg():
    cache = f"{DIR}/epg_canales.json"
    canales = []
    try:
        flujo = get(EPG_URL)
        if EPG_URL.endswith(".gz"):
            flujo = gzip.GzipFile(fileobj=flujo)
        for ev, el in ET.iterparse(flujo, events=("start", "end")):
            if ev == "start" and el.tag == "programme":
                break                                  # solo hace falta la cabecera
            if ev == "end" and el.tag == "channel":
                ic = el.find("icon")
                canales.append([el.get("id"), [d.text or "" for d in el.findall("display-name")],
                                ic.get("src") if ic is not None else ""])
                el.clear()
        json.dump(canales, open(cache, "w", encoding="utf-8"), ensure_ascii=False)
    except Exception as e:
        print("No se pudo bajar la guía, uso la copia guardada:", e)
        canales = json.load(open(cache, encoding="utf-8"))
    return canales

def compacto(k):
    return k.replace(" ", "")

def indice_epg(epg):
    idx = {}
    for pos, (cid, nombres, icono) in enumerate(epg):
        for n in nombres:
            k = clave(n)
            if k: idx.setdefault(k, pos)
    return idx

def cargar_alias():
    alias = {}
    f = f"{DIR}/alias_epg.txt"
    if os.path.exists(f):
        for l in open(f, encoding="utf-8"):
            if "=" in l and not l.strip().startswith("#"):
                a, b = l.split("=", 1)
                alias[clave(a)] = clave(b)
    return alias

def buscar_epg(k, idx, comp, alias):
    """Devuelve la posición del canal en la guía o None."""
    if k in alias and alias[k] in idx: return idx[alias[k]]
    if k in idx: return idx[k]
    k2 = EXACTOS.get(k, k)
    for a, b in SINONIMOS:
        k2 = re.sub(rf"\b{re.escape(a)}\b", b, k2)
    if k2 in idx: return idx[k2]
    c = compacto(k2)
    if c in comp: return comp[c]
    for m in difflib.get_close_matches(c, list(comp), n=3, cutoff=0.88):
        if re.findall(r"\d+", m) == re.findall(r"\d+", c):   # los números deben coincidir
            return comp[m]
    return None

def main():
    os.makedirs(DIR, exist_ok=True)
    epg = cargar_epg()
    idx = indice_epg(epg)
    comp = {compacto(k): p for k, p in idx.items()}
    alias = cargar_alias()

    cats = {c["category_id"]: c["category_name"] for c in api("get_live_categories")}
    live = api("get_live_streams")
    out = [f'#EXTM3U url-tvg="{EPG_URL}" x-tvg-url="{EPG_URL}"']
    sin_epg = []

    for pq in PAQUETES:
        nom = pq["nombre"]
        canales = collections.OrderedDict()
        for s in live:
            g = cats.get(s.get("category_id"), "").upper()
            if not g.startswith("ES|") or not pq["grupos"](g) or s["name"].strip().startswith("#"):
                continue
            canales.setdefault(clave(s["name"]), []).append(s)

        pos = {k: buscar_epg(k, idx, comp, alias) for k in canales}
        con = [k for k in canales if pos[k] is not None]
        sin = [k for k in canales if pos[k] is None]
        orden = sorted(con, key=lambda k: pos[k]) + sin      # orden de la guía; el resto al final
        sin_epg += [f"{nom}: {k}" for k in sin]

        cuenta = collections.Counter()
        for capa in range(MAX_CAPAS):
            for k in orden:
                v = sorted(canales[k], key=lambda s: (puntos(s["name"]), s["num"]))
                if capa >= len(v): continue
                s = v[capa]
                if pos[k] is not None:
                    cid, _, icono = epg[pos[k]]
                else:
                    cid, icono = "", s.get("stream_icon") or ""
                out.append(f'#EXTINF:-1 tvg-id="{cid}" tvg-name="{k}" tvg-logo="{icono}" '
                           f'group-title="{nom} {capa+1}",{k}')
                out.append(f'{HOST}/live/{USER}/{PASS}/{s["stream_id"]}.ts')
                cuenta[capa + 1] += 1
        print(f"{nom}: {len(orden)} canales | con guía: {len(con)} | sin guía: {len(sin)}")
        print("   " + "  ".join(f"capa {c}: {cuenta[c]}" for c in sorted(cuenta)))

    open(f"{DIR}/lista_capas.m3u", "w", encoding="utf-8").write("\n".join(out) + "\n")
    open(f"{DIR}/sin_epg.txt", "w", encoding="utf-8").write("\n".join(sin_epg) + "\n")
    print(f"\nLista: {DIR}/lista_capas.m3u\nCanales sin guía: {DIR}/sin_epg.txt")

if __name__ == "__main__":
    main()
```

Notas:
- `clave()`, `puntos()` y `buscar_epg()` son el núcleo; el resto es entrada y salida.
- `SINONIMOS` y `EXACTOS` son alias incorporados: deben ser **datos editables**, no código fijo.
- La guía se descarga en streaming (descomprimiendo el `.gz` al vuelo) y se detiene en la primera emisión, porque los canales van al principio del XML.
- Los alias del usuario (`alias_epg.txt`) tienen prioridad sobre todo lo demás.
