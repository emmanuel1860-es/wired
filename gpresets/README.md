# G-Presets más silencioso (sin comandos nuevos)

Cambios sobre G-Presets 1.5.4-beta-10. **No añade comandos ni ventanas**; todos los comandos y funciones existentes se mantienen.

## 1. Menos susurros en el chat
Todos los mensajes pasan por `GPresets.sendVisualChatInfo`. Solo se filtra el **susurro que llega al cliente**; el resto de G-Presets (flujo de copiar/pegar, diagnóstico, paredes) recibe el texto completo como antes.
- Se ocultan los avisos de progreso: "Colocando…", "Configurando…", "Ajustando…", "Importando…", "Exportando…", "Leyendo…", "Quitar: recogiendo…".
- Siempre se muestran: errores, resultados (`importado correctamente`, `Exported`…), preguntas (`Haz clic…`, `Select the…`), respuestas a comandos (`G-Presets:`, `Baldosa`, `Memoria de furnis`, `Paredes`, etc.), avisos (`Ojo:`, `Omitidos`).
- Cualquier otro mensaje sale como mucho 1 cada 3 s.

## 2. Revisión silenciosa del preset al elegirlo
Al elegir un preset, G-Presets lo revisa en segundo plano (`PresetValidator`, versión Java de `wiredtool validar`):
- Si **no hay problemas, no dice nada**.
- Si hay, escribe un resumen y hasta 12 líneas en el **registro** (pestaña Avanzado).
- Solo susurra, una vez, si hay **errores**: `ERROR: el preset tiene N errores (mira el registro)`.
- Solo lee el archivo del preset; no toca la sala.

## Archivos
- `PresetValidator.java`: clase nueva (`extension/tools/`).
- `GPresets-silencioso.patch`: cambios en `GPresets.java` sobre el decompilado con los arreglos de `(Object)`.

## Cómo se reconstruyó
```
java -jar cfr.jar GPresets.jar --outputdir src --jarfilter '^extension\.GPresets$'
# quitar los "(Object)" del decompilado; dejar .then((ContextMenu)null).otherwise(contextMenu)
patch -p0 < GPresets-silencioso.patch     # (o aplicar a mano)
javac --release 8 -cp GPresets.jar:/usr/share/openjfx/lib/*.jar -d cls src/extension/GPresets.java src/extension/tools/PresetValidator.java
cp GPresets.jar GPresets_silencioso.jar && (cd cls && jar uf ../GPresets_silencioso.jar $(find . -name '*.class'))
```

## Sin probar
Solo se comprobó que las clases compilan, cargan y verifican con `-Xverify:all`, y que el validador da el mismo resultado que la versión Python. **No se probó dentro de G-Earth.**
