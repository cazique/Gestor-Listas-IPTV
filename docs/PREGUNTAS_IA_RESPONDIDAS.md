# PREGUNTAS_IA_RESPONDIDAS.md — Resultado de contrastar las respuestas de 4 IAs

Fecha de verificación: 9 de octubre de 2026.

Lo que sigue compara las respuestas de las cuatro IAs con fuentes primarias y con pruebas hechas sobre los archivos reales. Las decisiones ya están incorporadas a `SPEC.md`.

## 1. Resumen

**Confirmado con fuente primaria o con los archivos reales:** A8, B1 (parcial), C2 (parcial), D1, D3, D4, D5 y la versión de Ktor.

**Corregido (alguna IA se equivocó):**
- `dataSync` no sirve para un servidor que deba estar siempre activo (D1).
- Compose for TV **no** está en beta: es estable (D5).
- Las horas de actualización de la guía **no tienen zona horaria declarada** (C2).
- El código 884 **no es de Cloudflare**: la cabecera real fue `server: CDN PROXY SERVICE` (B1).
- Un ajuste de TiviMate ("Preferir logos de la EPG") que una IA afirmó **no está verificado**; no se ha incorporado a nada (A8).

**No verificable desde aquí (Reddit bloqueado, sin documentación oficial de TiviMate):** A1 a A7, B2, B3, C1 y D2. Después guardaste dos hilos de Reddit en PDF; lo que aportan está en la sección 9. Las IAs discrepan entre sí en A1, A2, A4 y A5. Se resuelven con la prueba de la sección 5, en tu Google TV.

## 2. Fiabilidad de cada IA (solo en lo que pude comprobar)

| IA | Valoración |
|---|---|
| IA 2 (la que "pensó 4 min") | Todo lo comprobable era correcto: A8, RFC 9110, D1, D3, D4, D5, Ktor. Marcó como no verificado lo que no lo estaba. Es la mejor calibrada |
| IA 1 (la primera) | Errores: D5 (dijo beta o alfa), D1 (recomendó `dataSync`), A8 (no lo sabía e inventó un ajuste), C2 (afirmó zona horaria sin base), A1 (dijo que sí lee la cabecera sin fuente concreta). Presentó como "confirmado oficial" cosas sin fuente |
| IA 3 | Errores: D1 ("dataSync es el más adecuado"), B1 (atribuyó el 884 a Cloudflare), C2 (licencia y zona horaria como "confirmado en documentación del proyecto", y no lo está). Acertó en A8 y D5 |
| IA 4 | Cautelosa. No pudo abrir la página de A8. B1 mencionó Cloudflare sin base. D1 casi correcto, aunque la declaración en Play Console solo aplica si se publica en Google Play |

## 3. Tabla de verificación

Estados: **Confirmado** (fuente primaria o prueba propia), **Consenso** (las IAs coinciden, sin fuente primaria), **Contradicción** (las IAs se contradicen), **Pendiente** (hay que probarlo).

