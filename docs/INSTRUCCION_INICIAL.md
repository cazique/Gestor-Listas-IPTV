# LEEME_CLAUDE_CODE.md — Instrucción inicial

Eres Claude Code. Este zip es un kit de inicio completo. Ejecuta lo siguiente **tal cual, sin hacer ninguna pregunta al usuario**. Si algo de este archivo choca con `SPEC.md`, manda `SPEC.md`.

## 1. Preparar el repositorio

1. Descomprime este zip en la **raíz del repositorio actual** (`git rev-parse --show-toplevel`). Si no encuentras el zip como adjunto, búscalo en la carpeta de adjuntos de la sesión, en el directorio actual y en la raíz del repositorio. Tras descomprimir, deja en el repositorio: `SPEC.md`, `fixtures/`, `docs/` y `tools/` (y este archivo en `docs/INSTRUCCION_INICIAL.md`). **No subas el zip al repositorio** (añádelo a `.gitignore`).
2. **El repositorio de GitHub es el de esta sesión.** Averígualo con `git remote get-url origin` y, si tienes `gh`, con `gh repo view` (para conocer también si es público o privado). No pidas el nombre.
3. Si la sesión no tiene remoto o no puedes escribir en él, **no preguntes**: trabaja en local, haz commits igualmente y deja en el informe final los comandos exactos para crear o enlazar el repositorio y subirlo todo.

## 2. Ejecutar la especificación

1. Lee **`SPEC.md` completo** antes de escribir código.
2. Ejecútalo por fases, **empezando por la Fase 0** (sección 13): proyecto mínimo, clave de firma, GitHub Actions, primer Release firmado y **autoactualización completa** (sección 6.4). Publica después una **segunda versión trivial** para que el usuario pueda probar la actualización de extremo a extremo.
3. Continúa con las fases siguientes en la misma sesión, haciendo un commit descriptivo al terminar cada una.
4. **Prioridad máxima:** que la autoactualización funcione. Es el requisito más importante del proyecto.

## 3. Reglas que no se negocian

- **No hagas preguntas.** Decide, impleméntalo y regístralo en `DECISIONES.md`.
- **Verifica versiones y datos actuales** (herramientas de compilación, librerías, acciones de GitHub, documentación de Android) en vez de fiarte de la memoria, y anota la fecha de comprobación.
- **La compilación y las pruebas oficiales las hace GitHub Actions.** Lee el resultado de cada ejecución y corrige hasta que quede en verde. Si no puedes leerlo, dilo en "Verificación pendiente". Nunca declares verificado lo que no se ha ejecutado.
- **Secretos:** la clave de firma y sus contraseñas van **solo** como secretos de GitHub Actions (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Nunca en el repositorio, en los registros ni en el APK. Antes de cada commit, busca en el árbol de archivos claves, contraseñas y direcciones del proveedor, y aborta si aparecen.
- Los archivos de `fixtures/` ya están saneados. Si más adelante aparecieran datos con credenciales, sustituye usuario, contraseña y dirección del proveedor por `{USER}`, `{PASS}` y `{HOST}` antes de guardarlos.
- `docs/PREGUNTAS_IA_RESPONDIDAS.md` resume qué se ha verificado y qué no sobre TiviMate y Android. Úsalo como contexto; no repitas esas consultas.
- `tools/capas_epg.py` es la implementación de referencia en Python del núcleo (sección 15 de `SPEC.md`). `tools/prueba_tivimate.m3u` es una lista de prueba para el usuario.

## 4. Informe al terminar cada fase

Responde al usuario **solo** con esto, en español y en pocas líneas:
1. **Dónde descargar el APK** (dirección de la página de Releases) y la versión publicada.
2. **Qué tiene que hacer en el móvil** (los pasos de la primera prueba).
3. **Qué secretos tiene que guardar.** Entrega el almacén de claves codificado y sus contraseñas **una sola vez**, con la advertencia de que debe guardarlos fuera del chat y de que, si se pierden, no se podrá actualizar la app.
4. **Verificación pendiente**, si la hay, con los pasos exactos.
