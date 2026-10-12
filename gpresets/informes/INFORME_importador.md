# Informe imp (alcance reducido) - GPresetImporter / StackTileUtils

Archivos entregados (completos): `src/extension/tools/GPresetImporter.java`, `src/extension/tools/importutils/StackTileUtils.java`.
`CAMBIOS.diff` = diff -u contra el CFR crudo (`src_all`); incluye ademas los arreglos mecanicos para que compile (casts, genericos, variables CFR).
Pruebas: `test/ImpTest.java`, `test/Sweep.java`; fp: `test/fp2.py` (fp.py + normaliza ldc_w y StringBuilder), salida en `fp_despues.txt`.

## Fidelidad (fp)
- Sin tocar (solo arreglos de compilacion): GPresetImporter 0 metodos distintos salvo `setupWired` (solo `checkcast`, porque tipe las variables Object de CFR; la secuencia ordenada de invokes/get/put/ldc es identica: seqfp.py 0 de 104); StackTileUtils 0 (tras declarar `List<HPoint>`).
- Despues: GPresetImporter 20 metodos con diferencias, todas intencionadas: onWiredAllVariables, saveWired, (IMP-03); onObjectAdd, typeIsRejected (IMP-09); moveFurni (roomChanged + Math.round IMP-05); setupWired (IMP-06: -2 getX/-2 getY/-2 rootLocation; el resto son los checkcast de base); runImportSafely (IMP-01: catch Throwable); beginImport, moveAvatar, dropFurni, attemptSetState, setupAds, reset, constructor, resetTracking (IMP-02/03/09: campos y guardas); nuevos: onRoomLeft, abortRoomChanged, roomChanged, needsVariableMapping. StackTileUtils: solo los limites de los bucles (no cambian invokes).

## Por correccion
- IMP-03 (reconfirmado en el bytecode original de `lambda$saveWired$6`: `"0".equals(id) && !mapa.containsKey(id)` -> solo "0", literal que nunca esta en el mapa, asi que `needVariableIds` queda siempre `["0"]` y `onWiredAllVariables` nunca lo vacia: 5 s completos por pieza). Aplicado: filtro = ids a traducir (no "0", ni "-*", ni "~*", como `applyWiredConfig`) y no mapeados; solo piezas no-variable; la peticion se hace como mucho una vez por pegado (`variableDiffsRequested`). No se elimina el bloque porque en presets sin piezas `variables` pero con `variables_map` el unico refresco que hoy existe es ese (accidental). Ademas `onWiredAllVariables`/`saveWired` actualizan y leen mapa y lista bajo `synchronized(realVariableIdMap)` (evita ConcurrentModificationException). Censo (test): demo 102 esperas -> 0 (510 s), grande 116 -> 0 (580 s), Ocultar puestos 38 -> max 1, Compra LDC 20 -> max 1, Recompensas 19 -> max 1 (esos tres referencian ids que no estan en su lista de variables; peor caso +5 s por pegado). El mapeo de ids no pierde nada: el refresco solo puede anadir entradas.
- IMP-04: limites inclusivos `n - n3 + 1`. Barrido 1600 casos contra la clase real (orig vs nueva): nulos 156 -> 0, 0 resultados no nulos cambian.
- IMP-05: `(int)Math.round(d*100.0)`. 0.00..40.00: truncado mal en 272 de 4001, redondeo 0.
- IMP-06: destino por defecto del binding parcial = baldosa actual (sin sumar la raiz). Verificado por fp (desaparecen exactamente 2 getX, 2 getY, 2 getfield rootLocation); sin prueba funcional (requiere FloorState/hotel).
- IMP-09: `lastAckAny` (ObjectAdd de cualquier tipo); una gota solo cuenta perdida si hubo un ack despues de enviarla, o si lleva >15 s. Prueba con la clase real: parada sin acks -> no rechaza; acks de otros tipos -> rechaza; >15 s sin acks -> rechaza. (El umbral de 15 s es mio: evita que un rechazo total del hotel deje de detectarse.)
- IMP-01 (importador): `runImportSafely` captura Throwable (prueba: un AssertionError en el hilo ya no escapa y el estado queda NONE). No aplicado: validacion de numeros de `:ip` y rango de getFurniOnTile (fuera de alcance).
- IMP-02: `public void onRoomLeft()` (aborta con "Importacion cancelada" + log + diagnostico; no hace nada sin pegado); `importRoomId` se fija en beginImport tras prepare(); `roomChanged()` en dropFurni, moveFurni, attemptSetState, saveWired, setupAds y en los clics AWAITING_* (no consume el clic). `reset()` lo limpia. Pruebas con la clase real y FloorState falso: onRoomLeft, dropFurni en otra sala (aborta sin enviar), misma sala no aborta. No aplicado: WallItems.placePending.

## Riesgos / no verificable sin hotel
- IMP-03: si el servidor no responde a WiredGetAllVariablesDiffs, las variables mapeadas por nombre siguen dependiendo del refresco tras la ultima variable (igual que hoy). Comprobar en hotel el tiempo de la fase "Configurando wired" del demo (~8,5 min menos) y que los wireds con variable siguen apuntando a ella.
- IMP-04/05: confirmar en hotel la z de furnis en x/y = 0 y de los de z=4.6.
- IMP-02: requiere que GPresets llame a `importer.onRoomLeft()` en el callback de salir de sala (lo hace el integrador 'shell'); las guardas por roomId funcionan aunque no se llame.
- Descartado por el cambio de alcance: ETA/EtaPegado, IMP-07, 08, 10, 12-16, 17-21, IMP-11, IMP-13 (:abort).
