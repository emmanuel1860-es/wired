---
name: "gpresets"
description: "Usar G-Presets (extensión de G-Earth para Habbo): comandos de export/import de presets, formato JSON (wallFurni y floorPlan), copiar/pegar sala con piso, recorte de plano y cómo modificar y recompilar el jar."
---

# G-Presets (Building & Wired Presets) — guía de uso y modificación

Extensión de G-Earth (Habbo) que exporta e importa salas como presets JSON: furnis de piso, wireds, enlaces, furnis de pared y, en la versión modificada más reciente, el piso (plano) de la sala. Responder siempre en español.

Versiones que existen (todas Java 8, clase principal `extension.GPresetsLauncher`):
- **Original 1.2.11**: sin furnis de pared ni plano.
- **GPresets_paredes (1.2.11-fix1)**: añade `wallFurni` (sección 4).
- **GPresets_Recorte (1.2.11-recorte3)**: añade el recorte de RoomCropper, el plano dentro del preset y la pestaña Inicio con Copiar/Pegar (secciones 6 a 9). Es la más reciente; incluye todo lo de las anteriores. Título en G-Earth: «G-Presets Recorte».

## 1. Instalación y carpetas

- Ruta típica: `G-Earth/Extensions/G-Presets - Building & Wired Presets_1.2.11/extension/`
  - `GPresets.jar`: la extensión (o `GPresets_Recorte.jar`; el nombre del jar da igual, la carpeta `presets/` se busca junto al jar).
  - `presets/`: un `.json` por preset. Al sobrescribir uno, el anterior queda como `<nombre>.json.bak`.
  - `Diagnosticos/`: diagnósticos `diag_<fecha>_<preset>.txt` (también con `:diag`).
  - `Cache/`: furnidata.
- Para reemplazar el jar: cerrar G-Earth por completo y finalizar cualquier proceso Java u OpenJDK en el Administrador de tareas antes de copiar. Si no, sigue corriendo la instancia vieja y cada comando responde dos veces; las dos guardan el mismo archivo y gana la última.
- Si se usa GPresets_Recorte, quitar `RoomCropper.jar` y el GPresets viejo: dos extensiones con los mismos comandos (`:crop`, `:ep`, `:ip`) los ejecutan dos veces.
- La extensión solo ve los furnis que llegan al entrar a la sala. Si se abrió después de entrar, salir y volver a entrar. Esto también vale para que lea el grosor de paredes y suelo (necesario para `:crop` y `:plano`).

## 2. Comandos de chat

Exportar:
- `:ep` o `:exportpreset`: pide clic en la esquina inicial y luego en la final del rectángulo, y después el nombre.
- `:ep all` o `:exportpreset all`: exporta toda la sala (rectángulo 0,0 a 100,100) y pide el nombre. En la versión Recorte también guarda el piso recortado (`floorPlan`).
- El nombre no puede tener `< > : " / \ | ? *`.
- Mensaje final: `Exported "<nombre>" successfully (N furni)`. En la versión con paredes se agrega `+ M de pared`.

Importar (antes, elegir el preset en la interfaz):
- `:ip` o `:importpreset`: hacer clic en una baldosa libre, que será la esquina de destino.
- `:ip x,y`: importa con la esquina fija en x,y. Para un preset con piso, usar `:ip 0,0`.
- `:ip auto` o `:ip rec`: elige la posición automáticamente.
- Añadir `forzar` o `force` importa sin revisar ni ajustar nada.
- Añadir `wired` o `furni` importa solo esa parte; si no se pone, rige el modo por defecto de la interfaz. Los modos son: todo, solo furnis de sala, solo wired.

Otros:
- `:rp` o `:recomendar`: recomienda una posición.
- `:aw` o `:acomodar [on|off]`: con `:ip x,y`, mueve las pilas de wired que no quepan a baldosas libres.
- `:diag` o `:diagnostico`: guarda un diagnóstico.
- `:a` o `:abort`: cancela el export o import en curso.
- `:paredes` o `:walls` (versiones con paredes): vuelve a colocar los furnis de pared del último preset.

