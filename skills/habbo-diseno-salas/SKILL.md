---
name: "habbo-diseno-salas"
description: Diseñar, construir y corregir salas de Habbo en JSON de furnis (export/import) con bloques BC, pisos, muros, techos, pixel art, casas, escenas y decoración por capas. Usar cuando el usuario hable de diseño de sala, decorar, construir, furnis, BC, bloques, pixel art, coordenadas x/y/z, pisos, techos, mapas, o pida armar/corregir una sala o escena.
---

# Habbo: Diseño de salas

Skill para planificar y generar el JSON de furnis de una sala (posiciones, rotaciones, alturas, estados). El usuario escribe en español; responde en español.

Para wireds, presets y juegos usar la skill `habbo-wired`. Esta skill solo cubre la parte física/visual de la sala. Si la sala incluye wireds, estos van en `furni` y se entregan solo como pilas (sin definiciones de variables), ver sección 9.

## 0. Flujo de trabajo

1. **Entender el pedido**: temática, tamaño (ancho × largo en baldosas), si es de 1 o varios pisos, para qué se usará (juego, hangout, tienda, evento) y si hay medidas o un `roomLayout` existente.
2. **Proponer un plano corto antes de generar JSON**: zonas (entrada, zona principal, decoración, zonas de juego), paleta de colores, 3 a 6 furnis protagonistas. Si la sala es grande, esperar OK.
3. **Construir por capas** (sección 2), de abajo hacia arriba.
4. **Generar el JSON** respetando el formato de la sección 1.
5. **Revisar con la checklist** de la sección 8.
6. **Avisar** de lo que no se puede resolver solo con JSON (furnis sin equivalente en catálogo, wireds por configurar a mano, límites de furnis de la sala).

Si el usuario pasa un JSON o `.txt` con una sala, **leerlo primero**: extraer el `roomLayout`, los `className` usados, las alturas z y el estilo, y mantener la coherencia con eso al modificarla.

## 1. Estructura del JSON

- Raíz: `{wired:{...}, bindings, furni}`; a veces `roomLayout` `{modelName, floorplanWidth, floorplanHeight, scale, floorplanSignature, wallHeight}`, `srcAnchorFloorHeight` y `adsBackgrounds`, o `exportOrigin` `{x, y, floor}`. No asumir que `roomLayout` existe: hay exports de salas sin él. En una sala sin wireds, `wired` trae sus 7 listas vacías y `bindings` va vacío.
- Cada furni: `{id, name "clase[n]", className, location{x,y,z}, rotation}`. Muchos furnis añaden `state` (en una sala de ejemplo, 993 de 1737), no solo los BC. **`state` es un string** (`"0"`, `"3"`) y elige la variante/estado del furni. Algunos furnis traen `variables` precargadas.
- `x` e `y` son enteros; solo `z` lleva decimales.
- Los furnis normales y las antenas van **solo** en `furni`, sin entrada en `wired`.
- `id` único por furni. No reutilizar IDs al añadir elementos nuevos: continuar desde el máximo existente.
- En el JSON se usa el `className` (ej. `soft_sofachair_norja*9`), **no** el ID numérico del catálogo.
- `z` puede venir como `1.49012E-8` (≈ 0).
- Los textos con acentos van en **doble codificación** (utf8 leído como latin1, "nÃºmero"); mantener la convención del archivo.
- Catálogo para buscar classname/ID: habbofurni.com/furniture.

## 2. Capas de construcción

Construir siempre en este orden:

1. **Piso base** a z 0: furnis 2×2 cada 2 baldosas, o un bloque BC por baldosa (ver sección 12) (`lt_patch`, `garden_grass`, `seaside_c26_sand`, `party_floor`, `xmas11_woodfloor`, `tile_marble`, `tile_brown`, `pixel_floor_silver`…).
2. **Detalles de piso** a z 0.001 – 0.1 (alfombras, caminos, marcas, líneas de luz).
3. **Muebles y objetos** (sofás, mesas, plantas, estructuras).
4. **Muros y decoración vertical**, apilados.
5. **Techo** y elementos aéreos.

### Superponer sin chocar
- Sumar milésimas para evitar parpadeo/choque visual: 0.001, 0.01, 4.0001.
- Evitar repetir **el mismo furni** en la misma x, y, z. Clases distintas en la misma posición sí se usan a propósito (piso + pared, planta sobre piso, basura sobre otra, fondos); en una sala de ejemplo hay ~80 casos así y son capas intencionales, no errores.
- **Segundo piso**: pisos a z 3.2 – 4.4, muebles a z 4.0001, techo a ~8 – 9.
- **Escenas flotantes** hasta z ≈ 40.

