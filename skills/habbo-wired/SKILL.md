---
name: "habbo-wired"
description: "Crear, corregir y organizar Wireds de Habbo (JSON de export/import), presets/juegos completos y diseño de salas con furnis BC. Usar cuando el usuario hable de wired, preset, juego, sala, furni, JSON de Habbo o pida armar/corregir una pila de wireds."
---

# Habbo Wired + Diseño de salas

Skill de referencia para generar y revisar wireds en el JSON de export/import de Habbo y para diseñar salas. El usuario escribe en español; responde en español.

## 0. Cómo trabajar un preset (flujo recomendado)

1. **Entender el juego**: pedir (o deducir) objetivo, jugadores, comandos, premios, condiciones de victoria/derrota.
2. **Proponer un diseño antes de escribir JSON**: lista de pilas con nombre, trigger, condiciones, efectos y variables que usa. Mostrarlo corto y esperar OK si el preset es grande.
3. **Definir primero las variables** (usuario, sala, furni, contexto) y su significado; después las pilas.
4. **Generar el JSON** respetando el formato de la sección 2.
5. **Revisar** con la checklist de la sección 6 (redundancia, duplicación, orden, IDs únicos, antenas enlazadas).
6. **Incluir siempre una pila `:info`** con instrucciones del juego (varios `show_message` con delay, estilo 252).
7. Avisar de todo lo que haya que configurar a mano (wireds en `furni` pero sin entrada en `wired`).

Si el usuario pasa un `.txt` o JSON con información nueva, integrarla a este conocimiento y señalar qué códigos están confirmados y cuáles no.

## 1. Estructura del archivo JSON

- Raíz: `{wired:{effects,variables,addons,conditions,triggers,selectors,variables_map}, bindings, furni}`; a veces `roomLayout` {modelName, floorplanWidth, floorplanHeight, scale, floorplanSignature, wallHeight}, `srcAnchorFloorHeight` y `adsBackgrounds`.
- `furni` = `{id, name "clase[n]", className, location{x,y,z}, rotation}` (más `state` en bloques BC y `variables` precargadas). Antenas y furnis normales van **solo** en `furni`.
- Cada wired enlaza con `wiredId` = `id` del furni. Un wired sin entrada en `wired` = hay que configurarlo a mano.
- `bindings` (snapshots): `{wiredId, furniId, state}` o `{wiredId, furniId, altitude, location}` (puede incluir `rotation`).
- `variables_map`: nombre → id. IDs inventados (ej. 900001) funcionan al importar. Variables de contexto llevan `variableId ""`.
- Los textos con acentos van en **doble codificación** (utf8 leído como latin1, "nÃºmero"); mantener esa convención.
- `z` puede venir como `1.49012E-8` (≈0).

## 2. Códigos de wireds

### Fuentes (userSources / furniSources)
- user: 0 = quien activó, 200 = selector, 201 = usuario de la señal, 100 = bot/elegido, 900 = en addons de movimiento, 11 = usuario clickeado (probable), 101 = otro usuario en condiciones.
- furni: 100 = elegidos, 200 = selector, 201 = otro selector, 901 = solo en rotate_to_dir.

### Variables (`wf_act_change_var_val` / `wf_cnd_var_val_match`)
- options = `[tipoDestino, operación, usaVariable(0/1), ?, valorConstante, tipoFuente]`; variableIds = `[destino, fuente o "0"]`.
- Tipos: 1 = usuario, -10 = sala, -20 = contexto. Con constante: usaVariable 0 y tipoFuente -10. Al copiar desde variable interna: tipoFuente 0 = furni, 1 = usuario.
- Operaciones: 0 asignar, 1 suma, 2 resta, 4 división, 6 módulo, 41 limitar a máximo, 50 aleatorio con límite, 100 AND, 101 OR, 102 XOR (en duckGame parece rango/aleatorio), 104 `<<`. Sin confirmar: 3 (multiplicación), 40, 105, 114, 120.
- Comparaciones: 0 `<`, 1 `=`, 2 `>`, 4 `≠`, 5 `≥` (3 probablemente `≤`).
- `give_var` options `[tipo,0,valorInicial,0]`; `has_var`/`neg_has_var` `[1]`; `wf_var_user` `[10,1]`; `wf_var_furni` `[1,10]` o `[0,10]`; `wf_var_room` `[10]`; `wf_var_context` `[1]` (config = nombre).
- `wf_act_remove_var` `[0]`. `wf_trg_var_changed` `[0|1,1,0,7,-1]` con variableIds `[var]`.
- Los furnis pueden traer `"variables": {nombre: valor}` precargadas.

### Variables internas
- Furni: -100 id único, -101 tipo (id de catálogo), -110 estado, -120 x, -121 y, -122 rotación, -123 altitud (centésimas).
- Usuario: -230 x, -231 y, -232 dirección, -233 altitud, -240 handitem, -270 id del usuario.
- Otras: -222 (permisos/dueño en comandos), -401 cantidad de seleccionados (contexto), -450 tipo de mensaje (1 dijo, 2 susurró), -451 id del estilo de burbuja. Sin confirmar: -241, -2101/-2102/-2106 (eventos internos).

### Triggers
- `wf_trg_says_something` options `[0,1,1]` = exacto, `[0,1,0]` = contiene/con parámetros, `[0,2,1]` config "" = cualquier mensaje.
- `wf_trg_click_furni` items [furni]; `wf_trg_click_user`; `wf_trg_walks_on_furni`; `wf_trg_state_changed`; `wf_trg_enter_room`; `wf_trg_leave_room`; `wf_trg_period_short` [1]; `wf_trg_periodically` [1]; `wf_trg_period_long`; `wf_trg_clock_counter` [seg,0,0]; `wf_trg_at_given_time`; `wf_trg_user_performs_action` [5]; `wf_trg_transaction_complete`; `wf_trg_stuff_state`.
- `wf_trg_recv_signal` items [antena].

### Captura de texto (comandos con parámetros)
- `says_something` con patrón `":cmd #(x)"` + `text_input_variable` (options [1], config = nombre del `#()`) en la pila del trigger.
- `text_output_variable` options `[0,tipo,usaConector]`; `wf_xtra_var_text_connector` (formato "valor=texto" por línea; puede mapear 0=a…26=z) va en la pila donde se definen las variables.
- `wf_xtra_text_output_username` config "name" → `$(name)`. `wf_xtra_text_output_variable` config nombre + variableIds → `$(nombre)` en mensajes.