Comandos de la versión Recorte (ver sección 7): `:crop ...` y `:plano ...`.

Origen de los furnis al importar (`ItemSource` en la interfaz): ONLY_BC, PREFER_BC, PREFER_INVENTORY, ONLY_INVENTORY. Los de BC se colocan con `BuildersClubPlaceRoomItem`; los del inventario con `PlaceObject`.

Avisos frecuentes:
- `Preset importado con omisiones. Furni omitidos: clase xN`: esos furnis no se pudieron colocar (no están en el catálogo BC ni en el inventario, o Habbo los limita). No usar esas clases en salas generadas.
- `Habbo no deja colocar mas '<clase>'`: se alcanzó un límite y se omite el resto.
- Para importar hace falta que el inventario esté cargado y que haya un stack tile (`tile_stackmagic...`) en la sala; si no, el botón Pegar/Importar no queda listo.

## 3. Formato del preset JSON

```json
{
  "furni": [{"id":1,"className":"clase","name":"clase[0]","location":{"x":0,"y":0,"z":0.0},"rotation":0,"state":"0"}],
  "wired": {"triggers":[],"conditions":[],"effects":[],"addons":[],"selectors":[],"variables":[],"variables_map":{}},
  "bindings": [],
  "adsBackgrounds": [],
  "exportOrigin": {"x":0,"y":0,"floor":0},
  "wallFurni": [{"className":"clase_pared","wallPosition":":w=18,0 l=3,115 r","state":"0"}],
  "floorPlan": {"model":"xxxx\n0000\n0000","doorX":0,"doorY":1,"doorDir":2,"wallThickness":0,"floorThickness":0,"wallHeight":-1,"tilesBefore":62,"tilesAfter":13,"margin":0}
}
```

- `furni`:
  - `location` es relativa a la esquina del área exportada; `exportOrigin` guarda esa esquina en coordenadas absolutas y `floor` es la altura más baja.
  - `name` es `clase[n]`, con un contador por clase.
  - `state` es un string; a los wireds se les quita.
- `adsBackgrounds`: solo aparece si hay `ads_background`.
- `tile_stackmagic4x4` es la herramienta para mover furnis: ignorarla en los exports del usuario y no generarla.
- `wallFurni` (versiones con paredes):
  - `wallPosition` es la posición de pared de Habbo `:w=X,Y l=OFFSET,ALTURA r|l`. X,Y es la casilla del muro; OFFSET es el desplazamiento horizontal (`@wallitem_offset` en Wired Creator Tools); ALTURA es `@altitude`; `r` o `l` indica el lado.
  - Es absoluta: el furni de pared se coloca en la misma pared aunque el preset se importe en otra esquina. Solo existe sobre paredes reales de la sala, nunca sobre muros hechos con furnis.
- Un furni de pared se reconoce en Wired Creator Tools por `@dimensions` 0×0 y por tener `@wallitem_offset`. Ejemplo: la "Ventana" del Almacén Club del Arquitecto, con `@type` 1 (BC).
- `floorPlan` (versión Recorte; opcional, solo en exports con `:ep all`):
  - `model`: el plano en texto, filas separadas por `\n`. Cada carácter es una baldosa: `x` = sin baldosa, `0-9` y `a-w` = altura. Fila = y, columna = x. Ya viene recortado a las baldosas con furnis (más margen y camino hasta la puerta) y sin filas/columnas vacías al final, así que las coordenadas de los furnis no se mueven.
  - `doorX`, `doorY`, `doorDir`: la puerta. `wallThickness` y `floorThickness` usan los valores del paquete (-2 muy fino, -1 fino, 0 normal, 1 grueso); `-1` también se usa como «no se conocía al exportar» y al aplicar se toma el de la sala actual.
  - `wallHeight`: la altura de pared tal como llegó (`-1` = automática).
  - Solo se guarda si `exportOrigin` es 0,0, porque el plano solo encaja con las coordenadas de la sala completa. Un `:ep` por área no lo guarda.
  - Los presets exportados antes de la versión Recorte no lo traen: para que lo tengan hay que volver a exportar la sala con `:ep all` (o el botón Copiar).
  - Las versiones anteriores deberían ignorar esta clave sin romperse (leen solo las claves que conocen); no se probó.

