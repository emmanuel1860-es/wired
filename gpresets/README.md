# GPresets con `:validar` y `:susurros`

`PresetValidator.java` es la versión Java de `wiredtool validar` y se engancha a la extensión G-Presets 1.5.4-beta-10.

- Comando de chat: `:validar` (preset elegido en la lista) o `:validar <nombre>`.
- Responde por susurro con el resumen y las primeras 6 incidencias, y guarda el informe completo en `presets/<nombre>.validacion.txt`.
- Solo **lee** el archivo del preset. No toca la sala, no cambia el preset y no se ejecuta solo al importar.

## Qué se cambió en el jar
1. Clase nueva `extension/tools/PresetValidator.class` (fuente en este directorio).
2. `extension/GPresets.class` recompilada con **una línea añadida** en `initExtension()` después de `new RoomCrop(this);`:
   `new PresetValidator(this);`
   (más los arreglos de `(Object)` del decompilado, ver abajo).

## Cómo se reconstruyó
```
java -jar cfr.jar GPresets.jar --outputdir src --jarfilter '^extension\.GPresets$'
# quitar los "(Object)" del decompilado; dejar .then((ContextMenu)null).otherwise(contextMenu)
# añadir la línea new PresetValidator(this); y el import
javac --release 8 -cp GPresets.jar:/usr/share/openjfx/lib/*.jar -d cls src/extension/GPresets.java src/extension/tools/PresetValidator.java
cp GPresets.jar GPresets_validar.jar && (cd cls && jar uf ../GPresets_validar.jar $(find . -name '*.class'))
```

## Diferencias con `wiredtool` (Python)
- Falta el aviso "varios efectos sin retardo escriben la misma variable sin exec_in_order".
- Por lo demás las reglas son las mismas; en cuatro presets de prueba el resultado coincide (el de la demo da 12 avisos en vez de 13 por la regla anterior).

## Sin probar
Solo se comprobó que las clases cargan y verifican con `-Xverify:all` y que `validar` da el mismo resultado que la versión Python. **No se probó dentro de G-Earth.**

## `:susurros` (menos mensajes en el chat)
Todos los mensajes de G-Presets pasan por `GPresets.sendVisualChatInfo`. Se filtró **solo el susurro que llega al cliente**; los avisos internos (flujo de copiar/pegar, diagnóstico, colocación de paredes) siguen recibiendo el texto completo.

| Nivel | Qué muestra |
|---|---|
| `:susurros normal` | todo, como antes |
| `:susurros bajo` (por defecto) | errores, resultados, preguntas y respuestas a tus comandos; oculta el progreso ("Colocando furni...", "Configurando wired.."); el resto como mucho 1 cada 3 s |
| `:susurros minimo` | solo errores, resultados, preguntas y respuestas a comandos |

El nivel se guarda en la caché de G-Earth. Los cambios están en `GPresets-susurros.patch` (sobre el `GPresets.java` descompilado con los arreglos de `(Object)` y la línea de `PresetValidator`).