### Efectos frecuentes
- `show_message` options `[0,estilo]` o `[0,estilo,-1]`; estilos vistos: 34, 200, 201, 202, 210, 211, 223, 229, 251, 252. `@green@` / `@red@` al inicio colorea.
- `bot_talk` config "NombreBot\tmensaje", userSources [100], options [1] o [1,-1]; `bot_give_handitem` options [idHandItem], userSources [100,0].
- `send_signal`/`neg_send_signal` options `[0,1]` (1 = pasa contexto) o `[0,0]`.
- `call_stacks`/`neg_call_stacks` items [primer efecto de la pila llamada]; `exec_in_order` sin opciones; `execution_limit` [1,N]; `xtra_random` [4,1] o [0,1]; `or_eval` [1,0,0]; `periodically` [n × 0,5 s]; `clock_counter` [segundos,0,0].
- Movimiento: `move_to_dir` [dir 0–7, pasos, 0]; `move_rotate` [dir,0,0]; `set_altitude` [20,0|1] (subir/bajar 0,20); addons `wf_xtra_mov_physics` [a,b,c,0] userSources [900], `wf_xtra_anim_time` [ms], `wf_xtra_mov_curve` [1,valor,-10], `wf_xtra_mov_carry_users` [0], `wf_xtra_mov_no_animation`; `furni_to_furni`/`furni_to_user`/`user_to_furni` [0,0] o [1,0]; `teleport_to`; `chase`.
- Colocar/borrar furnis: `wf_act_place_furni` options `[0,1,2,0,0,0,1,1,0,1]`, secondItems [destino], config una línea por plantilla; `wf_act_remove_furni` sin opciones.
- Juego: `wf_act_join_team` [3], `give_score` [1,0], `leave_team`, `control_clock` [0]=reset [1]=parar [2]=iniciar (items [wf_upcounter1]), `freeze`/`unfreeze`, `kick_user`, `give_currency`, `log`/`neg_log` ([0] dice, [1] susurro), `match_to_sshot` [1,1,1,1,0] (snapshot).
- Pagos: `wf_act_init_transaction`, `wf_contract_payment` (secondItems), `wf_trg_transaction_complete`, `wf_act_cancel_transaction`, `wf_contract_reward`.

### Condiciones y selectores
- Condiciones: `wf_cnd_triggerer_match` config = nombre (userSources [0,101]); `actor_in_group`/`not_in_group` config = id de grupo; `match_snapshot` [1,0,0,0]; `slc_quantity` [0,0,N]; `furnis_hv_avtrs` [1]; `var_age_match` [1,0,0,0,0,1]; `trggrer_on_frn` (usuario sobre furni); quantifier 1 = "todos".
- Selectores: `users_area` [x1,y1,x2,y2]; `users_bytype` [1] usuarios / [4] bots; `users_byname` config nombre; `users_with_var`/`furni_with_var` [2,0,0,0,-10]; `furni_area` [x,y,ancho,alto]; `furni_bytype` [0] items [ejemplo]; `furni_picks`; `furni_neighborhood` (18 valores); `furni_onfurni`; `users_neighborhood`; `users_onfurni`; `users_byaction`; `furni_signal`; `remote`. `filter`/`inverse` son booleanos (`inverse` en `users_bytype` + -401 = contar usuarios en sala).
- Addons de variable visuales: `wf_xtra_varfx_levelling/status/prog/hp/boss/number`, `wf_xtra_var_lvlup_system`, `wf_xtra_var_time_util`, `wf_xtra_filter_users_by_var`/`furni_by_var`.

### Handitems (IDs)
34 Pescado, 38 Naranja, 58 Copa de sangre, 59 Saco de monedas, 62 Agua envenenada, 70 Muslo de pollo, 73 Ponche de huevo, 74 Copa de brindis, 82 Telescopio, 87 Vaso de agua, 88 Botella cowboy, 98 Tomate, 99 Berenjena, 100 Repollo, 103 Banana, 104 Aguacate, 105 Uvas, 111 Cangrejo, 112 Chili rojo, 123 Sopa, 128 Helado de carbón, 130 Queso, 131 Pan, 132 Camarón, 133 Brócoli, 134 Sandía, 136 Salchichas, 175 Hongo venenoso, 1001 Rosa negra, 1031 Antorcha, 1037 Serpiente, 1038 Palo, 1039 Mano cortada, 1040 Corazón, 1041 Calamar, 1043 Gusano, 1044 Rata muerta, 1045 Dentadura, 1047 Bola de cañón, 1049 Martillo, 1053 Pato, 1058 Quinqué, 1072 Brújula, 1090 Caña de pescar, 1094 Espada, 1117 Cuchillo, 1126 Pistola.

## 3. Patrones y combinaciones que funcionan

- **Comando de chat**: `says_something` → condición de permisos (-222 o `triggerer_match`) → efectos. Con parámetros: `#(x)` + `text_input_variable`.
- **Pila reutilizable**: poner la lógica una sola vez y llamarla con `call_stacks` o con `send_signal` + `recv_signal` (antena común) en vez de copiarla.
- **Contador en pantalla**: var de sala → `% 10` al `-110` del `bc_alpha1_num` de unidades y `/ 10` al de decenas; se dispara con `wf_trg_var_changed`.
- **Tienda/curación/armas**: variable por ítem (ej. `arma_actual` 0–4) + comparaciones `≥` contra precio + `give_var`/`change_var_val` + `show_message`.
- **Turnos (parchís, conecta4)**: variable `turno` en sala + dado `numero_dado` + condiciones por jugador; mínimo de jugadores y cuenta regresiva.
- **Furni que sigue al usuario**: periodic copia -230/-231 del usuario a -120/-121 del furni; var de furni `selected_furni_by_user_id` = -270.
- **Disparos/proyectiles**: handitem (-240 = 1126) + click en usuario + `furni_to_user` con curva + `remove_furni` al impactar.
- **Pagos**: `init_transaction` + `contract_payment` + `transaction_complete`; mensaje con `$(name)`.
- **Anti-spam / seguridad**: `execution_limit`, interruptor con `match_snapshot` negado + `kick_user`, `wf_conf_*` para bloquear handitems/wired.
- **Aleatoriedad**: `xtra_random` o `change_var_val` op 50; premios por tabla de rangos.

## 4. Orden de wireds dentro de una pila

1. **Trigger** (uno por pila; si hay varios, que hagan lo mismo).
2. **Selectores y filtros** (definen sobre quién/qué actúan los efectos).
3. **Condiciones** (todas deben cumplirse; usar `or_eval` si se necesita "o").
4. **Addons** (`wf_xtra_*`: físicas, curvas, textos, conectores) en la misma pila que el efecto que modifican.
5. **Efectos** en orden lógico: validar → cambiar variables → acciones visibles → mensajes. El orden entre efectos sin delay **no está garantizado**: usar `exec_in_order` o delays (unidad = 0,5 s) cuando importe.

## 5. Diseño de salas (JSON de furnis)