## 4. Versión con furnis de pared (GPresets_paredes)

Cambios sobre la 1.2.11:
- `extension/tools/WallItems.java` (nuevo):
  - Sigue los furnis de pared de la sala con los paquetes Items, ItemAdd, ItemUpdate e ItemRemove.
  - Exporta los furnis de pared: con `:ep all` exporta todos; con área, los que tienen casilla dentro del área de los furnis, más 2 casillas de margen.
  - Al terminar el import (mensaje `... importado correctamente` o `... importado con omisiones`) los coloca:
    - desde el inventario con `PlaceObject` y el texto `"-<id> <wallPosition>"`;
    - desde BC con `BuildersClubPlaceWallItem` (pageId -1, offerId, "", wallPosition, true).
  - Si el usuario coloca a mano un furni BC de pared con la extensión abierta, aprende la forma exacta de ese paquete y la usa desde entonces.
- `PresetConfig`: añade el campo `wallFurni` (lectura y escritura). El constructor de export lo llena en `setExportOrigin`, cuando ya se conoce la esquina.
- `WallItemDetails`: añade `bcOfferId` (`bcofferid` del furnidata, o -1).
- `GPresets`:
  - crea `WallItems` en `initExtension`;
  - en `sendVisualChatInfo` lanza la colocación de pared al terminar el import;
  - agrega `+ M de pared` al mensaje de export.

Pendiente de confirmar en el hotel: la forma del paquete BC de pared y el formato de id de `PlaceObject` para furnis de pared del inventario.

## 5. Cómo modificar y recompilar el jar

1. Extraer: `unzip -q GPresets.jar -d x`. Las clases propias están en `extension/`, `game/` y `furnidata/`; las de G-Earth en `gearth/`. Recursos de la interfaz: `extension/ui/gpresets.fxml` y `extension/ui/styles.css`.
2. Descompilar con CFR (Maven Central puede estar bloqueado; GitHub funciona):
   `curl -sSfL -o cfr.jar https://github.com/leibnitz27/cfr/releases/download/0.152/cfr-0.152.jar`
   `java -jar cfr.jar GPresets.jar --outputdir src --jarfilter '^(extension|game|furnidata)\..*'`
3. JavaFX para compilar `GPresets.java`: `apt-get update && apt-get install -y openjfx`; los jars quedan en `/usr/share/openjfx/lib/`.
4. Compilar solo las clases tocadas, contra el jar original:
   `javac -nowarn --release 8 -encoding UTF-8 -cp GPresets.jar:$(ls /usr/share/openjfx/lib/*.jar | tr '\n' ':') -d cls <archivos.java>`
   - ~~El código descompilado de `GPresetImporter` y `PresetPlacementPlanner` no recompila (variables `void`): no tocarlos.~~ **Corregido en la §11**: sí recompilan con arreglos mecánicos y prueba de fidelidad. `GPresetExporter` tampoco es seguro de recompilar (el decompilado referencia lambdas sintéticas como `lambda$export$9`). Engancharse desde `GPresets`, `PresetConfig` o clases nuevas.
   - En `GPresets.java` hay que quitar los casts `(Object)` que CFR mete en `getColumns().add`, `getItems().add`, `getStyleClass().add` y `select(...)`. Ojo: si se quitan todos con un reemplazo global, `Bindings.when(...).then((Object)null).otherwise((Object)contextMenu)` queda ambiguo; hay que dejar `.then((ContextMenu)null).otherwise(contextMenu)`.
   - Los imports nuevos (por ejemplo `ComboBox`, `Node`) hay que escribirlos a mano en `GPresets.java`.