| P | Qué dijeron las IAs | Mi verificación | Estado | Decisión en SPEC.md |
|---|---|---|---|---|
| A1 | IA 1: sí lee la cabecera. IA 2, 3 y 4: no es fiable, añadir la guía a mano | **Leí el hilo de Reddit que guardaste en PDF** (2021, TiviMate 4.0, un solo usuario): TiviMate no mostró la guía de `url-tvg` y hubo que crear la fuente a mano. Es un dato antiguo y de un caso con tres URLs en la cabecera, no una prueba de la versión actual | Evidencia en contra de IA 1; Pendiente de tu prueba | Asumir que no; la app muestra la URL de la guía para añadirla a mano |
| A2 | IA 1 y 3: sí, la guía sale en todas. IA 2: debería, pero hay informes de duplicados. IA 4: no se sabe | No verificable | Contradicción, Pendiente | Mantener el mismo `tvg-id`; conservar la opción de listas separadas; probar con P1 a P3 |
| A3 | URL con intervalo configurable; archivo local no se relee solo; `127.0.0.1` y la red local por HTTP aceptadas | No verificable | Consenso, Pendiente | URL como vía principal; botón de actualizar |
| A4 | Orden de la lista respetado por defecto (4 de 4). `tvg-chno`: IA 1 dice que solo con ordenación por número; IA 3 y 4 dicen que no se usa; IA 2 sin confirmar | Mi búsqueda no encontró nada de TiviMate sobre `tvg-chno` (solo de otros reproductores) | Orden: Consenso. `tvg-chno`: Contradicción, Pendiente | No depender de `tvg-chno` (opción desactivada) |
| A5 | `#EXTVLCOPT`: no fiable. User-Agent por lista: IA 3 dice que se aplica a todo, IA 4 que no a la descarga de lista y guía | No verificable | Contradicción, Pendiente | Opciones desactivadas; probar con el servidor de registro (sección 5) |
| A6 | Emparejado exacto, sensible a mayúsculas y espacios; admite `.gz`; varias fuentes en Premium | La página de dobleM confirma que publica variantes `.xml.gz` pensadas para TiviMate. **El hilo "Multiple EPG in playlist" (2024) indica que en Premium se pueden añadir varias fuentes de guía, que hay que activarlas dentro de la lista y que la asignación por canal es una a una.** El resto, no verificable | Consenso (`.gz` y varias fuentes: Confirmado por esos dos puntos) | Copiar el `id` exacto de la guía; sin normalizar |
| A7 | Multivista y grabación pueden abrir conexiones extra | No verificable, plausible | Consenso | Avisos en la app y en el README |
| A8 | IA 2 y 3: recomienda `guiatv_sincolor*.xml.gz`; TiviMate no soporta imágenes ni colores. IA 1 y 4: no lo sabían | **Leí la página.** Seis variantes, todas `.xml.gz`; confirma lo de IA 2 y 3 | **Confirmado** | Variante por defecto `guiatv_sincolor.xml.gz`, con las otras cinco seleccionables |
| B1 | 884 no es estándar; causa desconocida. Algunas IAs lo atribuyen a Cloudflare, IP o User-Agent | RFC 9110: los valores fuera de 100 a 599 son inválidos y las implementaciones usan 600 a 999 para comunicación interna de estados no HTTP. La cabecera que vimos es `CDN PROXY SERVICE`, no Cloudflare | **Confirmado** (no estándar). Causa: no verificable | No codificar el significado ni intentar saltarse el bloqueo |
| B2 | `active_cons` útil pero con retraso; al superar el límite se corta o se rechaza | No verificable | Consenso | Dato orientativo; esperar entre pruebas |
| B3 | 3 IAs: BK es "backup", RAW es mejor calidad. IA 2: ninguna etiqueta es estándar | No verificable | Contradicción | A igual calidad, `BK` después; RAW no es automáticamente lo mejor; medir tasa de bits (sección 7.3) |
| C1 | IA 1: coincide en gran medida. Las demás: no está confirmado | No pude contrastarlo con los dorsales oficiales | No verificable | El orden de la guía es el orden preferido, sin afirmar que sean los dorsales oficiales |
| C2 | Licencia, zona horaria y `.gz` | **Probado:** `guiatv.xml.gz`, `sincolor` y `sincolor2` tienen los mismos 640 IDs en el mismo orden. Las emisiones llevan desfase `+0200`. El README no declara zona horaria. El listado de archivos del repositorio no incluye ningún `LICENSE` | `.gz` y desfase: **Confirmado**. Zona horaria de las actualizaciones y licencia: no declaradas | No empaquetar la guía ni los iconos en el APK; no fijar zona horaria |
| D1 | IA 1 y 3: `dataSync`. IA 2: no `dataSync`. IA 4: `specialUse` | **Documentación de Android (1-10-2026):** `dataSync` tiene un límite de 6 horas por cada 24 desde Android 15; `connectedDevice` exige permisos concretos; `specialUse` es de uso libre con una propiedad explicativa | **Confirmado** (IA 2 acertó) | `specialUse`, iniciado por el usuario, con notificación y botón de parada |
| D2 | 4 de 4: muchos televisores no traen selector de carpetas | No probado | Consenso, Pendiente | SAF si existe; si no, `MediaStore`, compartir o servidor local |
| D3 | Las 4: `EncryptedSharedPreferences` está obsoleta | **Referencia oficial:** "Deprecated in 1.1.0" y remite a `SharedPreferences`, que no cifra. `DataStore` tampoco cifra | **Confirmado** | Clave en Keystore (AES-GCM o Tink) y valores cifrados en DataStore |
| D4 | IA 3 y 4 sugirieron `domain-config` para un host configurable; IA 2 matizó | **Documentación de Android:** desde Android 9 el tráfico en claro está desactivado; `domain-config` exige dominios fijos escritos de antemano; para cualquier dominio solo vale `base-config`, que la documentación desaconseja | **Confirmado** (IA 2 acertó) | HTTPS primero; HTTP solo con consentimiento por host; sin degradar ante errores de certificado |
| D5 | IA 1: beta o alfa. IA 2, 3 y 4: estable | **Documentación de AndroidX (última actualización 6-5-2026):** `tv-material` 1.1.0 y `tv-foundation` 1.0.0, ambas estables | **Confirmado** (IA 1 se equivocó) | Compose for TV estable. Ktor: última versión 3.6.0 (17-9-2026), a verificar de nuevo al implementar |

