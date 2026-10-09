# Lista de Compra INDIVIDUAL (8 asientos)

Preset: `lista_compra_individual_8.json`. Parte de la sala "Lista de Compra" grupal (`sala_125421289.json`): misma sala, mismo piso, mismo dispensador. Cambia la lógica de juego y reduce los asientos.

**Estado: generado y validado de forma estática. NO probado en el hotel.** Ver "Qué probar".

## Reglas

- Hasta 8 jugadores, 1 asiento cada uno (entran por las 8 puertas `divider_silo3`).
- Cada ronda hay **1 item**. Se consigue con el interruptor del carril y se entrega pisando el asiento.
- Pasan los primeros en entregar. Los que no entregan, salen:

| Jugadores | Pasan |
|---|---|
| 8 | 6 |
| 6 | 4 |
| 4 | 3 (sale 1) |
| 3 | 2 (sale 1) |
| 2 | final: gana el primero en entregar |

  Con 5 o 7 jugadores también funciona (7 → 5 → 3 → 2 → final).
- Si se acaba el tiempo, los que no entregaron salen igualmente. Si **nadie** entrega, nadie sale y se repite la ronda con otro item.
- **VIP** (grupo `551428`): escribe `:comodin` una vez por partida y entrega **cualquier** item en vez del pedido.

## Comandos

| Comando | Quién | Qué hace |
|---|---|---|
| `:empezar` (o clic en el wired) | admin | Cierra puertas, cuenta a los sentados (mínimo 3) y arranca. |
| `:parar` | admin | Detiene la partida y devuelve a todos al lobby. |
| `:comodin` | VIP | Activa el comodín (1 vez por partida). |
| `:info` | todos | Muestra las reglas. |
| `:puertas`, `:vip`, `exit` | como antes | Sin cambios. |

## Qué cambió respecto al original

**Sala**
- Asientos: de 30 rocas a **8** (la del medio de cada estación). Se quitaron también sus carriles sobrantes: 22 rocas, 22 puertas de vidrio, 22 interruptores del dispensador y las 2 estaciones de grupo VIP.
- Antenas: de 26 a 12 (solo las que usa el nuevo juego).

**Wired**
- Se eliminó toda la lógica por grupos: luces de "grupo completo", puertas del grupo VIP, comprobación de 3 miembros y la variable de grupo.
- Se rehízo con variables de usuario (`en_juego`, `entregado`, `comodin_on`, `vip_usado`) y de sala (`vivos`, `cupo`, `entregas`, `item_ronda`, `ronda_activa`).
- Un solo pool de items, tomado de lo que el dispensador realmente entrega y que tiene nombre.
- Se conservó sin cambios lo que no depende del juego: reloj de México, cola del lobby, anti-AFK, `exit`, `:vip`, `:puertas`, decoración animada, créditos y mensajes de entrada.
- Se quitaron dos `send_signal` sin antena (efectos muertos del original).
- Se completaron los nombres del conector de texto: `131=Pan`, `133=Brócoli`.
- `bindings` vacío (apuntaba a wireds eliminados).

## Qué probar en el hotel (lo que no pude confirmar)

1. **`wf_cnd_actor_in_group`** (condición de `:comodin`). El nombre sale de tu skill, pero no conozco su formato de opciones. Abre ese wired, comprueba que el grupo sea `551428` y que evalúe a quien escribe.
2. **Conteo de jugadores.** Usé la variable `-401` como "cantidad de usuarios seleccionados" (en el original solo aparecía `-400`, que cuenta furnis). Si `:empezar` dice siempre "se necesitan al menos 3", revisa el efecto de la pila (2,54).
3. **Duración de la ronda.** El trigger de reloj mantiene `[30,1,1]` sobre el reloj 2026, igual que el original. Si la ronda no dura 1:30, ajusta ese trigger.
4. **Entrega.** Pisar la roca del asiento con el item correcto. Pila (35,51) y siguientes.
5. **Final con nombre.** El mensaje de felicitación usa el nombre de quien entrega. Si sale vacío, revisa `text_output_username` en la pila de entrega final.
6. **Puntaje.** `join_team` / `give_score_tm` / `leave_team` se copiaron del original. Solo se ejecutan cuando gana quien entrega en la final; si gana por tiempo, no se suman puntos.
7. **Items sin nombre.** El dispensador también entrega 89, 81, 168, 155, 32 y 50. Se excluyeron del sorteo para que el aviso muestre el nombre.

## Importar

1. Copia `lista_compra_individual_8.json` a la carpeta `presets/` de G-Presets y elígelo en la interfaz.
2. En una sala vacía: `:plano` (si quieres el piso) y luego `:ip 0,0`.
3. Todos los wireds quedan configurados. No hay furnis de wired sin entrada.