5. Empaquetar: `cp GPresets.jar GPresets_mod.jar && (cd cls && jar uf ../GPresets_mod.jar $(find . -name '*.class'))`. Si se tocó la interfaz, añadir también los recursos conservando su ruta: `(cd res && jar uf ../GPresets_mod.jar extension/ui/gpresets.fxml extension/ui/styles.css)`.
6. Verificar:
   - bytecode Java 8 (`ca fe ba be 00 00 00 34`);
   - que las clases carguen con `Class.forName` (con JavaFX en el classpath);
   - un roundtrip `new PresetConfig(json).toJsonObject()` con `wallFurni` y `floorPlan`;
   - la interfaz: ver sección 9 (prueba con Xvfb).
7. Entregar solo el `.jar` (el usuario pidió explícitamente sin zip ni archivos extra; el código fuente solo si lo pide), con un informe al final, y decir qué falta probar en el hotel.

Paquetes útiles (nombres de G-Earth):
- Al servidor: `PlaceObject` (texto `"-id x y rot"` en piso), `BuildersClubPlaceRoomItem` (-1, offerId, "", x, y, rot, true), `BuildersClubPlaceWallItem`, `MoveObject`, `SetCustomStackingHeight`, `UseFurniture`, `Chat`, `MoveAvatar`, `GetHeightMap`, `GetRoomEntryTile`, `GetOccupiedTiles`, `UpdateFloorProperties`.
- Al cliente: `Objects` y `ObjectAdd` (furnis de piso), `Items`, `ItemAdd`, `ItemUpdate` e `ItemRemove` (furnis de pared, `HWallItem`, `getLocation()` devuelve el texto `:w=`), `FurniList` (inventario), `Whisper` (avisos en el chat), `RoomReady`, `FloorHeightMap`, `RoomEntryTile`, `RoomOccupiedTiles`, `RoomVisualizationSettings`, `RoomEntryInfo`.

## 6. Versión Recorte: qué trae y de dónde viene

Se fusionó **RoomCropper** (extensión de WiredSpast, rediseño en español; recorta el plano de la sala a las baldosas con furnis) dentro de GPresets, sin su ventana 3D. Todo va por chat y por la pestaña Inicio.

Clases nuevas (paquete `extension.tools`):
- `crop/FloorPlan`: el plano en texto (`parse`, `keepOnly`, `trimTrailing`, `withExtraTiles`, `sameAs`, `serialize`). `withExtraTiles(src, claves)` añade las baldosas de otro plano que estén en un conjunto de claves (clave = `y<<16 | x`).
- `crop/Cropper`: la lógica de recorte (`Cropper.compute`). Reescrita limpia porque el decompilado no compilaba; da el mismo resultado que el original (probado en 300 salas aleatorias). Reglas: se quedan las baldosas con furnis (ocupadas según el servidor), la puerta, el margen N alrededor de los furnis y el camino más corto desde la puerta hasta cada grupo (primero respetando diferencias de altura ≤ 1, y si no se puede, sin esa restricción). Luego recorta filas de abajo y columnas de la derecha vacías.
- `crop/Backup`: copias de seguridad en `~/RoomCropper/sala-<id>-<fecha>.json` (compatible con las del RoomCropper original). Guardan modelo, puerta, grosores y altura de pared.
- `RoomCrop`: controlador. Escucha `RoomReady`, `FloorHeightMap` (boolean, int alturaPared, string plano), `RoomEntryTile` (x, y, dir), `RoomOccupiedTiles` (n pares x,y), `RoomVisualizationSettings` (boolean, grosorPared, grosorSuelo) y `RoomEntryInfo` (id sala, boolean dueño). Pide al servidor `GetHeightMap` (solo si no tiene el plano), `GetRoomEntryTile` y `GetOccupiedTiles`. Para cambiar el plano envía `UpdateFloorProperties(modelo con \r, doorX, doorY, doorDir, grosorPared, grosorSuelo, alturaPared)` y espera a que llegue un `FloorHeightMap` igual al enviado (hasta 12 s).
- `SimpleFlow`: el flujo Copiar/Pegar de la pestaña Inicio (sección 8).

