# Manual de revisión y asignación manual

La app nunca te obliga a aceptar un emparejado dudoso ni lo pierde en la siguiente sincronización. Todo lo que decides a mano se guarda, tiene prioridad sobre el algoritmo y se puede deshacer.

## 1. La bandeja «Por revisar»

Sección **Revisar** (barra inferior en el móvil y menú lateral en la TV). Ordena los casos por importancia:

| Tipo | Qué significa | Botón principal |
|---|---|---|
| Sin guía | El canal no se ha emparejado con ninguna entrada de la guía de dobleM. Se muestran las tres candidatas más parecidas con su porcentaje | «Confirmar sugerencia» (la primera) o «Elegir otra» (buscador) |
| Emparejado dudoso | Se emparejó por parecido aproximado (menos del 95 %) | «Confirmar sugerencia» o «Elegir otra» |
| Posible duplicado | Dos canales del mismo paquete con nombres muy parecidos y los mismos números | «Sí, unir» (pasan a ser variantes del mismo canal) o «No, son distintos» |
| Canal nuevo | Apareció en la última sincronización | «Visto» |
| Desaparecido con decisiones | Una decisión manual ya no encuentra su canal (ni por id ni por nombre y grupo) | «Descartar decisión» o reasignar desde la ficha |

**Ignorar** quita el caso de la bandeja sin cambiar la lista (también se puede deshacer en el historial).

### Revisar en tanda

Activa el filtro **«Revisar en tanda»**: se muestra un caso cada vez y, al decidir, pasa al siguiente.

Atajos del mando (se muestran en pantalla):
- **⏩ avance rápido**, **CH+** o **N**: siguiente caso.
- **⏪ retroceso**, **CH−** o **P**: caso anterior.
- **Flechas** y **centro**: moverse entre botones y pulsar.
- **Atrás**: salir.

## 2. La ficha del canal

Desde **Lista** (vista previa), toca un canal (o pulsa el centro del mando). Verás el icono, la guía asignada (con el método y la confianza), el paquete y todas sus variantes, de la capa 1 en adelante.

Acciones del canal:
- **Asignar guía**: buscador sobre los 640 canales de la guía, con nombre e icono. Vale para todas las capas y operadoras con esa clave.
- **Quitar guía**: si el emparejado automático se equivocó.
- **Cambiar icono**: pega una URL (vacío = el de la guía).
- **Ocultar canal / Mostrar canal**.

Acciones de cada variante:
- **Preferida**: pasa a la capa 1 aunque no sea la de mayor calidad.
- **Ocultar / Mostrar**: no sale en la lista exportada.
- **Separar**: se convierte en un canal independiente.
- **Unir con…**: pasa a ser una variante de otro canal del mismo paquete.
- **Paquete…**: moverla a otro paquete (M+, Vodafone, Orange o uno propio) o dejarla sin paquete (vuelve a su grupo original).

## 3. Cómo se guardan las decisiones

Cada decisión de variante se guarda con **dos identidades**: el `stream_id` y el nombre normalizado más el grupo de origen. Si el proveedor reorganiza y cambia el `stream_id`, la decisión se vuelve a aplicar por nombre y grupo. Las decisiones de canal (guía, icono, ocultar) se guardan por la **clave** del canal.

- **Historial** (Ajustes → Decisiones manuales → Historial): todas las decisiones, con **Deshacer** y **Rehacer**. Nunca se borran.
- **Exportar (JSON)**: copia de seguridad o paso del móvil a la TV. No incluye credenciales.
- **Importar**: el mismo JSON o el formato simple de `alias_epg.txt` de los scripts de Python, una línea por canal:

```
# comentario
TDP = Teledeporte
VAMOS = M+ Vamos
```

Cada línea se convierte en una decisión «Asignar guía».

## 4. Consejos

- Empieza por los paquetes (M+, Vodafone, Orange): son los que se reparten en capas y los que TiviMate mostrará con más repeticiones.
- Lo que resuelvas aquí no tendrás que asignarlo en TiviMate, donde la asignación de guía es manual y de canal en canal.
- Muchos canales sin guía son regionales, internacionales, HBO o de alquiler, que la guía de dobleM no cubre: ignóralos si no te interesan.
