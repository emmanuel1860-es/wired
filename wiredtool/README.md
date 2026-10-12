# wiredtool

Herramienta de línea de comandos para presets de wired (JSON de G-Presets). **Solo lee y escribe archivos**: no toca el cliente de Habbo ni intercepta paquetes. Python 3, sin dependencias (matplotlib es opcional para el PNG del plano).

```
python3 -m wiredtool validar "Compra LDC.json"            # revisa antes de importar
python3 -m wiredtool catalogo exports/*.json              # formatos de wired vistos en tus exports
python3 -m wiredtool catalogo a.json --buscar init_trans  # un tipo concreto
python3 -m wiredtool comparar viejo.json nuevo.json       # qué cambió (por contenido, no por id)
python3 -m wiredtool plano preset.json -o plano.png       # huellas; sin -o imprime texto
python3 -m wiredtool generar descripcion.json -o preset.json
```

## Qué valida
- ids de furni repetidos, `wiredId` que no apunta a un furni, wired en la sección equivocada, `items` inexistentes.
- wired en `furni` sin entrada en `wired` (hay que configurarlos a mano); antenas sin emisor o sin receptor.
- variables definidas dos veces o usadas sin definir; subvariables de tiempo (`s…`) y de zona (`~…`).
- pilas: sin trigger, trigger no abajo, dos wireds a la misma altura, efectos sin retardo que escriben la misma variable sin `exec_in_order`.
- textos con acentos sin la doble codificación del proyecto.
- **notas "sin confirmar"** para los códigos que todavía no se han probado en el hotel (lista en `conocimiento.py`).

Salida `1` si hay errores, `0` si no.

## Descripción para `generar`
Ver `ejemplos/tienda.json`. Resumen:
- `variables`: `{nombre, ambito: usuario|sala|contexto, persistente, con_tiempo}`.
- `furnis`: `{nombre, clase, x, y, estado}` (antenas, cajas, contratos, controladores) que luego se citan por nombre.
- `pilas`: `{x, y, trigger, selectores, condiciones, addons, efectos}`.
  - triggers: `dice`, `entra`, `periodico`, `senal`, `pago_ok`, `click_usuario`
  - condiciones: `admin`, `tiene_var` (`negar`), `usuario_cmp`/`sala_cmp`/`contexto_cmp` (`op`: `< = > != >=`), `edad` (`unidad`: segundos…semanas), `no_a_si_mismo`, `usuarios_seleccionados`
  - efectos: `mensaje`, `dar_var`, `quitar_var`, `asignar`, `sumar`, `restar`, `estado_furni`, `estado_seleccion`, `zona` (`campo`: root_x, root_y, width, length), `cobrar`, `senal`
- Todo usa los formatos de opciones vistos en exports reales. Lo que no esté en esa lista no se genera.

## Límites conocidos
- La validación es estática: no sabe si el hotel acepta un valor.
- El número central de las subvariables de tiempo (`s241546-2147418298-7\t1`) no se puede generar; hay que copiarlo de un export.
- `comparar` identifica furnis por clase y posición relativa; si moviste todo el preset, saldrá como cambio.