Cambios en clases existentes:
- `PresetConfig`: campo `floorPlan` (JSON), `getFloorPlan()`; en `setExportOrigin` pide el plano preparado a `RoomCrop.collectForExport(x, y)` (solo si el origen es 0,0).
- `GPresets`: crea `RoomCrop` y `SimpleFlow` en `initExtension`, nuevos controles de la pestaña Inicio, `getCurrentPresetName()`, `selectPresetByName`, `selectPresetLater`, `homeInfo`, y en `sendVisualChatInfo` avisa a `SimpleFlow`.

## 7. Comandos de la versión Recorte

`:crop` (recorta el plano de la sala actual; solo el dueño):
- `:crop`: recorta con el margen actual (por defecto 0).
- `:crop N` (0 a 5): recorta con N baldosas de margen.
- `:crop ver`: dice cuántas baldosas quedarían y cuántas se quitarían, sin tocar nada.
- `:crop copiar`: copia el plano recortado al portapapeles (para pegarlo en el editor de planos de Habbo).
- `:crop deshacer`: vuelve al plano de la última copia de seguridad de esa sala.
- `:crop margen N`: fija el margen para `:crop` y `:ep all` sin recortar.
- `:crop altura auto|N`: fuerza la altura de pared que se envía (de -1 a 15).
- `:crop info`: muestra lo que la extensión leyó (plano, puerta, grosores, altura, dueño, último envío).
- `:crop ayuda`.

`:plano` (piso de la copia elegida en la lista):
- `:plano`: pone en la sala el piso de la copia, **conservando las baldosas donde ya hay furnis** (así no se pierde nada, ni el stack tile).
- `:plano exacto [forzar]`: pone el piso tal cual; se niega si dejaría furnis fuera, salvo con `forzar`.
- `:plano deshacer`: igual que `:crop deshacer`.
- `:plano guardar on|off`: si `:ep all` guarda o no el piso en la copia.
- Después de `:plano` se importa con `:ip 0,0`.

Todos los mensajes salen como susurro con el prefijo `G-Presets: `. Antes de cambiar el plano siempre se guarda una copia; recortar o cambiar el piso recarga la sala para todos los que estén dentro.

## 8. Interfaz de la versión Recorte (pestaña Inicio)

La ventana tiene cuatro pestañas: **Inicio** (por defecto), **Avanzado** (la antigua «Presets»: estado, registro, lista de presets, qué importar), **Ajustes** y **Configuración del preset**.

Inicio es un flujo de dos pasos:
1. **Copiar esta sala**: campo de nombre (vacío = `sala_<id>`) y botón. Hace lo mismo que `:ep all` y además guarda el piso recortado. Al terminar elige la copia nueva y muestra «Listo: copia «X» guardada…».
2. **Pegar en otra sala**: lista desplegable de copias, resumen (`N furnis · M wireds · con piso (T baldosas)` o `sin piso`) y botón **Pegar en esta sala** (más «Cancelar»).
   - Si la copia trae piso, aparece una ventana con dos opciones: **Solo furnis y wireds** (equivale a `:ip` con clic en un espacio libre) o **Furnis, wireds y floor** (primero `applyPlan` conservando baldosas con furnis, luego espera ≥ 2,5 s y a que la sala y el importador estén listos y ejecuta `:ip 0,0`).
   - Si no trae piso, no pregunta y pega directo.
   - Si el importador no está listo (inventario sin cargar, sin stack tile, sin permisos), avisa el motivo antes de tocar el piso.