- **Capas**: 1) piso base a z 0 (furnis 2x2 cada 2 baldosas: lt_patch, garden_grass, seaside_c26_sand, party_floor, xmas11_woodfloor…); 2) detalles de piso a z 0.001–0.1; 3) muebles; 4) muros/decoración vertical apilados; 5) techo.
- **Superponer sin chocar**: sumar milésimas (0.001, 0.01, 4.0001). Segundo piso: pisos a z 3.2–4.4, muebles a z 4.0001, techo a ~8–9. Escenas flotantes hasta z ~40.
- **Apilar** (delta z): bc_block_1 1.0 (state 3 ≈ 0.25), bc_block_0 1.0, bc_panel 2.0, bc_round 0.15–0.25, bc_ramp 1.1, greek_pillars 3.0, xmas11_elewood 0.5, tokyo_c18_sakura 2.2, present_wrap 3, bc_cylinder 1.0, wired 0.65 (addons 0.37), catcafe_c23_wall 4, xmas_c16_wall 7–9.
- **Rotaciones**: pares (0/2/4/6) = ejes; impares (1/3/5/7) = diagonales (bc_quartercircle, rocas, prismas).
- **Bloques BC**: `bc_<forma>*N` donde N = color del selector del catálogo BC (11 por fila, *1 = primer cuadro). No hay negro puro: oscuros = *60 y *13; blanco = *14; grises = *14, *19, *18, *13; piel = *42/*43. `state` cambia grosor (state 3 placa delgada). `bc_alpha1_num` usa `state` como dígito (0–9); `bc_alpha1_<letra>` lleva la letra en el nombre.
- **Paleta BC (hex)**: 1 #f5eabb, 2 #fed736, 3 #fe9800, 4 #fe98bb, 5 #e04117, 6 #662812, 7 #007122, 8 #91d03c, 9 #ab59b0, 10 #8259b0, 11 #5da9f6, 12 #aacfd1, 13 #515151, 14 #fefefc, 15 #98fecb, 16 #656500, 17 #6e3c0b, 18 #767f83, 19 #bbbfc3, 20 #a0e7fb, 21 #2bbefe, 22 #0073fe, 23 #0000fe, 24 #0000c7, 25 #0084fe, 26 #6584fe, 27 #bbb6fe, 28 #dcb5fc, 29 #9b72fe, 30 #673dfe, 31 #3e1ac6, 32 #95008d, 33 #dd00d6, 34 #fc71fe, 35 #fcb5fc, 36 #f3bee0, 37 #fe539b, 38 #e6005e, 39 #ad0023, 40 #fe3500, 41 #fe7751, 42 #f3d0b2, 43 #fbe0b0, 44 #fca03c, 45 #e65f00, 46 #8c1500, 47 #503000, 48 #ac8000, 49 #fbd87d, 50 #99bc00, 51 #daf76e, 52 #b9f800, 53 #00b900, 54 #007800, 55 #2d622d, 56 #00a900, 57 #51d94f, 58 #b5f86f, 59 #89ad00, 60 #0c370b, 61 #005800, 62 #00a842, 63 #4df898, 64 #adefd7, 65 #00f6fb, 66 #00e8e4, 67 #008794, 68 #003f58, 69 #326565.
- **Pixel art con BC**: plano vertical (y fijo) con bc_block_1 apilado cada 1; no mezclar bc_block_0 y bc_block_1 en un mismo piso (alturas distintas). Diagonales/medios píxeles: pares bc_block_0 con rotation 6 y 2 superpuestos (z y z+0.02).
- **Casa BC**: cimiento bc_stone/bc_block_1 state 3, paredes bc_panel apiladas cada 2, techo a dos aguas con bc_quartercircle, esquinas con rotation 3/5, escalera bc_stairs, columnas bc_cylinder con bc_round encima.
- **Catálogo**: habbofurni.com/furniture para buscar classname/ID. En el JSON se usa el `className` (ej. `soft_sofachair_norja*9`), no el ID numérico.

## 6. Cosas a evitar (checklist de revisión)

- **Redundancia**: no repetir la misma lógica en varias pilas; centralizar con `call_stacks` o señales.
- **Duplicación**: no dos triggers que hagan lo mismo ni dos pilas que modifiquen la misma variable sin necesidad; no definir dos veces la misma variable.
- IDs de wired/furni repetidos o `wiredId` que no apunta a un furni existente.
- Antenas sin pareja (`send_signal` sin `recv_signal` o al revés).
- Efectos que dependen del orden sin `exec_in_order` ni delays.
- Variables sin definir en `variables`/`variables_map`, o variables de contexto con `variableId` distinto de `""`.
- Olvidar la pila `:info`, o mensajes sin estilo consistente.
- Comandos sin chequeo de permisos cuando son de administrador.
- Bucles `periodically` pesados: usar `execution_limit` y selectores acotados.
- Usar códigos marcados "sin confirmar" sin avisarlo al usuario.
- Cambiar la convención de acentos (doble codificación).
- Mezclar formato viejo (sin sources/variableIds, como Leaderboard_point) con el nuevo.

## 7. Ejemplos de presets del usuario (referencia de estilo)

Numermind (mastermind de 4 dígitos), Piedra papel o tijera, BANZAING_PRO (eventos con bots), Gacha de Anja21 (tickets con `:t<cantidad>`, premio del cofre), pachinko (bolas, packs, VIP), parchís (4 jugadores, ~1000 furnis), bingo, conecta4, dardos/duckGame, preset sobrevivientes (RPG zombie con tienda, armas, vida), followFurni, fumada (comandos por nombre letra a letra), lavafloor, gunmola, muerte (:rip/:alive), sala completa de bolos / LDC Individual (ver sección 8).

## 8. Sala completa de bolos ("LDC Individual") + tienda de pases

Sala de eliminación por rondas con bots (bot1–bot5), sillas mágicas, "Bola de Cañón" como ítem, duelo entre jugadores y vidas extra. 2291 furnis, 187 triggers, 464 efectos, 245 addons, 119 selectores, 45 variables, 109 señales (`wf_antenna2` x90).

### Códigos nuevos confirmados
- `wf_trg_transaction_fail` furniSources [100] (par de `transaction_complete`); `wf_act_cancel_transaction` options [1] userSources [0] furniSources [100].
- `wf_act_init_transaction` options `[0,10,0,-20,reintento(0|1),monto]` userSources [0,200] furniSources [100,101,200] variableIds ["0","0","0"]; el monto puede venir de una variable de contexto (`ctx_pagar` desde `:pagar #(x)`).
- `wf_act_give_currency` options `[0,1,1,1,1,11]` userSources [11,0] variableIds [var con el monto] (premio en créditos al usuario clickeado).
- `wf_act_bot_teleport` / `wf_act_bot_move`: config = nombre del bot (ej. "bot1"), userSources [100], furniSources [100] (destino).
- `wf_act_toggle_state` options [0] furniSources [100]; `wf_act_user_to_furni` options [2]; `wf_act_furni_to_user` sin options; `wf_act_control_clock` [2] = iniciar.
- `wf_act_move_rotate_user` options `[4,2]` o `[-1,9]`; `wf_act_freeze` options `[3,0]` o `[0,0]`; `wf_act_teleport_to` options [1] con delay 2 y userSources [11] posible.
- `wf_trg_stuff_state` options [1] furniSources [100]; `wf_trg_click_user` options `[1,0]` o `[0,0]`; `wf_trg_clock_counter` [20,0,0]; `wf_trg_enter_room` / `leave_room` sin options.
- `wf_xtra_filter_furni` y `wf_xtra_filter_users` options `[1,0,1]` variableIds ["0"]; `wf_xtra_mov_curve` `[0,160,-20]`; `wf_xtra_anim_time` [300] o [450]; `wf_xtra_var_time_util` options `[timestamp,0]`.
- `wf_xtra_text_input_variable` options [0] variableIds [var] config = nombre del `#()`.
- `wf_cnd_var_age_match` options `[1,2,0,0,N,1]`: compara la antigüedad de la variable (cooldown de 5, AFK de 40); `wf_slc_users_neighborhood` options de 18 valores (`[1,0,0,128,0,…]`, `[0,0,0,32,0,…]`).
- `wf_act_log` options `[2]` = mensaje de salida de sala, `[3]` = entrada (ej. "$(a) ha Entrado a Sala!"), `[0]` dice.
- `show_message` options `[1,estilo,-1]` = a toda la sala, `[0,estilo,-1]` = solo al usuario; estilos nuevos: 220, 222, 250, 252, 211, 34.
- `bot_talk` config con nombre vacío (":::\tmensaje") y etiquetas `[blue] [green] [red] [b] [u]`.
- `says_something` acepta comandos sin ":" (`info`, `exit`, `ldc`, `zana`) y con parámetros (`:pagar #(x)`, `:limite #(xx)`, `:prob #(xx)`); `[0,0,1]` y `[0,1,0]` = contiene.

### Patrones de diseño aprendidos
- **Máquina de rondas con señales**: variables de sala `estado_ronda`, `ronda_actual`, `juego_actual`, `aux_prox_ronda`, `total_jugadores_in_game`, `jugadores_a_eliminar`, `jugadores_a_salvar`; cada fase es una antena (`send_signal` → `recv_signal`) con `exec_in_order`.
- **Jugador en juego**: variables de usuario `in_game`, `eliminado`, `salvado`, `elegido_duelo`, `user_afk`, `silla_index`; furni de sala `silla_magica`, `silla_estado`, `nro_silla_ldc`, `nro_ocultador_ldc`.
- **Anti-AFK**: `wf_cnd_var_age_match` (40) sobre `user_afk` → "pierde por ausencia" / expulsión.
- **Ítem aleatorio de duelo**: `x_prob_item_duelo`, `x_limite_aleatorio_duelo` configurables con `:prob` y `:limite`; el resultado sale por `bot_talk` ("te ha salido Bola de Cañón").
- **Vida extra**: variable `pase_vida_extra_bolos` (tiempo) + `pase_vida_extra_bolos_activa`; al perder, si está activa, se salva y avisa.
- **Membresías de 31 días**: `pase_*` guarda la marca de tiempo, `wf_xtra_var_time_util` + módulo/división separan días, horas, minutos y segundos (`$(d)d, $(h)h, $(m)m, $(s)s`); comando `info` (sin ":") muestra lo que queda; aviso automático al vencer.
- **Tienda y regalos**: `:vida` / `:item` compran con `init_transaction`; `:regalo` abre menú (`tienda_regalo_menu`), `:regalar` + clic en usuario (`regalar`, `regalador`) entrega el pase; todo queda en `wf_act_log`.
- **Premios**: `:paga` activa/desactiva pago automático (`paga_auto`); `give_currency` + mensaje "Felicidades $(a) ganaste $$(x) Créditos!".
- **Cooldown** de comandos con `cooldown` + `var_age_match`.

### Decoración de la sala
`tile_marble`, `tile_brown`, `tile_stella`, `pixel_floor_silver`, `bc_block_1*13/*14`, `val12_marble2`, `soft_sofachair_norja*2` (36 asientos), `room_invisible_sit_tile` (73), `room_invisible_block`, `conf_area_hide` (52), `icedblk_c16_div3*2`, `celestial_c19_rug`, `tile_walkmagic`, `queue_tile1*8`, `bb_rnd_tele` (teleports), `hs_lightstrip_blue`, `disco_c26_lightbarblue`, `snowb_light1`, `fireworks_06`; alturas hasta z ≈ 22.

## 9. Furnis de la "Red Wired" y extras nuevos (catálogo, solo nombre y código)

Fuente: captura del catálogo del usuario (nombres en portugués; "Mobi" = furni). **Confirmado: clase (`className`) y función por el nombre. Sin confirmar: `options`, `variableIds`, fuentes y `config`** — pedir un JSON de ejemplo antes de generar estos wireds y avisar al usuario si se usan sin él.

### Red Wired (mensajería entre salas/torres)
| Nombre (ES) | Código |
|---|---|
| Torre de la Red Wired | `wf_link` |
| Radio de la Red Wired | `wf_xtra_link_speaker` |
| WIRED EFECTO: Enviar mensaje a la red | `wf_act_send_message` |
| WIRED EFECTO: Enviar dataframe a la red | `wf_act_send_dataframe` |
| WIRED ACTIVADOR: Recibir mensaje de la red | `wf_trg_receive_message` |
| WIRED ACTIVADOR: Recibir dataframe de la red | `wf_trg_receive_dataframe` |

- Par emisor/receptor análogo a `send_signal`/`recv_signal`, pero a través de la torre (`wf_link`) y la radio (`wf_xtra_link_speaker`) de la red; todo `send_*` debe tener su `trg_receive_*` (aplica la regla de antenas sin pareja de la sección 6).
- *Dataframe* = paquete de datos (probablemente valores de variables) frente a *mensaje* = texto; sin confirmar el formato.

### Efectos, activadores y variables nuevos
| Nombre (ES) | Código |
|---|---|
| WIRED EFECTO: Definir realce del furni | `wf_act_set_highlight` |
| WIRED EFECTO: Definir transparencia del furni | `wf_act_set_visibility` |
| WIRED ACTIVADOR: Sala inicializada | `wf_trg_room_initializes` |
| WIRED VARIABLE: Puntero de variable | `wf_var_pointer` |
| Extra Variable: Importar | `wf_xtra_var_import` |
| Extra Variable: Compartir | `wf_xtra_var_share` |
| Extra Variable: Marcador (leaderboard) | `wf_xtra_var_leaderboard` |

- `wf_trg_room_initializes`: se dispara al inicializarse la sala (útil para resetear variables/estado en vez de depender de `enter_room`). Sin confirmar si corre también al recargar el preset.
- `wf_var_pointer`: variable que apunta a otra variable (indirección). Sin confirmar el formato de `options`.
- `wf_xtra_var_import` / `wf_xtra_var_share`: importar/compartir variables (probablemente entre salas con la Red Wired). `wf_xtra_var_leaderboard`: tabla de puntuaciones por variable.

### Utilidades
| Nombre (ES) | Código |
|---|---|
| Anotación Wired | `wf_label` |
| Desactivador de `:chooser` | `conf_chooser_disable` |

- `wf_label`: nota/comentario visual para documentar pilas (no ejecuta nada).
- `conf_chooser_disable`: bloquea el comando `:chooser` en la sala (familia `conf_*`, como `conf_area_hide`; ver "Anti-spam / seguridad" en la sección 3).

## 10. Items de mano (handitem): lista completa, traducida

Fuente: https://habboxwiki.com/Hand_Items (lista de 2011 en adelante, puede estar desactualizada). Son 295 items, del 0 al 1130; los IDs que no aparecen (65, 78, 139, 140, 145, 156-158, 160-162, 164, 170, 174, 184-999, 1010, 1012, 1016-1018, 1020) no existen en la lista. Los nombres están traducidos al español; "(2)" = nombre repetido en la fuente con otro ID, "(?)" = nombre dudoso en la fuente. No se comprobaron en el hotel.

Uso en wireds: el ID es el que se compara con la variable interna `-240` (handitem del usuario, ver sección 2) y el que va en `bot_give_handitem` (options `[idHandItem]`). `0` = sin item en la mano. Los IDs 1126 (pistola) y 1127 (antídoto) ya se usan en el patrón de disparos de la sección 3 (`-240` = 1126).

Formato `ID=nombre`:

```
0=Ninguno
1=Té refrescante
2=Jugo
3=Zanahoria
4=Helado de vainilla
5=Leche
6=Grosella negra
7=Agua
8=Café normal
9=Descafeinado
10=Café con leche (latte)
11=Moca
12=Macchiato
13=Espresso
14=Café negro
15=Chocolate caliente
16=Capuchino
17=Café java
18=Agua del grifo
19=Habbo Cola
20=Cámara
21=Hamburguesa
22=Habbo Soda de lima
23=Habbo Soda de remolacha
24=Jugo burbujeante de 1978
25=Poción de amor
26=Helado Calippo
27=Té árabe
28=Sake
29=Jugo de tomate
30=Líquido radiactivo
31=Champán rosado
32=Delicia de coco
33=711 Cola
34=Pescado
35=Champán
36=Pera
37=Durazno
38=Naranja
39=Rebanada de queso
40=Jugo de naranja
41=Taza de sumppi
42=Jugo de naranja (2)
43=Refresco frío
44=Tubos de ensayo
45=Moodi emocionado
46=Mi Moodi
47=Moodi
48=Chupetín
49=Frasco de yogur
50=Botella de jugo burbujeante
51=Pipas G (semillas)
52=Cheetos
53=Espresso (2)
54=Tazón de cereal
55=Botella de Pepsi
56=Cheetos (2)
57=Refresco de cereza
58=Copa de sangre
59=Bolsa de monedas
60=Castañas
61=Botella de Sunny Delight
62=Agua envenenada
63=Palomitas de maíz
64=Jugo de limón
66=Batido de banana
67=Chicle azul
68=Chicle rojo
69=Chicle verde
70=Muslo de pollo
71=Tostada
72=Lata de refresco
73=Ponche de huevo
74=Copa de brindis
75=Helado de fresa
76=Helado de menta
77=Helado de chocolate
79=Algodón de azúcar rosa
80=Algodón de azúcar azul
81=Perro caliente
82=Telescopio
83=Manzana envenenada
84=Hombrecito de jengibre
85=Americano
86=Frapuccino
87=Vaso de agua
88=Ron
89=Pastelito (cupcake)
90=Champán rosado (2)
91=Té oriental
92=Chicle azul (2)
93=Chicle rojo (2)
94=Chicle rosa
95=Chicle verde (2)
96=Rebanada de pastel
97=Cruasán
98=Tomate
99=Berenjena
100=Repollo
101=Jugo burbujeante espumoso
102=Bebida energética
103=Banana
104=Aguacate
105=Uvas
106=Batido
107=Jugo de verduras
108=Pesa (mancuerna)
109=Hamburguesa (2)
110=Carta
111=Cangrejo
112=Chile rojo
113=Batido cítrico
114=Batido verde
115=Batido de frutos rojos
116=Limón
117=Galleta
118=Limonada rosa
119=Limonada azul
120=Raspado de arándanos
121=Raspado de fresa
122=Takoyaki (bolitas de pulpo)
123=Borsch (sopa de remolacha)
124=Té de burbujas de taro
125=Té de burbujas de matcha
126=Té de burbujas de fresa
127=Cono de helado
128=Helado de carbón
129=Yogur
130=Queso
131=Pan
132=Camarón
133=Brócoli
134=Sandía
135=Dona
136=Salchichas
137=Paleta helada
138=Papas fritas (abierta)
141=Huevo de Pascua luminoso
142=Bebida helada
143=Malvaviscos cubiertos de chocolate en palito
144=Fresa cubierta de chocolate en palito
146=Café elegante
147=Botella de Fanta
148=Lupa
149=Chocolate caliente de lujo
150=Dalgona con forma de estrella
151=Dalgona con forma de círculo
152=Dalgona con forma de cuadrado
153=Tostada de Hello Kitty
154=Jarabe rosa de Kryptomon
155=Refresco de oso
159=Cajita de jugo de pera
163=Helado arcoíris
165=Malvavisco en palito
166=Pastelito de dedo
167=Mini helado de vainilla
168=Papa
169=Coxinha
171=Pastel de pato
172=Batido de banana (milkshake)
173=Frapuccino de banana de lujo
175=Hongo venenoso
176=Espumoso
177=Espumoso (2)
178=Taza de té con rosa
179=Pitahaya comestible (fruta del dragón)
180=Helado de la selva
181=Habbo Cola de la selva
182=Licor casero (moonshine)
183=Refresco de uva (?)
1000=Rosa
1001=Rosa negra
1002=Girasol
1003=Libro rojo
1004=Libro azul
1005=Libro verde
1006=Flor de regalo
1007=Estramonio
1008=Delicia amarilla
1009=Pandemia rosa
1011=Portapapeles
1013=Analgésico
1014=Jeringa
1015=Bolsa de riesgo biológico
1019=Flor Bolly
1021=Jacinto 1
1022=Jacinto 2
1023=Flor de Pascua (nochebuena)
1024=Pudín
1025=Bastón de caramelo
1026=Regalo
1027=Vela
1028=Tazón de cereal (2)
1029=Globo
1030=HiPad
1031=Antorcha Habbo-lympix
1032=Major Tom
1033=OVNI
1034=Cosa alienígena
1035=Hueso
1036=Patito de gomita
1037=Serpiente
1038=Palo
1039=Mano cortada
1040=Corazón
1041=Calamar
1042=Excremento de murciélago
1043=Gusano
1044=Rata muerta
1045=Dentadura postiza
1046=Crema Clearasil
1047=Bola de boliche negra
1048=Bandera Ditch the Label
1049=Martillo
1050=Huevo de Pascua rosa
1051=Pincel
1052=Bandera blanca Ditch the Label
1053=Pato
1054=Globo naranja
1055=Globo verde
1056=Globo azul
1057=Globo rosa
1058=Lámpara de aceite
1059=Papel higiénico
1060=Spray
1061=Clavel
1062=Calavera de caramelo rosa
1063=Calavera de caramelo verde
1064=Calavera de caramelo azul
1065=Muñeca de juguete
1066=Osito de peluche
1067=Soldadito de juguete
1068=Manga
1069=Cómic
1070=Libro amarillo
1071=HiPad dorado (vendedor)
1072=Brújula
1073=Huevo de dinosaurio
1074=Alosaurio verde
1075=Triceratops amarillo
1076=Saurolophus morado
1077=Toalla verde de spa
1078=Lagarto en palito
1079=Ciervo volante (escarabajo)
1080=Escarabajo rinoceronte
1081=Regadera
1082=Bandera del orgullo
1083=Calabaza espeluznante
1084=Bolsa del supermercado
1085=DVD de acción
1086=DVD de suspenso
1087=Cuaderno
1088=Lápiz
1089=Papas fritas (cerrada)
1090=Caña de pescar: pez atrapado
1091=Caña de pescar: bota atrapada
1092=Caña de pescar: mensaje en una botella
1093=Bandera dorada Ditch The Label
1094=Espada
1095=Corazón (2)
1096=Teléfono móvil
1097=Brote
1098=Robot diminuto
1099=Osito (recolor)
1100=Pato (recolor)
1101=Balón de fútbol
1102=Bate
1103=Pelota de tenis
1104=H-Phone
1105=Micrófono de Don Robbie
1106=Balón de fútbol (2)
1107=Arco astral
1108=Virvontavitsa (varita de hechicero)
1109=Pez dorado
1110=Teléfono móvil (2)
1111=Globo de pato
1112=Pizarra de dibujo
1113=Vela LED
1114=Corazón verde
1115=Cacao
1116=Pato rosa
1117=Cuchillo
1118=Consola
1119=Anillo de diamante
1120=Hoja de diamante
1121=Tarro de miel
1122=Pitahaya (fruta del dragón)
1123=Gema de la selva
1124=Cartel Slay
1125=Peluche de tiburón
1126=Pistola
1127=Antídoto
1128=Arco
1129=Glock (pistola)
1130=Daga de bronce (?)
```


## 11. Sistema de pago, vidas y VIP con tiempo (aprendido del preset "el banco de emmanuel1860" y de Compra LDC)

Todo lo marcado "confirmado" sale del export del banco del usuario (funciona en su hotel). Lo marcado "sin confirmar" lo asumí al generar `Compra LDC.json` y no está probado.

### Cobro con créditos (confirmado en el banco)
- **Furnis necesarios**: una caja `wf_storage_coins2` (state "0", recibe el pago) y **un `wf_contract_payment` por producto**. Los contratos y la caja van solo en `furni`, sin entrada en `wired`; se configuran a mano.
- **Iniciar el cobro** (`wf_act_init_transaction`): options `[0,10,0,-20,reintento,monto]` (monto = último valor, reintento 1 solo en el pase del banco), userSources `[0,200]`, furniSources `[100,101,200]`, variableIds `["0","0","0"]`, **items `[id de la caja]`, secondItems `[id del contrato]`**.
- **Antes de cobrar**, `wf_act_cancel_transaction` options `[0]`, userSources `[0]`, furniSources `[100]`, items `[contrato]`: cancela una transacción pendiente de ese contrato.
- **Pago completado**: `wf_trg_transaction_complete` furniSources `[100]`, items `[contrato]`. Hay **un trigger por contrato**, así que no hace falta variable auxiliar para saber qué se compró. Los efectos de entrega van en esa misma pila.
- El banco dispara la compra desde una señal (`recv_signal`) tras un texto `#(o).` guardado en la variable de contexto `aux_buy` con `wf_xtra_text_input_variable` options `[0]`, config = nombre del `#()`; los efectos `init_transaction` se protegen con `neg_has_var`/`var_val_match` y un `wf_xtra_or_eval` options `[0,0,0]` con items = las condiciones que deben cumplirse (OR).
- Precios vistos en el banco: 30 (pase semanal) y 300 (vida extra), en la moneda del contrato.

### Variables persistentes vs temporales (confirmado)
- `wf_var_user` options `[10,1]` = **persiste** (pase, vida extra); `[0,1]` = temporal (monedas de partida, `ahora_dia`). `wf_var_room` `[10]`; `wf_var_context` `[1]` con `variableId ""` (los ids de contexto solo viven en `variables_map`).
- Si una variable de vidas o VIP debe sobrevivir a salir de la sala, usar `[10,1]`.

### Vida extra como contador (confirmado)
- Dar la variable: `wf_act_give_var` options `[1,0,0,0]` (valor inicial 0), userSources `[0]`, furniSources `[100]`; después sumar con `wf_act_change_var_val` options `[1,1,0,0,1,-10]` (usuario, suma, constante 1). Restar: operación 2. `give_var` sobre una variable que ya existe se usa como "asegurar que existe" antes de sumar.
- Comprobar que tiene: `wf_cnd_has_var` options `[1]`, userSources `[0]`, furniSources `[100]`; contar `>= 1`: `wf_cnd_var_val_match` options `[1,5,0,0,1,-10]`.
- Usarla (banco, `:usarvida`): `give_var` de `vida_xtra_usada` `[1,0,1,0]` + restar 1 a `vida_xtra`; condición `vida_xtra > 0` (`[1,2,0,0,0,-10]`) combinada por OR con `juego_encendido = 0` (`[-10,1,0,0,0,-10]`) mediante `wf_xtra_or_eval`. Al eliminar al jugador, la pila de eliminación revisa `vida_xtra_usada` y reenvía por señal; al terminar se limpia con `remove_var`.
- Mostrar el número: `wf_xtra_text_output_variable` options `[0,1,0]` (1 = variable de usuario), userSources `[0]`, furniSources `[100]`, config = nombre (`v`) → `$(v)` en el mensaje.

### Regalar entre jugadores (patrón del banco, `trg_click_user`)
- `wf_trg_click_user` options `[1,0]`; el usuario clickeado es la fuente **11**; `wf_cnd_not_triggerer_match` options `[1]`, userSources `[0,11]` evita regalarse a uno mismo.
- Copiar/sumar entre usuarios: efectos `wf_act_change_var_val` con userSources `[11,200]` (destino = clickeado) o `[0,200]` (quien hace clic). Nombres: `wf_xtra_text_output_username` options `[0]`, userSources `[0]` o `[11]`, config `a` / `b`.
- **Solo antes de empezar** (Compra LDC, sin confirmar): variable de sala `ldc_partida` (0 = lobby, 1 = juego) puesta a 1/0 por antenas que envía el juego (`recv_signal`) o por comandos de admin (`wf_cnd_has_var` con `-222`); `:regalar` comprueba `ldc_partida = 0` y que tiene vidas, deja la marca `ldc_regalando`, y el clic ejecuta la transferencia.

### VIP / pase con tiempo: complemento "Utilidades de Tiempo" (confirmado)
- Es `wf_xtra_var_time_util`, options `[1152,1]`, que se pone **encima de una variable en la misma baldosa** (z de la variable + 1,2). Ventana: "Opciones" = fecha de creación o de actualización (el `1` es "fecha de creación") y "Crear subvariables": milisegundos, segundo, minuto, hora, día de la semana, día del mes, día del año, semana del año, mes del año, año.
- `1152` = bits 7 y 10 = **día del año + año** (cada subvariable es 2^posición, empezando en 1 = milisegundos).
- Las subvariables se referencian en `variableIds` como cadenas `s<idVariable>-<número>-<posición>\t<opción>` (con una tabulación real), por ejemplo `s241546-2147418298-7\t1` = día del año (posición 7) de la fecha de creación de `pase_semanal_xtra` (id 241546). El número del medio no se sabe cómo se genera; **al generar un preset hay que copiar el par variable-subvariable del banco** (ids 241546 y 241562) o seleccionar la subvariable a mano en el juego.
- Se necesitan **dos variables de usuario con el complemento**: la del pase (se da al comprar, `give_var` options `[1,0,1,1]`) y `ahora_dia` (se borra y se vuelve a dar en cada consulta para que su fecha de creación sea "hoy"). Más dos variables de contexto: `plan_x` y `ahora_x`.
- **Días que quedan** (pila `:pase` del banco, con `wf_xtra_exec_in_order` y `wf_xtra_execution_limit` `[100,1]` en la pila y los efectos en este orden): `remove_var ahora_dia` `[0]` → `give_var ahora_x` `[-20,0,0,1]` → `give_var plan_x` `[-20,0,0,0]` → `give_var ahora_dia` `[1,0,1,1]` → `plan_x = subvariable de la compra` (`change_var_val` `[-20,0,1,0,0,1]`, userSources `[0,0]`) → `ahora_x = subvariable de ahora_dia` (misma forma, furniSources `[0,200]`) → `ahora_x += 365` `[-20,1,0,0,365,-10]` → `ahora_x -= plan_x` `[-20,2,1,0,0,-20]` → `ahora_x %= 365` `[-20,6,0,0,365,-10]` → `ahora_x = mín(ahora_x, 7)` `[-20,41,0,0,7,-10]` → `plan_x = 7` → `plan_x -= ahora_x` → mensaje con `$(d)` (text_output_variable de `plan_x`, options `[0,-20,0]`, config `d`). Para otra duración, cambiar los dos 7 por los días del pase (Compra LDC usa 31). La cuenta usa el día del año, así que falla en años bisiestos por un día y no sirve para más de un año.
- **Vencimiento al entrar a la sala** (confirmado en el banco con 1 semana): `wf_trg_enter_room` + `wf_cnd_var_age_match` options `[1,2,0,0,N,unidad]` sobre la variable del pase + `remove_var`. Unidades vistas: 1 = segundos (cooldown de 5, AFK de 3), 5 = semanas; **2 = minutos, 3 = horas y 4 = días son suposición** (Compra LDC usa `[1,2,0,0,31,4]`).
- Mensaje de "sin pase": `wf_cnd_neg_has_var` + `show_message`.

### Antiejemplos (error mío al generar Compra LDC v1)
- No hacer 31 pilas de tramos con `var_age_match` para mostrar los días: con "Utilidades de Tiempo" se resuelve con una sola pila.
- No inventar el cobro: usar el patrón caja + contrato + `cancel_transaction` + `init_transaction` + `transaction_complete` de arriba.
- No dejar las variables de vida y VIP con options `[0,1]` si deben conservarse al salir.


## 12. Controlador de zona (`conf_area_hide`) y variables de furni `~area_hide` (export kiliki.json, honkk.json)

### Qué es
- `conf_area_hide` ("Controlador de zona", Almacén del Club del Arquitecto) es un furni que **oculta los furnis de una zona rectangular**. Se exporta como un furni más (`state` "0" o "1") **sin ningún dato de la zona**: raíz, ancho y largo se guardan dentro del furni y no salen en el JSON, así que un preset no puede traer las zonas ya puestas; hay que escribirlas con wireds.
- `conf_invis_control` ("Controlador: Furni Invisible") es otro furni distinto; no se probó qué hace con la zona.
- `state` pasa de "0" a "1" cuando el controlador tiene una zona elegida. **Sin confirmar**: que `@state` (-110) en 1 oculte y en 0 muestre.

### Variables de Cambiar Valor de Variable (captura de la ventana)
- Lista de variables de furni: `x`, `y`, `z`, `~area_hide` (con submenú), `~chest`, `@id`, `@class_id`, `@height`, `@state`, `@position` (submenú), `@rotation`, `@altitude`, `@is_invisible`, `@occupation`, `@type`.
- En gris (solo lectura para ese efecto, interpretación de la captura): `~chest`, `@id`, `@class_id`, `@height`, `@is_invisible`, `@type` y las subvariables `hiding_wallitems`, `inverted`, `is_invisible_furni`.
- Escribibles: `@state`, `@position`, `@rotation`, `@altitude`, `@occupation` y las subvariables `length`, `root_x`, `root_y`, `width` de `~area_hide`.

### IDs de las subvariables (confirmado en kiliki.json, orden asumido)
- Se escriben en `variableIds` como `"~110"`, `"~111"`, `"~112"`, `"~113"` (prefijo `~`, no la forma larga `s<id>-…` del complemento de tiempo), seguidos de `"n"` (segunda variable sin usar).
- Con valores de prueba 1, 2, 3, 4 el export dio `~110`=1, `~111`=2, `~112`=3, `~113`=4 → **root_x = ~110, root_y = ~111, width = ~112, length = ~113** (suponiendo que se eligieron en ese orden; los otros tres se miran abriendo el wired).
- Efecto completo: `wf_act_change_var_val` options `[0,0,0,0,valor,-10]`, userSources `[0,200]`, furniSources `[100,200]`, variableIds `["~110","n"]`, items `[id del controlador]`, delay 0. El valor es una constante (último-1 de options).
- **Sin confirmar**: que raíz x/y sean coordenadas absolutas de la sala y que el cambio se vea al instante.

### Patrón: ocultar la zona de un puesto vacío (preset "Ocultar puestos LDC")
- **Sala LDC "honkk"**: 18 puestos = 6 columnas (x = 15, 17, 19, 21, 23, 25) × 3 filas (y de la silla = 17, 23, 29). Silla `romantique_pianochair*5`, puerta `divider_silo3*2` en (x, y−1), interruptor `wf_floor_switch2` en (x, y−4), suelo `val09_floor2` 2×2. **Zona de un puesto: raíz (x, y−4), ancho 2, largo 6.**
- **Un controlador por puesto** (18). Una pila por puesto: `wf_trg_periodically` options `[2]` → `wf_slc_users_area` options `[x,y,1,1]` sobre la baldosa de la silla → `wf_cnd_slc_quantity` options `[1,0,1]` (= 0 usuarios; `[1,0,2]` sería > 0) con userSources/furniSources `[200]` → `wf_cnd_var_val_match` sobre `ldc_partida` = 1 (`[-10,1,0,0,1,-10,0]`) → `wf_act_change_var_val` sobre `-110` del controlador (`[0,0,0,0,1,-10]`, furniSources `[100,200]`, items `[controlador]`).
- **Reinicio**: periódica + `wf_slc_furni_bytype` options `[0]` items `[un controlador]` + `ldc_partida` = 0 + `change_var_val` `-110` = 0 con furniSources `[200,200]`.
- **`:zonas`** (admin con `wf_cnd_has_var` `-222`): 18 pilas con 4 efectos que fijan root_x, root_y, width y length con delays 0, 1, 2, 3.
- Referenciar `ldc_partida` por su id de la sala destino (245540 en honkk). Un preset sin definiciones de variable usa los ids de la sala; el importador remapea las variables que sí se definen en el preset (aparecen con ids nuevos al reexportar).

### Otros datos de los exports
- El export lleva `exportOrigin` con la esquina de la zona exportada; el controlador sin furnis alrededor se exporta en (0,0) relativo a ese origen.
- Una pila puede mezclar un selector de usuarios y uno de furnis: la condición cuenta usuarios con `[200]` y el efecto usa los furnis seleccionados con `[200,200]`.
- Web API de variables (beta de Habbo, mayo 2026): complemento global "Variable: Web API" que da claves de lectura y escritura por sala; leer, listar, modificar y borrar variables desde fuera. Sin documentación oficial a la vista: guardar vidas, VIP y puntos en variables de usuario persistentes `[10,1]` por si se usa.

## 13. Piano: canciones como tabla de datos (export `piano_100ms.json`)

### Qué es (analizado)
- Preset **solo de datos**: `wired` vacío (sin triggers/efectos/condiciones/selectores/addons/variables) y solo `variables_map`: `notas` 244583, `order` 244584, `nextplay` 244585. **No hay wireds**: la lógica que lee la tabla vive en la sala y no viene en el archivo.
- 708 furnis `room_invisible_block4`, todos en (0,0,0), nombres `room_invisible_block4[i]` (i = id−1), ids 1..708, `rotation` 0. Cada uno trae **variables de furni precargadas** `"variables": {"order": N, "notas": K}`.
- **Una nota = un bloque.** `order` = instante en unidades de **100 ms** (de ahí `piano_100ms`; 1..891 = 89,1 s, 412 instantes distintos). `notas` = tecla 1..37 (37 teclas = 3 octavas + 1). Bloques con el mismo `order` suenan a la vez: acordes de 1 (201), 2 (126) y 3 notas (85, máximo). Ordenados por `order` y, dentro del mismo instante, por `notas` ascendente.
- Huecos entre eventos: 1 tick (103), 2 (202), 3 (63), 4 (23), 5 (19), 7 (1) → ritmo de semicorcheas/corcheas a ~100–200 ms. No hay duración de nota: solo el instante de inicio.
- Notas más usadas: 1 (139), 6 (89), 11 (71), 4 (51), 25 (44), 13 (42): bajo y acordes. Tonalidad aparente con tecla 1 = Do: Do, Re, Mib, Fa, Sol, La, Sib (Do dórico/menor).
- `nextplay` está en `variables_map` pero ningún furni la trae: es un contador/puntero de sala que apunta al siguiente `order` a reproducir (inferido, sin confirmar).

### Patrón de reproducción (inferido, la parte de wired no se vio)
Tick periódico de 100 ms → avanza el puntero (`nextplay`) → selector de furnis con la variable `order` = puntero (`wf_slc_furni_with_var`, por variable y no por posición, porque todos están en 0,0) → por cada bloque leer `notas` y disparar la tecla correspondiente. Varias canciones en la misma tabla: **desplazar el `order`** (la 2.ª empieza en 1001) y arrancar el puntero en 1 o 1001; así no hace falta una variable nueva.

### Generar canciones
- `wiredtool/ejemplos/piano_halloween.py`: notación por nombres (`A5:2 G5:2 (D3 A3):8 R:4`), `tecla = MIDI − 48 + 1` (**supuesto sin confirmar**: tecla 1 = Do3, tecla 37 = Do6; ajustar `BASE_MIDI`), error si una nota sale del rango 1..37, acordes de ≤3 notas, ids y nombres secuenciales, mismos `variables_map`. Preset resultante: `Piano Halloween.json` (Toccata y fuga en Re menor, tema inicial simplificado; Dies irae en Re dórico).
- Las melodías las escribe Claude de memoria: revisar de oído antes de darlas por buenas.
- `wiredtool/ejemplos/midi_a_piano.py cancion.mid salida.json [--inicio 1001] [--base 48] [--transponer N] [--pistas 0,1]`: convierte un MIDI del usuario a esta tabla (cuantiza a 100 ms, ignora percusión, elige las octavas que dejan más notas en las 37 teclas, pliega el resto, máximo 3 notas por instante: la más aguda, la más grave y la más fuerte). Probado con ida y vuelta sobre un MIDI sintético. **Claude no transcribe de memoria canciones con derechos de autor ("This Is Halloween", etc.): se usa el MIDI que aporte el usuario.** Sin red a sitios de MIDI desde el sandbox.

## 14. Sorteos y recompensas por tiempo (preset "Recompensas por tiempo", sala de picnic.json; sin probar en el hotel)

### Tiempos
- `wf_trg_periodically` usa unidades de **0,5 s**: 3600 = 30 min, 960 = 8 min. Los textos de aviso de la sala deben coincidir con el periodo real (la sala tenía avisos de 14 min).

### Sorteo con probabilidad ponderada y un solo ganador (diseño propio, no hay primitiva exacta)
- Dado por usuario: `azar = random(1000)` con `change_var_val` operación **50 (aleatorio)**; `dif = aura + 50` (copia de la variable de sala `aura` con op 1/add sobre una copia); gana si `azar < dif` con `wf_cnd_var_val_match` (comparación 0 `<`). Probabilidad por ronda = (aura + 50) / 1000: más aura, más opciones, pero no estrictamente proporcional.
- **Un solo ganador**: bandera de sala `*_entregado` (se pone a 0 al empezar el ciclo, se comprueba `= 0` en cada candidato y se pone a 1 al ganar) + `wf_xtra_execution_limit [1,60]` (supuesto: 1 ejecución cada 60 de las unidades del wired; **unidades sin confirmar**) para que dos usuarios no ganen en el mismo instante.
- **10 rondas**: una señal (`wf_act_send_signal`) por ronda, separadas 12 unidades, para repetir el sorteo si nadie pasa; si nadie gana en 10 rondas ese ciclo no reparte. Parámetros a ajustar: `BASE` (50), `ESCALA` (1000), `RONDAS` (10), `ESPACIO` (12).
- Participantes: solo usuarios con la variable VIP (`wf_slc_users_with_var`); todos los de la sala pueden ver el sorteo. Si se quiere limitar por **grupo** hace falta `wf_cnd_actor_in_group` (formato de opciones sin confirmar).
- Premio en créditos: `wf_act_give_currency` options `[0,1,1,1,1,11]`, `variableIds [PREMIO]`; la fuente de usuario `201` (señal) con `userSources [201,0]` es **un supuesto sin confirmar** (en la sala del usuario solo había `[11,0]` de un clic).
- Si el preset referencia variables que ya existen en la sala por su id (aura, vip…), el validador avisa "no definida" (aviso esperado); las nuevas (`cred_entregado`, `furni_entregado`) sí se definen en el preset.

### Cofre de furnis (wireds vistos en picnic.json)
`wf_cnd_chest_has_items [0,0,-20,2]`, `wf_xtra_scan_chest_furni_by_type [0]`, `wf_act_give_furni [0,1,0,-20,1,0]` con `userSources [201,200]`. En la exportación los `items` de esos wireds vienen vacíos: **el cofre hay que seleccionarlo a mano** tras importar. `wf_xtra_filter_users [1,0,1]`: semántica desconocida. Reutilizar la pila existente de la sala para no duplicar entregas (desactivar las viejas).

## 15. Importación de presets: lo que hace G-Presets con los wireds (auditoría del jar, 12/10/2026)

- En `variableIds` el id `"0"` (y los que empiezan por `-` o `~`) son **literales, no variables**: no hay que mapearlos. El jar original esperaba 5 s por cada wired con `"0"` (en la demo de Lista de Compra, 102 piezas ≈ 8,5 min de espera); corregido en `GPresets_mejorado_v2/v3` (ver skill gpresets §11).
- Los furnis en x=0 o y=0 no recibían baldosa mágica ni la altura del preset (off-by-one); alturas `z` se truncaban 1 centésima (`(int)(d*100)`): corregido en el mismo jar. Las pilas de 0,65 en z no se ven afectadas por el redondeo nuevo.
- Los furnis de pared (`wallPosition ":w=X,Y l=.. r"`) son **coordenadas absolutas**: el jar no los desplaza con la raíz de importación (sin corregir). Importar siempre con raíz = `exportOrigin` (p. ej. `:ip 1,7` + `:paredes` en halloween_crematorio_v2) o las ventanas quedan descolocadas.
- El validador del jar (y `wiredtool validar`) tratan `wf_glassdoor` y `wf_game_upcounter2` como furnis **sin entrada de wired** (adornos/props del juego; supuesto, aparecen así en los presets del usuario).