### Apilar (delta z)
| Furni | Altura que suma |
|---|---|
| bc_block_1 | 1.0 (state 3 ≈ 0.25) |
| bc_block_0 | 1.0 |
| bc_panel | 2.0 |
| bc_round | 0.15 – 0.25 |
| bc_ramp | 1.1 |
| bc_cylinder | 1.0 (en una sala de ejemplo se apilan de 0.5 en 0.5) |
| bc_block_small | 0.25 (observado en apilados de 0.25) |
| greek_pillars | 3.0 |
| xmas11_elewood | 0.5 |
| tokyo_c18_sakura | 2.2 |
| present_wrap | 3 |
| catcafe_c23_wall | 4 |
| xmas_c16_wall | 7 – 9 |
| wired | 0.65 (addons 0.37) |

### Rotaciones
- Pares (0 / 2 / 4 / 6) = ejes (norte, este, sur, oeste).
- Impares (1 / 3 / 5 / 7) = diagonales; solo sirven en piezas que las admiten (`bc_quartercircle`, rocas, prismas). Usarlas en otras piezas puede dar un resultado inesperado.

## 3. Bloques BC

- Nombre: `bc_<forma>*N`, donde **N = color** del selector del catálogo BC (11 por fila, `*1` = primer cuadro).
- No existe negro puro: oscuros = `*60` y `*13`. Blanco = `*14`. Grises = `*14`, `*19`, `*18`, `*13`. Tonos de piel = `*42` / `*43`.
- `state` cambia el grosor (state 3 = placa delgada).
- Letras y números: `bc_alpha1_<letra>` lleva la letra en el nombre; `bc_alpha1_num` usa `state` como dígito (0 – 9).

### Paleta BC (hex)
1 #f5eabb, 2 #fed736, 3 #fe9800, 4 #fe98bb, 5 #e04117, 6 #662812, 7 #007122, 8 #91d03c, 9 #ab59b0, 10 #8259b0, 11 #5da9f6, 12 #aacfd1, 13 #515151, 14 #fefefc, 15 #98fecb, 16 #656500, 17 #6e3c0b, 18 #767f83, 19 #bbbfc3, 20 #a0e7fb, 21 #2bbefe, 22 #0073fe, 23 #0000fe, 24 #0000c7, 25 #0084fe, 26 #6584fe, 27 #bbb6fe, 28 #dcb5fc, 29 #9b72fe, 30 #673dfe, 31 #3e1ac6, 32 #95008d, 33 #dd00d6, 34 #fc71fe, 35 #fcb5fc, 36 #f3bee0, 37 #fe539b, 38 #e6005e, 39 #ad0023, 40 #fe3500, 41 #fe7751, 42 #f3d0b2, 43 #fbe0b0, 44 #fca03c, 45 #e65f00, 46 #8c1500, 47 #503000, 48 #ac8000, 49 #fbd87d, 50 #99bc00, 51 #daf76e, 52 #b9f800, 53 #00b900, 54 #007800, 55 #2d622d, 56 #00a900, 57 #51d94f, 58 #b5f86f, 59 #89ad00, 60 #0c370b, 61 #005800, 62 #00a842, 63 #4df898, 64 #adefd7, 65 #00f6fb, 66 #00e8e4, 67 #008794, 68 #003f58, 69 #326565.

Para elegir un color: tomar el hex objetivo, buscar el más cercano en esta tabla y usar ese `N`. Si la diferencia es notable, decirlo al usuario.

## 4. Pixel art con BC

- Hacerlo en un **plano vertical** (y fijo) con `bc_block_1` apilado de a 1 en z.
- **No mezclar** `bc_block_0` y `bc_block_1` en un mismo piso (alturas distintas).
- Diagonales y medios píxeles: pares de `bc_block_0` con rotation 6 y 2 superpuestos (z y z + 0.02).
- Flujo recomendado: definir la cuadrícula (ancho × alto en píxeles) → mapear cada color de la imagen a un `*N` de la paleta → generar un furni por píxel (omitir los transparentes) → numerar `id` de forma correlativa.
- Avisar del total de furnis antes de generarlos: una imagen de 32×32 puede ser ~1000 furnis.