## 4. Cambios aplicados en SPEC.md

1. Guía por defecto: `guiatv_sincolor.xml.gz`, con las seis variantes seleccionables y la comprobación de que comparten canales, IDs y orden. El código de referencia ya descomprime el `.gz` al vuelo (probado: 640 canales en una fracción de segundo).
2. Horas de la guía: sin zona horaria fija; sincronización cada pocas horas.
3. Licencia: la guía y los iconos se descargan de la URL remota, no se empaquetan.
4. Servidor local: `specialUse` en lugar de `dataSync`; opción de subir a un servidor propio para disponibilidad permanente.
5. Credenciales: Keystore más DataStore, sin `EncryptedSharedPreferences`.
6. HTTP: HTTPS primero, consentimiento por host y política global documentada.
7. Calidad: `RAW` ya no se da por mejor; `BK` va después a igual calidad; la app mide la tasa de bits real de cada variante.
8. Enlaces: aviso del retraso de `active_cons`.
9. Compose for TV y Ktor: versiones estables verificadas.

## 5. Prueba de 10 minutos en tu TiviMate

Usa `prueba_tivimate.m3u`. No lleva credenciales y sus streams apuntan a `127.0.0.1:9`, una dirección que no existe, así que **no gasta tu conexión del proveedor**. Sirve para ver guía, iconos y orden, no vídeo.

**Antes de empezar:** crea la lista en TiviMate **sin añadir ninguna fuente de guía previamente**.

1. **A1 (cabecera):** tras importar la lista, abre los ajustes de esa lista y mira sus fuentes de guía. ¿Aparece sola la dirección de dobleM? Si no aparece, añádela tú como fuente XMLTV con `https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/guiatv_sincolor.xml.gz` y actualiza la guía.
2. **A2 (mismo `tvg-id`):** en la guía, mira `P1`, `P2` y `P3`. Las tres usan el ID `La 1 HD`, en dos grupos distintos y una repetida dentro del mismo grupo. Anota cuáles muestran programación.
3. **A6 (mayúsculas y nombre):** `P4` usa `la 1 hd` en minúsculas y `P8` no tiene `tvg-id`, solo `tvg-name`. Anota si alguna de las dos recibe programación.
4. **A4 (orden y números):** en el grupo "Prueba D", `P5`, `P6` y `P7` tienen `tvg-chno` 3, 1 y 2 pero están en ese orden en el archivo. Anota en qué orden salen y qué número muestra cada una.
5. **A3 y A5 (opcional):** en tu PC o en la DietPi, en la carpeta del archivo, ejecuta lo siguiente y carga la lista desde `http://IP:8000/prueba_tivimate.m3u`. El registro muestra el User-Agent con que TiviMate descarga la lista. Si cambias el archivo y fuerzas la actualización, ves si lo relee.

```
python3 - <<'PY'
from http.server import SimpleHTTPRequestHandler, HTTPServer
class H(SimpleHTTPRequestHandler):
    def do_GET(self):
        print(self.path, '| User-Agent:', self.headers.get('User-Agent'))
        super().do_GET()
HTTPServer(('0.0.0.0', 8000), H).serve_forever()
PY
```

