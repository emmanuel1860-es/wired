---
name: "habbo-salas"
description: "Construir y diseñar salas de Habbo en JSON de export/import (furnis, alturas, capas, bloques BC, pixel art, casas, vitrinas, segundos pisos). Usar cuando el usuario pida crear, decorar, copiar o corregir una sala, una casa, un escenario o pixel art en Habbo."
---

# Habbo: construcción y diseño de salas

Skill para generar salas en el JSON de export/import de Habbo. El usuario escribe en español; responde en español. Los wireds y presets están en otro skill (`habbo-wired`); aquí solo diseño y construcción.

## 0. Flujo de trabajo

1. **Preguntar lo mínimo**: tipo de sala, tema/estilo, tamaño aproximado, si lleva segundo piso o techo, y si hay una sala de referencia (JSON).
2. **Proponer un plan corto** antes de generar: capas, furnis principales, paleta de colores, alturas.
3. **Generar el JSON con un script** (Python): listas de furnis por capa, ids únicos, coordenadas dentro del plano de la sala.
4. **Validar**: ids únicos, sin furnis fuera del plano, z coherente, nada flotando por error, rotaciones válidas, colores BC existentes, **sin colisiones de huellas** (calcular la huella w×l de cada furni con su rotation; ver sección 12.6) y **alcanzabilidad** (recorrido desde la puerta). Ojo: en Habbo cada baldosa tiene UNA sola altura (la del furni más alto), así que lo que queda bajo una losa o techo no se pisa; no declarar "transitable" un diseño de pisos apilados (ver 15.6).
5. **Entregar** el `.json` listo para importar y avisar qué decisiones se tomaron (colores, alturas, qué queda a ajustar).
6. Si la sala lleva juego o wireds, pasar esa parte al skill `habbo-wired`.
7. **Si el usuario es detallista** (manda capturas y dice que algo está "mal puesto"): revisar la captura pieza por pieza (alineación con la mesa, muros que tapan la vista, objetos flotantes o sueltos) y simplificar antes que añadir. Una sola pieza fuera de lugar se nota. Si marca con círculos, esos son exactamente los furnis a corregir.
8. **Al rediseñar una sala suya** (cambiar estilo o colores): leer el export, listar las clases con su conteo, pedir medidas de lo que no se conoce, dibujar un plano de huellas (matplotlib) para revisar antes de entregar, y conservar los furnis funcionales (`room_switcher2`, `conf_invis_control`) en su misma posición.
9. **Si el usuario manda una captura de referencia de un efecto**: medirla con código (PIL/numpy: posiciones de líneas de marco, separación de barras) en vez de estimar a ojo, y copiar esas medidas. Ver la técnica en la sección 14.
10. **Antes de usar un furni nuevo, comprobar cómo se ve**, no solo su medida (ej. `mafia_c26_sidetable_dark` es una mesita redonda de pedestal, no un panel). Si no se sabe, usar uno ya visto en una captura del usuario o pedir una muestra.
11. **Cuando el usuario muestra un detalle que le gusta** ("esto es hermoso, aprende esto"): pedirle el export `:ep` de esa zona (con su G-Presets modificado salen también los furnis de pared) y analizar el JSON (classnames, z, posiciones de pared convertidas con la fórmula de la sección 14.2) antes de guardarlo en esta skill. Si el export no deja claro qué piezas forman el detalle, marcar lo observado y lo inferido, y confirmarlo con el usuario. Los detalles aprendidos así están en la sección 14.
12. **Todo preset con ventanas debe llevar al menos un furni de suelo**: el importador (G-Presets) rechaza un archivo con solo `wallFurni`. Siempre indicar el comando exacto `:ip X,Y` (la esquina de `exportOrigin`), nunca `:ip auto`, y que después puede escribir `:paredes` para recolocar las ventanas (sección 15.1).
13. **Usar solo furnis que se vieron llegar** (en una captura suya o en las listas de las secciones 11, 12 y 15.2). Que un furni aparezca en un export de otra sala suya no garantiza que se pueda importar: en el museo no llegaron varios que estaban en esos exports.
14. **Salas de cristal a gran escala** (cafetería, museo, casa de cristal): seguir la sección 15, que trae la calibración de altura de su sala 13×13, la fórmula del lado `l`, las recetas aprobadas, las reglas de muebles dentro del vidrio y el límite de caminar por pisos apilados (15.4 a 15.6).

## 1. Formato del JSON de sala

- Raíz: `{wired:{...vacío...}, bindings:[], furni:[...]}`; opcionales: `roomLayout` {modelName, floorplanWidth, floorplanHeight, scale, floorplanSignature, wallHeight}, `srcAnchorFloorHeight`, `adsBackgrounds` [{furniId, imageUrl, offsetX, offsetY, offsetZ}] (fondos con imagen, roombg), `wallFurni` (furnis de pared, ver sección 14).
- `wired` vacío = `{effects:[], variables:[], addons:[], conditions:[], triggers:[], selectors:[], variables_map:{}}`; el export añade `exportOrigin` {x:0, y:0, floor:0}.
- Cada furni: `{id, name "clase[n]", className, location{x,y,z}, rotation}`; los bloques BC añaden `state` (0–3, a veces 4) y pueden traer `variables`.
- `id` único por furni; `name` = `className[n]`. `z` puede venir como `1.49012E-8` (≈0) o `1.0E-6` (altura mínima para superponer).
- Una sala solo de decoración tiene `wired` vacío; sirve para exportar/importar salas completas.
- `state` puede venir como texto (`"state": "0"`) y solo aparece en furnis que lo tienen (muebles simples como sofás o sillas no lo traen). Mantener esa convención al generar.
- Los furnis `wf_*` en `furni` con `wired` vacío no traen configuración: se configuran a mano.
- En el JSON los furnis se identifican por **className** (ej. `soft_sofachair_norja*9`, `*N` = variante de color), no por el ID numérico del catálogo. Para buscar classnames y medidas: **habboassets.com/furniture?search=<texto>** (lista nombre + classname) y **habboassets.com/furniture/<classname>** (da Xdim × Ydim × Zdim y flags sentar/pisar). habbofurni.com/furniture redirige a habboassets.com. El shell de la sesión no llega a habboassets (el proxy lo bloquea); WebFetch de sus páginas de búsqueda da nombres y categorías, no medidas.

## 2. Reglas de construcción por capas

1. **Piso base** a z 0 con furnis 2x2 colocados cada 2 baldosas (lt_patch, seaside_c26_sand, party_floor, tokyo_c18_road, hween_c25_mossyfloor, country_grass, etc.). Usar solo suelos de las secciones 11 y 12 (los demás no están disponibles para el usuario); `tile_marble/brown/stella` miden 1x1.
2. **Detalles de piso** a z 0.001–0.1 (maleza, parches, alfombras).
3. **Muebles** encima.
4. **Muros y decoración vertical** apilados.
5. **Techo**.

- **Superponer sin chocar**: sumar milésimas (0.001, 0.01, 1.0E-4, 4.0001). Para incrustar se usan z bajos (0.05, 0.2, 0.3).
- **Niveles/islas flotantes**: escenas enteras a z 15, 19, 22, 24, 28; alturas hasta ~40. Los z enteros de cada zona (0, 17, 22, 26 en la sala de ciudad, sección 13) parecen ser alturas de baldosa del propio mapa de la sala; los detalles se suman encima.
- **Segundo piso**: pisos (pastel_c19_floor, santorini_c17_piermid, pirate_stage) a z 3.2–4.4 y muebles a z 4.0001; techos (zengarden_c20_roof, cland_c15_roof) a ~z 8–9 o más. Ojo: lo que queda debajo de un piso o techo es solo decorado, no se puede caminar (15.6). Con `tile_marble` flotante como losa: ver 15.4.
- **Rotaciones**: pares (0/2/4/6) = ejes; impares (1/3/5/7) = diagonales (rocas, bc_quartercircle, maniquíes, pirate_stage2).
- **Tamaños**: pisos y alfombras grandes suelen ser 2x2 (colocar cada 2 en x/y); bloques BC, elewood, eleblock, party_block2 son 1x1.
- **Alfombra sobre piso**: un piso fino encima (ej. `tile_marble` 1x1x0.15 sobre un suelo negro) funciona como alfombra; los muebles que van encima se suben a z = alto de la alfombra (0.15).
- **Suelo de tamaño impar con piezas 2x2**: cubrir con piezas cada 2 y rellenar la fila/columna sobrante con piezas 2x2 solapadas a z +0.001 y +0.002 (la esquina), sin dejar huecos (sección 15).

## 3. Alturas al apilar (delta z)

| Furni | Alto |
|---|---|
| bc_block_1 | 1.0 (o 0.5 según state; state 3 ≈ placa de 0.25) |
| bc_block_0 | 1.0 (se ve alto; bc_block_1 más plano) |
| bc_ramp | 1.1 |
| bc_panel | 2.0 (0.2 delgado) |
| bc_round | 0.15–0.25 |
| bc_standingtriangularprism | 0.3–0.65 |
| bc_glasspanel | ~1.9 |
| bc_cylinder | 1.0 |
| purablk_c16_eleblock | 1 |
| party_block2 | 0.6–0.74 |
| greek_block | 0.5 |
| greek_pillars / greek_corner | 3.0 |
| lt_patch | 2.5 (ocupa alto) |
| xmas11_elewood | 0.5 |
| pirate_stage3 | 0.8 |
| santorini_c17_piermid | 0.7–0.8 |
| celestial_c19_rug | 0.4–0.5 |
| rare_icecream | 2.5 |
| CF_50_goldbar | 0.37 |
| CF_1_coin_bronze | 0.16 |
| wired | 0.65 (addons 0.37) |
| attic15_box | 0.8 |
| bump_tires | 0.7 |
| urban_sidewalk | 0.2 |
| sb_block (Skate Platform) | 0.5 |
| xmas_c22_elewoodv2 (Brick Beam) | 0.5 |
| tile_marble | 0.15 (apilable: 4 piezas = pedestal de 0.6) |
| wildwest_desk | 1.1 |

Muros/columnas apilables: catcafe_c23_wall cada 4, xmas_c16_wall cada 7–9, hween_c25_oldwall ~3.7, hygge_c25_wall1 cada 6, cpunk_c15_building2 cada 2.05, classic9_bookshelf cada 5, tokyo_c18_sakura cada 2.2, greek_pillars cada 3.0, present_wrap cada 3, badge_display3 cada 2.25 (muro de placas).

## 4. Bloques BC (Builders Club)