## 5. Plantillas de construcción

### Casa BC
- Cimiento: `bc_stone` o `bc_block_1` state 3.
- Paredes: `bc_panel` apiladas cada 2 en z.
- Techo a dos aguas con `bc_quartercircle`; esquinas con rotation 3 / 5.
- Escalera: `bc_stairs`.
- Columnas: `bc_cylinder` con `bc_round` encima.

### Sala de varios niveles
- Planta baja: piso z 0, muebles a su altura natural.
- Segunda planta: piso a z 3.2 – 4.4, muebles a z 4.0001, techo a ~8 – 9.
- Conectar con `bc_stairs`, `bc_ramp` o teleports (`bb_rnd_tele`).

### Sala de juego / evento
- Zona de juego despejada y delimitada con bloques o `room_invisible_block`.
- Asientos con `room_invisible_sit_tile` sobre los muebles de sentarse.
- Zona de espera/entrada separada de la de juego.
- Reservar baldosas libres para wireds, relojes y marcadores (z 0.65 cada wired).

### Escena flotante
- Sube hasta z ≈ 40; mantener una zona caminable a z 0 o un teleport de acceso.

## 6. Catálogo disponible y ambientación

- **Todos los furnis del Almacén del Club del Arquitecto se pueden usar** (Bloques del Arquitecto, Bloque Alfabeto, Tendencia, Por tipo, Por diseño, Por ambiente, Gaming, Wired, Construcción de sala). No limitarse a los `bc_*`: mezclar con los de las colecciones temáticas.
- El Almacén tiene un **límite de furnis prestados (7700)**. Avisar del total de furnis que consume cada diseño.
- Usar solo `className` que el usuario haya mostrado o que se vean en sus ejemplos; si no se está seguro de uno, decirlo.

### Ambientación

- **Elegir una paleta** de 3 – 5 colores y repetirla; no mezclar más estilos de los necesarios.
- **Iluminación**: `hs_lightstrip_blue`, `disco_c26_lightbarblue`, `snowb_light1`, `fireworks_06`.
- **Pisos y alfombras**: `tile_marble`, `tile_brown`, `tile_stella`, `pixel_floor_silver`, `val12_marble2`, `celestial_c19_rug`, `tile_walkmagic`, `queue_tile1*8`.
- **Asientos**: `soft_sofachair_norja*N` (el color va en `*N`).
- **Divisores**: `icedblk_c16_div3*2`.
- **Utilitarios invisibles**: `room_invisible_sit_tile`, `room_invisible_block`, `conf_area_hide` (oculta zonas).
- Dejar pasillos de al menos 1 baldosa entre muebles para que los usuarios puedan caminar.
- Evitar sobrecargar: priorizar pocas piezas llamativas bien ubicadas sobre muchas piezas pequeñas.

## 7. Cómo generar el JSON

1. Fijar el `roomLayout` (dimensiones) o respetar el existente.
2. Crear los furnis por capas, de abajo hacia arriba, con `id` correlativos.
3. Agrupar por zona en el orden de generación (piso → muebles → muros → techo) para que sea fácil de revisar y de editar.
4. Usar bucles/patrones para repeticiones (pisos 2×2, paredes apiladas, filas de píxeles) en lugar de escribir cada furni a mano; verificar el conteo final.
5. Entregar el archivo `.json` y un resumen corto: dimensiones, total de furnis por capa, paleta usada y avisos.

## 8. Checklist de revisión

- **IDs** repetidos o saltos que rompan referencias.
- **Colisiones**: el mismo furni repetido en la misma x, y, z sin intención; piezas que se atraviesan (capas distintas superpuestas a propósito sí valen).
- **Alturas**: z coherente con la tabla de apilado; muebles del segundo piso a 4.0001, no a 4.0.
- **Rotaciones impares** en piezas que no son diagonales.
- **Mezcla** de `bc_block_0` y `bc_block_1` en el mismo plano de pixel art.
- **Colores BC** fuera de rango (`*N` mayor al disponible) o negro "puro" inexistente.
- **Furnis inexistentes**: classnames inventados o con el ID numérico en vez del `className`.
- **Accesibilidad**: pasillos, escaleras y entradas transitables; asientos alcanzables.
- **Cantidad de furnis**: avisar si la sala es muy pesada.
- **Convención de acentos** (doble codificación) cambiada por error.
- **Wireds**: furnis de wired sin entrada en `wired` (hay que configurarlos a mano) o antenas sin pareja.