- Barra de estado con el último mensaje y el progreso, y botón «Deshacer último cambio de piso».

Detalles técnicos del flujo:
- Copiar y Pegar no tocan `GPresetExporter` ni `GPresetImporter` (no recompilan). Los activan por reflexión llamando a su método privado `onChat(HMessage)` con un paquete `Chat` fabricado (`new HPacket("Chat", TOSERVER, texto, 0, 0)` dentro de un `HMessage`): `:ep all` y después el nombre; `:ip 0,0` para pegar. Si la reflexión falla, el mensaje indica el comando equivalente del chat. El motivo por el que el importador no está listo se lee por reflexión de `notReadyReason()`.
- El piso se calcula en un hilo aparte antes del export (`RoomCrop.prepareExportPlanNow`), porque el export corre en el hilo de paquetes y bloquearlo impediría recibir la respuesta del servidor. `collectForExport` consume ese piso una sola vez y solo si tiene menos de 10 minutos.
- Las baldosas ocupadas se leen con `GetOccupiedTiles` (el servidor cuenta los furnis de varias baldosas); `FloorState.getFurniOnTile` solo conoce la baldosa base y no sirve para esto.

## 9. Aprendizajes y trampas

- **Error de Habbo «Error validación modelo de suelo: (General): Pared no válida. Altura fija.»**: lo devuelve el servidor al recibir `UpdateFloorProperties`. Apunta a la altura de pared: el valor `-1` (altura automática, el que trae `FloorHeightMap` en salas normales) parece no aceptarse al guardar un plano. Desde recorte2 se envía `0` cuando la sala tiene `-1` (`:crop altura N|auto` lo cambia; el valor enviado se registra en el log y `:crop info` lo muestra). **No se pudo confirmar en el hotel**; si vuelve a fallar, pedir la salida de `:crop info` y los valores enviados que aparecen en el aviso de fallo.
- Los avisos de esta extensión salen sin tildes (`No llego la confirmacion`); si el usuario muestra avisos parecidos con tildes, puede haber otro jar cargado (por ejemplo el RoomCropper original).
- Si `:crop` o `:plano` no reciben confirmación en 12 s, no hay cambio (el servidor rechazó el plano); la sala sigue como estaba.
- No se verificó si un `Chat` bloqueado por un interceptor sigue llegando a los demás de la misma extensión; por eso `RoomCrop` escucha `:ep all` sin bloquearlo y deja que el exportador siga su curso.
- Cambiar el plano puede sacar de la sala (al inventario) los furnis de baldosas que desaparecen, incluido el stack tile que el importador necesita. Por eso la interfaz usa «conservar baldosas con furnis» y `:plano exacto` pide `forzar` si dejaría furnis fuera. Tras pegar, se puede limpiar con `:crop`.
- Controles de la interfaz: en el FXML cada `fx:id` necesita un campo público con el mismo nombre en `GPresets` (si no, el cargador falla) y cada `onAction="#metodo"` un método público `void metodo(ActionEvent)`. `styleClass="a, b"` admite varias clases separadas por coma. Un `Label` con la clase `gp-pill` necesita además una clase `lbl*` (por ejemplo `lblgrey`) o se ve blanco sobre fondo claro; `updateLabel` las cambia.
- Probar la interfaz sin hotel: instalar `xvfb` (`apt-get install -y xvfb`) y lanzar una clase de prueba con `xvfb-run -a java --module-path /usr/share/openjfx/lib --add-modules javafx.controls,javafx.fxml,javafx.swing --add-opens javafx.graphics/javafx.scene.text=ALL-UNNAMED --add-opens javafx.graphics/com.sun.javafx.text=ALL-UNNAMED --add-opens javafx.graphics/javafx.scene=ALL-UNNAMED --add-opens javafx.controls/javafx.scene.control=ALL-UNNAMED -cp "prueba:GPresets_Recorte.jar" Clase`. La prueba carga `/extension/ui/gpresets.fxml` con `FXMLLoader`, agrega la hoja de estilos y la clase `g-light` o `g-dark` a la raíz y guarda una captura (`root.snapshot` + `SwingFXUtils`). No se puede probar la parte de red/paquetes fuera de G-Earth.
- Para comparar `Cropper` con el de RoomCropper se cargan ambos jars en `URLClassLoader` separados y se ejecuta `compute` por reflexión sobre salas aleatorias.