- Nombre: `bc_<forma>*N`; N = posición del color en el selector del catálogo BC (11 por fila, izquierda a derecha; *1 = primer cuadro). Vale para todos los furnis BC.
- Formas vistas: bc_block_0, bc_block_1, bc_block_small, bc_block_grass, bc_block_marble, bc_panel, bc_glasspanel, bc_round, bc_ramp, bc_curvedramp, bc_stairs, bc_standingtriangularprism, bc_standinghalfcylinder, bc_pyramidtip, bc_stick, bc_cylinder, bc_sphere, bc_sphere_small, bc_hemisphere, bc_quartercircle, bc_wedgecrnr, bc_stone, bc_alpha1_<letra>, bc_alpha1_num.
- `state` cambia el grosor/forma: state 3 = placa delgada (~0.25), state 2 = medio bloque (0.5), state 4 otra variante. Hipótesis: state 1 ≈ 0.75 de alto.
- `bc_alpha1_num`: el `state` es el dígito (0–9). `bc_alpha1_<letra>`: la letra va en el classname.
- bc_block_1 existe en colores *1 a *69 (todos confirmados).
- No hay negro puro: lo más oscuro es *60 (verde casi negro) y *13 (gris oscuro). Blanco = *14; grises = *14, *19, *18, *13; piel = *42/*43.
- **Paleta (hex)**: 1 #f5eabb, 2 #fed736, 3 #fe9800, 4 #fe98bb, 5 #e04117, 6 #662812, 7 #007122, 8 #91d03c, 9 #ab59b0, 10 #8259b0, 11 #5da9f6, 12 #aacfd1, 13 #515151, 14 #fefefc, 15 #98fecb, 16 #656500, 17 #6e3c0b, 18 #767f83, 19 #bbbfc3, 20 #a0e7fb, 21 #2bbefe, 22 #0073fe, 23 #0000fe, 24 #0000c7, 25 #0084fe, 26 #6584fe, 27 #bbb6fe, 28 #dcb5fc, 29 #9b72fe, 30 #673dfe, 31 #3e1ac6, 32 #95008d, 33 #dd00d6, 34 #fc71fe, 35 #fcb5fc, 36 #f3bee0, 37 #fe539b, 38 #e6005e, 39 #ad0023, 40 #fe3500, 41 #fe7751, 42 #f3d0b2, 43 #fbe0b0, 44 #fca03c, 45 #e65f00, 46 #8c1500, 47 #503000, 48 #ac8000, 49 #fbd87d, 50 #99bc00, 51 #daf76e, 52 #b9f800, 53 #00b900, 54 #007800, 55 #2d622d, 56 #00a900, 57 #51d94f, 58 #b5f86f, 59 #89ad00, 60 #0c370b, 61 #005800, 62 #00a842, 63 #4df898, 64 #adefd7, 65 #00f6fb, 66 #00e8e4, 67 #008794, 68 #003f58, 69 #326565.
- Para elegir color de un diseño: calcular el hex más cercano de la paleta a cada color deseado (distancia en RGB) y usar ese *N.

## 5. Técnicas por tipo de sala

### Pixel art con BC
- Plano vertical (y fijo) con bc_block_1 apilado cada 1 en z; fondo de un solo color (ej. *20 celeste).
- No mezclar bc_block_0 y bc_block_1 en la misma imagen o piso (quedan a distintas alturas).
- Diagonales/medios píxeles: pares bc_block_0 con rotation 6 y 2 superpuestos (z y z+0.02/0.03) de dos colores; state 2 = medio bloque a z .5.
- Pisos en pixel art: usar el mismo furni y la misma altura para toda la imagen.
- Ejemplos: pato (contorno *13, amarillo *2, pico *3), ganso (fondo *20; colores *13, *14, *19, *11, *40, *44, *69).

### Casa con bloques BC ("sala_hermosa", 12x13 baldosas, 353 furnis)
- Cimiento: bc_stone*14 a z 0 + franja bc_block_1*18 (state 3) a z 0.2 bajo los muros.
- Muros: hween_c20_housewall a z 0.4 y otro encima a z 4.4 (alto 4); lado interior a 5.45; rotation 2 y 4 para los dos ejes; puerta xmas_c23_door a z 0 en el hueco.
- Cornisa/viga: bc_block_1*18 state 3 a z 5.2; franja bc_block_1*13 state 4 a z 6.4.
- Techo a dos tonos: capa bc_block_0*18 a z 9.23 + bc_block_0*13 a z 9.3 (la diferencia de 0.07 deja borde), rotation 4 (y 2 en la otra ala).
- Piso interior: val09_floor2 (states 0/2/4/5 cambian color) a z 0; zócalo de luz bb_lightstrip a z 0 y z 1.14.
- Detalles: mode_vase*0 en fila, suncity_c19_solarpanel en techo, silo_c24_hangingpictures a z 5.55, jardín con country_grass, garden_pond, zengarden_c18_bamboo; muebles darkmodern_c20_*, icedblk_c16_sofachair.
- Otra técnica (casa.json): base bc_block_1*14 state 3; paredes bc_panel*14 (state 0 entero, state 2 franja para ventanas) apiladas cada 2; techo a dos aguas con bc_quartercircle*4 (z 6.4/7.4) y *2 (z 9.15/10.15) + losa bc_block_1*4 state 4 a z 8.5; esquinas diagonales rotation 3/5; escalera bc_stairs*14; columna bc_cylinder*14 cada 1 con bc_round encima; tile_walkmagic para pisar arriba.

### Mesa de juego
Base bc_block_1*14 (state 1, z 5) + cubierta bc_block_1*4 (state 3, z 5.75), bc_round en las esquinas y wf_button encima.

### Vitrina de raros
xmas11_elewood como repisa (z 0 y z 3), raros encima (helados a z 0.5, trofeos a z 3.5), xmas11_woodfloor de piso; alturas mínimas tipo 1.0E-6 para superponer. Colecciones: scifirocket*0–*10 (escalera, z de 11 a 5), rare_icecream*0–*12, rare_prize15*1–*24 (trofeos), rare_dragonlamp, CF_750_goldenskull, CF_50_goldbar, totem_leg/head/planet apilados.

### Vitrina de cristal (comida, objetos) y baranda de cristal con ventanas
Ver la sección 14: se hacen con `window_square` (furni de pared) sacado de la pared, no con muros ni bloques. A gran escala (cafetería, museo, casa de cristal de varios pisos) ver la sección 15.

### Cascada de regalos ("present_cayendo")
12 columnas pegadas a paredes con present_wrap*1–*8 a z 6, 9, 12, 15 (cada 3); el resto amontonado a z 17 bajo un techo de nubes (celestial_c19_rug a z 18–19) para la ilusión de caída. Árbol alto: tokyo_c18_sakura apilado cada 2.2; plataformas flotantes celestial_c19_rug a z 0.8/6.2; trofeos a z 18.5 sobre las nubes.

### Bingo
- bc_block_1 state 3 = placa de 0.25 (los números se ponen encima a z 0.25).
- Cartón 5x5 de bc_alpha1_num*14 sobre placas bc_block_1*14, cartones separados cada 7 baldosas; letras B-I-N-G-O (bc_alpha1_*7) al costado; wf_colortile (baldosas que cambian de color al pisar) para marcar; wf_contract_reward como premios; lounge elevado para espectadores.

### Salas temáticas
- **Post-apocalíptica/Halloween**: lt_patch, hween_c25_* (muros, musgo, basura), army_c15_wiredividr (alambradas), autos abandonados. Ver sección 12 para el catálogo Halloween verificado y la sección 13 para la sala de ciudad en ruinas.
- **Pirata/Navidad**: pirate_stage/2/3 como tarimas, xmas_c16_wall, badge_display3 como muro de placas, classic9_bookshelf.
- **Playa/zen**: seaside_c26_sand, thai_c21_crystalwater/waterrock (rotaciones 1/3/5), santorini_c17_piermid como muelle, zengarden_c20 roof/door, casa hygge elevada a z 4.
- **Ciudad NY**: nyc_c23_blockblue/buildingblack, urban_sidewalk, cpunk_c15_road, bc_cylinder*19 apilados cada 1 como poste. Receta completa en la sección 13.
- **Griego/mármol**: greek_block, greek_pillars, greek_corner, val12_marble1/2, olympus_c25_*.
- **Rancho/naturaleza**: ranch_c26_* (earth/grass/fence), country_grass, autumn_c20_tree4, nest_lion, xmas11_elewood, dino_c15_table.
- **Cielo/nubes**: celestial_c19_*, patch_cloud. **Disco**: disco_c26_*, party_*. **Mafia**: mafia_r26_globe/moneytile.
- **Museo / casa de cristal / cafetería con vitrinas**: sección 15.

### Sala de juegos tipo sala de bolos (referencia)
tile_marble, tile_brown, tile_stella, pixel_floor_silver, bc_block_1*13/*14, val12_marble2, soft_sofachair_norja*2 (asientos), room_invisible_sit_tile, room_invisible_block, conf_area_hide, icedblk_c16_div3*2, celestial_c19_rug, tile_walkmagic, queue_tile1*8, bb_rnd_tele (teleports), hs_lightstrip_blue, disco_c26_lightbarblue, snowb_light1, fireworks_06; alturas hasta z ≈ 22.

## 6. Furnis útiles de juego y configuración

- Juego: wf_button, wf_button_arrow (4 flechas, rotation 0/2/4/6), wf_arrowplate_8dir, wf_storage_furni1, wf_storage_coins2, highscore_mostwin*1, room_invisible_block2/4, tile_stackmagic/2/4x4, tile_walkmagic, queue_tile1, wf_colortile, wf_teammeter1, wf_numberscreen, wf_upcounter1, wf_glowball, wf_firegate, wf_room_linker, wf_antenna1/2, CF_* monedas (CF_1_coin_bronze, CF_5_coin_silver, CF_10_coin_gold, CF_20_moneybag, CF_50_goldbar).
- Configuración de sala: conf_area_hide, conf_handitem_block, conf_invis_control, conf_wired_disable.
- Asientos de jugadores: soft_sofachair_norja*2/*6/*9, chair_plasto de colores; marcadores bc_alpha1_a*14 / bc_alpha1_b*14 (jugador A/B).
- Decoración variada: val14_gate, hblooza14_duckgate, catcafe_c23_wall, pirate_cannon, pirate_cannonballs, dng_treasure2, rare_royalchair, hc_gift_31days.

## 7. Cosas a evitar

- Mezclar bc_block_0 y bc_block_1 en un mismo piso de pixel art.
- Furnis a la misma (x, y, z) sin diferencia de milésimas (se superponen mal).
- Furnis fuera del plano de la sala o ids repetidos.
- Colores BC que no existen (solo *1 a *69).
- Apilar sin conocer la altura real del furni (usar la tabla de la sección 3).
- Olvidar muros/techo cuando la sala es una casa cerrada, o dejar sin puerta/hueco de acceso.
- Sobrecargar la sala: más de ~2000 furnis ya es una sala pesada (la de bingo tiene 2094, la de bolos 2291, parchís 1017).
- Dejar wireds a medio configurar dentro de una sala solo decorativa.
- **Un muro de furni en el lado de la cámara** (x máximo o y máximo): tapa la escena. Los muros de furni van en el fondo (y mínimo) y a la izquierda (x mínimo).
- **Lámparas de pie flotando a z alto** como "lámpara colgante": se ven como un poste suelto. Si no hay un furni colgante verificado, no poner lámpara en el aire.
- **Apilar bloques de piedra como respaldo de sofá**: se ve como rocas. Los sofás ya traen respaldo.
- Objetos sueltos (velas, niebla) fuera de la escena: el usuario detallista los marca como mal puestos.
- Sofás o bancas más largas que la mesa: alinear mesa y asientos (ver receta 12.7).
- Dejar los objetos de un nivel a z 0 cuando el piso de esa zona está en otro z: todo el contenido de una zona usa su z base (ver sección 13).
- **Usar `mafia_c26_sidetable_dark` como poste o panel de marco**: es una mesita redonda de pedestal; apilada parece un bosque de mesitas (error cometido en la primera vitrina).
- **`hween_c25_oldwall` con rotation 2 como muro lateral suelto**: mide 2·1·3.7 y queda como un pilar de piedra grueso.
- **Vitrina con ventanas puestas en la pared real** (`:w=X,Y0`): el furni de pared se dibuja detrás de los de piso y el vidrio queda atrás de la comida (ver sección 14).
- **Vitrina con demasiadas ventanas** (11 columnas × 5 filas se ve saturado) o **con la misma `ly` en columnas de distinto `lx`**: las barras quedan desalineadas como una escalera (ver sección 14). Excepción: cuando el usuario pide expresamente "muchas vitrinas", sí se usan cientos de ventanas, repitiendo el módulo (sección 15).
- **Copiar `ly` de un export a otra sala sin convertir**: depende de la altura de la baldosa de esa posición; usar la altura real `c = ly − lx/2` (sección 14.2).
- **Decir `:ip auto`**: las ventanas son absolutas y los furnis relativos a la esquina; con `auto` quedan desalineados. Dar siempre `:ip X,Y` con la esquina exacta.
- **Estilo gótico/gótico-mafioso por defecto**: el usuario pidió "madera oscura" y rechazó la cafetería con velas góticas, `tray_cake` gigante y sin suelo. Elegir el estilo que pidió y siempre poner suelo.
- **Furnis no vistos llegar** (ver 15.2): quedan huecos vacíos en las vitrinas.
- **Lámparas u objetos en pasillos de una baldosa**: bloquean el paso; validar con un recorrido (BFS) desde la puerta.
- **Muebles en terrazas abiertas o en la fila exterior pegada al cristal sur/este**: sobresalen del vidrio (le pasó en la primera casa isométrica: plantas y sillones se veían fuera de la caja). Ver 15.6.
- **Prometer que se puede caminar por pisos apilados o por escaleras en espiral/superpuestas**: una baldosa tiene una sola altura (la del furni más alto), así que lo que queda bajo una losa no se pisa y las vueltas apiladas tampoco. Se dijo que las escaleras de la casa isométrica v2 eran transitables y "validadas"; era falso (15.6). No afirmar "validado" sin modelar altura única por baldosa.
- **Escaleras de peldaños altos (~0.45) sin comprobar**: usar contrahuella ≈ 0.30 (15.6); el máximo que acepta el servidor no está verificado.

## 8. Sala "soggiorno" (395 furnis, solo decoración) — escena por niveles

Sala de ambientación tipo mafia/crimen con **cuatro zonas a distinta altura dentro del mismo plano**; cada zona ocupa su propio rectángulo de x/y, así no se pisan:

| Zona | z | Rango x / y | Contenido |
|---|---|---|---|
| Sótano/escena del crimen | 0 | x 4–21, y 1–18 | piso val13_floor1 + val12_marble2, muebles attic15, cuerpo y sangre |
| Lounge de mármol | 14 | x 11–19, y 13–27 | val12_marble2 (33), mesas mafia_c26_coffeetable_dark, bar, sillas |
| Oficina | 28 | x 21–27, y 21–34 | hween_c24_darkschoolhallfloor de piso, escritorio, chimenea, lámparas dragón |
| Escenario | 41 | x 28–34, y 28–42 | borde de hween_c20_floorstage |

### Técnicas aprendidas
- **Niveles separados por z fijo** (0, 14, 28, 41) en zonas distintas del mapa: sirve para meter varias habitaciones en una sala. Todo el contenido de una zona usa su z base más milésimas (0.01, 0.02, 0.03) para ordenar encima del piso. (Es el modelo que sí permite caminar: rectángulos distintos, nunca uno sobre otro; ver 15.6.)
- **Piso**: val12_marble2 (100 piezas, se coloca cada 2 a lo largo del eje; huella aparente 1x2, sin confirmar), val13_floor1 (23; el `state` 0–8 cambia el aspecto), hween_c24_darkschoolhallfloor (27, 2x2, cada 2 en x/y), hween_c20_floorstage (11, borde de escenario).
- **Divisores y columnas** en vez de muros: val13_div_1, val13_div_3, val13_div_3_crnr2 (esquinas, 14), val12_pillar1 (9), greek_block (4, apilables).
- **Mesas modulares**: mafia_c26_coffeetable_dark (13 piezas, `state` 0/1) apilado a z 14, 14.4 y 15.1 sobre base de mármol; val13_div_3 a z 14.8 como repisa/respaldo; rotaciones 2/4/6 para armar L y U.
- **Narrativa con detalles**: det_body y sc_bodybag (cuerpo), hween13_bldtrail (rastro de sangre, `state` 1/5), hween_c18_bloodfloorstreak (3 piezas a z 0.510/0.511/0.512 apiladas para mezclar texturas), hween10_fog (niebla), mafia_c26_gunrack, mafia_c26_cash, nft_sc_moneybag, nft_h26_contraband/crates, safe_silo*2/*8, wildwest_cabinet/desk, attic15_* (sofá, mesa, caja, lámpara de aceite, gate, reloj, cuadro de piso), waasa_chair_wood (sillas de comedor con ads_idol_tblCloth), mafia_c26_vintagechair, army_c15_chair, val_c20_chair.
- **Premios y lujo**: CF_50_goldbar, CF_500_goldenglobe, prizetrophy3*1, nft_emerald_1000, nft_h26_cornucopia_silver, mafia_r26_globe, rare_dragonlamp*11/*12 (lámparas apiladas a 28 y 28.9).
- **Elementos de ambiente**: roombg_city2 (fondo de ciudad), exe_light (`state` 1/2), exe_elevator, camera, xmas12_fireplace, val_r19_recordplayer, jungle_c16_radio, easter_c18_curtains.
- **Escalera de piedra**: lt_c26_stonestairs con `state` 0/1 y rotación 1/4 para subir entre zonas.
- **Objetos de detalle sobre mesas**: z = z de la mesa + 0.2–0.4 (copas, cartas, comida a 1.21 sobre mesa a 0.01).

## 9. Almacén Club del Arquitecto: furnis temáticos (no solo bloques)

El Club del Arquitecto (BC) no trae solo las formas `bc_*`: en "Por diseño" aparecen los furnis de muchas campañas, y se pueden colocar como los bloques. **Al decorar con BC, usar también estos furnis temáticos**, no solo bloques.

### Campañas visibles en el catálogo (captura del usuario)
Fiesta en la P…, 20 Aniversario, África, Alhambra, Grecia Antigua, Japón Antiguo, Ancients, Anna, Área, Army Bootcamp, Desván, Automóvil, Árboles Otoñales, Base, Instrumentos para B…, Baño, Bensalem, Bling, Festival Bohemio, Boutique, Bubblejuice, Celestiales, Café Gato, Habbowood, Circo, Reino de Coral, Habbo Clásico, Coco, Country, Cubie, Hygge Danés, Cafetería, Drago, Reino de l…, Ejecutivo, Festa no Brasil, Élficos, Jardinería, Villa de la Fa…, Regalos, Cristal, Griego, Gótico, Habbfather, Habbo Stars, Universidad, País de las…, HabboLympix, Spa Serenidad, Ice Cream Parlour, Iced, Japan, Jet Set, Carrera en la…, Habbo Jurásico, K-Ciudad, Cocina, Rústico, Furnis Rústicos, Lost Tribe, Científico Loco, Maya, Mode, Furnis Mode, Mexicano, Místicos, Neón, Neo-Habbo, Ciudad de Nueva…, Norja, Organo, Olimpo, Palooza, Paris, Piratas, Plasto, Piscina, Orgullo, Prisión, Pura, Relax, Día Lluvioso, Fiesta sobre…, Romantique, Runway, Santorini, Sci-Fi, Seaside, Escuela, Shalimar, Silo, Steampunk, Deportes, Perdidos en la Jungla, Bazar de Verano, Verano Hygge, Ciudad de la Lu…, Tiki, Tokio, Tailandeses, Set de TV, Urban, Usva, Sensaciones, Vikingos, Victorianos, Waasa, Boda, Salvaje Oeste.

No aparecen en la captura: Mafia, Halloween, San Valentín, Navidad (varios furnis `mafia_c26_*`, `hween_*`, `val12_*`, `val13_*` de las salas del usuario **no** son de arquitecto). Corrección verificada al importar: los suelos `hween_*` sí están disponibles para el usuario, y los `mafia_c26_*` Dark Mansion también (ver sección 12), pero `val*`, `xmas*` y `nft_*` casi nunca; ver secciones 11 y 12. Los prefijos NO predicen disponibilidad: solo cuenta lo que llegó al importar.

### Prefijos de classname por campaña (inferidos por el nombre; confirmar al probar)
Desván `attic15_*` · Army Bootcamp `army_c15_*` · Habbo Clásico `classic8_*`, `classic9_*` · Salvaje Oeste `wildwest_*` · Ejecutivo `exe_*` · Silo `silo_c24_*`, `safe_silo*N` · Waasa `waasa_*` · Usva `usva2_*` · Hygge `hygge_c25_*` · Pura `purablk_c16_*` · Iced `icedblk_c16_*` · Griego `greek_*` · Piratas `pirate_*` · Celestiales `celestial_c19_*` · Santorini `santorini_c17_*` · Tokio `tokyo_c18_*` · Tailandeses `thai_c21_*` · Norja `soft_sofachair_norja*N`, `chair_norja*N`, `norja_c24_floor*N` · Plasto `chair_plasto*` · Romantique `romantique_*` · Olimpo `olympus_c25_*` · Urban `urban_*` · Jet Set `jetset_*` · Seaside `seaside_c26_*` · Otoñales `autumn_c20_*`.

### Reglas para orientar furnis (deducidas de sillas alrededor de una mesa)
- `rotation` indica hacia dónde mira el furni: **0 = -y, 2 = +x, 4 = +y, 6 = -x**. Una silla pegada a una mesa debe mirar a la mesa (silla en y menor que la mesa → rotation 4; en y mayor → 0; en x menor → 2; en x mayor → 6).
- `waasa_chair_wood` (1x1, se puede sentar), `icedblk_c16_sofachair` (sillón 1x1), `soft_sofachair_norja*N`, `army_c15_chair` sirven como asientos reales; los bloques BC no.
- Sin huella conocida (probar antes de apilar muchos): `attic15_sofa`, `attic15_table`, `classic8_sofa`, `classic9_carpet`. (`wildwest_desk` mide 2·1·1.1: confirmado; la cara con cajones mira a +y con rotation 0, así que la silla va en el lado +y con rotation 0.)
- 1x1 confirmados por uso en las salas del usuario: `safe_silo*N`, `army_c15_crate`, `pirate_crate`, `attic15_box`, `attic15_oillamp`, `wildwest_cabinet`, `hygge_c25_coffeetable`, `exe_light`, `usva2_shelf2`, `greek_pillars` (alto 3.0, apilable).
- Altura de apoyo sobre un piso de placas BC (state 3): z = 0.25 (no 0.01).

### Receta: sala mafiosa con BC (referencia)
Piso/paredes/alfombra/mesa con bloques `bc_*`; asientos `waasa_chair_wood`; sillones `icedblk_c16_sofachair` + `hygge_c25_coffeetable`; despacho `wildwest_desk` + `wildwest_cabinet` + `exe_light`; bóveda `safe_silo*2/*8` + cajas `army_c15_crate`/`pirate_crate`/`attic15_box`; pilares `greek_pillars` apilados con remate `bc_sphere*48`.

## 10. Medidas verificadas de furnis temáticos (habbofurni.com) y construir sin bloques

**Cómo consultar medidas**: habboassets.com/furniture?search=<prefijo> da classname y nombre; habboassets.com/furniture/<classname> da las medidas (ancho x largo x alto) y si se puede sentar o pisar. Convención usada: ancho en x y largo en y con rotation 0/4; con rotation 2/6 se intercambian.

| Classname | Nombre | Medidas (x·y·alto) | Uso |
|---|---|---|---|
| classic9_floor1 | Library Stone Floor | 2·2·0 | piso de piedra |
| classic9_floor2 | Library Floorboards | 2·2·0 | piso de madera |
| classic9_carpet | Library Rug | 2·2·0 | alfombra |
| classic9_wall | Library Wall | 2·1·7.4 | muro |
| classic9_bookshelf | Library Bookcase | 1·2·5.2 | biblioteca alta |
| classic9_counter | Library Counter | 1·1·1.1 | barra (se alinean) |
| classic9_table | Library Table | 2·2·0.7 | mesa baja, se puede pisar |
| classic9_chair | Library Chair | 1·1·1 | silla (sentarse) |
| classic9_stool | Library Stool | 1·1·1.2 | taburete (sentarse) |
| classic9_light / statue / plant | lámpara, busto, planta | 1·1·1 | decoración (el busto `classic9_statue` NO llegó al importar en el museo, ver 15.2) |
| wildwest_cabinet | Saloon Drinks Cabinet | 3·1·1 | trasbarra de bebidas |
| wildwest_desk | Saloon Bar Desk | 2·1·1.1 | barra/escritorio (llegó: barra de madera oscura con cajones) |
| wildwest_table | Barrel Table | 1·1·1.4 | mesa alta |
| wildwest_chair | Saddle Seat | 1·1·1.3 | asiento |
| wildwest_saloondr | Saloon Doors | 2·1·0.01 | puertas de salón |
| exe_wrkdesk | Work Desk | 2·1·1 | escritorio |
| exe_drinks_cabinet | Executive Drinks Cabinet | 2·1·1 | armario de bebidas |
| js_exe_sofa | Vanilla Sofa | 3·1·1 | sofá de 3 (sentarse) |
| js_exe_bardesk / js_exe_corner | Vanilla Bar / Bar Corner | 1·1·0.98 | barra y esquina |
| attic15_bigtable | Covered Long Table | 2·1·0.9 | mesa larga |
| attic15_clock | Grandfather Clock | 1·1·1 | reloj |
| purablk_c16_mdl1 / mdl2 | Dark Pura Left / Right Module | 1·1·1 | módulos de sofá (sentarse) |
| icedblk_c16_sofa | Dark Iced Sofa | 2·1·1 | sofá de 2 (sentarse) |
| window_square | Square Glass Window | pared (0·0), 1 baldosa de ancho × 1.156 de alto | ventana de cristal, base del efecto vitrina y de la baranda de cristal (sección 14) |

Otros classnames del catálogo (medidas por confirmar): Ejecutivo `exe_glassdvdr`, `exe_gate`, `exe_copier`, `exe_map`; Salvaje Oeste `wildwest_saloonwall`, `wildwest_crate`, `wildwest_light`, `st_wildwest_cards`; Pura `purablk_c16_mdl4/mdl5/bar/fridge/lamp2`; Desván `attic15_shelf/bigshelf/carpet/divider/window/chair/lamp/cabinet`; Iced `icedblk_c16_shelves/div1/div5/chair`; Norja `norja_c24_shelf/hanginglight`; Biblioteca `classic9_counter/books`.

### Regla del usuario: no usar bloques BC salvo que él lo indique
No usar ningún `bc_*` por iniciativa propia: el usuario indica dónde van. Construir con furnis temáticos del Almacén (ver secciones 11 y 12 para los que están verificados). En una sala normal, usar los muros y papel tapiz de la propia sala en vez de muros de furnis. (Excepción: el pasamanos de la sección 14.6 parece ser un `bc_panel` que él mismo colocó; si se quiere reproducir, avisar que es BC. La `window_square` sí se usa libremente: es su técnica favorita.)

### Receta: sala mafiosa sin bloques (124 furnis, 0 bloques bc_*)
Piso `classic9_floor1` (borde) + `classic9_floor2` (interior) cada 2; alfombra `classic9_carpet` 3x3 bajo la mesa; póker `classic9_table` + 8 `classic9_chair` mirando a la mesa; bar `wildwest_cabinet` x2 (trasbarra) + 6 `classic9_counter` + 3 `classic9_stool` + busto encima; despacho `exe_wrkdesk` + `exe_drinks_cabinet` + sillas; biblioteca 5 `classic9_bookshelf`; lounge `purablk_c16_mdl1/mdl2*3` + `attic15_bigtable` + `attic15_oillamp`; bóveda `safe_silo*2/*8` + cajas; ambiente `classic9_light`, `attic15_clock`, `classic9_plant`. Alturas: piso z 0, alfombra z 0.01, objetos z 0.02, objetos sobre mesa/barra = z del mueble + su alto.

## 11. Furnis disponibles para el usuario (verificado importando muestras en su sala, 05/10/2026)

Lo que está aquí **sí se puede colocar** en la sala del usuario; lo que no aparece, o no está disponible o no se ha probado. Al generar salas, usar solo estos furnis (más los de sala/juego de la sección 6, que ya usa en sus salas, y los de la sección 12). Reglas del usuario:

- **No usar bloques `bc_*`** salvo que él indique dónde (ver sección 10).
- **Nunca incluir `tile_stackmagic4x4` en salas generadas**: es el furni que él usa para mover furnis. Al leer un export suyo, ignorarlo.
- Las salas de referencia de las secciones 2, 5 y 8 usan suelos que hoy **no** están disponibles para él (`val09_floor2`, `val12_marble1/2`, `val13_floor1`, `xmas11_woodfloor`, `patch_cloud`, `pastel_c19_floor`, `garden_grass`): al reproducir esas salas, sustituirlos por un suelo de 11.1 o 12.5.
- Medidas: ancho x largo x alto en baldosas, sacadas de habboassets.com. "Sin dato" = el catálogo no indica el alto.
- Para probar furnis nuevos: JSON con un furni de cada uno en grilla (cada 2 baldosas, o cada 4 si se desconoce el tamaño), z 0, rotation 0, sin bloques BC; el usuario lo importa y devuelve el export; llegan solo los disponibles. Al importar, el export puede devolver las coordenadas desplazadas (en una prueba: +12 en x) y con `state: "0"` añadido; si el export trae piezas de otra sala que él tenía puesta, ignorarlas.
- Los furnis que ya aparecen en un export suyo (aunque sean `xmas_*` o `nft_*`) los tiene; al rediseñar una sala suya se pueden conservar. **Matiz (museo, 05/10/2026)**: eso vale para rediseñar *esa* sala; para salas nuevas, `rare_dragonlamp*12`, `nft_emerald_10`, los maniquíes `clothing_r26_*` y otros que solo estaban en el export de otra sala no se vieron llegar (ver 15.2).

### 11.1 Suelos disponibles (58)

- **2x2, alto 0 o sin dato** (38): `classic9_floor1`, `classic9_floor2`, `hween_c24_darkschoolhallfloor`, `hween_c25_mossyfloor`, `lt_patch`, `seaside_c26_sand`, `party_floor`, `tokyo_c18_road`, `norja_c24_floor*N`, `cpunk_c15_road`, `country_grass`, `wonderland_c25_floor`, `hween_c26_wornfloorboards`, `easter_c26_elvenfloor`, `easter_c26_elvenwoodfloor`, `easter_c26_elvenstage`, `hween_c25_tiledfloor`, `lt_c26_stonefloor`, `lt_c26_jungleleaffloor`, `lt_c26_junglefloor`, `disco_c26_floor`, `lodge_dark_floor`, `olympus_c25_floor1`, `olympus_c25_floor2`, `hygge_c25_floor1`, `hygge_c25_carpetfloor`, `br_c25_stonefloor`, `br_c25_wavymosaicfloor`, `br_c25_ptfloor`, `dream_c24_floor`, `dream_c24_waterfloor`, `circus_c24_floor`, `catcafe_c23_floor`, `hween_c22_marblefloor` (mármol negro, 2·2·0), `bubblejuice_c21_floor`, `icecream_c21_floor`, `hween_c20_floor`, `sunsetcafe_c20_floor`, `hween_c21_stonefloor`.
- **2x2 con alto** (z de apilado): `hween_c20_floorstage` 1, `pixel_floor_silver` 0.01, `urban_sidewalk` 0.2, `pirate_stage` 0.8, `hween_c23_tombfloor` 0.1, `fantasy_c22_cobblestonefloor` 0.1, `skorea_c22_brickfloor` 0.05, `hween_c20_porchfloor` 1.
- **1x1** (una por baldosa, no cada 2): `tile_marble` 0.15 (en su sala se ve **crema/beige**, no blanco; caminable y puede flotar), `tile_brown` 0.15, `tile_stella` 0.15, `pirate_stage2` 0.8, `wonderland_c25_tile1`, `wonderland_c25_tile2`, `wonderland_c25_tile3`.
- **Otros tamaños**: `pirate_stage3` 1x2x0.8, `santorini_c17_piermid` 2x1x0.7, `celestial_c19_rug` 1x2x0.2, `jp_c22_tatami` 1x2x0.1.
- `norja_c24_floor*N`: 2x2x0, medido solo en el azul (*7); el `*1` (rosa) y el `*3` (negro) están disponibles. Los `wonderland_c25_tile*` son 1x1 sin alto en el catálogo; `wonderland_c25_floor` 2x2.
- Con nombre que sugiere elevación pero sin alto confirmado: `olympus_c25_floor2`, `easter_c26_elvenstage`.
- Más suelos probados en la segunda ronda: ver 12.5.

### 11.2 Suelos NO disponibles (32) — no usar

`val13_floor1`, `val09_floor2`, `val12_marble1`, `val12_marble2`, `garden_grass`, `xmas11_woodfloor`, `patch_cloud`, `pastel_c19_floor`, `survival_c26_foliagefloor`, `ducket_c26_floor`, `nft_cm2_icefloor`, `ducket_c25_warmfloor`, `xmas_c24_blizzardfloor`, `nft_h24_cefalu_floor`, `xmas_c23_checkerfloor`, `fall_c23_leafyfloor`, `wisp_c23_starryfloor`, `uni_c23_floor`, `ads_aftv_floor`, `easter_c23_floor`, `arcade_c23_arcadefloor`, `xmas_c22_woodenfloor`, `jp_c22_tatami2`, `school_c22_floor`, `magitech_c22_floor`, `easter_c22_dirtfloor`, `easter_c22_rockfloor`, `bohogarden_c21_floor`, `easter_c21_digitalfloor`, `darkelegant_c20_floor`, `zengarden_c20_plankfloor`, `darkmodern_c20_floor`. Segunda ronda: ver 12.5.

Patrón observado: llegaron todos los `hween_*` suelos probados; no llegó ninguno de `val*` (San Valentín), `xmas*` (Navidad), `nft_*`, el `ads_*` ni los `easter_c21/c22/c23` (sí llegaron los `easter_c26_elven*`).

### 11.3 Sillas disponibles (147) — todas se pueden sentar

Medidas conocidas (52): todas **1x1**, alto entre 0.75 y 2.5. Alto entre paréntesis.

`africa_c15_chair` (1), `anc_c15_chair` (1.3), `anna_chair*1` (1.2), `antique_c21_armchair` (1.2), `arabian_chair` (1.3), `army_c15_chair` (1.3), `army_c15_leatherchr` (1.2), `attic15_chair` (1.2), `bar_chair_armas` (1.39), `barchair_silo` (1.5), `bb_crchair` (1), `bb_crchair2` (1), `bb_crchair3` (1), `bling11_seat2` (1.25), `bling_chair_a` (1.01), `bling_chair_b` (1.01), `bling_chair_c` (1.01), `bubblejuice_c21_chair` (1.4), `bw_chair` (1), `bw_chair_g` (1), `bw_chair_p` (1), `bw_lgchair` (2.5), `catcafe_c23_chair` (1.4), `celestial_c19_starchair` (1.3), `chair_basic*1` (0.99), `chair_norja` (1), `chair_silo` (1.11), `circus_c24_dressingtablechair` (1.3), `circus_c24_foldingchair` (1.4), `classic1_chair` (1.1), `classic3_chair*0` (1.1), `classic5_chair_blu` (0.9), `classic5_chair_yel` (0.9), `classic6_chair` (1.3), `classic7_chair` (1), `classic9_chair` (1), `coco_chair` (1.3), `coco_chair_c2` (1.3), `coco_chair_c3` (1.3), `coco_chair_c4` (1.3), `coralking_c18_chair` (1.2), `cubie_chair_b` (1.3), `cubie_chair_p` (1.3), `diner_chair*1` (1.6), `easter_c26_elvenchair` (sin dato), `exe_chair` (1), `exe_chair2` (1.1), `foldablechair_beige` (0.75), `foldablechair_camo` (0.75), `SF_chair_blue` (1.3), `SF_chair_green` (1.3), `SF_chair_red` (1.3).

Sin medir (95; las 52 medidas son todas 1x1, pero confirmar el tamaño antes de alinear o apilar muchas, sobre todo `hygge_c25_hangingchair`, `hween_c24_stackedchairs`, `runway_bigchr_*` y los sofás-sillón):

`glass_chair`, `gothic_chair*1`, `greek_c19_chair`, `grunge_chair`, `habbo20_c20_artdecochair`, `hblooza14_stool`, `hobbies_c26_chair`, `hs_chair`, `hween09_chair`, `hween13_chair`, `hween14_chair`, `hween_c16_barchair`, `hween_c16_chair`, `hween_c18_labchair`, `hween_c19_bewitchedchair`, `hween_c21_chair`, `hween_c24_promchair`, `hween_c24_stackedchairs`, `hween_c26_readingchair`, `hygge_c18_chair`, `hygge_c25_armchair`, `hygge_c25_hangingchair`, `icecream_c21_chairorange`, `icecream_c21_chairpurple`, `icedblk_c16_chair`, `icedblk_c16_sofachair`, `igor_seat`, `ktchn_c15_chair_b`, `ktchn_c15_chair_g`, `ktchn_c15_chair_p`, `kuurna_chair`, `lc_chair`, `mafia_c26_armchair_dark` (1x1x1.3 medido), `mafia_c26_vintagechair`, `mode_gold_diningchair`, `nft_h26_cardchair`, `ny2013_chair`, `olympics_c16_chair`, `org_chairblk` (1x1x1.3 medido, negro), `org_chairpnk`, `paris_c15_chair`, `party_chair`, `party_seat`, `picnic_chair`, `pixel_chair_blue`, `pixel_chair_green`, `pixel_chair_pink`, `pixel_chair_red`, `purablk_c16_chair*1`, `rainbow_c19_chair`, `rela_hchair`, `romantique_chair*1`, `romantique_pianochair*1`, `room_invisible_sit_tile`, `runway_bigchr_1`, `runway_bigchr_2`, `runway_bigchr_3`, `runway_bigchr_4`, `runway_bigchr_5`, `runway_chair_1`, `runway_chair_2`, `santorini_c17_chair`, `school_chair`, `school_chair_b`, `school_chair_g`, `skorea_c22_chair`, `small_chair_armas`, `sofachair_polyfon`, `sofachair_silo`, `soft_sofachair_norja`, `steampunk_chair`, `steampunk_sofachair`, `stories_sch_chair`, `studio_chair`, `studio_stool`, `summer_chair*1`, `suncity_c19_chair`, `theatre_seat_b`, `theatre_seat_g`, `uni_c15_chair`, `uni_chair`, `uni_girlchair`, `usva2_chair`, `usva3_chair`, `usva4_chair`, `usva5_chair`, `usva_chair`, `val11_chair`, `val14_recchair`, `val15_chair`, `vikings_chair_g`, `vikings_chair_r`, `waasa_chair`, `waasa_chair_wood`, `wildwest_chair`.

Notas de sillas:
- Importar sillas con `rotation` 0/2/4/6 según la regla de la sección 9. Estas 9 volvieron del export con rotation 2 (no aceptan la 0), así que generarlas con rotation 2: `bw_lgchair` (Lifeguard Tower, 1x1x2.5), `bw_chair`, `bw_chair_p`, `bw_chair_g`, `bb_crchair`, `bb_crchair2`, `bb_crchair3`, `summer_chair*1`, `attic15_chair`.
- Familias con colores: el nombre base y el `*N` no se comportan igual. Disponibles con nombre base: `chair_norja`, `chair_silo`, `barchair_silo`, `sofachair_silo`, `sofachair_polyfon`, `soft_sofachair_norja`, `icedblk_c16_chair`, `icedblk_c16_sofachair`, `glass_chair`. Disponibles solo con `*N` (probado con *1; *0 en classic3): `anna_chair*1`, `chair_basic*1`, `classic3_chair*0`, `diner_chair*1`, `gothic_chair*1`, `purablk_c16_chair*1`, `romantique_chair*1`, `romantique_pianochair*1`, `summer_chair*1`. Los demás colores no se han probado.
- `room_invisible_sit_tile` (silla invisible) está disponible. De los `nft_*` solo llegó `nft_h26_cardchair` (1 de 85).

### 11.4 Sillas NO disponibles (281 de las 428 probadas) — no usar

No llegó casi ningún `nft_*` (84 de 85), ni los `hc*`/`hcc_*`/`hc_*` (HC), `ads_*`, `*_nt_*` (no tradeables), `xmas11_*`, `xmas_c*`, `val_c*`, `usva*_nt`, ni `chair_plasto*`/`chair_plasty*`/`nft_chair_plasto` (ni el nombre base ni `*1`), `noob_chair*`, `rubberchair*1`, `val_hSeat*1`, `mode_gold_armchair*1`, `olympics_c16_swissball*1` ni `sofachair_polyfon*1`. Si hace falta una silla fuera de la lista 11.3, probarla primero con una muestra.

## 12. Halloween, Dark Mansion, comida, velas, lámparas y mesas (verificado importando muestras, 05/10/2026)

Medidas ancho·largo·alto. "?" = alto no confirmado.

### 12.1 Halloween: sí llegaron (46 de 75 probados) — usar

- **Muros y estructura**: `hween_c20_housewall` 2·1·4, `hween_c25_oldwall` 2·1·3.7 ("Worn Wall", muro de piedra claro; con rotation 2 suelto queda como un pilar grueso), `hween_c25_oldwallwindows` 2·1·? ("Busted Windows": ventanas rotas con barrotes oscuros, NO rombos), `hween_c23_tombwall` 1·2·6, `hween_c24_darkschoolwall` 1·2·4, `hween_c24_talldarkschoolwall` 1·2·8, `hween_c22_gothicblock` 1·1·1 (se puede pisar), `hween_c22_gothicpillar`, `hween_c22_gothicstairs`, `hween_c23_tombpillar`, `hween_c23_tombslab`, `hween_c25_junkwall`, `hween_c25_signage`.
- **Mobiliario**: `hween_c22_gothictable` 2·3·1.5, `hween_c24_promtable` 2·2·?, `hween_c26_readingcouch` 3·1 (sentarse), `hween_c26_bookshelf`, `hween_c26_glassdisplay`, `hween_c22_gothicbed`, `hween_c22_mirror` 1·3·1, `hween_c26_hauntedmirror`, `hween_c22_gothicrug` 2·3·0.1, `hween_c22_pitofsouls` 2·2·0, `hween_c22_gothicbanner`, `hween_c22_gothicportrait` (cuadro; el catálogo lo lista como 1·2·0.00001), `hween_c22_gothicdrapes`.
- **Pequeños**: `hween_c22_bloodchalice`, `hween_c22_gothicteaset`, `hween_c22_undeadfood`, `hween_c22_lucifer`, `hween_c22_darkskull`, `hween_c22_bookofdead`, `hween_c22_zombiehand`, `hween_c22_gothicstatue`, `hween_c24_candybowl`, `hween_c24_eyejar`, `hween_c26_gothcandle` 1·1, `hween_c26_ivystatue`, `hween_c26_letters`, `hween_c26_circle`, `hween_c25_weed1`, `hween_c25_bedofmushrooms`, `hween_c23_ancientweb`, `hween_c23_walltorch`, `hween_c23_sculptureofanubis` (estatua egipcia grande; la que más le gustó en el museo), `hween10_fog`.
- Suelos Halloween: ver 11.1 (`hween_c20_floor`, `hween_c21_stonefloor`, `hween_c22_marblefloor`, `hween_c23_tombfloor`, `hween_c24_darkschoolhallfloor`, `hween_c25_mossyfloor`, `hween_c25_tiledfloor`, `hween_c26_wornfloorboards`) y `hween13_floor2` (castillo, 2·2), `hween_c19_autumnfloor` (2·2·0.1), `hween09_floor` (parquet oscuro, 2·2, visto en su cafetería).

### 12.2 Halloween: NO llegaron — no usar

`hween_c22_`: `darkmagictable`, `taintedtable`, `gothicsofa`, `taintedsofa`, `darkcandles`, `bloodvial`, `gothiclamp`, `taintedlamp`, `roseskull`, `archdarkangel`, `gothicbookshelf`, `taintedbookshelf`, `darkfeathers`, `darkangelstatue`, `darkmagictele` · `hween_c24_`: `cupcakes`, `neoncupcakes`, `paperskulls`, `skullsnail`, `streamers`, `pumpkinwitch`, `yellowskullbottle` · `hween_c26_`: `ivycandle`, `skullvase`, `dresser`, `cage`, `batpillow`, `darkfiend` · `hween_c25_`: `campfire`, `makeshifttable`, `makeshiftendtable` · `hween09_chandelier`, `hween_r16_chandelier`/`2`, `hween14_hangskull`, `hween14_skullcandle`, `hween_c19_bewitchedcandles`.

Sustitutos usados cuando falta algo: mesas = `gothicblock` + tapa, sofás = `readingcouch`, lámparas = `gothcandle`/`gothiccandelabra`.

### 12.3 Dark Mansion (`mafia_c26_*_dark`): sí llegaron — usar

| Classname | Medidas | Notas |
|---|---|---|
| `mafia_c26_sofa_dark` | 3·1·1.3 | se puede sentar |
| `mafia_c26_armchair_dark` | 1·1·1.3 | se puede sentar |
| `mafia_c26_coffeetable_dark` | 1·2·0.837 | con rotation 2 ocupa 2·1; se alinean varias para armar mesas largas; una sola pieza con rotation 2 sirve de barra de 2 baldosas (ver sección 14) |
| `mafia_c26_sidetable_dark` | 1·1·0.992 | **mesita redonda de pedestal** (no es panel ni poste: apilada se ve como un bosque de mesitas) |
| `mafia_c26_wall_dark` | 2·2·2.25 | panel/muro bajo oscuro |
| `mafia_c26_tallwall_dark` | 2·2·4 | muro alto |
| `mafia_c26_divider_dark` | 2·1·0 | divisor pisable |
| `mafia_c26_vase_dark` | 1·1·1 | florero oscuro con rosas (visto en su sala) |
| `mafia_c26_planter_dark` | 1·1·2 | maceta con planta verde (visto en su sala; sirve de arbusto) |
| `mafia_c26_drinkcabinet` | 3·1·3.1 | gabinete de bebidas (máquina), oscuro |
| `mafia_c26_drinktray` | 1·1·0.6 | bandeja |
| `mafia_c26_pasta` | 1·1·0.2 | espagueti con albóndigas |

También llegó antes: `mafia_c26_vintagechair`. NO llegaron: `mafia_c26_window_dark`, `mafia_c26_walllamp_dark`. Del juego de luz (`_white`) no se probó nada.

### 12.4 Comida y bebida

- **Sí llegaron**: `uni_pizza` 1·1·1, `pizza`, `ktchn_plates` 1·1·0, `xmas_c24_plate` 1·1·0.1, `picnic_food1` 1·1·1 (quesos), `picnic_food2`, `classic2_burger`, `classic2_tray`, `classic2_drinkcup`, `mexico_c24_tacoplate`, `school_stuff_03`/`04`/`05`/`06`, `catcafe_c23_food1`/`food2`, `br_c25_savouryfood`, `br_c25_sweetfood`, `paris_c15_food2`, `thai_c21_food`, `army_c15_food`, `hween14_grossfood`, `hween11_dish1`/`2`/`3`/`4` (ojos, cerebros, cabeza, dedos), `mafia_c26_pasta`, `tray_cake` 1·1·0 (torta elegante), `tray_glasstower` (torre de copas de champán con botella, visto en su museo), `mafia_c26_drinktray`, `dessertcafe_c17_glass` 1·2·1, `pirate_bottle_4` 1·1·1, `hween_c24_blueskullbottle`/`greenskullbottle`/`pinkskullbottle`, `drinks`, `classic7_drinks`, `ktchn_c15_drinks`, `uni_drinks`, `sunsetcafe_c20_drinks`, `exe_drinks`, `fantasy_c22_frothydrink`, `wonderland_c25_drinkme`, `toaster`, `ktchn_bBlock`, `paris_c15_breadstall`, `vwave_c21_mocktails` (state 4, visto en su sala).
- **NO llegaron**: `diner_tray_0`–`6`, `xmas_c16_food1`–`3`, `paris_c15_food3`, `nyc_c23_fastfood`/`salad`, `ktchn_c15_food`, `xmas14_lobsterdin`, `easter_c17_*` (fruitsalad, choccupcake, carrotcake, sweetpastries), cupcakes (`hween_c24_*`, `pastel_c19_cupcake`), `coffee_cup`, `mm_lemon_drink`, `xmas13_eggnogglass`/`eggnogbowl`, `CF_350_glass`, `dessertcafe_c17_donut`/`froyo`/`mochi`/`creamrolls`, `pirate_bottle_1`–`3`, `val_c19_bottle`, `samovar`, `val_c20_cake*`, `xmas_c23_*cake*`, `paris15_cake`, `gothiccafe_c20_*`, `petfood10`, `wonderland_c25_eatme`, `hhistory_c24_megamooncake`, `ny2015_cctray`/`drktray`, `xmas_c19_stackedicedrinks`, `ktchn15_coffeemaker`, `ktchn_c15_bread`.
- **Llaves**: ninguna llegó (`xmas_c21_key1`–`5`, `shome_keys`, `CF_1000_goldenkey`, `nft_h23_bigkey`).

### 12.5 Velas, candelabros, lámparas, suelos extra y mesas

- **Velas y candelabros (llegaron)**: `gothiccandelabra` 1·1·1 (candelabro alto de pie, visto en su museo), `hween13_piglamp`, `hween13_candle`, `gothic_candles`, `grunge_candle`, `rela_candle1`, `rela_candle2`, `rela_candles1`/`2` (1·1·0)/`3`, `hween14_candletrail` 1·1·0.1, `rainyday_c20_scentedcandles`, `skullcandle`, `lodge_dark_candle`, `thai_c21_candledish`, `arabian_tray2`, `lamp2_armas`, `hween_c26_gothcandle`, `gift_incense` (state 1) con `hs_smoke` encima (incienso con humo, visto en su sala). NO llegaron: `xmas13_snowflake1`, `triplecandle`, `rcandle`, `rcandleset`, `wcandle`, `wcandleset`, `ducket_c26_*candle`, `xmas_c16_candles`/`deskcandles`, `bohogarden_c21_candlebowl`, `spa_c22_candle`, `nft_ff23_goldencandles`.
- **Lámparas y arañas (llegaron)**: `gothic_c15_chandelier` 1·1·0.001, `lodge_dark_chandelier` 1·1·0.001, `lodge_chandelier`, `silo_c24_chandelier*0`/`*1`, `bling_c15_chandalier`, `js_chandelier`, `bolly_lamp`, `exe_artlamp`, `silo_c24_standinglamp*0` (1·1·1, negra), `silo_c24_desklamp*0` (1·1·1, negra), `norja_c24_standinglamp*3` (1·1·1, negra), `org_lampblk` (1·1·0, negra), `attic15_lamp`, `bazaar_c17_lamp`, `usva5_lamp`. Las arañas miden 0.001 de alto: son luminarias de techo; no probadas en una escena, no usarlas flotando sin ver cómo se ven. NO llegaron: `darkelegant_c20_light`, `xmas_c19_chandelier`, `ads_idol_lamp`.
- **Suelos extra (llegaron)**: `santorini_c17_mosaicfloor` 2·2·0.001, `greek_c15_floor` 2·2·0.001, `hween_c19_autumnfloor` 2·2·0.1, `classic3_floor3*0` 2·2·0.1, `bolly_tile2`, `pixel_floor_brown`, `pixel_floor_blue`, `mystics_bfloor`, `tile`, `hween_c18_bloodfloorstreak`, `norja_c24_floor*3` (negro, 2·2·0.0001), `hween13_floor2`. NO llegaron: `art_c20_floor`, `xmas12_floortile`/`cfloor`/`cfloorbig`, `xmas11_stonefloor`, `xmas11_luxfloor1`, `val_c20_floor`, `mystics_btile1`, `darkmodern_c20_floorlight`, `dessertcafe_c17_floor`. (Nota: `xmas12_cfloorbig` aparece en un export suyo, así que lo tiene aunque la prueba lo marcó como no llegado.)
- **Mesas (llegaron)**: `icedblk_c16_table` 2·2·0.8 (y `*2`, `*9`), `table_silo_med*2` ("Black Coffee Table" 2·2·0.7), `table_norja_med*2` ("Black Coffee Table" 2·2·0.8), `mode_gold_largecoffeetable`, `mode_gold_smallcoffeetable`, `table_polyfon_med`, `classic9_table`, `mafia_c26_coffeetable_dark`. NO llegaron: `romantique_c15_table*5`, `gothiccafe_c20_coffeetable`, `elegant_c17_table`, `darkmodern_c20_lowtable`, `dessertcafe_c17_coffeetable`, `val_table1`, `val12_table`.
- **Piezas blancas/negras conocidas (para diseños en blanco y negro)**: negros = `hween_c22_marblefloor`, `norja_c24_floor*3`, `org_chairblk`, `org_lampblk`, `table_silo_med*2`, `table_norja_med*2`, `silo_c24_standinglamp*0`, `mafia_c26_*_dark`, `purablk_c16_fridge` (2.5 alto, 1·1), `goth_table` (1·5·1.2, negra, de su sala). Blanco/claro = `hween_c25_oldwall` (piedra clara); `tile_marble` resultó **crema/beige**, no blanco (captura del museo). No se ha confirmado ningún suelo 2·2 blanco: si hace falta, probar la variante blanca de `norja_c24_floor*N` o `greek_c15_floor` con una muestra.

### 12.6 Técnica: validar huellas y alinear

- Huella = (w, l) del furni; con rotation 2/6 se intercambian. El ancla es la esquina mínima (x, y). Dos furnis chocan si comparten celda y nivel (z de piso vs z de objeto sobre mesa).
- Objetos sobre una mesa: z = alto de la mesa (ej. 0.837 con coffeetable_dark) y celda dentro de la huella de la mesa.
- Mesa larga a partir de piezas: tres `mafia_c26_coffeetable_dark` con rotation 2 en (x, y), (x, y+1), (x, y+2) forman una mesa 2·3 que coincide con dos sofás `mafia_c26_sofa_dark` de 3·1 (rotation 2 a la izquierda, 6 a la derecha).
- Muros de furni: `hween_c25_oldwall` / `oldwallwindows` rotation 4 en el fondo (y=0, cada 2 en x) y rotation 2 a la izquierda (x=0, cada 2 en y); nunca en el lado de la cámara.
- Un muro 1·2 (como `hween_c24_darkschoolwall`) no sirve de fondo a lo largo de x sin probar: su cara larga queda en el eje y. Para el fondo, preferir muros 2·1 (`oldwall`, `housewall`).
- Dibujar un plano de huellas con matplotlib (rectángulos por clase, texto con la clase) y mirarlo antes de entregar.

### 12.7 Receta: cabina de restaurante oscura (escena de la película Obsession, Little Toni's)

Referencia visual: cabina de dos con banca de cuero oscuro, mesa baja con platos y velas, ventanales de rombos ámbar atrás, luz tenue. Armado (32 furnis): piso 2×(2·2) `hween_c24_darkschoolhallfloor` en x1–8, y1–4; fondo `hween_c25_oldwall` + 3 `hween_c25_oldwallwindows` centrados sobre la mesa; muro izquierdo `oldwall` rotation 2; paneles `mafia_c26_wall_dark` en x1 y x7 (y1 e y3) detrás de las bancas; `mafia_c26_sofa_dark` en (3,1) rotation 2 y (6,1) rotation 6; tres `mafia_c26_coffeetable_dark` rotation 2 en (4,1),(4,2),(4,3); sobre la mesa a z 0.837: pasta en (4,2) y (5,2), candelabro `gothiccandelabra` (4,1), botella (5,1), `tray_cake` (4,3), vela (5,3); dos candelabros de piso en (3,4) y (6,4). Lo que no se logró: los rombos ámbar de las ventanas (no hay furni disponible parecido) y no se pudo igualar el color de las bancas.

### 12.8 Receta: cabaña minimalista en blanco y negro (199 furnis, sin bc_*)

Rediseño de la cabaña del usuario (x 2–20, y 3–15, original de 239 furnis con madera, techo y desorden). Reglas del estilo: quitar techo, adornos, comida y plantas; conservar solo las zonas (cocina, salón, comedor, escritorio) y los furnis funcionales.

- Piso: `hween_c22_marblefloor` (negro) 2·2 en x3–19 y y4–14 cada 2 (54 piezas).
- Alfombras blancas: `tile_marble` 1·1 (0.15) en x3–7 × y4–10 (salón) y x12–20 × y9–14 (comedor, centrada en la mesa); todo lo que va encima sube a z 0.15.
- Muros: `hween_c25_oldwall` rotation 4 en y=3 (x 3 a 19 cada 2) y rotation 2 en x=2 (y 3 a 15 cada 2); 3 `oldwallwindows` (x7, x15 al fondo; y7 a la izquierda).
- Salón: `mafia_c26_sofa_dark` rotation 4 en (4,4) y rotation 0 en (4,9); `mafia_c26_coffeetable_dark` rotation 0 en (5,6); 2 `mafia_c26_armchair_dark` rotation 6 en (7,6),(7,7); `silo_c24_standinglamp*0` en (7,4); `mafia_c26_drinkcabinet` rotation 4 en (8,4).
- Cocina (misma posición que el original, z 0): `ktchn10_block` en (11,4),(14,4),(17,4), columna x11 y5–7 e isla y7 en x12,13,16–19; `ktchn10_sink` (12,4), `ktchn_stove` (15,4), `purablk_c16_fridge` rotation 4 en (18,4), `ktchn_trash` (19,4); hueco de paso en x14–15 de la isla.
- Comedor: 2 `goth_table` rotation 2 en (14,11) y (14,12) (mesa 5·2 negra) + 8 `org_chairblk` (norte (15,10),(17,10) rotation 4; sur (15,13),(17,13) rotation 0; oeste (13,11),(13,12) rotation 2; este (19,11),(19,12) rotation 6) + 2 `gothiccandelabra` sobre la mesa a z 1.35.
- Escritorio: `table_silo_med*2` (5,13), `silo_c24_desklamp*0` a z 0.7, `org_chairblk` (5,12) rotation 4.
- Funcionales conservados: `conf_invis_control` y `room_switcher2`.
- Sin verificar visualmente: el blanco de `tile_marble` (después se vio que es crema), el color de `ktchn10_*`, la altura del `oldwall`.

## 13. Sala de ciudad/zombies (1165 furnis, 374 de diseño) — técnicas aprendidas

Sala de juego post-apocalíptica con **cinco zonas a distinta altura** (se ignoran los wireds, ~790 piezas `wf_*`, `sb_*` de juego e invisibles). Aprendido de su export (05/10/2026).

### 13.1 Zonas y niveles

| Zona | z base | Rango x / y | Contenido |
|---|---|---|---|
| Pasillo/bodega | 0 | x 15–26, y 9–20 (L de 2 de ancho) | cajas, luces de suelo, suciedad |
| Lote urbano | 17 | x 28–39, y 22–33 | concreto, acera, barricada de llantas |
| Calles y edificios | 22 | x 20–40, y 14–34 | calles en U con edificios y mobiliario urbano |
| Refugio | 26 | x 17–24, y 11–18 | piso blanco, sofás, mesita central |
| Techo del refugio | 31 | x 17–24, y 11–18 | borde de techo en L |

Otros niveles chicos: z 15 (apoyos de wired en x 11, y 45–46) y luces `snowb_light1` sueltas a z 1.95, 5.64 y 6.64. Los z enteros 17, 22 y 26 coinciden con alturas de baldosa de la sala; el piso de cada zona va a ese z y los objetos suman encima (acera 0.2, cajas 0.8, etc.).

### 13.2 Calles y ciudad (z 22)

- Calzada: `sb_tile` ("Concrete Patch", 2·2·0, se puede pisar) cada 2 en x/y.
- Aceras: `urban_sidewalk` (2·2·0.2, 4 estados) como borde de la calzada; rotation 4 en franjas horizontales (y 14 y 20, x 28–38 cada 2) y rotation 2 en las verticales (x 20 y x 26, y 22–32 cada 2). `state` 0 liso, 1 y 3 variantes con grietas.
- Fachadas: `nyc_c23_buildingblack` ("Black City Brick", 2·1·7) sobre la acera a z+0.2 (22.2); el `state` 0–8 cambia el edificio (en la sala se usan 0, 3, 4, 5, 6, 7); rotation 0 a lo largo de y=14 y rotation 2 a lo largo de x=20, alternando estados para que no se repitan.
- Mobiliario urbano sobre la acera a z+0.2: `urban_bin` (state 0/1), `urban_bench` (2·1·1.2, rotation 2/4), `urban_lamp` (1·1·1, state 1 encendida), `nyc_c23_manhole` (alcantarilla), `nyc_c23_mailbox`, `nyc_c23_signs`, `nyc_c23_wallposters`, `tokyo_c18_pole`, `urban_bsktbll`, `paris_c15_vegstall1`, `nyc_c23_roaditem3`, `grunge_barrel` (1·1·0), `urban_carsofa`.
- Escaleras de incendio: `nyc_c23_firestairs` (2·1·0.001) pegadas a la fachada, repetidas a +1.4 y +2 de alto.
- Fondo de cielo: `roombg_stars` / `roombg_color` (1·1·0.000001).

### 13.3 Suciedad y narrativa (decals)

- Suciedad en el piso: `vwave_c21_trash` ("Promenade Litter", 2·2·0.0001, states 0–3), `hween_c16_webs` (1·1·0), `hween_c17_lichen` (1·1·0, states 0–2), `hween_c25_weed1/2/3`, `hween_c15_rubbish`, `paris_c15_shadow` (sombra) y `hween_c18_bloodfloorstreak` (sangre) a z de piso +0.0001.
- Se reparte: no más de una de cada por casilla, alternando state y rotation 0/2 para que no se repitan; las manchas más densas junto a muros y esquinas.
- Narrativa: `det_body`, `sc_bodybag`, `sc_horsehead`, `hween_c17_torturebed`, `nft_h24_haf_zombie`, `hween_c17_rat`, `nyc_c23_pizzarat`, `nyc_c23_citypigeons`, `hween_c22_darkskull`, `lt_c26_dagger`, `army_c15_jerrycan`, `bazaar_c17_meat`.

### 13.4 Apilar objetos

- Montones: `attic15_box` (1·1·0.8) apiladas cada 0.8 (22.2 y 23.0), y encima ropa (`nft_cf25_leather`, `nft_cf25_nylon`, `nft_cf25_fabricscissors`) a +0.8; las piezas del mismo montón suman +0.001 para que se dibujen en orden.
- Barricada: 19 `bump_tires` (1·1·0.7) en L (columna x30 y 24–33 y fila y24 x30–39) sobre el lote a z 17; encima 18 `room_invisible_sit_tile` a z 17.7 para poder sentarse sobre las llantas.
- Bloqueo de paso: `room_invisible_block` a z de acera (17.2, 22.2) bajo la franja decorativa (x29, y 22–33) para que no se camine por encima del decorado.

### 13.5 Pasillo/bodega (z 0)

- Pasillo en L de 2 de ancho: tramo horizontal y 19–20 (x 15–26) y vertical x 25–26 (y 9–20).
- Piso: 11 `sb_tile` y luces `exe_light` (1·1·0, sobre el piso, `state` 1/2/3 = colores) cada 2–3 casillas.
- Mobiliario `attic15_*` (cajas, sillas, estante, lámpara de aceite, cuadros de piso a 0.8 y 1.66) y `grunge_*`.
- Pilares de esquina: `xmas_c22_elewoodv2` (1·1·0.5) a z 0 y a z 2, con `easter_c26_elvenpillar` a 1.9 en (26,20).
- Techo: `sb_block` ("Skate Platform", 2·2·0.5) a z 3.76 sobre todo el pasillo, y `exe_light` encima a 4.26.

### 13.6 Refugio (z 26)

- Piso blanco: 4×4 `val12_marble1` (2·2·0) cada 2 (no disponible hoy para el usuario: usar un suelo de 11.1 o 12.5).
- Límites: `studio_fence` (1·2·1) en el lado este (x24, y 11–16) y el sur (y18, rotation 2); esquina `studio_fencecr`.
- Asientos: `soft_sofa_norja*3` (2·1) contra el norte (y11, rotation 4) y el oeste (x17, rotation 2); `attic15_table` (2·2) en dos esquinas.
- Centro de 2·2: cuatro `ktchn_c15_crn` a z 26.35 con rotation 0, 2, 6, 4 formando un bloque; `patch_cloud` debajo, `pj_c26_nightlightghost` y `hobbies_c26_rose` encima.
- Ambiente: `hween10_fog` suelto en tres casillas, `hween_c16_webs`, `hween_c17_lichen`, `bazaar_c17_bubblejuiceblower`, `val_c20_blackboard`.
- Techo: 14 `hween_c19_angleroof` (Witch Roof 1·1·1) a z 31 solo en el borde norte (y11, x18–24, rotation 4) y oeste (x17, y12–18, rotation 2) + `hween_c19_roofcorner` rotation 3 en (17,11): es un alero que marca dónde irían las paredes.

### 13.7 Reglas de diseño que se repiten

- Cada zona = rectángulo propio de x/y y z base propio; todo su contenido usa ese z + decimales.
- Capas: piso base → piso fino/acera → decals (+0.0001) → muebles (+0.2 sobre acera) → objetos apilados (+0.8) → detalles.
- El `state` es la variante visual (edificios, aceras, luces, cajas, maleza): variarlo evita repetición.
- Variar rotation 0/2 en decals iguales para romper el patrón.
- Los límites se hacen con muebles bajos (llantas, vallas, aleros) más invisibles de bloqueo/asiento, no con muros altos.

## 14. Cristal con `window_square` (furni de pared): vitrina y baranda — verificado por el usuario (05/10/2026)

Las **ventanas** (`window_square`, furni de pared del Almacén BC, 1 baldosa de ancho × 1.156 de alto, no ocupa casilla) son una de las técnicas favoritas del usuario para dar detalle: con ellas arma **vitrinas** (comida u objetos detrás de un cristal con marcos finos en cuadrícula, como en una cafetería) y **barandas de cristal** con pasamanos oscuro. El resultado de la vitrina (14.4) lo confirmó como "perfecto"; la baranda (14.6) solo está analizada desde su export, sin probar una reproducción. Después la usó a gran escala: cafetería, museo y casas de cristal (sección 15).

### 14.1 Cómo funciona

- Los furnis de pared van en la lista `wallFurni` del JSON (solo los lee/coloca la versión modificada de G-Presets; ver skill `gpresets`): `{"wallPosition": ":w=X,Y l=LX,LY r", "className": "window_square", "state": ""}`. Son **absolutos**: no se mueven con la esquina de import. Los furnis de piso sí son relativos a esa esquina. La cadena puede terminar en `l` o `r` (pared de cada lado) y a veces trae un sufijo extra (` a=228`, significado desconocido): conservarla tal cual si se copia un export.
- **Un furni de pared se dibuja siempre detrás de los furnis de piso** si está en la pared real. Para que el cristal quede **delante** de la comida hay que "sacarlo de la pared": subir la coordenada Y de `:w=X,Y` en 1 respecto al muro real (muro real = `Y0`, ventanas en `Y0+1`). Los furnis de piso van en la primera fila junto al muro (y absoluta `Y0+1`). El servidor aceptó esa posición fuera de la pared.
- Por eso se importa con `:ip X0,Y0+1` (ej. `:ip 18,1`, con ventanas en `:w=18..,1` y furnis en x/y relativos desde 0,0). Quitar antes las ventanas de prueba que ya haya en el muro.
- **Probado después (sección 15)**: el servidor también acepta ventanas a muchas baldosas de la pared real (caras sur y este, mamparas en medio de la sala), alturas de z≈10.8 y hasta 981 furnis de pared en una sala.

### 14.2 Fórmula de la posición de pared (verificada en el juego)

Del código de Nitro (`LegacyWallGeometry`), escala 64. La baldosa (x,y) cubre [x−0.5, x+0.5]×[y−0.5, y+0.5] y `H` es la altura de la baldosa (0 en suelo plano).
- **Pared `r`** (norte/sur): x del mundo = `X + LX/32 − 0.5`; el plano está en `Y + 0.5`. z = `H − (LY − LX/2)/32`. Para una fila de altura `c`: `LY = round(c + LX/2)`.
- **Pared `l`** (oeste/este): el plano está en x = `X + 0.5`; y del mundo = `Y + 0.5 − LX/32`; z = `H − (LY + LX/2 − 16)/32`. Para una fila de altura `c`: `LY = round(c + 16 − LX/2)`.
- **Plano de cada borde de una baldosa (x,y)**: cara norte → `r` con `Y = y−1`; cara sur → `r` con `Y = y`; cara oeste → `l` con `X = x−1`; cara este → `l` con `X = x`.
- **El ancla es el CENTRO** de la ventana de 1 baldosa (no una esquina).
- En paredes reales `LY` suele ser positivo; fuera de la pared o sobre suelo elevado puede ser **negativo** (en su export hay ventanas con `LY` de −29 a −168).
- Un píxel de pantalla nativo = 1 unidad de `LX`/`LY`. Una baldosa de ancho = 32 unidades; una ventana mide 37 unidades de alto.
- **Importante**: la altura real depende de `c = LY − LX/2` (lado `r`), no solo de `LY`. Dos ventanas en la **misma fila** deben cumplir la fórmula con `c` constante por fila. Si se usa el mismo `LY` en columnas con distinto `LX`, las barras quedan desalineadas como una escalera (error cometido en la v7). Con `LX` constante y `X` distinto no hay problema.
- Posición horizontal: `u = X*32 + LX`; para pasar de `u` a `(X, LX)` usar `X = X0 + u // 32` y `LX = u % 32`.
- Para otra sala, no copiar `LY`: calcular `c` de la fila (ver calibración en 15.1) y ajustar todas las `c` por igual si queda corrida.
- El módulo de 2 baldosas (14.4) cubre exactamente [X0−0.5, X0+1.5].

### 14.3 Medidas de la vitrina de referencia (tomadas de una captura del usuario)

- Para copiar un efecto de una captura, **medirla con código** (PIL/numpy: columnas de color del marco, y de las barras horizontales). El zoom de las capturas del usuario suele ser 2× del cliente × 4× de recorte: bloques de 8 px = 1 píxel nativo (las líneas de 1 px miden 8 px).
- Marcos verticales a 14 y 18 unidades uno del otro (paso medio ≈ 16: medio ancho de ventana). Barras horizontales cada 18–19 unidades (medio alto de ventana). Es decir, las ventanas van **solapadas**, no una al lado de la otra; eso da la cuadrícula fina.
- El "escalonado" que se veía entre columnas era solo la pendiente isométrica de la pared, no un desfase real: todas las columnas de una fila están a la misma altura real.
- La vitrina de referencia cubre unas **2 baldosas de ancho** y unas 2.7 ventanas de alto. Más cantidad no es mejor para una vitrina suelta: 11 columnas × 5 filas (55 ventanas) se ve saturado. (Esto cambió cuando el usuario pidió expresamente "muchas vitrinas, hasta 1000 ventanas": entonces se repite el módulo; ver sección 15.)

### 14.4 Receta de la vitrina (15 ventanas + barra, 2 baldosas de ancho) — aprobada por el usuario

- Columnas en `u` = 16, 30 y 48 (desde la baldosa `X0`): `(X0, 16)`, `(X0, 30)`, `(X0+1, 16)`.
- Filas: 5 filas con `c` constante por fila, `LY = round(c + LX/2)`. Total 3 × 5 = 15 ventanas, todas en `:w=X,Y0+1`. **Las `c` dependen de la sala**: en su sala 13×13 actual son −26, −44, −63, −81, −100 (sección 15.1); en su sala anterior eran 109, 91, 72, 54, 35.
- Barra: 1 `mafia_c26_coffeetable_dark` rotation 2 (2·1·0.837) en (0,0), z 0 (en la cafetería 13×13 se usó `wildwest_desk`, 2·1·1.1, que llegó bien).
- Objetos encima a z 0.838: `picnic_food1` en (0,0), `tray_cake` en (1,0). Más opciones verificadas como disponibles: `br_c25_savouryfood`, `pirate_bottle_4`, `uni_pizza`, `catcafe_c23_food1`, `ktchn_plates`, `picnic_food2`, `thai_c21_food`, `gothiccandelabra`.
- Para una vitrina más ancha, repetir el módulo de 2 baldosas (una barra y 3 columnas por cada 2 baldosas) en vez de añadir columnas intermedias.
- Script de referencia (Python): generar `furni` relativos y `wallFurni` absolutos, `exportOrigin {x: X0, y: Y0+1, floor: 0}`, y avisar el comando `:ip X0,Y0+1`.

### 14.5 Qué no se hizo / queda por ajustar (vitrina)

- No se logró el marco de madera oscura, el techo ni el muro blanco con cuadro de la captura original: la parte verificada es el cristal con su barra. Si el usuario los quiere, probar muros oscuros 1·2 (`hween_c24_darkschoolwall`, 4 de alto) como postes y los tablones `hween_c24_darkschoolhallfloor` como techo, sin usar `mafia_c26_sidetable_dark`.
- Las `c` de las filas se calculan con 14.2/15.1; si en otra sala queda corrida, mover todas las `c` por igual.

### 14.6 Baranda de cristal con pasamanos (analizada de su export `pasamano.json`, esquina de export x12,y16)

Detalle que el usuario muestra como "hermoso": una baranda baja de cristal con un pasamanos oscuro encima, junto a una barra. **Qué piezas forman la baranda es una inferencia mía a partir del export (no la captura), sin confirmar con él**; lo observado en el JSON:

- **Cristal (probable baranda)**: ventanas bajas, una por baldosa, y cada una **doble**: dos ventanas casi coincidentes, desfasadas 2–3 unidades en horizontal y 1–4 en vertical, que engrosan el marco. Pared `r`, `Y=19`: en `X=14` `(l=7, ly=−33)` y `(l=9, ly=−29)`; en `X=15` lo mismo (la de `l=7` lleva ` a=228`). Pared `l` (el otro lado de la esquina): `X=15, Y=14`, `(l=6, ly=−31)` y `(l=9, ly=−30)`. Altura real `c = ly − lx/2` ≈ −36.5 y −33.5 en la pared `r`, −34 y −34.5 en la `l` (en la `l` la fórmula de `c` es la de 14.2).
- **Pasamanos (probable)**: lo único largo y delgado de ese export son 2 `bc_panel*47` (marrón oscuro `#503000`) con `state` 4 y rotation 4, apilados a z 1.65 y 1.75 en (1,2) relativo. Es **bloque BC puesto por el usuario**: si se reproduce, avisarle (regla de no usar `bc_*` por iniciativa propia).
- **Altura**: el piso cercano está a z 0.5 (`hween_c24_darkschoolhallfloor` a 0.5) y el pasamanos empieza a z 1.65 = 0.5 + 1.15, la altura de una ventana. Receta tentativa: una ventana de alto a lo largo del borde del nivel, pasamanos encima a z = piso + 1.15. Inferido: parece que la `c` de la ventana mide su borde superior (no verificado; en 15.1 se midió que `c` calibra el borde inferior).
- **Barra y detalles junto a la baranda** (estilo cafetería oscura): placas `bc_block_1*47` state 3 (z 1.5) y `bc_block_1*6` state 4 (z 1.8) como encimera; encima `hween_c18_olddocs1` (state 1 o 4), `gift_incense` state 1 + `hs_smoke` (incienso con humo, z +0.05), `hobbies_c26_dicetray` (state 1, z 2.2), `paris_c15_food2`, `vwave_c21_mocktails` state 4 sobre `xmas_c16_woodtile` (z 2.6/2.7); taburetes `barchair_silo*4` rotation 2 a z 0.5; piso `hween_c24_darkschoolhallfloor` (z 0 y 0.5) y `xmas12_cfloorbig` (state 1).
- Furnis de pared decorativos que ya tiene en esa sala (disponibles): `hc_wall_lamp` (state 1), `ktchn10_cabnt` (state 0), `poster` (state 19), `attic15_painting`, `hween09_wall1` (state 0), `wildwest_wallpaper` (state 0), `vikings_wall_r`, `arabian_wall` (state 0), `usva_wallrug` (state 0). Los cuadros se ponen a varias alturas sobre el cristal/pared para dar profundidad.
- Pendiente: pedirle una captura de la baranda con el export para confirmar qué ventanas son, qué significa el sufijo ` a=228`, y probar una reproducción en su sala.

### 14.7 Datos reales de su vitrina de cristal alta (mismo export, `Y=21`, pared `r`)

Para comparar con la receta 14.4, su propia vitrina usa 17 ventanas en tres columnas: `u` = 425 (`X=13, l=9`), 448 (`X=14, l=0`) y 457 (`X=14, l=9`). Las columnas `u=425` y `u=457` (una baldosa de distancia) tienen **6 filas muy solapadas** con `c` = −81.5, −90.5, −99.5, −107.5, −115.5, −125.5 (paso ≈ 8.8, o sea 4 capas de vidrio por punto); la columna `u=448` tiene 3 filas con `c` = −82, −100, −116 (paso ≈ 17). Conclusión de estilo: el usuario **apila muchas ventanas casi superpuestas** (pasos de 9 a 18 unidades, no 37) para obtener un cristal de cuadrícula fina y ligeramente tintado. Estos `ly` negativos dependen de la altura de baldosa de su sala; para otra sala usar la `c` (fórmula 14.2), no copiar `ly`.

## 15. Cristal a gran escala: cafetería, museo y casas de cristal (sala 13×13 del usuario, 05/10/2026)

Todo esto se construyó solo con `window_square` (vitrinas) más furnis de suelo, para su sala actual. Resultados vistos por el usuario en capturas: cafetería 13×13 ("quedó hermosa"), castillo/caja de cristal, museo Louvre ("me gustó"), casa de cristal cúbica de 3 pisos ("me gusta") y la casa isométrica escalonada ("me encanta el diseño isométrico"; vio la captura y marcó los muebles que sobresalían del vidrio, ver 15.6; la v2 le gustó y pidió la v3 con niveles al doble de altura).

### 15.1 Datos de su sala actual y reglas de importación

- Suelo de 13×13: x 3–15, y 1–13; la puerta está en (2,13) (se entra a la baldosa (3,13)); el muro norte real está en y=0 y el oeste en x=2. Las baldosas tienen `H=0`. El resto del mapa lo rodea con suelo de otras baldosas.
- Esquina de import: `:ip 3,1` y `exportOrigin {x:3, y:1, floor:0}`; los furnis de suelo van relativos (x−3, y−1) y los `wallFurni` absolutos. **Nunca `:ip auto`.** Después de importar, `:paredes` (o `:walls`) recoloca las ventanas del último preset; `forzar` si hace falta. Antes de importar una versión nueva, quitar los furnis y ventanas de la anterior.
- **El preset debe traer al menos un furni de suelo** o el importador no lo acepta (le pasó con la caja de cristal "castillo": se añadió una maceta).
- **Calibración de altura (medida de su captura)**: para una baldosa con `H=0`, el borde inferior de una ventana queda a z = (−c − 26)/32, con `c` según 14.2. Fila de base: `c = −26`. Paso de medio vidrio = 18.5 de `c` (0.58 de z); una ventana entera = 37.4 (1.17 de z). Fórmula general: **`c = round(−26 − 32·z_base)`**. Cinco filas de vitrina: `c` = −26, −44, −63, −81, −100 (cubren z 0–3.5). En su sala anterior las `c` eran 109, 91, 72, 54, 35: si cambia de sala, recalibrar con una prueba y una captura.
- Funciona hasta alturas altas: la casa de 3 pisos llegó a `c` ≈ −330 (z ≈ 10.8) sin problema. El servidor aceptó hasta `c` ≈ −335. **La v3 de la casa isométrica sube hasta `c` ≈ −675 (z ≈ 21.5): sin confirmar** que el servidor las acepte o que se dibujen; si el usuario ve que las filas altas faltan, bajar la altura de los niveles o dividir por tramos.
- **Límite de cantidad**: el museo se importó con 970 ventanas + 11 cuadros (981 furnis de pared) y se vio completo. El usuario dijo "no importa si usas hasta 1 mil ventanas" y luego "el tope de ventanas son unas 3 mil" (la v3 usa 1064).
- **Módulos**: lado `r` (norte/sur): `(X0,16)`, `(X0,30)`, `(X0+1,16)`; lado `l` (oeste/este): `(Y0,16)`, `(Y0,2)`, `(Y0+1,16)`; 5 filas. Una baldosa suelta = una columna `(X,16)` (o `(Y,16)`); entre baldosas contiguas se añade `(X,30)` / `(Y,2)` para la cuadrícula fina, sin la última (para que no sobresalga de la esquina).
- **Planos por baldosa**: ver 14.2. La cara hacia la cámara (sur y este) queda delante de los furnis de la baldosa; en la pared real (norte y oeste, `Y=0` o `X=2`) queda detrás. Las ventanas no bloquean el paso de los avatares: se atraviesan.
- **Suelo de 13 de ancho con piezas 2×2**: piezas cada 2 en x=3..13 e y=1..11, más solapes: columna x=14 a z 0.001, fila y=12 a z 0.001 y la esquina (14,12) a z 0.002.
- Siempre validar con script: huellas sin choques, cobertura del suelo y que todas las baldosas libres son alcanzables desde (3,13) (BFS, modelando una sola altura por baldosa; ver 15.6).

### 15.2 Qué llegó y qué no (museo y casas, según sus capturas)

- **Llegaron (se ven)**: `hween_c23_sculptureofanubis` (estatua egipcia grande, la que más le gustó), `mafia_c26_vase_dark` (florero con rosas), `mafia_r26_globe` (globo terráqueo), `tray_glasstower` (torre de copas con botella), `gothiccandelabra`, `mafia_c26_planter_dark`, `silo_c24_standinglamp*0`, `silo_c24_desklamp*0`, `wildwest_desk` (barra de madera oscura con cajones), `hween09_floor` (parquet oscuro), `tile_marble` (crema/beige), `mafia_c26_sofa_dark`, `mafia_c26_armchair_dark`, `mafia_c26_coffeetable_dark`, `mafia_c26_drinkcabinet`, `org_chairblk`, `table_silo_med*2`, `hween_c22_marblefloor`, `attic15_painting` (retratos de un militar) en la pared norte. `country_grass` y `window_square` también llegaron.
- **No se vieron (no llegaron o no se pudo ver)**: `classic9_statue`, `clothing_r26_mannequinskin`, `clothing_r26_scarface`, `CF_50_goldbar`, `CF_500_goldenglobe`, `nft_emerald_10`, `prizetrophy3*1`, `rare_dragonlamp*12` y `club_sofa` (bancas). Que estén en un export de otra sala suya no basta. En el primer museo aparecieron Anubis en 8 cajas aunque solo se asignaron 2 (causa desconocida); en los mostradores esas piezas simplemente no aparecieron.
- Por eso, para museos y casas usar solo: Anubis, floreros, globo, torre de copas, candelabro, macetas, sofás/sillones/mesas/gabinete oscuros y lo de 12.3–12.5; y probar con una muestra antes de usar otros.

### 15.3 Museo Louvre (receta `louvre_v2`: 970 ventanas, 297 furnis, aprobada)

- **Perímetro de mostradores-vitrina** (22): `wildwest_desk` de 2 baldosas con 15 ventanas cada uno, al norte (6, y=1, x 4–14 cada 2), oeste (5, x=3, y 2–10), este (6, x=15, y 2–12) y sur (5, y=13, x 5–13). Dos piezas de exhibición encima a z 1.12.
- **Anillo de 28 cajas de cristal de una baldosa** alrededor de la plaza (x=5 y x=13 en y 3–11, y=3 e y=11 en x 6–12) con huecos en el centro de cada lado: pedestal de `tile_marble` apilado 4 veces cada 0.15 (Anubis) o 3, y la pieza encima; 4 caras de cristal con 5 filas; las ventanas compartidas entre cajas contiguas se eliminan (dedup por posición).
- **Plaza central** 7×7 de `tile_marble` (z 0.01) y **pirámide escalonada** de 4 niveles (7, 5, 3 y 1 baldosas): niveles `r` con `Y = 3+t` y `10−t`, `l` con `X = 5+t` y `12−t`; dos filas por nivel (`c` y `c − 14.4`, con `c = −26 − 28.8·t`), columnas `(X,16)` y `(X,30)`: 224 ventanas. En la punta, el globo sobre un pedestal de 4 `tile_marble`.
- Cuadros: `attic15_painting` en la pared norte con `:w=X,0 l=0,−60 r` (X = X del módulo + 1) quedaron altos, sobre el cristal; en la oeste se usó `:w=2,Y l=0,−44 l` (no verificado).
- Pasillos de 1 baldosa entre mostradores y cajas: no poner lámparas ahí (se cortó la entrada en un intento; validar con BFS).

### 15.4 Casa de cristal isométrica escalonada (receta `casa_isometrica`, v1 a v3)

Idea (contexto de otros jugadores: vista isométrica fija de 30°, cámara al sureste que ve las caras sur y este): la profundidad se lee con volúmenes que retroceden hacia la cámara. Se arma una pirámide escalonada apoyada en la esquina noroeste (la más lejana) que retrocede hacia el sureste; las terrazas quedan hacia la cámara y los pisos de arriba sobresalen al norte (voladizo). Antes se hicieron la caja cúbica de 3 pisos (765 ventanas; "me gusta") y la casa de la colina con techo inclinado (365 ventanas), a partir de fotos de referencia.

- **Volúmenes** (x0–x1, y0–y1): R0 = x 4–12, y 4–12 (9×9); R1 = x 4–10, y 3–9 (7×7); R2 = x 4–8, y 2–6 (5×5). Alturas entre niveles: v1/v2 3.65 y 3.5 (F1 = 3.81, F2 = 7.31, losa de techo a z 10.66); **v3 el doble: 7.3, 7.0 y 7.0 (F0 = 0.16, F1 = 7.46, F2 = 14.46, techo 21.46)**. Losa del techo en x 3–9, y 1–7 (alero de 1 baldosa).
- **Suelo exterior**: `tile_marble` 1×1 en toda la sala a z 0.01 (tope 0.16). Planta baja: `hween_c22_marblefloor` (mármol negro 2×2) en R0 con solapes (x=11 y y=11 a +0.001, la esquina a +0.002).
- **Losas**: `tile_marble` 1×1 a z = nivel − 0.15 sobre R0∪R1 (piso 2) y R1∪R2 (piso 3), con huecos para las escaleras. La parte del techo de un piso que no cubre el siguiente queda como terraza abierta con baranda baja de una fila (barandas en x=12/y=12 a F1 y en x=10/y=9 a F2).
- **Cristal**: por volumen, 4 caras en los planos `X = x0−1` y `x1` (lado `l`), `Y = y0−1` y `y1` (lado `r`); columnas `(X,16)` por baldosa más `(X,30)` entre baldosas. Filas por nivel de altura H: `n = ceil((H−1.17)/1.17)+1` con paso `(H−1.17)/(n−1)` y `c = round(−26 − 32·z)`: 3 filas para H=3.5, 6 para 7.0, 7 para 7.3. Totales: v1/v2 528 ventanas, v3 1064 (hasta `c` ≈ −675, sin confirmar; ver 15.1).
- **Escaleras**: en v1/v2 eran switchback (ver 15.6, **no transitables**); en v3 son **espirales decorativas** (anillo 3×3, 3 vueltas, 23 peldaños de ~0.30, huecos 3×3 en las losas): SP1 centro (5,11) en F0→F1 y SP2 centro (9,8) en F1→F2. Son solo decorado.
- Versiones anteriores: caja cúbica 9×9 de 3 pisos de 3.5 con mampara de cristal en el hueco de escalera y 478 furnis de suelo (`tile_marble` 412, `hween_c22_marblefloor` 25, macetas 8, sillas `org_chairblk` 8, sofás 5, sillones 4, gabinetes 4, floreros 3, Anubis 3, mesas 2, candelabros 2, lámpara 1, mesa `table_silo_med*2` 1).

### 15.5 Cómo trabajar con él en este tipo de salas

- Pide que "todo se vea estéticamente lindo" y rechaza lo que se sale de su estilo (gótico, sin suelo, ventanas mal puestas): mirar sus capturas antes de seguir y corregir primero lo que señala. Marca con círculos lo que está mal.
- Cuando le gusta un concepto, pide **más escala** ("muchas más vitrinas", "no tengas miedo de subir más", "tope unas 3 mil ventanas", "si hace falta, haz la sala más grande", "altura de las vitrinas entre niveles al doble"): subir cantidad y altura sin miedo, pero validando y avisando qué puede fallar (altura máxima, cantidad de ventanas, escaleras). Si la sala hubiera que agrandarla, avisar el nuevo `:ip` y que él cambie el plano.
- Le gusta el estilo **minimalista** (pocos muebles, negro sobre crema): añadir "unos pocos más", no llenar. Quiere ver bien lo que hay dentro de cada nivel: cristal alto y muebles dentro de las zonas interiores.
- Le dan contexto con fotos de referencia (casa de cristal, casa en la colina, pirámide del Louvre) y con conversaciones de otros jugadores ("perspectiva isométrica", "3D"): interpretar el contexto como diseño de volúmenes y profundidad, no como pedir un cubo plano.
- Siempre entregar: archivo `.json`, comando `:ip` exacto, `:paredes`, qué se probó y qué no.

### 15.6 Muebles "metidos" en el vidrio y límite de caminar por pisos apilados (casa isométrica v2/v3, 05/10/2026)

La v1 de la casa isométrica la vio en el juego: plantas y sillones en la fila exterior (x=12, y=12, junto al cristal sur/este) y sobre las terrazas abiertas se veían **sobresaliendo** del vidrio. Pidió que se vea la ilusión de que están metidos, que la gente pueda subir del piso 1 al último, y unos pocos muebles más. La v2 (`casa_isometrica_v2.json`, 528 ventanas, 417 furnis de suelo) corrigió los muebles y le gustó; la v3 (`casa_isometrica_v3.json`, 1064 ventanas, 445 furnis de suelo) duplica la altura de los niveles. Ninguna de las dos tiene aún captura suya del resultado final.

**Reglas para que un mueble se vea dentro del cristal**
- Ningún mueble en terrazas abiertas (solo baranda baja) ni en la fila exterior de un volumen: dejar **una baldosa libre** entre el mueble y las caras sur y este (que son las que se dibujan delante). Las caras norte y oeste quedan detrás del mueble y no molestan.
- El cristal de cada volumen cubre toda la altura del piso y los muebles miden menos que eso (el más alto, `mafia_c26_drinkcabinet`, 3.1).
- Zonas interiores usadas: L0 x 4–10, y 4–11; L1 x 4–8, y 3–8; L2 x 4–7, y 2–5 (menos lo que ocupen las espirales). Validar por script que cada celda de cada mueble cae dentro de su zona interior y sobre suelo/losa de su nivel.
- Muebles de la v2/v3 (minimalistas, ≈14 en L0, 11 en L1, 7 en L2): sofá `mafia_c26_sofa_dark` rot 4 contra el norte con mesa `mafia_c26_coffeetable_dark` rot 2 y sillones enfrentados; comedor `table_silo_med*2` + 4 `org_chairblk` + `gothiccandelabra` sobre la mesa (z mesa + 0.7); `mafia_c26_drinkcabinet` rot 4 en el norte; escritorio `wildwest_desk` rot 0 con silla en su lado +y (rot 0) y `silo_c24_desklamp*0` encima (z +1.1); lámparas `silo_c24_standinglamp*0`, macetas, florero y Anubis sueltos.

**LÍMITE: una baldosa, una sola altura (corrección de un error mío)**
- En Habbo cada baldosa (x,y) tiene **una sola altura caminable**, la de la cara superior del furni más alto de esa celda, y los avatares no pasan por debajo de nada. Por eso, con pisos apilados sobre las mismas x/y: lo que queda bajo una losa o techo **no se pisa y no se puede llegar a él**; solo se pisan las terrazas sin nada encima, y las escaleras en espiral con vueltas apiladas sobre las mismas baldosas **tampoco se caminan**.
- Error cometido: dije que la v2 (escaleras switchback de 11 peldaños de 0.30 + descanso, con el pie bajo una losa en (12,12)) estaba "validada" y era transitable. Era falso: mi grafo por alturas permitía nodos de suelo bajo las losas. Los interiores de la casa isométrica (v1, v2 y v3) son **solo decorado visual**, las escaleras también.
- **Diseño realmente transitable**: los niveles deben ocupar rectángulos **distintos** de x/y (terrazas contiguas, estilo ladera, como en las secciones 8 y 13), sin losas ni techos sobre las zonas que se pisan, y con escaleras que sean una cinta de baldosas de una sola capa (nunca vueltas apiladas). Con contrahuella ~0.30, una escalera para 3.5 de desnivel necesita unas 12 baldosas de largo y para 7.0 unas 24; en una sala 13×13 eso obliga a cintas en zigzag al borde. La altura máxima de paso entre baldosas vecinas **no está verificada** (0.30 se supone seguro; 0.44 sin probar). Si el usuario quiere caminar, proponer primero una variante de bandas escalonadas (ladera) y avisar el costo en espacio.
- **Validación por alturas correcta**: grafo con nodos solo en la cara superior del furni más alto de cada celda; descartar todo nodo que tenga algo encima (losa, techo); aristas entre vecinos 4-conexos con diferencia de altura ≤ 0.35; quitar celdas ocupadas por muebles de suelo; recorrer desde la puerta (3,13). Un diseño con pisos superpuestos no pasa esta prueba y no debe llamarse transitable.
- **Espiral decorativa** (v3): anillo 3×3 (8 baldosas, centro libre), 3 vueltas apiladas en las mismas baldosas con peldaños de ~0.30 (rise = (top − base)/24, 23 peldaños, `tile_marble` flotante a z = tope − 0.15); el orden de las baldosas define el sentido (SP1: (1,0),(1,1),(0,1),(−1,1),(−1,0),(−1,−1),(0,−1),(1,−1); SP2 el espejo). Se ve como escalera de caracol dentro del cristal pero no se camina. Si lo que se pide es decorado, no hace falta cortar un hueco 3×3 por nivel salvo para que la losa no tape la espiral.
- Ideas pendientes si él las pide: agrandar la sala para un cuarto piso o escalera más ancha, doble capa de vidrio para más "profundidad" (hay margen hasta 3000 ventanas), cubierta de techo inclinado sobre el volumen superior, variante transitable en bandas escalonadas.

## 16. Sala de Halloween "crematorio" (preset halloween_crematorio_v2, 12/10/2026; verificado solo por render/validación, no en el hotel)

### Encargo y decisiones del usuario
- Zona **10 de alto × 24 de largo** (marcada con coordenadas del plano que el usuario pegó; una primera versión 24×10 se pegó en la esquina equivocada → pedir siempre coordenadas y orientación y comprobar el `exportOrigin`). Transponer una sala girada: `x↔y` y rotaciones `{0:6, 2:4, 4:2, 6:0}`.
- **Club del Arquitecto, sin bloques `bc_*`**; tema Halloween: primero cementerio, luego **crematorio** con cena victoriana/sala de recepción, rastro de sangre y manos; sin Anubis; paredes del doble o triple de alto; candelabros; **8 ángeles del techo con vitrinas** de cristal; **sin vitrinas en la mitad inferior**; "mejorar brutalmente" el crematorio con mucho detalle (más detalle abajo, no vitrinas).
- Resultado: 251 furnis de suelo + 110 de pared (96 `window_square` formando las 8 vitrinas, 12 por vitrina; el resto cuadros/paneles). Importar con **`:ip 1,7` y luego `:paredes`** (`exportOrigin` = 1,7).

### Furnis usados (todos llegaron al inventario del usuario al importar muestras)
`hween_c25_oldwall` (32), `hween_c22_marblefloor` (30), `hween_c25_tiledfloor` (30), `hween_c25_oldwallwindows` (16), `hween_c19_bewitchedchair` (12), `hween_c26_ivystatue` (10), `hween13_bldtrail` (10, rastro de sangre), `gothiccandelabra` (9), `hween_c22_gothicrug`, `hween_c18_bloodcurtains`, `lt_c26_bobbasmoke`, `hween_c16_ghostash`, `hween_c18_bloodfloorstreak`, `hween14_skelepieces`, `hween_c26_readingchair`, `hween_c18_floormould`, `mafia_c26_vase_dark`, `farm_c21_pizzaoven` (horno del crematorio), `s12_bonfire`, `army_c15_jerrycan`, `grunge_barrel`, `attic15_box`, `hween_c22_gothicblock`, `hween10_fog`, `hween_c22_gothictable`. Pared: `window_square` (96), `hween_c18_labwallpanel` (5), `attic15_painting` (4), `ktchn_knives` (2), `country_fp`, `hween09_wall1`, `det_bhole`.

### Trampa conocida del importador
Los furnis de pared guardan `:w=X,Y ...` **absoluto** y G-Presets no los desplaza con la raíz de importación: importar siempre en la raíz = `exportOrigin`, o ajustar el `exportOrigin` del preset a la raíz con la que se va a importar. Ver skill gpresets §11.