## 9. Si la sala lleva wireds

- Los wireds y antenas van en `furni`; los wireds con configuración van además en `wired` con `wiredId` = `id` del furni.
- En los JSON de wireds entregar **solo la configuración principal (las pilas)**; no incluir las definiciones de variables (usuario/contexto) ni sus cajas de tiempo, porque la sala no permite dos variables iguales.
- Para lógica de juego, comandos, variables y pilas, aplicar la skill `habbo-wired`.

## 10. Reglas de estilo al responder

- Mostrar primero el plano/propuesta; el JSON después.
- Ser explícito con lo que es estimación (alturas de furnis poco comunes, colores aproximados) y lo que está confirmado en esta skill.
- No inventar `className`: si no se está seguro de que existe, decirlo y sugerir buscarlo en habbofurni.com.
- Si el usuario pide modificar una sala existente, **conservar** lo que no se pidió cambiar (IDs, posiciones, estilo).

## 11. Sala de ejemplo analizada: `sala_bellitsima.json`

Sala del usuario usada como referencia de estilo. Todo lo de abajo es **observado** en ese archivo (no es una regla confirmada).

- **Tamaño**: 1737 furnis, 280 clases distintas, x 1–57, y 4–42, z 0–25. Sin wireds, sin `roomLayout`, `exportOrigin {x:0,y:0,floor:0}`.
- **Estilo**: pueblo fantasía/mediterráneo por niveles (Santorini + elfos de Pascua + oeste + agua japonesa), con plataformas, torres de bloques, techos y vegetación.
- **Clases más usadas**: `br_c25_roof` (132), `bc_block_1` (123), `santorini_c17_pebblefloor` (108), `wildwest_stagew2` (89), `jp_c22_water` (87), `easter_c26_elvenwall` (77), `bc_round` (37), `easter_c22_rockerydark` (35), `thai_c21_waterrock` (29), `easter_c26_elvendivider` (26), `santorini_c17_gate` (24), `santorini_c17_pottedplants` (23), `val15_water` (23), `xmas11_elewood` (23), `jungle_c16_ctrick` (22), `lm_bigrock` (21).
- **Colores BC usados**: `*14` (blanco), `*5` (rojo), `*36` (rosa claro), `*20` (celeste). Colores en no-BC también van con `*N` (`chair_norja*3`, `bonusrare16_5*4`).
- **Pisos 2×2**: se colocan en coordenadas pares con paso 2 (ej. `santorini_c17_pebblefloor` en (4,23), (4,25), (6,23)…). Plataforma principal a **z 4**; el suelo base y el agua (`jp_c22_water`, `val15_water`) a z 0. `wildwest_stagew2` a z 0.5.
- **Niveles**: la altura se organiza en niveles de ~4 de z (0, 4, 8, 12, 16, 20); la mayor concentración está en z 0–2 (735 furnis) y z 4–6 (380).
- **Torres**: columnas de `bc_block_1` (state "0") o `bc_round` (state "0") que empiezan en **z 4.5** (sobre una plataforma a z 4) y suben de 1 en 1, rematadas con `bc_block_1` state "3" (placa) a ~18.1 y una valla encima (`easter_c17_fence` a 19.1).
- **Techos y paredes**: `br_c25_roof` a z 5.3 – 23.3 (mitad con state "3", mitad state "0"); `easter_c26_elvenwall` a z 4, 7, 8, 11, 12… con state "0" o "5".
- **Rotaciones**: casi todas pares (0: 1056, 2: 437, 4: 175, 6: 60); solo 9 impares en 1737.
- **Fondo**: `roombg_stars` a z 0 (varios, incluso repetidos en la misma posición).
- **Reglas que se deducen**: pisos por módulos de 2; torres que arrancan medio punto sobre la plataforma; remates en state 3; detalles de vegetación y agua en z 0; capas repetidas intencionales.

## 12. Segunda sala de ejemplo analizada: `sala_de_mian_2000.json`

Todo lo siguiente es **observado** en el archivo (no confirmado como regla).