Con lo que anotes se cierran A1, A2, A4, A6 y parte de A3 y A5. Pásame los resultados y actualizo `SPEC.md`.

## 6. Qué sigue abierto

| Duda | Cómo se cierra |
|---|---|
| A1, A2, A4, A5, A6 y A3 (en parte) | Prueba de la sección 5 |
| A7 (conexiones con una sola) | Observar `active_cons` con la función de comprobar enlaces, una función de TiviMate cada vez |
| B2 y B3 | Función de comprobar enlaces con medida de tasa de bits |
| C1 | Comparar con la guía oficial de Movistar Plus+ en el decodificador o la app |
| C2 (licencia) | Preguntar al autor de dobleM |
| D2 | Probar `ACTION_OPEN_DOCUMENT_TREE` en tu Google TV |
| Si los enlaces repetidos son la misma fuente y la primera sincronización real con tu proveedor | Solo con pruebas en tu cuenta |

## 7. Pruebas añadidas (aportadas por la síntesis de Gemini, con un ajuste)

**Prueba 6: retraso de `active_cons`.** Con VLC o TiviMate reproduciendo un canal, ejecuta esto en tu PC o en la DietPi (con tus datos en los marcadores). Anota cuánto tarda el valor en pasar de 0 a 1 al empezar y de 1 a 0 al cerrar. Ese tiempo es la espera que hay que configurar en la app entre comprobaciones. Consultar la API no debería abrir un stream, pero no está verificado en tu proveedor: no bajes de 5 segundos entre consultas.

```
while true; do date +%T; curl -s -A "VLC/3.0.20" "http://HOST/player_api.php?username=USUARIO&password=CONTRASENA" | python3 -c "import sys,json; print(json.load(sys.stdin)['user_info']['active_cons'])"; sleep 5; done
```

**Prueba 7: selector de carpetas en Google TV, sin compilar nada.** En lugar de crear una app solo para esto, lanza la acción desde `adb` (necesitas la depuración por red activada en el Google TV). No he probado este comando en un Google TV: si responde que no puede resolver el intent, no hay selector; si abre uno, sí existe.

```
adb shell am start -a android.intent.action.OPEN_DOCUMENT_TREE
```

## 8. Contraste con la síntesis de Gemini

Gemini acierta en lo principal: ya no aporta más seguir preguntando a IAs, porque lo que queda abierto solo se resuelve probando en tu Google TV. Pero **no pegues su bloque en `SPEC.md`**: sustituiría decisiones ya verificadas por afirmaciones sin base o incorrectas.

| Afirmación de Gemini | Veredicto | Qué dice `SPEC.md` |
|---|---|---|
| "Consenso técnico muy sólido" | Falso en A1, A2, A4 y A5: las IAs se contradicen | Esos puntos quedan como pendientes de prueba |
| Exigir `tvg-id` exacto; no depender de `url-tvg` ni de `tvg-chno` | Compatible, pero como prudencia, no como hechos comprobados | Igual, marcado como no verificado |
| Incluir `#EXTVLCOPT:http-user-agent` por defecto | No hay evidencia de que TiviMate lo use | Opción desactivada hasta probarlo |
| `get.php` "bloqueado por firewall con error 884" | Solo se sabe que 884 no es un código HTTP estándar; la causa no es verificable | Sin atribuir causa |
| `RAW` como mayor tasa de bits | Suposición no confirmada | Se trata como suposición y se mide la tasa de bits real |
| El orden del XML es "el dial canónico" | No verificado: no se ha cruzado con los dorsales oficiales | Es el orden de la guía de dobleM, sin afirmar que sean los dorsales de Movistar |
| Las horas de actualización son hora peninsular | El README no lo declara | No se fija zona horaria; las emisiones llevan su propio desfase |
| Servicio en primer plano `specialUse` o `dataSync` | **Error en `dataSync`:** la documentación de Android lo limita a 6 horas por cada 24 desde Android 15 | Solo `specialUse` |
| HTTP con consentimiento "sin aplicar políticas globales" | **No es posible con un host variable:** Android solo permite HTTP por dominios fijos o con una política global | Política global más consentimiento por host dentro de la app |
| Servidor local como vía principal y `MediaStore` como secundaria | Compatible; depende de la prueba de actualización de TiviMate | Servidor opcional, exportación a archivo y subida a servidor propio |

