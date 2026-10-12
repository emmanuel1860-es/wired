# wired: presets de Habbo (G-Presets)

Responde en español. Sé conciso: pide solo lo necesario y no repitas análisis largos.

## Archivos
- `lista_compra_individual_8_demo.json`: demo mínima del juego (sin decoración). Es la que se usa.
- `lista_compra_individual_8.json`: versión con la sala completa (2.244 furnis).
- `LISTA_COMPRA_INDIVIDUAL.md`: reglas, comandos, cambios y qué probar en el hotel.

## Herramienta
- `wiredtool/`: validador y generador de presets (solo archivos). Uso en `wiredtool/README.md`: `python3 -m wiredtool validar <preset.json>`.

## Juego: Lista de Compra individual
- Hasta 8 jugadores, 1 asiento cada uno. Cada ronda hay 1 item y se elimina por velocidad.
- Secuencia: 8 → 6 → 4 → 3 → 2 → final (gana el primero en entregar).
- Si nadie entrega, se repite la ronda. VIP (grupo `551428`): `:comodin` una vez por partida.
- Comandos: `:empezar`, `:parar` (admin), `:comodin`, `:info`.

## Formato del preset JSON
- Raíz: `wired`, `bindings`, `floorPlan`, `furni`, `wallFurni`, `exportOrigin`.
- Cada wired es un furni (`furni`) y una entrada en `wired.*` unidas por `wiredId` = `id`.
- Los textos con acentos van en doble codificación (utf8 leído como latin1: "Ã³" por "ó"). Mantenla.
- Delays en unidades de 0,5 s. Los IDs de variable inventados funcionan al importar.
- Estructura de pila: trigger, selectores, condiciones, addons, efectos. El orden entre efectos sin delay no está garantizado.

## Convenciones de wired confirmadas
- Selectores: `filter:false` = conjunto base; `filter:true` = intersecta; `inverse:true` = excluye.
- `wf_xtra_or_eval`: `options[0]` 0 = AND, 1 = OR sobre `items` (vacío = todas las condiciones de la pila).
- `change_var_val`: `options = [tipoDestino, op, usaVariable, ?, constante, tipoFuente, 0]`.
  Tipos: 1 usuario, -10 sala, -20 contexto. Operaciones: 0 asignar, 1 sumar, 2 restar.
- Comparaciones: 0 `<`, 1 `=`, 2 `>`, 4 `!=`, 5 `>=`. Variable interna `-240` = handitem del usuario.
- `slc_quantity` options `[1, valor, comparación]` (1 `=`, 2 `>`).

## Sin confirmar (avisar si se usan)
`-401` como "cantidad de usuarios seleccionados", `wf_cnd_actor_in_group` (formato de opciones), `[30,1,1]` del reloj, operación 3 de `change_var_val`.

## G-Presets
- Importar: elegir el preset en la interfaz, `:plano` (opcional, piso) y luego `:ip 0,0`.
- No ejecutar nada que intercepte el cliente de Habbo sin que el usuario lo pida expresamente.
- `gpresets/`: mejoras al jar de G-Presets (traductor alemán/inglés/C2, ritmo de escritura, avisos restaurados). Ver `gpresets/README.md`; la auditoría pendiente está en `gpresets/informes/AUDITORIA.md`.
- `Piano Halloween.json` y `wiredtool/ejemplos/piano_halloween.py`: piano como tabla de notas (formato de `piano_100ms.json`, ver la skill habbo-wired §12). Tecla 1 = Do3 es un supuesto sin confirmar.
- `Piano Halloween.json` y `wiredtool/ejemplos/piano_halloween.py`: piano como tabla de notas (formato de `piano_100ms.json`, ver la skill habbo-wired §12). Tecla 1 = Do3 es un supuesto sin confirmar.