## 10. Pendiente de confirmar en el hotel (versión Recorte)

- Que `:ep all` y el botón Copiar guarden `floorPlan`, y que `:plano` / «Furnis, wireds y floor» lo apliquen y `:ip 0,0` encaje los furnis.
- Que la sala se recargue bien al cambiar el plano y que el flujo espere lo suficiente antes del `:ip 0,0`.
- Que enviar altura de pared `0` en lugar de `-1` elimine el error «Pared no válida. Altura fija.».
- Que la llamada por reflexión a `onChat` del exportador y del importador funcione en G-Earth con el `Chat` fabricado.

## 11. Versión mejorada (v1–v3, 12/10/2026): qué trae, cómo se reconstruye y qué falta

Todo en la rama `claude/keen-johnson-thb39b` del repo del usuario, carpeta `gpresets/` (README, parches contra el descompilado CFR crudo, informes, `herramientas/`). Jars entregados aparte: `GPresets_mejorado.jar` (traductor), `_v2` (+ importador), `_v3` (+ interfaz). **Nada probado dentro de G-Earth ni en el hotel**; solo compilación, `-Xverify:all`, carga de clases, pruebas offline y traducciones reales de Google.

### Contenido
- **Avisos restaurados** (se quitó el filtro de susurros) + revisión silenciosa del preset al elegirlo (`PresetValidator`: solo registro; susurra solo si hay errores).
- **Traductor (pestaña Extras)** — flujo: entrante `ChatTraductor.recibir` → `Traductor.merece` → `Slang.detect(estricto)` (si es mi idioma, no traduce) → `Slang.normalize` (expande abreviaturas del idioma detectado) → `Traductor.traducir` (Google gtx; respaldos clients5, translate.google.com, MyMemory; memoria de 600) ; saliente `Extras.onSendChat` bloquea el mensaje y llama `Traductor.http(urlGoogle)`, que **devuelve un JSON con forma de Google** tras `NivelB2/NivelC2/EstiloDe.aplicar`.
  - `Slang` nuevo (detección por puntuación repartida entre idiomas) + **recursos dentro del jar** `extension/tools/slang/<idioma>.txt` (`abrev = significado`; `abrev = -` borra una entrada integrada) y `marks_<idioma>.txt` (palabras que delatan el idioma). Alemán 39→424 entradas, inglés +551. El contador de la interfaz ("Entiende N modismos") los refleja.
  - `EstiloDe`: alemán saliente en "du" (convierte Sie/Ihnen/Ihr solo si no es ambiguo), tono de chat, arregla "30 °C"→"30c". `NivelB2` corregido. **`NivelC2`** (~700 reglas, `Memoria/ingles_c2.txt`): entrada "Inglés C2" en la lista "Traducir lo que yo digo a…" (código `en-c2`, Google recibe `en`). Límite de chat de Habbo: 100 caracteres.
  - `Escritura`: retardo de escritura humano (≈15 car/s, pausa 350 ms, máx. 3,5 s, descontando lo que tardó Google) en `Memoria/escritura.txt`.
  - Archivos editables en `Memoria/`: `modismos.txt`, `ingles_b2.txt`, `ingles_c2.txt`, `aleman_chat.txt`, `escritura.txt`; registro en `traductor_registro.txt`.