Sobre sus 4 pruebas: la 1 ya la cubre `prueba_tivimate.m3u`; la 2 está dentro de la prueba de la sección 5, con registro del User-Agent; la 3 es la prueba 6; y la 4 la he sustituido por la prueba 7.

## 9. Evidencia de dos hilos de Reddit (guardados en PDF por Jorge)

El script `reddit2pdf.py` falló: las ocho direcciones devolvieron error 404 desde Termux, y no puedo saber si Reddit bloquea las peticiones o si alguno de los identificadores que dieron las IAs es incorrecto. Los PDFs guardados desde el navegador sí funcionaron. Los dos hilos coinciden por tema y por fecha con dos de las direcciones citadas, pero los PDFs no muestran la URL, así que no puedo confirmar que sean esas.

**Hilo 1: "Tivimate not able to use url-tvg ?" (hace unos 5 años, TiviMate 4.0 en un Sony Smart TV).**
- El autor importó una lista de iptv-org cuya cabecera llevaba `url-tvg` con tres direcciones separadas por comas. TiviMate no mostró la fuente de guía en la lista ni datos de guía en los canales. Al copiar la dirección a mano, crear una fuente de guía y asignarla a la lista, la guía apareció.
- El autor añade que creía que TiviMate usaría la primera dirección, "pero no en mi caso", y que parece que hay que añadir explícitamente en TiviMate la segunda, tercera y cuarta entradas.
- Un usuario sugirió usar `x-tvg-url` en lugar de `url-tvg`, "que funcionaba en OTT Navigator" (no dice que funcione en TiviMate).
- **Qué prueba:** que en 2021, con TiviMate 4.0, un usuario no consiguió que la cabecera cargara la guía. **Qué no prueba:** cómo se comporta la versión actual, ni qué pasa con una sola dirección. Por eso la prueba P1 de `prueba_tivimate.m3u` sigue siendo necesaria.
- Los PDFs dejan ver, en la barra lateral, un hilo con el título "Cannot get TiviMate to recognize x-tvg-url inside m3u playlist header" (hace unos 2 años, 6 comentarios). Existe, pero no se ve su contenido.

**Hilo 2: "Multiple EPG in playlist" (hace unos 2 años).**
- Un comentario dice que se pueden tener varias fuentes de guía añadiéndolas en las fuentes de guía y asignándolas **a cada canal manualmente**. Otro añade que hay que hacerlo **uno por uno** (el autor preguntó si se podía seleccionar varios a la vez) y que **hay que activar la fuente dentro de la lista**. Un tercero dice que hace falta la versión Premium.
- Un usuario cuenta que, por la ruta Guía, canal, "Assign EPG", solo veía las opciones de la fuente principal. No se ve respuesta en lo guardado; el PDF muestra "1 more reply" sin desplegar.
- **Qué prueba:** que TiviMate Premium admite varias fuentes de guía, que hay que activarlas dentro de la lista, y que la asignación de guía por canal en TiviMate es manual y de una en una. Esto refuerza el valor de que nuestra app deje el `tvg-id` ya puesto en el M3U.

**Hilos de la barra lateral que merece la pena guardar** (solo vi los títulos; busca cada título en Reddit): "Cannot get TiviMate to recognize x-tvg-url inside m3u playlist header" (A1); "use gz epg url ?" (A6, `.gz`); "Does tivimate take long to update playlist/epg?" y "Impossible to update playlist" (A3, actualización de listas); y "TiviMate Users Guide" (hace unos 9 días, 115 votos).

**Consejo para guardar hilos completos:** abre la versión antigua de Reddit (cambia `www.reddit.com` por `old.reddit.com` en la dirección) antes de imprimir a PDF. Muestra los comentarios desplegados; en la versión nueva quedan recogidos y falta contenido, como el "1 more reply" del hilo 2.