- **Tamaño**: 965 furnis, 102 clases, x 1–42, y 1–39, z 0–31.13. Incluye una pequeña lógica de wireds (4 triggers, 4 efectos, 2 addons, 1 condición, 1 selector, 6 bindings) y **sin variables definidas**.
- **Estructura**: es una sala BC en dos zonas. Un suelo grande a z 0 y una construcción elevada a z 15 – 31. Entre z 2 y z 14 casi no hay furnis (en un conteo por franjas de 2: 269 en z 0 – 2, 184 en z 14 – 16, 171 en z 18 – 20, resto repartido hasta z 30).
- **Suelo de bloques**: 268 `bc_block_1*14` con `state "4"`, uno por baldosa (1×1) a z 0. Distinto de la sala anterior, donde el piso eran furnis 2×2.
- **Paleta**: casi todo `*14` (blanco, 522 furnis) y `*52` (verde lima #b9f800, 199 furnis). Los acentos son pocos (`*5`, `*7`, `*2`, `*6`, `*4`, `*15`, `*3`). Esquema de dos colores + blanco.
- **Clases dominantes**: `bc_block_1` (517), `wf_teamcolortile` (80), `bc_block_small` (69), `bc_round` (39), `bc_cylinder` (32), `lt_patch` (20), `bc_alpha1_num` (20).
- **Estados de bloques**: `bc_block_1` state "4" (270), "0" (177), "3" (70); `bc_round` state "0" (29) y "3" (9). `bc_block_small` y `bc_cylinder` siempre "0".
- **Alturas**: `bc_block_1` y `bc_round` apilan de 1 en 1 desde z 15; `bc_block_small` en pasos de 0.25 (15, 15.25…); `bc_cylinder` en pasos de 0.5 (15.5, 16, 16.5…). `lt_patch` a z 15 / 15.01 / 15.5 / 16 y `tile_walkmagic` a z 15 – 15.5.
- **Marcador**: a z 18, `bc_alpha1_o/k/e/w` (cada una ×5) en x 24, y 36–39 forman un rótulo, y 20 `bc_alpha1_num*14` (state "0") a lo largo de y 35 forman los dígitos. Junto a ellos, 80 `wf_teamcolortile` (16 por cada state 0, 2, 4, 6, 8) en x 25–42, y 22–39 a z 18: son las baldosas de equipo (5 colores).
- **Wireds en la pila**: `wf_trg_user_performs_action` a z 15.3001 y `wf_act_show_message` a z 15.9501: la diferencia de 0.65 confirma el apilado de wireds (0.65) y el `+0.0001` evita choques con el piso.
- **Cableado**: 14 `wf_wire1` a z 15 y 2 `wf_wire3`.
- **Bindings con snapshot**: `{altitude, wiredId, furniId, rotation, location}` con `altitude` en centésimas (1500 = z 15.0, 1510 = 15.1) y `location {x,y}`.
- **Wireds nuevos vistos**: `wf_act_match_to_sshot` options `[1,1,1,1,0]` con furniSources [100] e items; `wf_xtra_mov_physics` options `[1,1,1,0]` con userSources [900] y furniSources [900,900]; `wf_trg_user_performs_action` options `[5]` y `[6]`; `wf_slc_users_area` options `[18,26,6,5]` (los valores 6 y 5 son menores que 18 y 26, así que probablemente es x, y, ancho, alto y no x1, y1, x2, y2; sin confirmar); `wf_cnd_slc_quantity` options `[1,0,1]`.
- **Textos**: los mensajes llevan doble codificación (`Â¡ !`) y estilos 34 y 252.
- **Rotaciones**: pares casi siempre (822 con 0), ninguna impar.
- **Duplicados**: 3 casos del mismo furni en la misma posición (apilados de textos/elementos en una celda).

## 13. Lecciones de la sala de Halloween "crematorio" (12/10/2026)

- Pedir **coordenadas del plano y orientación** antes de generar (una sala 24×10 se pegó girada y en otra esquina); `:ip x,y` debe coincidir con el `exportOrigin` del preset, sobre todo con furnis de pared (sus `:w=X,Y` son absolutos y el importador no los desplaza).
- Transponer una sala (`x↔y`): rotaciones `{0:6, 2:4, 4:2, 6:0}`.
- El usuario quiere **furnis temáticos del Club del Arquitecto, nunca `bc_*`**, paredes del doble o triple de alto, y detalle (rastro de sangre, manos, candelabros, niebla) en vez de vitrinas en la zona baja; las vitrinas (12 `window_square` cada una) van en altura, no en la mitad inferior.
- Receta y lista de furnis verificados: skill habbo-salas §16.