- **Importador** (`GPresetImporter`, `StackTileUtils`): sin las esperas de 5 s por wired con `"0"`; baldosa mágica y altura para x=0/y=0; altura redondeada; bindings parciales sin sumar la raíz dos veces; una parada de red larga ya no rechaza un tipo entero; `runImportSafely` captura `Throwable`; **`onRoomLeft()`** aborta la importación al salir de la sala (enganchado en el callback de salida de `GPresets`).
- **Interfaz**: pastilla duplicada oculta, prompt corto + Enter copia, lista vacía con guía, REGISTRO más grande (columna derecha), foco de teclado visible, mejor contraste, combo de Looks sin caja doble, radio 1x1 de Stack tile dentro de su `ToggleGroup`, Extras en `ScrollPane` y alto de ventana acotado al área visible en pantallas bajas (mín. 620).
- **No hecho** (auditoría completa en `gpresets/informes/AUDITORIA.md`, ≈85 hallazgos verificados): envoltorio global de excepciones en los interceptores (una excepción en cualquier escucha deja la extensión muerta), exportador (diálogo "zonas ocultas" bloquea el hilo de paquetes, bucle sin fin de variables, `cache.json` truncado impide arrancar, presets con BOM no cargan), ventana de Vista previa sin `gp-root`, diálogos con texto cortado, tarjeta de revisión del preset, soltar `.json`, tiempo estimado de pegado, tubería del traductor (proteger precios/emoticonos, idioma de origen, cola con orden).

### Reconstruir con garantías (método nuevo)
1. Descompilar con CFR 0.152 y compilar **solo las clases tocadas** contra el jar original (`javac --release 8 -encoding UTF-8`), `jar uf` al final.
2. **Corrección a la §5**: `GPresetImporter` y `PresetPlacementPlanner` **sí recompilan** con arreglos mecánicos (un `void varN` es un contador `int varN = 0` y se borra el `boolean` de sobra; además quitar `(Object)`, `?[]` → `Object[]`, imports de Java 21 como `SequencedCollection`). Hay ≈330 errores mecánicos en ≈40 clases; las únicas que CFR no pudo descompilar son `RoomCrop.sendPlan` y `LooksTematicos.armar`.
3. **Prueba de fidelidad** `herramientas/fp.py <jar_original> <clases_nuevas> <clase|--all>`: compara por método el multiconjunto de invocaciones, campos, `new`, `checkcast` y constantes frente al bytecode original. Sin tocar el código debe dar 0 diferencias (calibrado: `GPresets` da 0 en 62 métodos); tras los cambios solo deben diferir los métodos que se tocaron (el índice de `invokedynamic` desplaza otros métodos por ruido al añadir lambdas).
4. Pruebas con la clase real y un `FloorState` falso (`sun.misc.Unsafe` para instanciar sin constructor; compilar sin `--release 8`), `-Xverify:all` y carga de todas las clases cambiadas.
5. **Google bloquea a `HttpURLConnection` desde el sandbox (429) pero no a `curl`**: `herramientas/build.sh <src> <out> live` engancha `CurlShim` en `Traductor.get` solo para pruebas.
6. Render de la interfaz sin hotel: `herramientas/ui/render.sh <ui_dir> <fxml> <png> [w h] [pestaña]` (simple, quita controladores) y `herramientas/ui/rr.sh` + `RenderReal.java` (arranque real: tema G-Earth Dark, `styles.css`, `PremiumLook`, `HideTabs`, estados forzados). Hay que compilar con `javac` y ejecutar con `xvfb-run`; `Application.launch` no funciona con el lanzador de un solo archivo.
7. Las skills de este entorno son un **espejo** de la copia en la nube: las ediciones locales se pisan en la siguiente sincronización. Guardar el texto en el repo del usuario (`skills/`).

### Reglas del usuario para este programa
Sin comandos de chat nuevos, sin más susurros, interfaz calmada, conservar todas las funciones; no ejecutar nada que intercepte el cliente de Habbo sin que lo pida. Responder en español y conciso; avisar de lo que no se pudo probar.

