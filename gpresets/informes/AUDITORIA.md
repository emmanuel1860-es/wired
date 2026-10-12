# Auditoría de G-Presets (solo lectura)
Hallazgos verificados por 5 revisores independientes sobre el descompilado. **No se aplicó ninguno** (ver README). Rutas citadas = descompilado CFR.

## EXPORTAR

Auditoria solo lectura de exportacion, memoria de furnis, formato de preset y robustez. Hallazgos principales: (1) una excepcion no controlada en cualquier handler de paquetes desconecta G-Presets de G-Earth (comprobado con bytecode y una prueba), y el exportador tiene varios puntos que pueden lanzarla; (2) el cuadro 'quitar zonas ocultas' al copiar una sala se muestra con el hilo de paquetes y el lock del exportador retenidos, congelando la conexion con Habbo mientras esta abierto; (3) el bucle de reintentos de wireds puede girar sin fin cuando solo falta el mapa de variables, y falla en falso con un unico wired si el ping supera ~320 ms; (4) una copia con un ads_background sin offsets se guarda pero no se puede volver a cargar (reproducido con las clases reales); (5) el cargador es muy estricto (BOM, floats en options, state numerico, 'bindings' obligatorio) y los errores no dicen donde; (6) FurniMemory y las copias derivadas (_reemplazos, _limpio) se escriben sin atomicidad ni respaldo, y una memoria corrupta se reinicia en silencio. El resto son pulidos de poco riesgo. Rendimiento con salas grandes: sin problemas (preset de 2244 furni carga en 55-195 ms y se serializa en 42-86 ms). Los 6 JSON de /home/user/wired cargan bien (UTF-8 sin BOM, tipos correctos, sin ids repetidos). Observacion fuera de los hallazgos: WiredCatalogMerge.prepare envia por su cuenta GetCatalogPage (WiredCatalogMerge.java:316, 300 ms entre paginas) cada vez que llega el indice del Club del Arquitecto; es una funcion existente, pero es trafico automatico hacia el hotel que conviene que el usuario conozca.

### E01 · robustez · alta
**Una excepcion en cualquier handler de paquetes desconecta a G-Presets de G-Earth**

- Evidencia: javap gearth.extensions.Extension.run(): la llamada a modifyMessage (offset 693) esta dentro de un try cuya tabla de excepciones (50-772) solo captura IOException y ArrayIndexOutOfBoundsException; ambas cierran el socket y terminan el hilo, y cualquier otra RuntimeException sale de run() tras cerrar el socket. ExtensionBase.modifyMessage recorre los listeners sin try/catch. Prueba T4 (InternalExtension con dos listeners en el mismo header): el primero lanza NPE, modifyMessage lo propaga y el segundo no se ejecuta. GPresets no envuelve los intercept (ExtensionForm.intercept delega directo en la extension) y su handler por defecto solo registra el error, el hilo ya murio. Puntos reales en el exportador, que reacciona a TODA edicion de wired aunque no se este exportando: (a) unRegisteredWiredsInArea hace getFloorItemName(typeId).startsWith(...) y FurniDataTools.getFloorItemName devuelve Map.get sin valor por defecto (null si el tipo no esta en furnidata) -> NPE desde onChat/attemptExport; (b) maybeSaveBindings usa furniFromId(...).getTypeId() sin comprobar null (cacheWiredConfig si lo comprueba); (c) saveX/retrieveX parsean con new PresetWiredX(packet) y RetrievedWired.fromPacket, que lee HWiredContext aunque solo se use para wf_var_; un formato nuevo de paquete lanza IndexOutOfBounds; (d) VariableInternalType.fromInt lanza IllegalArgumentException para un tipo nuevo (hoy solo 0-3), usado en onWiredAllVariables y HWiredVariable; (e) maybeRetrieveBindings hace Integer.parseInt/indices sin control sobre el string de configuracion; (f) WiredCatalogPage.onCatalogIndex llama rebuild()/new Page(packet) fuera de cualquier try en CADA CatalogIndex, no solo en el Club del Arquitecto.
- Arreglo propuesto: Un unico arreglo central: en GPresets sobrescribir las tres sobrecargas intercept(...) para registrar el listener envuelto en try { l.act(m); } catch (Throwable t) { diagnostics.error("handler", t); } (cubre las 30+ clases). Ademas arreglos locales de bajo coste: null-guard con aviso 'furni tipo N desconocido' en getFloorItemName/furniFromId; try/catch alrededor de new PresetWiredX(packet) (si falla no se cachea y el export lo pedira de nuevo) y de new HWiredContext (contexto null); VariableInternalType.fromInt devuelve un valor UNKNOWN; try/catch en onCatalogIndex alrededor de rebuild().
- Riesgo: Bajo. Solo cambia el caso en que hoy el hilo muere. No tocar setBlocked dentro del wrapper para no alterar el comportamiento de los handlers que bloquean paquetes. UNKNOWN en el enum no afecta a los valores 0-3.
- Cómo verificar: Repetir T4 (scratchpad/tr/audit/exportar/t1/T4.java) antes y despues: despues ambos listeners corren y modifyMessage no lanza. Prueba unitaria sin hotel con InternalExtension + PacketInfoManager.EMPTY (por reflexion) y un listener que lanza; prueba de VariableInternalType.fromInt(4) y de un FurniDataTools simulado que devuelve null. Compilar con javac --release 8.

### E02 · bug · alta
**El cuadro 'quitar zonas ocultas' al copiar bloquea el hilo de paquetes y el lock del exportador**

- Evidencia: export() construye el PresetConfig y llama setExportOrigin -> dropUselessZones -> WiredZoneFilter.forExport -> askClean, que hace Platform.runLater + CountDownLatch.await() sin limite de tiempo hasta que el usuario pulsa un boton. En el flujo normal de 'Copiar sala' (con wireds por leer) el ultimo wired llega por retrieve*Conf, que corre en el hilo de lectura de G-Earth (el mismo hilo atiende todos los paquetes de ida y de vuelta, ver E01) y dentro de synchronized(lock) (maybeFinishExportAfterRetrieve). Mientras el cuadro esta abierto: no se procesa ningun paquete de Habbo (G-Earth espera la respuesta de la extension para cada uno), y si no hay wireds por leer el export corre en el hilo 'G-Presets copiar' pero mantiene lock, asi que el primer MoveAvatar o Chat que llegue se queda esperando ese lock y congela igual la conexion. Ademas clearCache() (boton 'limpiar wired', hilo FX) tambien usa ese lock: si se pulsa justo antes de que aparezca el cuadro, el hilo FX y el de paquetes se bloquean mutuamente. El cuadro solo aparece si hay algo que quitar (outcome.plan != null), caso comun en salas con zonas ocultas o de wired. Si la ventana de G-Presets queda detras del cliente el bloqueo puede durar mucho.
- Arreglo propuesto: Sacar la pregunta del hilo de paquetes y del lock: en export(), con el lock, tomar la instantanea y construir el PresetConfig; pasar el estado a un nuevo valor (p. ej. SAVING) y ejecutar dropUselessZones/askClean/savePreset en un hilo propio sin el lock, con tiempo maximo (p. ej. 120 s -> 'Dejar todo'). Alternativa mas simple: lanzar export() entero desde maybeFinishExportAfterRetrieve en un hilo nuevo y no mantener lock durante askClean (liberar antes de preguntar).
- Riesgo: Medio: cambia el hilo donde corre el final del export y hay que mantener el estado coherente (no permitir un segundo :ep mientras se guarda, reset() al terminar). No cambia el formato del preset.
- Cómo verificar: Offline: test de unidad de un 'ExportFinisher' con la pregunta inyectada como Supplier<Boolean> que tarda 5 s; comprobar que el handler simulado regresa en <50 ms y que otro hilo puede tomar el lock durante la espera. Revisar con javap que no queda ningun synchronized(lock) alrededor de askClean. Prueba manual final en el hotel: copiar una sala con zonas ocultas y caminar con el cuadro abierto.

### E03 · bug · media
**Bucle de reintentos sin fin cuando solo falta el mapa de variables y fallo falso con un solo wired**

- Evidencia: requestWiredConfigsLoop decide con 'n7 <= n5/2 -> reintentar, si no ERROR' (bytecode 165-171: if_icmpgt). Caso A: attemptExport pasa a FETCHING cuando faltan solo las variables (list.size()==0 pero !hasVariableMap && hasWiredVariables(), linea 595). Entonces n5=0, tras 300 ms n7=0 y 0<=0 es verdadero: avisa 'Retrying 0 missing wired..' por chat y se llama a si misma recursivamente cada ~320 ms (Utils.sleep suma extraSleepTime) hasta que llegue WiredAllVariablesDiffs; si el hotel nunca lo envia (sin permiso o sin soporte) no termina nunca: spam de susurros, pila creciente (no es Exception, StackOverflowError mataria el hilo dejando el estado en FETCHING), 'Open' y todos los wireds bloqueados (openWired/retrieve*Conf hacen setBlocked) hasta :abort. Caso B: la comprobacion se hace una sola vez 300 ms despues del ultimo Open; con 1 wired (n5=1) basta que la respuesta tarde mas de ~320 ms para que 1>0 y se aborte con 'missing wired configurations' aunque la respuesta llegue 50 ms despues (se ignora porque el estado ya es NONE). Con n5=2 o 3 solo tolera 1 pendiente. El hilo ademas es 'new Thread' no daemon y sin nombre (602).
- Arreglo propuesto: Separar las condiciones: esperar con un plazo total (p. ej. max(3 s, 3*getSafeFeedbackTimeout()) desde el ultimo paquete recibido) sondeando cada 100 ms hasta que no falte ningun wired y llegue el mapa de variables; si vence solo el mapa de variables, exportar con aviso (los wf_var_ conservan el nombre) o abortar con mensaje claro, nunca reintentar 'N=0'. Reemplazar la recursion por un bucle y poner un maximo de 2 pasadas. Hilo daemon con nombre.
- Riesgo: Bajo-medio: cambia solo la logica de espera; hay que cuidar que un export con variables sin mapa no deje variableId vacio (ver E12).
- Cómo verificar: Extraer la decision a una funcion pura nextAction(n5, n7, hasVarMap, msDesdeUltimoPaquete, plazo) y probarla con una tabla (n5=0/1/2/3, n7 variando, mapa si/no, plazo vencido o no) con javac --release 8; revisar con javap que ya no hay llamada recursiva. Sin hotel no se puede reproducir la latencia real.

### E04 · bug · media
**Un ads_background sin offsets produce una copia que no se puede volver a cargar**

- Evidencia: El exportador crea PresetAdsBackground con (String) mapa.get("imageUrl"/"offsetX"/...) que es null si el MapStuffData no trae esas claves; toJsonObject serializa el bean y org.json omite los null. Al cargar, PresetAdsBackground(JSONObject) usa getString("imageUrl"), getString("offsetX")... obligatorios. Prueba T3 con las clases reales: PresetConfig con PresetAdsBackground(1,"http://x/y.png",null,null,null) -> JSON {"adsBackgrounds":[{"furniId":1,"imageUrl":"http://x/y.png"}]} y new PresetConfig(thatJson) lanza JSONException: JSONObject["offsetX"] not found. Como un solo furni de ese tipo hace fallar todo PresetConfig(JSONObject), la copia completa de la sala queda ilegible justo despues de exportarla (SimpleFlow.selectPresetLater intenta cargarla a los 500 ms y muestra el error).
- Arreglo propuesto: Exportador: Objects.toString(valor, "") (o "0" para offsets) y omitir la entrada si imageUrl es null/vacia. Cargador: optString con valores por defecto y saltar entradas sin furniId. Con el cargador tolerante tambien se recuperan las copias ya exportadas.
- Riesgo: Bajo. Las copias validas no cambian; el importador de ads ya trabaja con strings (revisar que no asuma imageUrl no vacia).
- Cómo verificar: Repetir T3 (scratchpad/tr/audit/exportar/t1/T3.java): la recarga del JSON propio debe pasar; anadir caso con los 4 campos presentes y comparar toJsonObject() antes/despues sobre los 6 presets de /home/user/wired.

### E05 · bug · media · sin verificar
**Un furni cuyo tipo no esta en furnidata rompe la lectura de wireds y el export**

- Evidencia: FurniDataTools.getFloorItemName(int) es typeIdToNameFloor.get(typeId) (javap), null para tipos ausentes del furnidata cargado. unRegisteredWiredsInArea llama string.startsWith("wf_trg_") sobre ese valor (298) y export() hace new PresetFurni(id, className=null, ...) y luego ((String)object).equals("ads_background") (338): NullPointerException. unRegisteredWiredsInArea se ejecuta desde onChat (al escribir el nombre), desde el hilo de lectura de wireds y en cada respuesta de maybeFinishExportAfterRetrieve, asi que con :ep all en una sala con un solo furni raro el export falla de forma opaca y, por E01, puede desconectar la extension. La frecuencia real de tipos ausentes en una sala no se puede medir sin hotel.
- Arreglo propuesto: En esos dos puntos tratar null como furni no exportable: saltarlo y contar 'N furnis de tipo desconocido omitidos (ids ...)' en el log y en el chat. No usarlos como clave de wired.
- Riesgo: Bajo: hoy esos casos revientan; el resto sigue igual.
- Cómo verificar: Prueba con un FurniDataTools simulado (o refactor a una interfaz minima) que devuelva null para un typeId y comprobar que unRegisteredWiredsInArea lo ignora y export cuenta el omitido. javap para confirmar que no quedan dereferencias de getFloorItemName sin guard.

### E06 · robustez · media
**El cargador de presets es muy estricto y sus errores no dicen donde esta el problema**

- Evidencia: Pruebas con las clases reales (T1, T3, T10, org.json 20231013): (1) archivo con BOM UTF-8 (Notepad/PowerShell): 'A JSONObject text must begin with {' (readAllLines no quita el BOM); (2) archivo guardado en Latin-1 o UTF-16 (Out-File por defecto): MalformedInputException 'Input length = 1'; (3) falta 'bindings' o 'wired' o el 'name' de un furni: JSONException (el unico opcional de la raiz es adsBackgrounds); (4) options con un decimal (2.0, tipico de un generador Python) -> ClassCastException BigDecimal->Integer, con un entero > 2^31 o un string tambien; variableIds numericos ([-240]) -> ClassCastException Integer->String; items como strings -> ClassCastException; (5) 'state': 1 numerico -> 'is not a string' (null si pasa); (6) ids/coordenadas gigantes pasan sin error porque getInt trunca (99999999999 -> 1215752191, 2147483648 -> -2147483648). El mensaje que ve el usuario (selectPreset: exception.getMessage()) no indica seccion ni indice. Ademas FurniMemory.applyTo y WiredZoneFilter.cleanSelected leen con new String(bytes, UTF_8), que es indulgente y sustituye bytes malos por U+FFFD, mientras el cargador principal es estricto: el mismo archivo se lee distinto segun la ruta.
- Arreglo propuesto: Un unico PresetConfigUtils.readText(File): quitar BOM, detectar UTF-16 por BOM, decodificar UTF-8 estricto y, si falla, avisar y usar windows-1252; usarlo en todos los lectores. En los parseadores: bindings/name opcionales (name por defecto className[indice]), conversion tolerante de numeros (Number.intValue o Integer.parseInt de string, rechazando fuera de rango con mensaje) y de variableIds/state con String.valueOf, y envolver cada entrada en un try que relance con contexto ('wired.effects[12].options[3]: se esperaba entero, hay 2.0').
- Riesgo: Bajo-medio: solo acepta entradas que hoy fallan; hay que mantener intactos los textos con la doble codificacion (no re-decodificar). No tocar la semantica de options.
- Cómo verificar: Corpus: cargar los 6 JSON de /home/user/wired antes y despues y comparar toJsonObject() (deben ser identicos); anadir los casos de T1/T3/T10 y comprobar que pasan o dan un mensaje con ruta. javac --release 8.

### E07 · robustez · media
**La memoria de furnis se escribe sin atomicidad, se reinicia en silencio si esta corrupta y se toca desde varios hilos**

- Evidencia: save() hace Files.write directo sobre Memoria/furnis.json (trunca y escribe): si G-Earth se cierra o el equipo se apaga a mitad, el archivo queda incompleto. load() entonces cae en el catch, avisa 'empiezo una nueva' y deja data vacio; el siguiente save() lo sobrescribe sin copia, perdiendo el historial ok/fallos y los 'reemplazos' que el usuario ensenio con :reemplazo. section()/data se modifican desde command() (hilos 'G-Presets memoria' lanzados por onChat) sin synchronized, mientras save() y recordImport (synchronized) recorren data.toString(1): riesgo de ConcurrentModificationException y de perder la escritura. El tamano esta acotado (una entrada por clase) pero recordImport crea entrada para cada className de un preset ajeno, incluidos nombres inventados.
- Arreglo propuesto: Escribir a furnis.json.tmp y mover con ATOMIC_MOVE; si load() falla, renombrar el archivo danado a furnis.json.corrupto-AAAAMMDD y avisar; hacer command() y section() synchronized sobre this (o copiar data bajo lock antes de serializar); ignorar clases que no existan en furnidata al contar ok/fallos.
- Riesgo: Bajo. Mismo formato de archivo.
- Cómo verificar: Test unitario: archivo truncado a la mitad -> load() conserva una copia .corrupto y data vacio; test de hilos: 4 hilos haciendo put + save durante 2 s sin excepcion; comprobar que tras un 'kill' simulado (escribir solo el .tmp) el furnis.json anterior sigue intacto.

### E08 · robustez · media
**Copias de seguridad de presets: un solo .bak, doble movimiento no atomico y copias derivadas sobrescritas sin respaldo**

- Evidencia: savePreset escribe un .tmp (bien) pero luego mueve el preset actual a nombre.json.bak (REPLACE_EXISTING) y despues el .tmp a nombre.json: si falla o se corta entre los dos pasos, el preset deja de existir bajo su nombre (solo queda el .bak) y la lista lo pierde; y como el .bak se reemplaza en cada guardado, dos exportaciones seguidas con el mismo nombre destruyen la version original (p. ej. 'Compra LDC' hecho a mano). El nombre de la copia ya existente solo se avisa despues. FurniMemory.applyTo (NOMBRE_reemplazos) y WiredZoneFilter.cleanSelected (NOMBRE_limpio) usan Files.write directo: sobrescriben sin respaldo un archivo que el usuario pudo haber editado a mano, y escriben JSONObject.toString() en UNA linea de 900 KB, dificil de revisar (el exportador usa toString(4)).
- Arreglo propuesto: Funcion comun PresetConfigUtils.writeAtomic(File, String): escribir .tmp, Files.copy del actual a respaldos/nombre.AAAAMMDD-HHMMSS.json (conservar los ultimos 5) y Files.move(tmp, destino, ATOMIC_MOVE con REPLACE_EXISTING de reserva). Usarla en savePreset, applyTo y cleanSelected con toString(2). La carpeta respaldos no se lista como preset.
- Riesgo: Bajo. Solo anade archivos en una subcarpeta; listPresets filtra por .json en la raiz.
- Cómo verificar: Test sobre carpeta temporal: guardar 3 veces el mismo nombre y comprobar 3 respaldos y que el actual es el ultimo; simular fallo del segundo paso (destino de solo lectura) y comprobar que el original sigue en su sitio; confirmar que listPresets no cambia.

### E09 · ux · baja
**Los estados de espera del exportador no caducan y los comandos distinguen mayusculas**

- Evidencia: Tras :ep o :ep all el estado AWAITING_RECT1/AWAITING_RECT2/AWAITING_NAME no tiene temporizador: el siguiente clic para caminar se interpreta como esquina del rectangulo y la siguiente frase normal del chat se toma como nombre del preset (se bloquea, no llega a la sala) y dispara un export de toda la sala con ese texto. Solo reset() al salir de sala (callback de FloorState) o :abort lo cancelan. Ademas onChat compara string.equals(":ep") sin trim ni minusculas: ' :ep', ':EP' o ':ep  all' no se reconocen y salen como chat publico a la sala (FurniMemory, FurniReader y WallItems si normalizan).
- Arreglo propuesto: Guardar el instante de entrada en cada estado AWAITING_* y abortar con aviso a los 60-90 s de inactividad (comprobacion perezosa en onChat/moveAvatar); normalizar con trim().toLowerCase(Locale.ROOT) antes de comparar los comandos (conservando el texto original para el nombre).
- Riesgo: Bajo.
- Cómo verificar: Test de la maquina de estados con reloj inyectado (sin G-Earth): estado, avance del reloj 61 s, siguiente evento -> NONE y mensaje; tabla de comandos con variantes de mayusculas y espacios.

### E10 · rendimiento · media
**Coordenadas enormes en un preset bloquean el hilo de paquetes durante minutos**

- Evidencia: El cargador no valida rangos y getInt trunca valores grandes (T3: x=99999999999 se convierte en 1215752191). PresetUtils.presetDimensions devuelve el ancho/alto del cuadro de furnis y lowestFloorPoint(floorState, preset, origen) recorre origen..origen+dim (inclusive) llamando a floorState.floorHeight(i,j), que es synchronized. Prueba T7 con dos furnis en (0,0) y (100000,100000): dims 100001x100001 = 10 000 400 004 iteraciones; 100 millones de iteraciones synchronized sin contencion tardan 2,6 s, asi que como minimo ~4,3 min, sin contar el coste real de floorHeight. startImport se ejecuta dentro del handler MoveAvatar (hilo de paquetes) o desde la UI, congelando la conexion. Un error de un generador (unidades equivocadas) o un archivo ajeno produce esto.
- Arreglo propuesto: Dos capas: (1) en lowestFloorPoint recortar el bucle a [0, anchoPlano) x [0, altoPlano), resultado identico porque floorHeight devuelve 'x' (256, ignorado) fuera del plano; (2) en el cargador rechazar o avisar de furnis con |x|,|y| > 512 o dimensiones > 256 con un mensaje claro.
- Riesgo: Nulo para (1) (mismo resultado); bajo para (2) (solo afecta a presets que ya no son importables).
- Cómo verificar: Test: mismo resultado de lowestFloorPoint antes/despues en una cuadricula simulada y tiempo < 10 ms con dims 100000x100000; T7 para las dimensiones.

### E11 · ux · baja · sin verificar
**El export no informa de lo que deja fuera ni de que wireds faltan**

- Evidencia: (a) Tras exportar un rectangulo, items y secondItems de cada wired se filtran con set::contains (369-373): las referencias a furnis fuera del area se descartan sin ningun aviso, y el wired queda con menos objetivos en la copia. (b) El error 'Couldn't export due to missing wired configurations' (450-453) no dice cuantos ni cuales (clase/coordenadas) y obliga a repetir todo; un solo wired que no responde (n7 > n5/2 en el segundo pase) tira la copia entera. (c) WallItems.collectForExport usa bl = (origen == 0,0) como 'exportar todas las paredes': una seleccion parcial que empieza en la esquina 0,0 incluye todos los furnis de pared de la sala. (d) maybeFinishExportAfterRetrieve registra '0 wired configurations left' cuando solo falta el mapa de variables.
- Arreglo propuesto: Contar y registrar 'N referencias de M wireds apuntaban a furnis fuera del area' (con los ids); al fallar listar los wireds pendientes (clase, x,y) y ofrecer 'exportar igualmente sin ellos' como en el modo sin wired; en WallItems distinguir ':ep all' de un rectangulo que casualmente empieza en 0,0 (pasar un indicador desde el exportador).
- Riesgo: Bajo: son avisos; el cambio de (c) altera que paredes entran en copias parciales desde 0,0.
- Cómo verificar: Test de export() sobre un FloorState simulado con un wired que apunta a un furni fuera del area: debe quedar el aviso con el contador; revisar que los 6 presets existentes no cambian.

### E12 · bug · baja · sin verificar
**variableId queda vacio en wf_var_ leidos del servidor y el relleno por nombre no lo cubre**

- Evidencia: El constructor de PresetWiredVariable usado por RetrievedWiredVariable inicializa this.variableId = "" y solo lo sustituye si el HWiredContext trae una variable con el mismo nombre; en cambio el constructor desde paquete (UpdateVariable guardado por el usuario) deja variableId = null. export() solo completa desde variablesMap cuando variableId == null o "0" (356), asi que un wf_var_ cuyo contexto no coincida se exporta con "variableId": "" y el importador lo usa como clave (GPresetImporter 237: realVariableIdMap.put(variableId, ...)). HAllVariablesInRoom solo lee el hash (no la lista), por lo que roomVariablesList.variables siempre esta vacio y la rama de coincidencia por sala nunca se usa. No he podido comprobar con paquetes reales con que frecuencia no coincide el contexto.
- Arreglo propuesto: Tratar vacio como null en export(): variableId == null || variableId.isEmpty() || variableId.equals("0") antes de buscar por nombre en variablesMap.
- Riesgo: Bajo.
- Cómo verificar: Test de la condicion con tres valores (null, vacio, '0') y un variablesMap con el nombre; comparar el JSON exportado.

### E13 · ux · baja
**La convencion de doble codificacion se aplica de forma desigual**

- Evidencia: Prueba T2 con la libreria real: HPacket.readString()/appendString(String) usan ISO-8859-1 (bytecode), por eso los textos de wired viajan bien como 'mojibake' y la ida y vuelta es consistente (UTF-8 -> 'CÃ¡mara' -> bytes originales). Incoherencias: (1) el nombre del preset se lee con readString(UTF_8) (correcto) pero se repite al usuario con sendVisualChatInfo, que construye el susurro con HPacket(Object...) -> appendString Latin-1: 'Cámara ñ € ó' sale como bytes 43 e1 6d 61 72 61 20 f1 20 3f 20 f3 (acentos en un solo byte = UTF-8 invalido para el cliente, y el euro pasa a '?'), asi 'Exported "Cámara" successfully' se ve con un caracter roto; (2) un preset con acentos reales (no doble codificados) o caracteres > U+00FF (euro, puntos suspensivos, flechas) se envia al servidor corrupto sin ningun aviso del cargador; (3) el validador solo revisa la 'config' de effects/addons/triggers, no conditions, selectors ni variables (nombres de variable con acento), y no avisa cuando el texto mezcla 'Ã'/'Â' con un caracter > U+00FF.
- Arreglo propuesto: (1) En sendVisualChatInfo construir el paquete con appendString(texto, UTF_8) para el campo del texto (los mensajes actuales sin acentos no cambian); (2) al cargar, registrar un aviso por wired con caracteres > U+00FF en config ('se enviaran como ?'); no re-codificar automaticamente; (3) ampliar el validador a todas las secciones y a ese caso.
- Riesgo: Bajo. (1) no altera mensajes ASCII; (2) y (3) solo avisan.
- Cómo verificar: Repetir T2 tras el cambio: bytes de 'Cámara' = 43 c3 a1 ...; prueba de que el texto ASCII produce el mismo paquete; corpus de los 6 presets sin avisos nuevos.

### E14 · robustez · baja
**lecturas.csv permite inyeccion de formulas desde textos de otros jugadores**

- Evidencia: cell() solo cambia ';' por ',', y tabuladores/saltos por espacio. Los campos 'dueno' y 'texto_o_dato' vienen de furnis ajenos (placas, trofeos, estados). Prueba T9: =HYPERLINK(...), +cmd|..., @SUM(...) y -2+3 se escriben tal cual; abierto en Excel con separador ';' el contenido se interpreta como formula.
- Arreglo propuesto: En cell(): si empieza por = + - @ tab o CR anteponer una comilla simple.
- Riesgo: Nulo.
- Cómo verificar: Repetir T9: las cuatro cargas salen con prefijo '.

### E15 · robustez · baja · sin verificar
**Validacion de nombres de preset duplicada e incompleta**

- Evidencia: Ambas copias usan [^<>:"/\\|?*]*: evitan separadores y por tanto el recorrido de directorios ('..' solo es inofensivo), pero admiten nombres reservados de Windows (CON, PRN, AUX, NUL, COM1-9, LPT1-9: CON.json.tmp se resuelve al dispositivo), caracteres de control, punto o espacio final y longitudes > 255 con el campo de texto de la UI (el chat limita a ~100). En esos casos savePreset falla despues de leer toda la sala (mensaje generico) o, en un dispositivo, 'tiene exito' sin crear el archivo. No he podido probar los nombres reservados porque el sandbox es Linux.
- Arreglo propuesto: Un solo PresetConfigUtils.nombreValido(String) que rechace reservados, control, final '.'/' ' y longitud > 120, usado por el chat y por la UI, con mensaje especifico.
- Riesgo: Nulo.
- Cómo verificar: Tabla de nombres validos/no validos en un test unitario de la funcion pura.

### E16 · rendimiento · baja
**Las sugerencias de reemplazo cuestan 0,3-1,3 s por tipo que falla**

- Evidencia: suggest() copia el mapa nameToFloorItems por reflexion y llama a FurniMatch.score por cada candidato; score recalcula las caracteristicas del furni de origen cada vez y usa String.matches / replaceAll (compilan una regex por llamada). Prueba T5/T6 con un catalogo sintetico de 14 000 candidatos del mismo tamano: 657 ms por llamada de media, primera pasada 1291 ms; con patrones precompilados 280 ms en frio y 208 ms frente a 304 ms en caliente, con puntuaciones identicas en los 14 000 candidatos. recordImport ejecuta suggestionLines (hasta 6 por tipo) dentro de un metodo synchronized durante el informe final de la importacion, y announcePreview lo repite antes de colocar: con 40 tipos que faltan son de 10 a 50 s de espera.
- Arreglo propuesto: Precompilar los Pattern (MARKS, NOT_AZ, NUMERIC_TOKEN) y cachear por clase las caracteristicas del catalogo (kinds, nameWords, classTokens, traits) una vez por carga de furnidata, y las del furni de origen una vez por suggest(). Mover suggestionLines fuera de recordImport.
- Riesgo: Bajo: funcion pura; mantener el resultado exacto.
- Cómo verificar: Comparar score() antiguo y nuevo sobre el catalogo sintetico y sobre las clases reales de los presets (T6: 0 diferencias con patrones precompilados); medir tiempo por llamada.

### E17 · limpieza · baja
**Varios detalles de limpieza y concurrencia en el exportador y utilidades**

- Evidencia: (a) ExportWiredGuard lee el campo privado 'state' del exportador por reflexion cada 250 ms aunque existe getState(), y lee noExportWiredCbx.isSelected() desde un hilo que no es el de JavaFX. (b) FurniReader registra siempre un interceptor global de TODOS los paquetes en ambos sentidos y spy() es synchronized: cada paquete toma el monitor aunque el espia este apagado (spyUntil se comprueba dentro); el volcado a Lecturas\espia_*.txt guarda el contenido sin censurar (los archivos no se limpian nunca). (c) El constructor PresetConfig(JSONObject) tiene un efecto global: WallItems.setPending(...), asi que cualquier carga futura (vista previa, comparador, informe) cambia las paredes que se colocaran al terminar la siguiente importacion. (d) WallItems.onImportFinished comprueba 'placing' pero este se pone a true dentro del hilo ya iniciado: dos disparos seguidos (importacion y :paredes) pueden lanzar dos colocaciones. (e) hasWiredVariables recorre values() de Collections.synchronizedMap sin sincronizar (CME posible); wiredFurniBindings no se limpia en onFurniRemoved ni en clearCache.
- Arreglo propuesto: (a) usar exporter.getState() y leer el checkbox via una propiedad volatil actualizada en el hilo FX; (b) comprobar un boolean volatil antes de entrar en el bloque synchronized y no guardar paquetes sensibles (chat/susurros) en el espia; rotar o borrar espia_* antiguos; (c) mover setPending a selectPreset/prepare del importador; (d) AtomicBoolean.compareAndSet en onImportFinished; (e) synchronized(map) al iterar y limpiar wiredFurniBindings.
- Riesgo: Bajo cada uno; (c) hay que mantener que la importacion siga colocando las paredes del preset elegido.
- Cómo verificar: javap/compilacion; test de dos llamadas simultaneas a onImportFinished con un contador de arranques; test de PresetConfig cargado dos veces sin cambiar pending.

### E18 · funcion · media · sin verificar
**Funcion extra: autoverificar cada copia nada mas exportarla**

- Evidencia: Hoy una copia puede guardarse y resultar inutilizable sin que nadie se entere hasta cargarla (E04) o contener referencias descartadas (E11). Se podria reutilizar lo que ya existe: tras savePreset, volver a leer el archivo con PresetConfigUtils.loadPresetOrThrow, ejecutar PresetValidator.validar sobre el JSON y mostrar en el log (una sola linea) 'Copia verificada: N furnis, M wireds, 0 errores, 2 avisos' y, si hay errores, el primero. Anadir de paso un bloque opcional "meta" en la raiz con fecha, version de G-Presets, hotel y sala (el cargador ignora claves desconocidas: PresetConfig(JSONObject) solo lee las claves conocidas) y los contadores de referencias descartadas.
- Arreglo propuesto: Llamar a una funcion verificarCopia(nombre) en un hilo aparte justo despues del 'Exported ... successfully'; escribir el informe en NOMBRE.validacion.txt como ya hace :validar. Sin comandos nuevos de chat ni susurros extra (solo log y la linea de estado de la pestana Inicio).
- Riesgo: Bajo: solo lectura despues de guardar. Cuidado de no cambiar el texto 'Exported "..." successfully (N furni)' porque SimpleFlow.onChatInfo lo interpreta.
- Cómo verificar: Test con una copia valida (0 errores) y con una copia con ads_background sin offsets (debe mostrar el error actual hasta aplicar E04).

### E19 · funcion · baja · sin verificar
**Funcion extra: barra de progreso y cancelacion al leer los wireds de una copia**

- Evidencia: La lectura de wireds ocupa 100 ms + extraSleepTime por cada uno (482 wireds en lista_compra_individual_8 son ~60 s) y solo informa una vez al empezar y cada multiplo de 10 restantes en el log; la pestana Inicio no muestra avance aunque el importador usa updateProgress. El estado FETCHING se cancela solo con :abort por chat.
- Arreglo propuesto: Llamar a extension.updateProgress('Leyendo wireds 120/482 - faltan 40 s', ratio) desde maybeFinishExportAfterRetrieve y requestWiredConfigsLoop, y reutilizar el boton existente de abortar de la pestana Inicio para llamar a exporter.reset().
- Riesgo: Bajo (solo UI).
- Cómo verificar: Renderizado headless de la pestana Inicio con el progreso y revision de que reset() detiene el hilo de lectura (state != FETCHING).

## IMPORTAR

Auditoria solo lectura, con pruebas offline (javap, arneses Java contra el jar original, replicas de algoritmo y censo de los JSON del usuario). Los arneses estan en /tmp/claude-0/-home-user-wired/07f3ecc1-8f98-5c55-8b13-34fe1b96edab/scratchpad/tr/audit/importar/t/ (ListenerThrow, StackEdge/StackFix, ZTrunc, LowFloor, DropItems, LostSim2/3, PlanTest/PlanProp/PlanFail, Nfe). Lo mas rentable para el usuario: (1) IMP-03, el importador espera 5 s a ciegas por cada pieza de wired con "0" en variableIds: en su preset demo son 102 de 402 piezas = 510 s (8,5 min) de espera pura, mas del 60 % del tiempo total; (2) IMP-02, el importador no se entera de que sales o cambias de sala y sigue mandando PlaceObject a la sala nueva; (3) IMP-01, cualquier excepcion en un listener (el importador no envuelve ninguno) cierra la conexion de la extension con G-Earth y la deja muerta; (4) tres errores de altura/posicion con prueba (IMP-04 baldosa magica no se usa en x==0 o y==0, IMP-05 z truncado en 1 centesima en 20 furnis del demo, IMP-06 bindings con raiz sumada dos veces). El resto son robustez, UX y rendimiento de menor impacto. El planificador (PresetPlacementPlanner/RoomGrid) salio limpio en pruebas de propiedades (480 planes, 0 violaciones, 1-17 ms con el preset de 2.244 furnis). Cada propuesta de arreglo indica su riesgo y como comprobarla sin cliente de Habbo.

### IMP-01 · robustez · alta
**Una excepcion en cualquier listener mata la conexion de la extension con G-Earth**

- Evidencia: Extension.run() llama a modifyMessage() sin proteccion. javap de Extension.run: la tabla de excepciones del bucle (pc 50-772, donde esta modifyMessage en 693) solo captura IOException y ArrayIndexOutOfBoundsException; cualquier otra RuntimeException va a 'any' -> finally (cierra el socket) y sale de run(). ExtensionFormLauncher L69-71 hace run(); System.exit(0), asi que la JVM sigue viva (JavaFX) pero la extension queda desconectada y sin escuchar paquetes: la ventana se ve normal y ya no hace nada. Arnes ListenerThrow (modifyMessage real del jar): un listener que lanza NPE propaga la excepcion a quien llama y el otro listener registrado para el mismo paquete no se ejecuta o se ejecuta segun el orden de un HashSet. Ninguno de los 17 listeners del importador (ni los de FloorState/Inventory) esta envuelto; runImportSafely solo captura Exception, no Throwable. Disparadores concretos hoy: ':ip 99999999999,1' -> NumberFormatException en beginImport L447 (probado en arnes Nfe), ':ip -5,-5 forzar wired' -> IndexOutOfBounds en FloorState.getFurniOnTile (furnimap.get(n).get(n2), L2087 -> applyWiredOnlyFilter) dentro de moveAvatar/startFromUI, y un cambio de formato en WiredAllVariablesDiffs (onWiredAllVariables 212-248 hace new HWiredVariable(packet) sin proteger). Es latente: no hay un fallo conocido en flujo normal, pero el costo si ocurre es perder toda la extension a mitad de una importacion (el hilo importador queda enviando a un socket cerrado).
- Arreglo propuesto: En GPresets sobreescribir las tres sobrecargas de intercept(...) y envolver el MessageListener: m -> { try { l.act(m); } catch (Throwable t) { diagnostics.error("listener " + nombre, t); } } y delegar en super.intercept. Asi se protegen tambien FloorState, Inventory, RoomCrop, etc. (todos llaman a gPresets.intercept / iExtension.intercept, que resuelve al override). Ademas: runImportSafely -> catch (Throwable); en beginImport validar los numeros de :ip con try/catch NumberFormatException y mensaje claro; en applyWiredOnlyFilter/findRoomFurni comprobar rango 0..129 antes de getFurniOnTile.
- Riesgo: Bajo. No cambia la logica de ningun listener que hoy funcione; solo convierte un fallo que mata la extension en una entrada en Diagnostics. Un listener que lance se salta ese paquete (igual que hoy) pero el resto sigue funcionando.
- Cómo verificar: Offline: reutilizar el arnes ListenerThrow (package gearth.extensions) registrando el listener envuelto y comprobar que modifyMessage ya no propaga y que el segundo listener corre; test unitario de beginImport con ':ip 99999999999,1' y ':ip -5,-5 forzar wired' sobre un GPresets simulado comprobando que no escapa excepcion. No requiere hotel.

### IMP-02 · bug · alta
**El importador no se entera de que sales o cambias de sala y sigue colocando en la sala nueva**

- Evidencia: FloorState.reset() se dispara con RoomReady (entrar a otra sala), Quit (salir) y CloseConnection, y llama al callback onRoomLeave. javap de lambda$initExtension$11 (GPresets) confirma que ese callback solo hace updateUI(), exporter.reset() y un log 'Leaving room..'; el importador solo se resetea en onEndConnection (L413). Los bucles de las fases ADD_UNSTACKABLES/ADD_FURNITURE (L1691, L1502) y dropFurni (L1020-1054) comprueban unicamente this.state, no la sala, y envian PlaceObject/BuildersClubPlaceRoomItem con ids de inventario: si el usuario sale mientras se colocan 2.000 furnis (varios minutos) y entra a otra sala suya, siguen colocandose alli a las coordenadas del preset. Mientras tanto maybeBlockPlacements (L268) le bloquea al usuario colocar furni a mano en la sala nueva. En fase AWAITING_* (esperando clic) el primer clic en la sala nueva se consume como 'baldosa libre' / 'raiz' y se importa con un plan calculado para otra sala. En SETUP_WIRED, floorState.furniFromId lanza NPE (furniIdToItem=null) y la importacion termina con 'ERROR ... NullPointerException'. WallItems.placePending tampoco comprueba la sala.
- Arreglo propuesto: Anadir GPresetImporter.onRoomLeft(): synchronized(lock){ if (state != NONE) { abort(); } } y llamarlo desde el callback onRoomLeave de GPresets (L293-297). Defensa en profundidad: guardar roomId al lanzar (launchImportThread) y en dropFurni/moveFurni/saveWired salir si !floorState.inRoom() || floorState.getRoomId() != roomIdInicial. Hacer lo mismo en WallItems.placePending (guardar roomId al empezar).
- Riesgo: Bajo. Solo actua cuando la sala cambia durante una importacion; RoomReady se envia al entrar, no durante una importacion normal. La peticion de plano (RoomCrop.applyPlan) recarga la sala ANTES de lanzar :ip (SimpleFlow.pasteWithFloor espera a inRoom), asi que no interfiere.
- Cómo verificar: Offline: prueba unitaria con un GPresetImporter y un FloorState simulados (subclase con Unsafe como en StackEdge) que pone state=ADD_UNSTACKABLES, dispara reset() de FloorState y comprueba que state vuelve a NONE y que dropFurni no envia mas paquetes (EspiaExtension registrando sendToServer). En hotel: lanzar una importacion grande y salir de la sala a mitad (queda pendiente porque aqui no hay hotel).

### IMP-03 · rendimiento · media
**Cada pieza de wired con "0" en variableIds espera 5 s completos y sin remedio**

- Evidencia: En saveWired el filtro de 'ids de variable que faltan' conserva solo los ids IGUALES a "0" que no sean clave de realVariableIdMap (javap de lambda$saveWired$6: ldc "0"; String.equals; ifeq -> descarta cualquier otro id). "0" nunca es clave del mapa (las claves son ids reales del preset como 87287), asi que needVariableIds queda ["0"], se envia WiredGetAllVariablesDiffs y se espera wiredVariableConfirmation.tryAcquire(5 s), que solo se libera si needVariableIds se vacia (L244-246): nunca ocurre, siempre vence el timeout. applyWiredConfig (PresetWiredBase) trata "0", "-*" y "~*" como literales, es decir la condicion esta invertida respecto a lo que hace el resto del codigo. Censo de los JSON del usuario: lista_compra_individual_8_demo.json tiene 102 de 402 piezas de wired con "0" (change_var_val 56, var_val_match 33, give_var 8, users_with_var 5) = 510 s (8,5 min) de espera pura; lista_compra_individual_8.json 116 de 482 = 580 s; Ocultar puestos LDC 38 piezas = 190 s; Compra LDC 20 = 100 s; Recompensas por tiempo 19 = 95 s. Para el demo eso es mas del 60 % del tiempo total estimado (colocar ~1,5 min + mover ~1,3 min + guardar wired a ~0,3 s/pieza ~2 min + 8,5 min de esperas). Ademas se manda la peticion de diffs 102 veces.
- Arreglo propuesto: Borrar el bloque L1179-1192 (el refresco real ya ocurre tras la ultima variable en L1218-1229 y las variables se guardan primero, L1364-1371), o como minimo cambiar el filtro a !s.equals("0") && !s.startsWith("-") && !s.startsWith("~") && !realVariableIdMap.containsKey(s) y no esperar mas de una vez por importacion (flag 'diffsPedidos').
- Riesgo: Bajo-medio. Hoy la espera nunca resuelve nada, asi que quitarla no cambia el mapeo de ids; el unico caso a vigilar es un preset cuyas variables no esten en su lista 'variables' (hoy tambien acaban en "0" tras el timeout).
- Cómo verificar: Offline: test de la funcion de filtro con las piezas reales de los 5 JSON (censo ya hecho: 102/402) antes y despues del cambio; comprobar que el numero de esperas pasa a 0. En hotel: cronometrar la fase 'Configurando wired' del demo (el diagnostico ya guarda tiempos por fase).

### IMP-04 · bug · media
**Los furnis en x==0 o y==0 nunca reciben baldosa magica: se colocan sin la altura del preset**

- Evidencia: findBestDropLocation(x,y,d,floor) genera los origenes candidatos con 'for (i = n; i > Math.max(n - n3, 0); --i)'. Cuando n - n3 < 0 el limite inferior efectivo es 1, asi que el origen 0 nunca se prueba y para n == 0 la lista queda VACIA. moveFurni (L1081) entonces no mueve la baldosa ni llama a SetCustomStackingHeight y el furni se mueve con altura natural, sin aviso. Probado con la clase real del jar (arnes StackEdge, sala 20x20 plana): objetivos 0,5 / 5,0 / 0,0 devuelven NULL para d=2, 4 y 8. Con la correccion propuesta (arnes StackFix) los 156 casos nulos de 1600 pasan a 0 y los 1444 resultados no nulos no cambian. Impacto real en los presets del usuario: Compra LDC 31 furnis en el borde 0 (20 con z>0, p.ej. wf_xtra_var_time_util a 1,2), Ocultar puestos LDC 45 (30 con z>0), Recompensas por tiempo 31 (24 con z>0), halloween_crematorio_v2 73 (40 con z>0, p.ej. hween_c25_oldwallwindows a 3,7) cuando la raiz de importacion es 0,0 (el caso de :ip 0,0 con plano).
- Arreglo propuesto: En StackTileUtils.findBestDropLocation(int,int,int,FloorState) usar limites inclusivos correctos: for (i = n; i >= Math.max(n - n3 + 1, 0); --i) y lo mismo para j (L50-51). Ademas en GPresetImporter.moveFurni contar los casos en que stackTileInfo == null y avisar una sola vez en el log ('N furnis sin baldosa magica: altura no ajustada').
- Riesgo: Bajo. Solo anade candidatos que antes se excluian por error; en la prueba ningun resultado que ya era valido cambia.
- Cómo verificar: Offline: arnes StackFix (ya incluido) sobre varios tamanos y formas de sala (con huecos, alturas distintas); anadir el caso 'solo origen 0 valido'. En hotel: importar Compra LDC en 0,0 y comparar la z de los furnis del borde.

### IMP-05 · bug · media
**La altura se trunca con (int)(d*100.0): 1 centesima de menos en muchos valores**

- Evidencia: moveFurni envia (int)(d * 100.0) + heightOffset * 100. Con doubles, 0.29*100 = 28.999999999999996 y (int) da 28. Arnes ZTrunc: 18 de 401 valores z entre 0.00 y 4.00 salen mal (0.29->28, 0.57->56, 0.58->57, 1.13->112 ... 2.26->225); en el camino de bindings (altitud entera /100.0 *100.0, L1452/1457) fallan 69 de 1001 enteros (29->28, 57->56, 113->112...). Con Math.round fallan 0. En los presets del usuario: lista_compra_individual_8_demo.json tiene 20 furnis con z mal truncada (4.6 x9 -> 459, 2.3 x4 -> 229, 2.32 x3, 4.35, 4.06, 4.27, 10.12, 8.87), el preset grande 31, Compra LDC (8.45) y halloween_crematorio_v2 (0.006 x10). El furni queda 0,01 mas bajo que lo especificado.
- Arreglo propuesto: Sustituir por (int)Math.round(d * 100.0) en L1084 (y, si se quiere, pasar la altitud de bindings como int centesimas sin pasar por double).
- Riesgo: Muy bajo: solo cambia el resultado en los valores que hoy salen 1 centesima mal.
- Cómo verificar: Offline: arnes ZTrunc (ya incluido) ampliado a 0..40,00 y a las z reales de los JSON. Con hotel: comparar la z mostrada de uno de los 9 furnis a 4.6.

### IMP-06 · bug · media
**Bindings con rotacion/altitud pero sin posicion mueven el furni a 'baldosa actual + raiz'**

- Evidencia: Para un binding con rotation o altitude pero location == null, el destino por defecto se calcula como hFloorItem.getTile().getX()/getY() + rootLocation (L1430-1431; confirmado en javap setupWired pc 975-1009: getTile().getX() + rootLocation.getX()). getTile() ya es una coordenada absoluta de sala (pila de apilables o posicion final del no apilable), asi que se suma la raiz dos veces. Con raiz 10,10 y pila en 3,3 el furni se manda a 13,13: si esa baldosa no es valida o esta ocupada el movimiento se rechaza y la rotacion/altitud que el wired 'match to snapshot' debia capturar no se aplica, y si es valida el furni aterriza en un sitio ajeno antes de volver. Con location != null el calculo es correcto (L1435-1436). Los presets actuales del usuario no tienen bindings, por eso no se nota todavia.
- Arreglo propuesto: Cuando binding.getLocation() == null usar el destino = baldosa actual SIN sumar raiz (n5 = tile.x; n6 = tile.y) y mantener la suma de raiz solo cuando el binding trae location.
- Riesgo: Bajo: solo afecta a bindings parciales (solo rotacion y/o altitud), que hoy se comportan mal.
- Cómo verificar: Offline: test con un PresetConfig que tenga un binding {rotation:2} y raiz 10,10 sobre una espia de sendToServer, comprobando que MoveObject va a la baldosa actual. En hotel: wired match_to_sshot con solo rotacion.

### IMP-07 · bug · media
**Los furnis de pared se colocan en su posicion original absoluta: no se desplazan con la raiz de importacion**

- Evidencia: collectForExport guarda wallPosition tal como la ve el servidor (coordenadas absolutas de la sala original) y placePending (L281, L292, L303) la envia sin traducir. Los furnis de suelo si se desplazan (root - origen) y WiredCoordinateTranslator ajusta areas y colocar-furni, pero las paredes no. Datos reales: halloween_crematorio_v2.json tiene exportOrigin (1,7), furnis relativos x 0..9 / y 0..23 y 110 paredes ':w=1,6 l=16,-373 r', ':w=0,7 ...' (absolutas, w es una baldosa del rango del origen); al importarlo con raiz 0,0 el suelo cae en 0..9/0..23 pero las ventanas quedan a (+1,+7) de donde deberian. Con exportOrigin 0,0 y raiz 0,0 (el caso del preset grande y del flujo ':plano' + ':ip 0,0') no se nota, por eso pasa desapercibido; con ':ip auto', clic en otra posicion o presets exportados de un area, las paredes quedan descolocadas o el servidor las rechaza.
- Arreglo propuesto: Guardar en WallItems al lanzar la importacion el desplazamiento dx = root.x - origen.x, dy = root.y - origen.y (origen = activeOrigin si esta EXPORTED/ASSUMED; si es UNKNOWN no tocar y avisar como hace WiredCoordinateTranslator) y en placePending reescribir ':w=X,Y' sumando (dx,dy) con la regex W ya existente. Con dx=dy=0 el comportamiento es identico al actual.
- Riesgo: Medio-bajo: la logica solo cambia cuando dx o dy son distintos de 0. Hay que confirmar en hotel que w=X,Y son baldosas (los datos del usuario lo sugieren: w cae dentro del rango x/y de los furnis).
- Cómo verificar: Offline: test de la funcion de reescritura con cadenas reales (':w=1,6 l=16,-373 r' con dx=-1, dy=-7 -> ':w=0,-1 ...') y con dx=dy=0 (sin cambios). En hotel: importar halloween_crematorio_v2 en una raiz distinta y comprobar las ventanas.

### IMP-08 · bug · media
**Elegir otro preset mientras se importa cambia las paredes que se colocaran al final (y el nombre del diagnostico)**

- Evidencia: El combo de inicio (L207) y el doble clic de la lista (L329) llaman a selectPreset/selectPresetByName sin comprobar importer.getState(). El constructor PresetConfig(JSONObject) tiene el efecto lateral WallItems.setPending(wallFurni) (L67), de modo que cargar otro preset sustituye la lista global de paredes pendientes. Al terminar la importacion de A, sendVisualChatInfo('... importado correctamente') -> WallItems.onImportFinished -> placePending copia this.pending: coloca las paredes de B. Tambien cambia importer.presetName (usado en el nombre del diagnostico) y setPresetConfig. La recarga automatica por cambio de archivo y 'Recargar' si comprueban state == NONE (L732, L822); la seleccion no.
- Arreglo propuesto: Bloquear selectPreset mientras importer.getState() != NONE (mensaje en el log), y guardar en prepare() la lista de paredes de workingPresetConfig para llamar a WallItems.setPending(esaLista) justo antes de finishReport. Idealmente quitar el efecto lateral del constructor y hacer que sea el importador quien publique las paredes.
- Riesgo: Bajo. El unico cambio visible es que no se puede cambiar de preset durante una importacion.
- Cómo verificar: Offline: test unitario que carga el preset A, llama a prepare(), carga B, y comprueba que las paredes que se enviarian son las de A. En hotel: cambiar de preset a mitad de una importacion con paredes.

### IMP-09 · robustez · media
**Una parada de red o del hotel de >= 4 s hace que se rechace el resto de un tipo de furni aunque se hubiera colocado bien**

- Evidencia: typeIsRejected cuenta como 'perdida' toda gota cuya marca de envio tenga mas de 3500 + 2*feedback ms (~3,9 s) sin que llegue un ObjectAdd de ese tipo; al llegar a 2 perdidas rechaza el tipo para toda la importacion y se omite el resto. Mi hipotesis inicial (que 2 perdidas aisladas rechazan el tipo) es FALSA: la cola FIFO absorbe las perdidas intercaladas con exitos (LostSim: 300 gotas con 2 fallos -> 298 colocadas, 0 omitidas). Lo que si ocurre: si los ObjectAdd se retrasan >= ~4,2 s (parada de red, lag del hotel) las gotas en vuelo envejecen, se cuentan 2 y el tipo queda rechazado aunque los ObjectAdd lleguen despues. Replica fiel del algoritmo (LostSim2, 300 furnis del mismo tipo, parada solo de los acks): parada de 3,5 s -> 0 omitidos; 4,2 s -> 209 de 300 omitidos; 6 s -> 209 omitidos. El resumen final dira entonces 'Habbo rechazo X (limite o no permitido)', que es engañoso.
- Arreglo propuesto: Contar una gota como perdida solo si despues de enviarla llego algun ObjectAdd de cualquier tipo (lastDropActivity > marca): una parada global no es un rechazo. Cambio de una linea dentro del while: if (lastAckAny <= linkedList.peekFirst()) break; (guardar lastAckAny en onObjectAdd). Opcional: subir el umbral de rechazo a 3 perdidas.
- Riesgo: Bajo: un rechazo real del hotel (limite por tipo) sigue detectandose porque llegan acks de otros tipos; la replica LostSim3 conserva 136 omitidos en ese caso y baja de 104 a 0 en el caso de parada.
- Cómo verificar: Offline: LostSim3 (ya incluido) cubre ambos escenarios; falta contrastarlo con la clase real mediante un test con reloj inyectable. En hotel: provocar un corte de red de 5 s a mitad de la colocacion.

### IMP-10 · ux · media
**Si faltan furnis, los wireds se guardan con la seleccion recortada y no se avisa de cual**

- Evidencia: applyWiredConfig(ext, realFurniIdMap, ...) hace items = items.filter(map::containsKey) y lo mismo con items2: los furnis seleccionados que no se colocaron desaparecen de la configuracion y el wired se guarda 'con exito'. Arnes DropItems con la clase real: items del preset [11,12,13,14] y 13/14 no colocados -> items guardados [1011,1012], sin ningun aviso. El informe final solo cuenta wireds omitidos por falta de su propia caja (skippedWireds) y furnis omitidos por tipo; no dice que un wired concreto (p.ej. un 'mover furni' con 5 destinos) quedo con 2. El usuario recibe 'importado con omisiones' pero no sabe que logica esta rota; solo 'Solo wired' informa de enlaces (linkedFurni/linkTotal).
- Arreglo propuesto: En saveWired, antes de applyWiredConfig, contar referencias perdidas: n = items.stream().filter(id -> !realFurniIdMap.containsKey(id)).count() + lo mismo para secondItems; si n > 0 guardar '#id clase: perdio n de m furnis' en una lista y volcarla al diagnostico (seccion 'wireds con seleccion incompleta') y anadir al mensaje final solo un contador ('N wireds con menos furnis seleccionados'), sin mas susurros.
- Riesgo: Muy bajo: solo cuenta y registra; no cambia lo que se envia.
- Cómo verificar: Offline: arnes DropItems (ya incluido) mas un test que ejecute saveWired con una espia y compruebe el contador; revisar el diagnostico generado con un preset al que se le quiten furnis a mano.

### IMP-11 · ux · media
**Cancelar o fallar a mitad deja la baldosa magica movida, los furnis apilados en un punto y furnis aparcados**

- Evidencia: abort() y los caminos de error solo ponen state = NONE y muestran un mensaje. La restauracion de las baldosas magicas a su sitio (L1292-1296) y el 'volver a su sitio' de los furnis de bindings (L1456-1459) solo se ejecutan en el camino feliz. Si se cancela en ADD_FURNITURE/SETUP_WIRED/MOVE_FURNITURE, la baldosa elegida queda en la posicion temporal (stackTileLocation) y los furnis ya colocados siguen apilados en esa baldosa; si se cancela dentro de attemptSetState/bindings, un furni puede quedar aparcado en reservedSpace o en una posicion de binding. El usuario ve 'Importacion cancelada' sin indicacion de como deshacer (existe 'Quitar lo ultimo que pegue' de PasteUndo pero no se menciona).
- Arreglo propuesto: Marcar stackTilesMoved = true al mover la baldosa en dropAllOtherFurni; en un finally de runImportSafely (mismo hilo importador, sin la lock) enviar los MoveObject de allAvailableStackTiles a su sitio original si la importacion no termino normalmente, y anadir al mensaje de cancelacion/diagnostico una linea con la recomendacion de usar 'Quitar lo ultimo que pegue'. No hace falta ningun susurro nuevo.
- Riesgo: Bajo-medio: manda 1-3 paquetes extra tras cancelar; si el usuario cancela por flood del hotel, conviene esperar ~300 ms antes.
- Cómo verificar: Offline: test con espia de sendToServer que fuerza abort() en cada fase y comprueba que se emiten los MoveObject de restauracion una sola vez. En hotel: cancelar en la fase de mover.

### IMP-12 · robustez · baja
**Pulsar 'Cargar inventario' durante una importacion hace que los tipos aun no usados se den por 'no esta en tu inventario'**

- Evidencia: inventoryCache se rellena de forma perezosa en dropFurni la primera vez que aparece cada tipo. El boton Cargar inventario (loadInventoryClick) no esta bloqueado durante la importacion y Inventory.requestInventory() llama a clear() (vacia floorItemsByType). Los tipos que aun no se habian usado devuelven lista vacia y noStock() los omite ('no esta en tu inventario'), aunque los tuviera. Es plausible que el usuario pulse el boton cuando cree que falta algo.
- Arreglo propuesto: Tomar la foto del inventario de todos los tipos necesarios en prepare() (justo despues de AvailabilityChecker) y deshabilitar el boton mientras importer.getState() != NONE.
- Riesgo: Muy bajo.
- Cómo verificar: Offline: test que llama a requestInventory() en medio de dropFurni con un Inventory simulado y comprueba que no hay noStock.

### IMP-13 · bug · baja
**El comando de parada de emergencia :abort distingue mayusculas y espacios, el resto de comandos no**

- Evidencia: onChat calcula string2 = string.trim().toLowerCase() y lo usa para :rp, :acomodar, :ip y :diag, pero el aborto compara el texto crudo: string.equals(":abort") || string.equals(":a") (L379; javap: aload 5 = readString() crudo, no el slot 6 normalizado). ':Abort', ':abort ' o ':ABORT' no abortan Y ADEMAS no se bloquean, asi que viajan como charla normal a la sala.
- Arreglo propuesto: Usar string2 (y el mismo isCommand) para :abort/:a.
- Riesgo: Muy bajo.
- Cómo verificar: Offline: test de onChat con las 4 variantes. Ya confirmado por javap.

### IMP-14 · bug · baja
**lowestFloorPoint mira una region equivocada (una fila y columna de mas, y sin el minimo del preset)**

- Evidencia: lowestFloorPoint(floor, root, root+dims) recorre 'i <= hPoint2.x', con dims = (max-min+1) de presetDimensions, es decir dims+1 baldosas, y parte de root sin sumar el minimo del preset (los furnis caen en root+min..root+max). Arnes LowFloor: preset 3x3, suelo con altura 5 salvo una columna a 0 justo fuera del preset: lowestFloorPoint devuelve 0 en vez de 5 por la columna ajena. En importacion solo se usa con ':ip x,y forzar' (L678, y L693 cuando activePlan es null); el planificador usa su propia base (planner baseHeight) y es correcto. El exportador usa la misma funcion con (n+n3, n2+n4) inclusivo (GPresetExporter L375), de modo que exportOrigin.floor y las z relativas pueden restarse de un suelo mas bajo que el del rectangulo elegido si la fila/columna contigua es mas baja (mismo origen del error de altura en el aviso de 'piso disparejo').
- Arreglo propuesto: Calcular la base como el minimo de heightFromChar sobre las baldosas realmente cubiertas (x+root, y+root de cada furni, o el rectangulo exacto [root+min, root+max]) y en el exportador usar < en lugar de <=.
- Riesgo: Medio-bajo para el exportador (cambia exportOrigin.floor de presets nuevos en bordes con desnivel); bajo para el import forzado.
- Cómo verificar: Offline: arnes LowFloor (incluido) con varias formas; comparar contra planner.baseHeight en 100 salas aleatorias de PlanProp para asegurar que coinciden en el caso sin forzar.

### IMP-15 · robustez · baja · sin verificar
**El ping no esta acotado: una medicion absurda dispara todas las esperas del importador**

- Evidencia: LatencyPingResponse calcula ping = (now - latestPingTimestamp)/2 con latestPingTimestamp inicializado en onStartConnection y actualizado solo por LatencyPingRequest que la extension ve. Si llega una respuesta cuyo request no se vio (extension conectada a mitad de sesion), ping puede valer minutos/2. pingVariation se limita a 10 pero ping no. getSafeFeedbackTimeout() = (ping+var+10)*3 alimenta Utils.sleep(max(timeout,60)) en attemptSetState, el umbral de gota perdida (L991) y el reposo final (L1521), con lo que una importacion lanzada en esa ventana puede dormir minutos por furni con estado. No verificado en hotel: depende de la frecuencia con que el cliente envia LatencyPingRequest.
- Arreglo propuesto: Limitar ping a un maximo razonable (p.ej. 2000 ms) y descartar muestras si now - latestPingTimestamp > 10 s; poner latestPingTimestamp = -1 tras usarlo.
- Riesgo: Muy bajo.
- Cómo verificar: Offline: test unitario de la formula con timestamps antiguos.

### IMP-16 · robustez · baja · sin verificar
**Si no hay sitio para la baldosa magica se usa (0,0) en silencio y el fallo se presenta como 'Habbo rechazo'**

- Evidencia: findStackTileLocation() devuelve new HPoint(0,0) cuando ni la busqueda con plan (true) ni la libre (false) encuentran hueco (p.ej. baldosa 8x8 o 6x6 en una sala pequena casi cubierta por el preset). Entonces dropAllOtherFurni mueve la baldosa a (0,0) y deja caer todos los apilables alli; si (0,0) no es suelo, todos fallan, las gotas se envejecen y el diagnostico dice 'Habbo rechazo ... limite o no permitido'. Ademas el limite 64 esta fijo (L1627-1628) en lugar de usar el tamano real del plano y, con la baldosa 1x2 (dimension -1) los bucles de comprobacion de altura (k < -1) no se ejecutan, de modo que no se verifica que el hueco sea de una sola altura.
- Arreglo propuesto: Si no hay hueco, usar la posicion actual de la baldosa elegida (siempre valida) o cancelar con un mensaje explicito ('no hay hueco para la baldosa magica de tamano N'); usar floorState.getFloorplanWidth/Height y tratar dimension -1 como 1x2 (2x1 segun rotacion) al comprobar alturas.
- Riesgo: Bajo.
- Cómo verificar: Offline: arnes con FloorState simulado (sala 10x10 y baldosa 8x8 con plan que ocupa todo) comprobando que ya no devuelve (0,0) ni cuando dimension es -1. No verificado en hotel.

### IMP-17 · rendimiento · baja · sin verificar
**Movimientos redundantes de baldosa magica, estados sin comparar y niveles de velocidad casi cosmeticos**

- Evidencia: (a) Por cada furni apilable moveFurni manda MoveObject de la baldosa (65 ms), SetCustomStackingHeight (45 ms) y MoveObject del furni (65 ms) = ~175 ms, aunque la baldosa ya cubra el destino. Con el orden del fichero, simulando baldosa 2x2: en el demo 352 de 450 movimientos de baldosa y 91 fijaciones de altura se podrian omitir (~27 s de ~79 s), en el preset grande 1519 de 2244 y 659 (~2,2 min). (b) En la rama con permiso de wired, attemptSetState envia WiredSetObjectVariableValue y duerme max(feedback,60) ms (~195) para CADA furni con estado aunque ya tenga ese estado, y luego hace un moveFurni final a su propia baldosa (2 paquetes, ~130 ms) que no cambia nada util; la rama sin permiso si compara. En el demo 27 de 46 furnis con estado valen '0' (el valor al nacer), en el grande 331 de 897 (~108 s). (c) SpeedSetting solo cambia sleep extra de 22/5/0 ms sobre bases fijas de 150-230 ms: 'Rapida' es ~3 % mas rapida que 'Normal' en suelo y wired; solo cambia de verdad el retardo de paredes (260/180/140). Nada de esto esta comprobado contra los limites de flood reales del hotel.
- Arreglo propuesto: Recordar la posicion/rotacion/altura actual de la baldosa magica (campo en el hilo importador) y omitir MoveObject si cubre el destino con la misma altura de suelo, y SetCustomStackingHeight si la altura es la misma que la ultima fijada; saltar attemptSetState cuando StateExtractor.stateFromItem(item) ya coincide y retirar el moveFurni final de la rama con permiso de wired; hacer que 'Rapida' escale las esperas base (p.ej. x0,6) en lugar de sumar 0 ms.
- Riesgo: Medio: depende de que la altura custom de la baldosa se conserve al moverla y de que el estado local este al dia; dejar un interruptor de configuracion y activarlo solo tras probar.
- Cómo verificar: Offline: simulador del recorrido de moveFurniture sobre los JSON (como el censo hecho) para contar paquetes antes/despues. Hotel: importar el demo y comparar tiempos de fase del diagnostico y que ningun furni quede en z distinta.

### IMP-18 · rendimiento · baja · sin verificar
**El hueco de 300 ms por tipo se aplica a piezas consecutivas del mismo tipo y los fallos en cascada no cortan**

- Evidencia: saveWired obliga a esperar hasta 300 ms desde el ultimo guardado del MISMO tipo (latestXSave, L1159-1176) pero setupWired guarda todos los condiciones seguidos, luego todos los efectos, etc., con lo que casi cada pieza paga los 300 ms completos (demo: 402 piezas ~2 min; efectos son 215). Si se intercalaran tipos (round-robin respetando que las variables van primero) los huecos de cada tipo transcurririan mientras se guardan los otros: cota inferior ~215 x 0,3 s = 65 s en lugar de ~120 s. Por otro lado, si el hotel no responde a WiredSaveSuccess (sin permiso de wired, p.ej.), tras 4 fallos seguidos cada pieza sigue costando 2 s: 400 piezas = ~13 min sin aviso hasta el final; no se corta la fase. Tampoco se comprueba que el servidor limite por tipo (suposicion basada en que el codigo mantiene un reloj por tipo).
- Arreglo propuesto: Ordenar arrayList intercalando por tipo tras las variables y cortar la fase de wired tras N (p.ej. 10) fallos consecutivos con un mensaje claro 'no se confirma ningun guardado: revisa permiso de wired'.
- Riesgo: Medio-bajo (el orden de guardado no afecta al resultado, pero el limite del servidor es una suposicion; probar con el demo).
- Cómo verificar: Offline: simular la planificacion con el modelo de 300 ms por tipo sobre los 5 JSON. Hotel: tiempos de fase 'Configurando wired' antes/despues.

### IMP-19 · robustez · baja · sin verificar
**Estado compartido sin sincronizar entre el hilo de paquetes y el importador, y contador de variables mal llevado**

- Evidencia: realVariableIdMap (HashMap) y needVariableIds (ArrayList) se modifican en onWiredAllVariables (hilo de paquetes, sin lock) mientras saveWired los recorre (stream().noneMatch sobre keySet() sin lock, L1182): una respuesta tardia puede lanzar ConcurrentModificationException y abortar la importacion ('ERROR ... ConcurrentModificationException'). La ventana es pequena (la respuesta suele llegar cuando el hilo esta esperando). Ademas variablesProcessed se incrementa antes de comprobar la confirmacion (L1219) y otra vez en el reintento recursivo (L1236) y no se incrementa en la salida por fallo (L1149-1155): el refresco final tras la ultima variable puede dispararse antes de tiempo o, si la ultima variable falla, depender solo de las siguientes. Utils.extraSleepTime es un static int no volatile que escribe el hilo FX y lee el importador.
- Arreglo propuesto: Usar ConcurrentHashMap/CopyOnWriteArrayList o tomar la lock en onWiredAllVariables; contar variablesProcessed solo en exito o en fallo definitivo (una vez por pieza) y marcar extraSleepTime como volatile.
- Riesgo: Muy bajo.
- Cómo verificar: Offline: test de estres con dos hilos (uno llamando a onWiredAllVariables simulado, otro a saveWired) para reproducir la CME antes del cambio.

### IMP-20 · robustez · baja
**'Disponibilidad' falla con un className desconocido y 'Solo furnis' exige permiso de wired**

- Evidencia: availabilityBtnClick construye FurniDropInfo(-1,-1, furniDataTools.getFloorTypeId(className), ...): getFloorTypeId devuelve Integer null para un className inexistente y el auto-unboxing lanza NullPointerException en el hilo de JavaFX (el importador si pasa antes por UnknownFurniFilter). Para presets escritos a mano un typo en className es probable, y el boton no hace nada visible. Por otro lado isReady() (L313) exige canModifyWired() siempre que se exporte wired, aunque la importacion sea 'Solo furnis' y no toque wired, y notReadyReason lo explica como 'no tienes permiso para editar wired aqui'.
- Arreglo propuesto: Llamar a UnknownFurniFilter.dropUnknown (o filtrar nulls) en availabilityBtnClick y mostrar el aviso en el log; en isReady/notReadyReason ignorar el permiso de wired cuando defaultContent == FURNI.
- Riesgo: Muy bajo.
- Cómo verificar: Offline: test con un PresetConfig con className 'xx_no_existe' llamando a la logica de disponibilidad con FurniDataTools simulado.

### IMP-21 · limpieza · baja
**Pulido: tooltips nuevos cada 0,7-0,9 s, '-1x-1' en el diagnostico, codigo muerto y la baldosa 1x2 tratada como 2x2**

- Evidencia: (1) Las timelines crean un Tooltip nuevo en cada tick (700 ms y 900 ms) en lugar de reutilizar uno y llamar a setText: genera basura constante y, mientras el raton esta encima, el tooltip se sustituye bajo el cursor (el efecto visual exacto no se ha podido comprobar sin ventana). (2) StackTileSetting.toString() imprime '%dx%d' con la dimension, asi que la opcion 1x2 sale como '-1x-1' en describeEnvironment (GPresets 855). (3) Sin uso: WiredSaveInfo (clase entera), campos dropTotal/dropDone, constantes DROP_LOST_AFTER_MS/LOST_BEFORE_REJECT/FINAL_WAIT_MAX_MS duplicadas como literales 3500/2/20000. (4) PlanTileGuard.dimension() convierte -1 en 2 (comprueba y repone un 2x2 para una baldosa 1x2) y solo repone una baldosa por clase aunque se hayan recogido varias (classes se deduplica en restore).
- Arreglo propuesto: Reutilizar un Tooltip y setText solo si cambia el estado; toString -> '1x2' para Medium; borrar lo muerto o usar las constantes; en PlanTileGuard tratar -1 como 1x2/2x1 y reponer tantas como se recogieron.
- Riesgo: Muy bajo.
- Cómo verificar: Offline: compilar y renderizar con render.sh; test de StackTileSetting.toString.

### IMP-22 · funcion · baja · sin verificar
**Ideas de funcion para la importacion sin anadir comandos ni susurros**

- Evidencia: (1) ETA en la barra de progreso: progress() mapea fases a rangos fijos (la fase de mover ocupa el ultimo 8 % pero consume ~20-30 % del tiempo y la de wired tarda mucho mas de lo que sugiere su rango), asi que la barra salta; WallItems.wallProgress ya calcula 'faltan X min' y se puede copiar (solo cambia la etiqueta de la UI, no el chat). (2) Completar lo que falta: al repetir la importacion con el mismo preset y raiz, reutilizar detectExistingRoot/findRoomFurni (hoy solo en modo Solo wired) para saltar los furnis que ya estan y colocar solo los que faltan, en vez de duplicarlo todo; encaja con el boton Quitar existente. (3) Un segundo intento automatico, una sola vez, de las gotas que siguen en expectFurniDrops tras el reposo final si el tipo no fue rechazado (hoy las perdidas por lag no se reenvian). (4) Llevar el avatar a la baldosa libre elegida (reservedSpace) al empezar: hoy el avatar puede estar dentro de la zona y bloquea el MoveObject de furnis solidos en su baldosa (solo se reintenta una vez al final). Ninguna esta probada en hotel.
- Arreglo propuesto: Implementar por separado y tras corregir IMP-02/IMP-03/IMP-11; (1) es solo UI y de riesgo nulo; (2) y (3) necesitan prueba en hotel por el riesgo de duplicar si un ObjectAdd llega tarde.
- Riesgo: (1) nulo; (2)(3) medio por duplicados; (4) bajo.
- Cómo verificar: (1) render de la UI y cronometro simulado; (2) test con FloorState simulado y preset parcialmente presente; (3)(4) solo en hotel.

## INFRA

19 hallazgos, la mayoria reproducidos sin hotel con pruebas en .../scratchpad/tr/audit/infra/bench/ (usan una copia del jar en audit/infra/jar; no se toco /home/user/wired ni el proyecto). Rutas relativas a .../scratchpad/src_all/extension/; la libreria G-Earth decompilada esta en .../scratchpad/tr/audit/infra/dec/. Lo mas importante: (1) cualquier excepcion en un interceptor (p.ej. un paquete de Habbo con otro formato leido sin try/catch) mata la conexion de la extension con G-Earth y en el caso mas comun cierra la ventana entera (System.exit), y GPresets no protege ningun listener; (2) un cache.json truncado impide arrancar la extension; (3) el radio '1x1' de Stack tile esta fuera de su ToggleGroup (no hace nada y deja dos radios marcados); (4) toques a la UI desde el hilo de G-Earth (reproducido: NPE en el layout de JavaFX); (5) el cache de iconos de la vista previa recuerda para siempre los fallos; (6) un preset guardado con BOM/ANSI falla con mensajes criptico; (7) el sniper duplica hilos al reiniciar. El resto son pulidos de UX/visual. La Vista previa, el renderer (20.000 furnis en 2,4 s, 68 MB) y el Logger no tienen problemas de rendimiento medibles. Varias 'excepciones' que CFR muestra (SequencedCollection de Java 21, variable sombreada en VistaPreviaVentana, n8 sin inicializar en SalaRenderer.leer) son artefactos: el bytecode es version 52 y correcto.

### infra-01 · robustez · alta
**Una excepcion en cualquier interceptor mata la conexion de la extension (o cierra la ventana) y GPresets no protege ninguno**

- Evidencia: Extension.run() procesa todo en un unico hilo y solo captura IOException|ArrayIndexOutOfBoundsException alrededor del while; ExtensionBase.modifyMessage llama listener.act(msg) sin try/catch. Prueba bench/gearth/extensions/ListenerThrow.java: 6 listeners, el 3.o lanza -> modifyMessage lanza y los demas no corren (orden 4,5,3,2: ademas el orden es arbitrario porque correctListeners es un HashSet). bench/ReadPast.java: leer mas alla del final de un HPacket lanza ArrayIndexOutOfBoundsException. Consecuencia: con AIOOBE el bucle sale, run() retorna y ExtensionFormLauncher llama System.exit(0) (la ventana desaparece sin aviso); con NPE/IllegalState el hilo muere sin System.exit y queda una ventana huerfana que ya no recibe nada. Interceptores sin try/catch: game/Inventory.loadItems (FurniList) y varios de FloorState/RoomPermissions; GPresets no envuelve ninguno. Si Habbo cambia un paquete, la extension desaparece sin dejar rastro en Diagnosticos.
- Arreglo propuesto: En GPresets sobrescribir los 3 overloads de intercept(...) y envolver cada listener: private ExtensionBase.MessageListener safe(ExtensionBase.MessageListener l){ return m -> { try { l.act(m); } catch (Throwable t) { diagnostics.error("interceptor", t); /* limitar a 1 entrada por clase y 10 s */ } }; } con super.intercept(d, safe(l)) (FloorState/Inventory/RoomPermissions reciben 'this' y llaman a intercept virtual, asi que quedan cubiertos). Envolver tambien initExtension/onStartConnection/onEndConnection en try/catch(Throwable) con log rojo en el Registro.
- Riesgo: Bajo: solo cambia el comportamiento cuando un listener lanza (hoy: la extension muere). Un listener que falle a medias puede dejar su paquete sin procesar, pero es mejor que perder la extension.
- Cómo verificar: Hecho: bench/gearth/extensions/ListenerSafe.java (prototipo del override) imprime 'modifyMessage termino normal; listeners ejecutados: 6 de 6' con un AIOOBE en el 3.o. Offline adicional: javap -p sobre GPresets.class debe listar los 3 intercept; compilar con javac --release 8; ejecutar el harness con un GPresets sub-clase (patron de bench/SniperDouble.java).

### infra-02 · robustez · media
**Un cache.json truncado/vacio hace fallar el arranque de la extension; las escrituras de Cacher no son atomicas ni sincronizadas**

- Evidencia: loadCache() llama Cacher.getCacheContents() sin try/catch desde initialize() (FXML). Cacher solo captura IOException; new JSONObject(texto) lanza JSONException con archivo truncado o vacio. Prueba bench/CacheCorrupt.java: archivo '{"ratelimit":2' -> org.json.JSONException: Expected a ',' or '}'; archivo vacio -> JSONException; y Cacher.put tambien lanza. updateCache hace new FileWriter (trunca) + write: si el JVM muere (System.exit de infra-01, G-Earth cerrado, apagon) justo ahi queda vacio. Ademas el directorio de cache es <G-Earth>/Cache (compartido con G-Earth y otras extensiones) y Cacher.put es leer-modificar-escribir sin lock, llamado desde el hilo FX, el hilo de la extension (createPostConfig, linea 659) y el importador. Si ocurre, la ventana ni aparece (Exception in Application start method) hasta borrar Cache/cache.json a mano.
- Arreglo propuesto: Envolver loadCache() en try/catch (defaults + diagnostics.error + log naranja). Centralizar las escrituras en un metodo synchronized guardarAjuste(k,v) con try/catch; idealmente guardar los ajustes propios en Memoria/ajustes.json con escritura atomica (archivo .tmp + Files.move ATOMIC_MOVE) y no tocar el cache de G-Earth.
- Riesgo: Bajo. Si se migra a ajustes.json hay que leer una vez los valores antiguos de cache.json (con try/catch) para no perder los ajustes del usuario.
- Cómo verificar: Repetir bench/CacheCorrupt con el arreglo (debe arrancar con defaults); prueba unitaria de la funcion de guardado atomico; javap de initialize()->loadCache para ver el try/catch.

### infra-03 · bug · media
**El radio '1x1' de Stack tile esta fuera del ToggleGroup: no hace nada y deja dos radios marcados**

- Evidencia: FXMLLoader resuelve $stacktile_tgl en orden de documento: al llegar al 1x1 la variable aun no existe, asi que su toggleGroup queda null. Prueba bench/Group1x1.java con el controlador real: RadioButton 1x1 -> grupo=NULL; el controlador ve 4 toggles (2x2,4x4,6x6,8x8); tras pulsar 1x1: grupo.getSelectedToggle()=2x2 y a la vez 1x1 seleccionado=true y 2x2 seleccionado=true. El listener de la linea 224 nunca se dispara, asi que StackTileSetting.Small no se puede elegir. Ademas el fallback de loadCache (get(2)) apunta a '6x6' en vez de '4x4'.
- Arreglo propuesto: En el FXML declarar el grupo antes de usarlo: <fx:define><ToggleGroup fx:id="stacktile_tgl"/></fx:define> al inicio del contenedor (o mover <toggleGroup> al radio 1x1) y que todos usen toggleGroup="$stacktile_tgl". En loadCache cambiar el fallback por el toggle con texto '2x2'.
- Riesgo: Bajo, pero 1x1 pasa a ser elegible (tile_stackmagic): confirmar con el planificador/importador que acepta ese stack tile.
- Cómo verificar: Repetir bench/Group1x1 (debe mostrar 5 toggles y que al pulsar 1x1 se desmarca 2x2); render headless de la pestana Inicio.

### infra-04 · robustez · media
**La UI se modifica desde el hilo de G-Earth (no FX): showSelfDonateBtn y el cableado de initExtension**

- Evidencia: initExtension() y los onConnect(...) se ejecutan en el hilo 'Thread-N' de Extension.run, no en el FX. showSelfDonateBtn hace setVisible/setManaged de 4 nodos sin Platform.runLater. Reproducido en bench/OffThread.java (misma operacion sobre nodos mostrados en un VBox): en 1 de 3 ejecuciones el hilo FX lanzo NPE en Parent.getManagedChildren ('unmodifiableManagedChildren is null') durante el layout. En la practica el momento de choque es la conexion a Habbo (se llama justo cuando la ventana esta maquetando), la probabilidad por evento es baja pero el efecto es un layout roto/pulso con excepcion hasta reiniciar.
- Arreglo propuesto: showSelfDonateBtn: envolver el cuerpo en Platform.runLater. Mover a initialize() el cableado de UI que hoy esta en initExtension (presetListView.setOnMouseClicked y el listener de item_src_tgl; el codigo ya tiene guardas importer != null) o envolverlo en runLater.
- Riesgo: Bajo: solo cambia el hilo de ejecucion. Cuidado con updatePostConfig() (usa importer, que sigue creandose en initExtension).
- Cómo verificar: Revisar con javap que showSelfDonateBtn llama a Platform.runLater; repetir bench/OffThread cambiando el hilo por Platform.runLater (0 fallos en 20 ejecuciones).

### infra-05 · bug · media
**Vista previa: un icono que no se pudo cargar queda marcado como 'sin icono' hasta reiniciar (cache estatico de fallos)**

- Evidencia: ICONOS es un ConcurrentHashMap estatico y se guarda SIN_IMAGEN tambien cuando el fallo fue provisional: casilla 'Bajar iconos que falten' apagada, tope de 150 descargas por dibujo (MAX_DESCARGAS) o 25 s (TIEMPO_DESCARGAS_MS), timeout/5xx. Prueba bench/IconCache.java: render 1 sin icono (bajar=off) -> furnisSinIcono=1; luego se copia bc_block_1_icon.png a Sprites/; render 2 -> furnisSinIcono=1 (deberia ser 0). Efecto: con un preset nuevo de >150 clases, las que pasan del tope se dibujan como cajas para siempre; volver a marcar 'Bajar' o redibujar no ayuda; ni siquiera un icono copiado a mano se recoge.
- Arreglo propuesto: Cachear SIN_IMAGEN solo si el fallo es definitivo (HTTP 404 o la clase no esta en el indice). En los demas casos no guardar nada o guardar con caducidad (p.ej. reintentar tras 5 min). Alternativa simple: borrar las entradas SIN_IMAGEN de ICONOS al empezar cada render() si descargar==true.
- Riesgo: Bajo (solo afecta a la ventana de vista previa). Mas reintentos de red solo cuando el usuario redibuja.
- Cómo verificar: Repetir bench/IconCache (esperado 0 en el render 2); bench/RenderPerf para confirmar que el tiempo no cambia.

### infra-06 · bug · media
**Un preset guardado con BOM o en ANSI no carga y el error es criptico (la vista previa si tolera el BOM)**

- Evidencia: El boton 'Editar' abre el .json en el editor por defecto (Bloc de notas): versiones antiguas guardan UTF-8 con BOM o ANSI. Prueba bench/Bom.java con el mismo codigo de loadPresetOrThrow: BOM -> JSONException 'A JSONObject text must begin with '{' at 1 [character 2 line 1]'; ANSI con una tilde -> MalformedInputException 'Input length = 1'. El usuario ve eso en el Registro/barra de estado ("No pude cargar la copia ...") sin saber que es un tema de codificacion. SalaRenderer.leer ya hace substring(1) si empieza por ﻿, asi que la vista previa abre archivos que la importacion rechaza.
- Arreglo propuesto: Funcion unica PresetConfigUtils.readText(File): leer bytes, quitar EF BB BF, decodificar con CharsetDecoder REPORT; ante MalformedInputException lanzar IOException('el archivo no esta en UTF-8: guardalo como UTF-8 sin BOM'). Usarla en loadPresetOrThrow, RoomCrop.applySelectedPresetPlan y SalaRenderer.leer. NO cambiar el contenido (la doble codificacion Ã³ se mantiene tal cual).
- Riesgo: Muy bajo para archivos validos (solo se descarta el BOM). Para ANSI cambia un error criptico por uno claro.
- Cómo verificar: Repetir bench/Bom con la funcion nueva (BOM carga; ANSI da el mensaje claro); comparar el JSON cargado de un preset UTF-8 normal antes/despues con toJsonObject().toString().

### infra-07 · robustez · media
**updateUI() lanza NPE si se toca un control antes de initExtension (y stackTile() desempaqueta un Integer null); eso genera ademas un diagnostico automatico**

- Evidencia: Si G-Earth marca delayed_init (extension abierta antes de conectar a Habbo), floorState/inventory/permissions/importer/exporter son null hasta initExtension. Prueba bench/UpdateUiNpe.java (controlador GPresets real, cambiando Stack tile a 4x4): 'NullPointerException: Cannot invoke "game.FloorState.inRoom()" because "this.floorState" is null at GPresets.lambda$updateUI$26(GPresets.java:445)' y diagnostics.hasPendingProblems()=true, es decir el manejador de excepciones (linea 197) escribe un diag_*.txt a los 4 s solo por pulsar un radio. Segundo disparador: si el hotel no tiene en furnidata la clase del stack tile elegido (tile_stackmagic4x4/6x6/8x8), getItemsFromType hace unboxing de null y el NPE se repite en cada updateUI, que aborta antes de habilitar/deshabilitar los botones (Pegar, Elegir posicion...). Misma familia: AutoDonator.countFloorItemsByTypeId (infra-10).
- Arreglo propuesto: Al inicio del lambda de updateUI: if (floorState == null || inventory == null || permissions == null || importer == null) { /* solo etiquetas basicas */ return; }. En stackTile(): try/catch RuntimeException -> null (o getFloorTypeId con null-check antes de llamar a getItemsFromType).
- Riesgo: Bajo: solo evita excepciones.
- Cómo verificar: Repetir bench/UpdateUiNpe (esperado: sin NPE y hasPendingProblems=false); javap de lambda$updateUI.

### infra-08 · bug · media
**Sala llena: cada Detener+Vigilar (o :sniper repetido) de la misma sala deja un hilo extra consultando; no se detiene al desconectar**

- Evidencia: El hilo viejo duerme intervalMs; al despertar ve running=true y targetId==n (misma sala) y sigue. Prueba bench/SniperDouble.java (GPresets stub, intervalo 0,5 s, 3,2 s): una sola vigilancia -> 7 GetGuestRoom; tras stop()+start() de la misma sala -> 13. Cada repeticion de ':sniper 12345' (start llama a stop y a start) suma otro hilo, y ninguno muere hasta stop(). Consultas multiplicadas pueden activar el limite de flood del hotel. Aparte: onEndConnection no para la vigilancia; al volver a conectar sigue sola y puede mandar RoomForward (te saca de la sala, incluso en mitad de una importacion).
- Arreglo propuesto: Contador de generacion: private final AtomicInteger gen; start(): final int g = gen.incrementAndGet(); loop(n,g): while (running && gen.get()==g). stop() tambien incrementa gen. En GPresets.onEndConnection: RoomSniper s = RoomSniper.get(); if (s != null) s.stop("Conexion cerrada"). Opcional: no hacer RoomForward si importer.getState() != NONE.
- Riesgo: Bajo.
- Cómo verificar: Repetir bench/SniperDouble (esperado ~7 y ~7).

### infra-09 · funcion · media · sin verificar
**Los ajustes de la pestana Extras hechos antes de que Extras exista se pierden y la UI queda desincronizada**

- Evidencia: Extras.get() es null hasta initExtension (que G-Earth puede retrasar hasta conectar a Habbo). Los listeners de transInCbx/transOutCbx/idiomas/color/hcCbx solo actuan si Extras.get()!=null y nada reaplica el estado de la UI cuando Extras se crea: la UI muestra 'Traducir ...' marcado mientras el backend esta apagado (defaults), y 'Soy HC' sin efecto. Verificado leyendo: no existe ningun enlace posterior (applyChat solo se llama desde listeners). (Coordinar con quienes reescriben el traductor: la correccion es del shell.)
- Arreglo propuesto: En ExtrasController.refresh() (ya corre cada 600 ms): if (!sincronizado && Extras.get()!=null) { sincronizado = true; applyChat(); Extras.get().setHc(hcCbx.isSelected()); }. Opcional: persistir esos ajustes (ver infra-19).
- Riesgo: Bajo.
- Cómo verificar: Prueba con GPresets stub: marcar casillas, llamar a Extras.init(stub) y comprobar que setTranslate recibe los valores tras un refresh; render de la pestana.

### infra-10 · bug · baja · sin verificar
**Autodonar: el estado SELF_DONATING queda atascado si hay un NPE o un return temprano, bloqueando tus propios SelfDonateItem**

- Evidencia: Un preset con una clase que el hotel no conoce (o furniDataTools null tras desconectar) provoca NPE por unboxing en countFloorItemsByTypeId; el state ya vale SELF_DONATING, el NPE sale por el hilo FX y desde entonces cualquier SelfDonateItem tuyo se bloquea con 'Please wait for the auto donate to finish...' y se ocultan los SelfDonationResult hasta escribir :abort. Mismo atasco si el hilo de donacion muere por otra excepcion o si inventory==null. Ademas el modo 'Solo los que faltan' no comprueba Inventory.getState()==LOADED: con inventario sin cargar getFloorItemsByType devuelve vacio y se calcula 'faltan todos'. El boton solo se muestra en game-s2.habbo.com. Verificado solo leyendo el codigo.
- Arreglo propuesto: Poner state=SELF_DONATING justo antes de lanzar el hilo; try/finally que restaure NONE en donateAll y en el hilo; saltar clases con getFloorTypeId()==null (avisar con el log); exigir inventario LOADED en el modo 'faltan' y mostrar un confirm con el total de furnis antes de empezar; hacer el hilo daemon.
- Riesgo: Bajo.
- Cómo verificar: Prueba con GPresets stub que devuelve un FurniDataTools sin la clase: debe loguear el aviso y dejar isDonating()==false.

### infra-11 · ux · baja
**Los tooltips de 'Baldosa magica automatica' y 'Quitar lo ultimo que pegue' no llegan a mostrarse: se crea un Tooltip nuevo en cada tick**

- Evidencia: Reemplazar el Tooltip de un control cada <1 s reinicia el temporizador de activacion (1 s) y el tooltip nunca aparece con el raton quieto. Prueba bench/TooltipReplace2.java con eventos de raton sinteticos: control estatico (C) -> visible: true; mismo Tooltip con setText cada 700 ms (B) -> true; Tooltip nuevo cada 700 ms (A) -> false. Se pierde la unica forma de ver AutoTile.getStatus()/PasteUndo.getStatus().
- Arreglo propuesto: Crear el Tooltip una vez (campo final) y hacer setText solo si el texto cambio; Tooltip.install una vez.
- Riesgo: Nulo.
- Cómo verificar: Repetir bench/TooltipReplace2 con el patron corregido (visible: true).

### infra-12 · visual · baja
**Textos de estado/error largos se cortan con '...' en Extras (y en la barra inferior)**

- Evidencia: Labels con wrapText=true pero sin minHeight=-Infinity dentro de VBox: JavaFX los recorta a una linea. Render bench/RenderText -> audit/infra/shots/extras_long.png: 'No pude armar un atuendo al azar: el servidor d...' y 'No conozco el paquete "Chatt" en esa direccion (revisa ...' quedan cortados; inicio_long.png: el error 'No pude cargar la copia ...' de la barra inferior termina en '[character 2 lin...'. Son justo los mensajes de error que el usuario necesita leer.
- Arreglo propuesto: Añadir minHeight="-Infinity" a outfitLbl, transLbl, slangLbl, ruleLbl, postconfigErrorLbl, pixelart infoLbl/statusLbl y sniper stateLbl; en GPresets.homeInfo() poner tambien un Tooltip con el texto completo para homeStatusLbl.
- Riesgo: Bajo: puede crecer la altura de la tarjeta; comprobar que no empuja botones fuera de 730 px.
- Cómo verificar: Render headless con bench/RenderText y los mismos textos largos.

### infra-13 · funcion · baja
**La Vista previa depende de Sprites/indice.tsv que nada en el jar crea: sin el, todo se dibuja como cajas y 'Bajar iconos' no hace nada**

- Evidencia: grep: 'indice.tsv' solo se lee, nunca se genera ni se empaqueta (no hay recurso en el jar). Sin indice, buscar() devuelve null: medidas por defecto 1x1/alto 1.0 y bajar() sale con null (info==null), asi que ningun icono se descarga. La extension ya tiene en memoria lo necesario al conectar: FurniDataTools expone por clase id, revision, xDim, yDim y canStandOn (furnidata/details/FloorItemDetails). Puede que el usuario tenga ya un indice propio; es lo unico que la hace util.
- Arreglo propuesto: Funcion extra: al terminar de cargar FurniDataTools (onConnect) generar Sprites/indice.tsv si falta o tiene mas de 7 dias (columnas: clase, I/W, id, revision, xdim, ydim, altura=1.0, caminable=canStandOn, nombre) en un hilo aparte con escritura atomica; la altura real no esta en furnidata_json, mantener 1.0 y dejar que un indice existente tenga prioridad. Mostrar en el estado 'indice generado: N furnis'.
- Riesgo: Bajo: solo anade un archivo; no sobrescribir un indice propio del usuario (usar indice_auto.tsv como segunda fuente).
- Cómo verificar: Generar el tsv desde un JSON de furnidata de prueba y correr bench/RenderPerf con el (footprints correctos).

### infra-14 · ux · baja
**Las pestanas 'Pixel art' y 'Sala llena' se ocultan en caliente sin forma de recuperarlas; ademas los retoques de UI solo se aplican tras initExtension (salto visual al conectar)**

- Evidencia: HIDDEN={"pixel art","sala llena"} se elimina del TabPane. Pixel art queda inalcanzable (PixelArtController sigue cargado) y Sala llena solo por el comando :sniper (el panel de aforo/historial ya no se ve). Como el parcheo sale de initExtension, si el usuario abre la ventana antes de conectar ve el FXML original (tabs, slider de velocidad, sin iconos) y al conectar los tabs desaparecen, el slider se sustituye por una lista y cambia la opacidad. Confirmar con el usuario si ocultarlas es deliberado; el contexto indica que quiere conservar todas las funciones.
- Arreglo propuesto: Aplicar HideTabs/SpeedSetting/PremiumLook desde GPresets.initialize() (hilo FX, antes de mostrar). Mejor: quitar los tabs del FXML o dejar un CheckBox 'Mostrar herramientas avanzadas (Pixel art, Sala llena)' en Ajustes persistido en ajustes.json.
- Riesgo: Bajo-medio: mover el parcheo antes de initExtension exige que ratelimiter/ratelimit existan (initialize ya los tiene).
- Cómo verificar: Render headless de gpresets.fxml con y sin los tabs; comprobar que la ventana abierta antes de conectar ya sale con el aspecto final.

### infra-15 · robustez · baja · sin verificar
**Extras no reinicia su estado al reconectar o cambiar de cuenta (savedFigure, ownedSets, mi_ropa.json)**

- Evidencia: savedFigure/savedGender solo se borran en restoreOutfit(); Extras no escucha onEndConnection. Si copias un atuendo, cierras Habbo y entras con otra cuenta en la misma sesion de G-Earth, 'Volver al mio' aplica el atuendo guardado de la cuenta anterior. mi_ropa.json es unico para todas las cuentas: 'Con mi coleccion' puede usar prendas de otra cuenta hasta que llegue FigureSetIds. Verificado leyendo; no probado con dos cuentas.
- Arreglo propuesto: Extras.reset() (savedFigure=null, myId=-1, users.clear(), outfitStatus por defecto) llamado desde GPresets.onEndConnection; guardar mi_ropa por id de usuario (mi_ropa_<id>.json).
- Riesgo: Bajo.
- Cómo verificar: Prueba con stub: simular UserObject(id A), wear, onEndConnection, UserObject(id B) y comprobar canRestore()==false.

### infra-16 · ux · baja · sin verificar
**Pulido de la barra de presets: 'Editar' sin fallback, mensajes en ingles y la etiqueta de resumen no se refresca al recargar**

- Evidencia: Desktop.edit() lanza si .json no tiene verbo 'edit' en Windows (comun) y solo se muestra 'Couldn't open the preset in a text editor'; sin probar (no hay Windows aqui). Tras editar el JSON, el watcher recarga el preset (log verde) pero homePresetInfoLbl sigue con el recuento de furnis/wireds del archivo anterior; lo mismo con el boton Recargar.
- Arreglo propuesto: try edit(); catch -> open(); y como ultimo recurso getHostServices().showDocument(uri). Llamar a updateHomeInfo(config, nombre) tras recargar en checkPresetFolder y reloadPresetsClick. Traducir los mensajes.
- Riesgo: Nulo.
- Cómo verificar: Revision de javap/diff; render de la etiqueta tras simular el reload.

### infra-17 · limpieza · baja · sin verificar
**Diagnostics: un hilo nuevo por cada linea roja, informes automaticos por errores de usuario y historial.tsv sin limite**

- Evidencia: En una importacion con muchos rechazos hay cientos de lineas rojas y se crean cientos de hilos solo para que scheduleAutoReport devuelva al instante (importer.getState()!=NONE). Mensajes rojos que son errores de uso ('Failed to start auto donate, no preset selected', 'Can't autodonate...', 'Couldn't load ...') disparan a los 4 s un diag_*.txt y una linea purpura 'Diagnostico automatico guardado'. prune() limita a 60 diag_*.txt pero historial.tsv crece sin tope (pocas KB por mes, no urgente). Medido: el Logger y el area de texto soportan 22.440 lineas sin degradacion.
- Arreglo propuesto: Comprobar autoReportPending/lastAutoReport/importer antes de crear el hilo, o usar un unico ScheduledExecutorService daemon. Ampliar notAnError o marcar los mensajes de uso como naranja en vez de rojo. Podar historial.tsv a las ultimas 500 filas en prune().
- Riesgo: Bajo.
- Cómo verificar: Prueba unitaria de onLog con 1000 lineas rojas (conteo de hilos) y de prune().

### infra-18 · visual · baja · sin verificar
**Ventana fija de 1010x730 y no redimensionable: en portatiles de 1366x768 se corta la barra inferior**

- Evidencia: 730 px de contenido + barra de titulo de G-Earth (~30 px) = ~760 px; con la barra de tareas de Windows el area visible de una pantalla 1366x768 es ~728 px, asi que la barra de progreso y estado inferior quedan fuera de pantalla y no se puede reducir la ventana. No verificado (no hay pantalla pequena aqui).
- Arreglo propuesto: En GPresetsLauncher.initialize(stage): leer Screen.getPrimary().getVisualBounds(); si la altura es menor que 780 aplicar un factor de escala al root (Group/scale) o envolver el root en un ScrollPane y permitir setResizable(true) con tamano maximo = pantalla.
- Riesgo: Medio: toca el layout principal; probar con los render a 1010x730 y a 900x650.
- Cómo verificar: Render headless a 1010x730 (sin cambios) y a 1366x700 con la escala.

### infra-19 · funcion · baja · sin verificar
**Ajustes de ventana y de sesion sin recordar: 'Siempre encima', ultimo preset elegido, opacidad (solo por :vidrio) y tamano/posicion**

- Evidencia: Cada sesion hay que volver a marcar 'Siempre encima' y volver a elegir el preset; la ventana sale al 94% de opacidad por defecto y para cambiarlo hay que usar un comando de chat, algo que el usuario dijo que no le gusta anadir. Verificado leyendo.
- Arreglo propuesto: Guardar en ajustes.json (con escritura atomica): onTop, ultimoPreset (reseleccionarlo en el primer updateInstalledPresets si el archivo existe), opacidad (un slider en Ajustes en lugar del comando), posicion de la ventana. Mantener :vidrio como alias.
- Riesgo: Bajo.
- Cómo verificar: Prueba unitaria de lectura/escritura del archivo y render de Ajustes con el slider.

## INTERFAZ

Renderizado de cada pantalla tal como la ve el usuario, no con el render base. Hice un arnés nuevo, RenderReal.java con rr.sh, en /tmp/claude-0/-home-user-wired/07f3ecc1-8f98-5c55-8b13-34fe1b96edab/scratchpad/tr/audit/interfaz/. Reproduce el arranque real: hoja del tema de G-Earth Dark, luego styles.css con la URL real (...extension/ui/styles.css), barra de título de G-Earth, PremiumLook.apply (iconos, logo, insignia) y HideTabs (quita Pixel art y Sala llena). Datos de demo, estados forzados (hover, pressed, focused) y diálogos y ventana de vista previa reales. El render base de la herramienta (render.sh) engaña: sin tema, sin PremiumLook y con Pixel art y Sala llena visibles.

Resultado: 19 hallazgos verificados con captura o prueba mínima, o marcados como no verificados. Los de mayor impacto:
- La ventana Vista previa sale ilegible (le falta la clase gp-root).
- Los diálogos de confirmación recortan el texto y 3 de ellos salen sin tema.
- La franja superior duplica la píldora «Entra a una sala» y la pone en rojo.
- Los botones se encogen a «Vi…» o «Deshac…» porque el CSS pisa el minWidth del FXML.
- La lista de presets recorta los nombres largos sin elipsis y con barra horizontal.
- El REGISTRO es diminuto y no ajusta líneas.
- La pestaña Extras está comprimida.
- El foco de teclado no se ve en botones, casillas y opciones.
- styles.css tiene 5 capas de «premium» que se pisan: el 47 % de sus declaraciones sobran y el brillo dorado nunca se ve (la capa opaca queda encima).

Entregables verificados bajo .../tr/audit/interfaz/:
- ui_final/: FXML y CSS con todas las mejoras de CSS y FXML, y styles.css deduplicado (23,7 KB frente a 47,7 KB).
- ui_dedup/: solo la deduplicación del CSS original, pixel-idéntica en 9 escenarios y 2 de hover/pressed/focus, con control negativo.
- ui_mod/: lo mismo sin deduplicar, para comparar.
- shots/ y cmp/: capturas antes y después.
- rr.sh: arnés de render.
- dedup_css.py: genera el CSS deduplicado.

Salvedades:
1. Todo se renderiza con JavaFX 11, Linux, pipeline por software y fuente Inter. G-Earth en Windows puede usar otra versión de JavaFX y otro rasterizado, así que las medidas exactas de ancho pueden variar unos píxeles.
2. No se tocó ningún .java ni ningún archivo del proyecto. Los arreglos de Java están descritos y los comportamientos equivalentes se probaron en el arnés.
3. El FXML de ui_final está regenerado con ElementTree y sin comentarios. Es un prototipo de la estructura, no un parche aplicable: aplicar a mano las ediciones indicadas en cada hallazgo.
4. Nota de contexto: en la ventana real, SpeedSetting reemplaza el slider «Velocidad» por un combo (Segura, Normal, Rápida) y HideTabs quita dos pestañas. La interfaz real es más corta que el FXML.

### I01 · visual · media
**La ventana «Vista previa» sale lavada e ilegible: su raíz no lleva la clase gp-root**

- Evidencia: El BorderPane de la ventana hereda las hojas de estilo y las clases del tema (g-dark), pero no gp-root. Casi todo styles.css cuelga de .gp-root (fondo, texto claro, casillas, botones). Resultado: fondo gris claro de Modena con texto blanco del tema, título «Elige un preset» casi invisible, botones -, +, Ajustar, Tamaño real, Guardar imagen sin contraste, casillas con marca celeste y lista como bloque gris. Captura: .../shots/dlg1_preview.png (antes) y .../shots/pv_fix_preview.png (después de añadir gp-root).
- Arreglo propuesto: En VistaPreviaVentana, tras la línea 180 (borderPane.getStyleClass().addAll(...)), añadir borderPane.getStyleClass().add("gp-root"); (o ponerla antes, con el resto de clases). Opcional: tamaño inicial Math.min(720, Screen.getPrimary().getVisualBounds().getHeight()-40), porque 720 más el marco del SO no cabe en pantallas de 768 px de alto.
- Riesgo: Muy bajo: solo añade una clase CSS a una ventana secundaria. No toca lógica ni importación.
- Cómo verificar: Hecho: render con preview=1, antes (dlg1_preview.png) y con previewfix=1 (pv_fix_preview.png): tema coherente y legible. Sin cliente: rr.sh con preview=1.

### I02 · ux · media
**Diálogos de confirmación: texto largo recortado con «…» y tres de los cinco sin dueño ni estilo (salen en claro, con icono azul «?»)**

- Evidencia: (a) PasteUndoController usa setContentText con 200 caracteres: el diálogo (heredando el CSS del dueño) mide 360x156 y muestra «Son solo los que colocó la última pegada en esta …». Se pierde justo la aclaración «lo que ya estaba antes no se toca». Captura dlg4_dialog.png. (b) SimpleFlow:459 (askForce), WiredZoneFilter:325 (askClean) y VistaPreviaController:40 no llaman a initOwner: salen con Modena claro, fondo blanco y «?» azul sobre una app oscura (dlg2_dialog.png). Los que sí tienen dueño heredan hojas pero conservan el «?» azul y botones sin estilo gp.
- Arreglo propuesto: Un helper común, por ejemplo Dialogos.preparar(Alert a, Window dueno, String texto): a.initOwner(dueno) (si es null, el Stage de G-Presets: this.primaryStage o el primer Window.getWindows() visible); a.setGraphic(null); a.getDialogPane().getStyleClass().add("gp-root"); en vez de setContentText, Label l = new Label(texto); l.setWrapText(true); l.setPrefWidth(440); l.setMaxWidth(440); l.setMinHeight(Region.USE_PREF_SIZE); a.getDialogPane().setContent(l); a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE). En askPaste (SimpleFlow:149) basta añadir setGraphic(null) y gp-root, porque ya usa un VBox.
- Riesgo: Bajo: solo presentación; showAndWait y los ButtonType no cambian. Cuidar que initOwner se llame antes de show.
- Cómo verificar: Hecho en el arnés: dialog=undo dialogfix=2 (dlg5_dialog.png: 440x151, texto completo, tema oscuro) frente a dialog=undo (recortado) y dialog=noowner (claro). Sin cliente: rr.sh dialog=...

### I03 · visual · media
**Primera impresión: dos píldoras con el mismo texto y una en rojo («Entra a una sala» rojo + «Entra a una sala»/«Esperando a Habbo…» gris)**

- Evidencia: GPresets.updateUI llama updateLabel(homeRoomLbl, bl2) sin el flag de gris: fuera de sala el estado normal se pinta lblred (alarma). EstadoController pinta además su resumen en gris con el mismo texto (o «Esperando a Habbo…» si ni siquiera hay conexión, contradiciendo al rojo). Captura first_run.png. Además la píldora ocupa 160 px de la franja, que luego faltan para los textos largos del resumen y de los botones (ver I04).
- Arreglo propuesto: En gpresets.fxml:29 añadir visible="false" managed="false" a homeRoomLbl (el nodo sigue existiendo: updateUI y updateLabel siguen funcionando, igual que con las seis píldoras ocultas). El resumen de EstadoController ya dice todo («Entra a una sala», «Todo listo para pegar», «Falta: …»). Alternativa mínima si se quiere conservar: updateLabel(homeRoomLbl, bl2, false, !bl2).
- Riesgo: Muy bajo: ningún código lee la visibilidad de homeRoomLbl.
- Cómo verificar: Hecho: ui_mod/modC_inicio.png y mod2_inicio.png: el resumen «Falta: Conectado a Habbo, Sala detectada y 4 más» cabe entero y «Deshacer piso» deja de truncarse. Sin cliente: rr.sh demo=first|room,falta.

### I04 · visual · media
**Los botones .gp-btn-sm se encogen a «Vi…», «Deshac…» o «Quitar lo último que pegué (…»: el CSS pisa el minWidth=-Infinity del FXML**

- Evidencia: Las hojas de autor (styles.css línea 38 con -fx-min-width: 0, y el .button{-fx-min-width:30} del tema) ganan a los valores puestos por FXML o código, así que minWidth="-Infinity" no tiene efecto. Con el nombre real del usuario («lista_compra_individual_8_demo») el combo homePresetCombo (pref ancho = su texto más largo) comprime «Vista previa» a «Vi…» (captura real_inicio_demo_off.png). PasteUndoController reescribe cada 900 ms el texto a «Quitar lo último que pegué (2244)» o, sin nada que quitar, «Quitar lo último que pegué» (~200 px), no el «Quitar lo pegado» del FXML, y fuerza «Deshac…» (real_inicio_demo_falta.png). -fx-min-width: USE_PREF_SIZE no es CSS válido (probado: 'Could not resolve').
- Arreglo propuesto: (1) gpresets.fxml:69 añadir prefWidth="100.0" a homePresetCombo (sigue creciendo con HBox.hgrow, pero ya no exige su texto completo). (2) Acortar el texto dinámico: PasteUndoController:44 usar "Quitar lo pegado (" + n + ")" y dejar el texto largo en el tooltip. (3) Quitar la píldora homeRoomLbl (I03). (4) CSS: añadir al final .gp-list .list-cell,.gp-root .combo-box .list-cell{-fx-text-overrun:center-ellipsis;} para que el nombre acortado conserve el final (_demo).
- Riesgo: Bajo: ajustes de layout. El combo con prefWidth=100 se estira igual al ancho disponible.
- Cómo verificar: Hecho: ui_mod/mod2_inicio.png: «Vista previa» y «Deshacer piso» completos con el nombre largo. Sin cliente: rr.sh demo=falta (ya incluye el texto largo del botón de deshacer).

### I05 · visual · media
**Nombres largos de preset: recorte seco más barra de desplazamiento horizontal en la lista**

- Evidencia: La lista (250 px) calcula el ancho del contenido más ancho: «casa_de_playa_con_piscina_y_jardi» se corta sin elipsis y aparece una barra horizontal al pie (real_inicio_demo_off.png). Con nombres parecidos («..._8» y «..._8_demo») el final, que es lo que los distingue, es lo primero que se pierde.
- Arreglo propuesto: CSS al final de styles.css: .gp-list .list-cell, .gp-root .list-view .list-cell { -fx-pref-width: 0; -fx-text-overrun: center-ellipsis; } y .gp-root .combo-box .list-cell, .combo-box-popup .list-view .list-cell { -fx-text-overrun: center-ellipsis; }. Resultado: «casa_de_playa_…na_y_jardin_v2», sin barra horizontal.
- Riesgo: Bajo: solo CSS. Afecta a todas las .list-cell dentro de .gp-root (presets, usuarios, reglas, historial).
- Cómo verificar: Hecho: ui_mod/mod1_inicio.png (después) frente a real_inicio_demo_off.png (antes). Sin cliente: rr.sh demo=room.

### I06 · ux · media
**El REGISTRO es diminuto (≈8 líneas de 300 px) y no ajusta líneas: salen cortadas con barra horizontal**

- Evidencia: La tarjeta REGISTRO recibe lo que sobra de la columna 1 (≈115 px de alto) y el StyleClassedTextArea no tiene wrapText. Las líneas de bienvenida («* :crop [N|ver|copiar|deshacer] - recorta el plano a las baldosas con furnis», «* :ep all guarda tambien…») se cortan y aparece scrollbar horizontal. Las líneas rojas de error de una importación son lo más útil que muestra y es lo menos visible. Mientras, la columna 3 está vacía desde y≈440 hasta el pie (~260 px).
- Arreglo propuesto: (a) Logger.initialize: tras crear el área, this.area.setWrapText(true). (b) Mover la tarjeta REGISTRO de la columna 1 a la columna 3 bajo AJUSTES (VBox.vgrow=ALWAYS, ya lo tiene): pasa de ~300x115 a ~377x250 con 12+ líneas visibles. Sin ningún widget nuevo.
- Riesgo: Bajo: el log no cambia de API. La columna 1 queda con un hueco bajo PASO 2 (~170 px), que es aceptable y más calmado.
- Cómo verificar: Hecho: ui_mod/modC_inicio.png (columnas) y modC_wrap.png / z_log_wrap.png (wrap activado con lookup .styled-text-area y setWrapText(true)). Sin cliente: rr.sh demo=room wrap=1.

### I07 · visual · media
**Pestaña Extras apretada: la lista de usuarios queda en ~85 px, el hueco del look (70x120) está reservado vacío y los hints envueltos se truncan**

- Evidencia: extras.fxml pide 930 px de alto y solo hay ~580: el VBox reparte la falta de altura entre todos. La lista de usuarios (ListView con vgrow) queda en 85 px (≈2-3 filas); el ImageView lookVista reserva 120 px aunque no hay imagen, así que el texto del look aparece sangrado con un hueco. El hint «Bloquea un paquete o cambia un texto que venga exactamente ...» pierde su segunda línea porque los Label con wrapText tienen minHeight de una línea (real_extras.png). Con 650 px de alto, las filas de la lista se pintan encima de los botones.
- Arreglo propuesto: (1) looks.fxml:20 ImageView visible="false" managed="false", y en LooksController.initialize enlazar: lookVista.visibleProperty().bind(lookVista.imageProperty().isNotNull()); lookVista.managedProperty().bind(lookVista.imageProperty().isNotNull()). (2) minHeight="-Infinity" en los Label wrapText con clase gp-hint dentro de tarjetas con lista (ruleLbl extras.fxml:84, hint de color 64, outfitLbl). (3) La lista de usuarios pasa a ~205 px (5-6 filas). (4) Opcional: mover «Entra a una sala para ver a quién copiar» a placeholder del ListView (<placeholder><Label … text="…&#10;…"/></placeholder>), ya que ahí no se lee.
- Riesgo: Bajo: solo layout. Con look cargado la lista vuelve a ~85 px, pero es transitorio.
- Cómo verificar: Hecho: ui_mod/mod_extras.png (después) frente a real_extras.png (antes). Sin cliente: rr.sh tab=1 demo=room.

### I08 · ux · media
**Ventana fija de 1012x756 px (no redimensionable, sin barra de desplazamiento): en pantallas de ≤768 px de alto o con 150 % de escala se corta el pie con el progreso**

- Evidencia: javap de ThemedExtensionFormCreator.createForm (offsets 45-51): stage.setResizable(false) y sizeToScene(); la barra de título es propia (StageStyle.TRANSPARENT). Tamaño medido: 1012x756 (730 del FXML + 25 de barra). Un portátil de 1366x768 al 100 % deja ~728 utilizables; 1920x1080 al 150 % son 720 lógicos, y ahí se pierde el pie (estado, barra de progreso y %). Mínimos medidos del contenido: Inicio 468 px, Extras 528 px: la ventana puede achicarse a ~640 en Inicio, pero Extras comprime y recorta (captura small650_tab1.png con texto de casillas cortado y filas encima de botones). Cálculo de pantallas: analítico, no observado en un monitor real.
- Arreglo propuesto: En GPresetsLauncher.initialize(stage): leer Screen.getPrimary().getVisualBounds() y fijar root.setPrefHeight(Math.min(730, visual.getHeight() - 25 - 24)) (mínimo ~640). En gpresets.fxml envolver extras.fxml en <ScrollPane fitToWidth="true" fitToHeight="true" hbarPolicy="NEVER" styleClass="gp-scroll"> y dar a la raíz de extras.fxml minHeight="590" para que desplace en vez de comprimir. CSS: .gp-scroll,.gp-scroll>.viewport{-fx-background-color:transparent;-fx-background:transparent;-fx-padding:0;}. PremiumLook.walk ya recorre ScrollPane.
- Riesgo: Medio-bajo: toca el tamaño de la ventana y el contenedor de una pestaña. Sin el minHeight el ScrollPane comprime igual (probado). Revisar que ExtrasController y PremiumLook sigan encontrando sus nodos (lo hacen por id y por árbol).
- Cómo verificar: Parcial hecho: ScrollPane con minHeight=590 a 650 px desplaza sin recortar (ui_mod/modscroll_650.png) y a 756 queda igual (modscroll_756.png). Falta probar en un monitor pequeño real. Sin cliente: rr.sh w=1012 h=650 tab=1.

### I09 · ux · baja
**El foco de teclado es invisible en botones, casillas, opciones y barra (solo campos de texto y combos lo muestran)**

- Evidencia: Las reglas «el foco no cambia el color» (líneas 470-474) y las de :selected:focused anulan todo indicador. Con :focused forzado sobre copyBtn, pasteBtn, onTopCbx, onlyInvCbx y ratelimiter no cambia ni un píxel (z_focus_orig.png). Un usuario que navega con Tab no sabe dónde está.
- Arreglo propuesto: CSS al final: .gp-root .gp-btn-sm:focused,.gp-root .gp-btn:focused,.gp-root .gp-btn-danger-sm:focused{-fx-border-color:#d4b572;} .gp-root .gp-btn-primary:focused{-fx-border-color:#ffffff;} .gp-root .check-box:focused>.box{-fx-border-color:#d4b572;-fx-border-width:1.5;-fx-border-radius:5;} .gp-root .radio-button:focused>.radio{-fx-border-color:#d4b572;-fx-border-width:1.5;-fx-border-radius:20;} .gp-root .check-box:selected:focused>.box,.gp-root .radio-button:selected:focused>.radio,.gp-root .slider:focused .thumb{-fx-effect:dropshadow(three-pass-box,rgba(212,181,114,0.9),4,0.7,0,0);}
- Riesgo: Bajo: JavaFX no distingue foco de teclado y de ratón, así que tras un clic el botón conserva el borde dorado hasta que el foco se mueve. Es cosmético.
- Cómo verificar: Hecho: forcefocus en el arnés, antes z_focus_orig.png y después z_focus_mod.png (anillo dorado en casillas y opciones, borde en botones). Sin cliente: rr.sh forcefocus=copyBtn,onTopCbx,...

### I10 · ux · baja
**Estado vacío y de reposo con ruido: lista de presets vacía sin guía, prompt cortado, hint redundante, Cancelar y «Quitar lo último…» siempre visibles**

- Evidencia: Primera ejecución (first_run.png): un rectángulo vacío de 250x370 donde irá la lista; el prompt «Nombre de la copia (ej. Colomb…» se corta; el hint «Elige una copia de la lista» repite el prompt del combo «Elige una copia»; 8 controles deshabilitados a la vez; en rojo «Cancelar» (homeAbortBtn, solo útil durante una importación) y «Quitar lo último que pegué» (sin nada que quitar).
- Arreglo propuesto: (1) gpresets.fxml:58 promptText="Nombre de la copia". (2) <placeholder><Label styleClass="gp-hint" textAlignment="CENTER" wrapText="true" text="Aún no hay copias.&#10;Copia una sala (paso 1) o deja&#10;un .json en la carpeta."/></placeholder> dentro de presetListView. (3) Que updateHomeInfo(null) deje el hint vacío en vez de «Elige una copia de la lista». (4) GPresets.updateUI: homeAbortBtn.setVisible(bl); setManaged(bl). PasteUndoController: setVisible/setManaged del botón y de su VBox contenedor solo cuando n>0 o isWorking.
- Riesgo: Bajo. Ojo: «Pegar en esta sala» pasa a ocupar el ancho completo mientras no hay importación.
- Cómo verificar: Parcial hecho: placeholder y prompt en ui_final (cmp/f_tab_0_demo_first_speed.png). Lo de Cancelar y Quitar: solo razonado. Sin cliente: rr.sh demo=first.

### I11 · bug · baja · sin verificar
**El mensaje de «Agregar mis modismos…» desaparece en ≤600 ms: refresh() lo sobrescribe**

- Evidencia: slangClick escribe en slangLbl «Se abrió modismos.txt: agrega líneas como es: jalar = funcionar y guarda.» o el error, pero el Timeline de 600 ms (initialize) ejecuta refresh() que vuelve a poner «Entiende N modismos y abreviaturas.» (línea 168). El usuario apenas ve la instrucción. Verificado por lectura de código.
- Arreglo propuesto: private volatile long slangHastaMs; en slangClick poner slangHastaMs = System.currentTimeMillis() + 15000; en refresh(), solo reescribir slangLbl si System.currentTimeMillis() > slangHastaMs.
- Riesgo: Muy bajo.
- Cómo verificar: Revisión de código. Sin cliente: test unitario con un Label y dos llamadas a refresh() simuladas.

### I12 · bug · baja
**Se crea un Tooltip nuevo en cada tick: el anterior queda huérfano en pantalla**

- Evidencia: setTooltip(new Tooltip(...)) cada 700/900 ms. Prueba mínima (.../tt/TT.java, JavaFX 11): con t1 visible, b.setTooltip(new Tooltip("estado 2")) deja t1.isShowing()==true. Con el ratón sobre «Baldosa mágica automática» o sobre «Quitar lo último…» el tooltip queda congelado o duplicado y no se actualiza. EstadoController lo hace bien: un solo Tooltip y setText.
- Arreglo propuesto: Campo Tooltip fijo creado una vez en initialize (autoTileCbx.setTooltip(tip)), y en el tick solo tip.setText(status) si el texto cambió.
- Riesgo: Muy bajo.
- Cómo verificar: Hecho: la prueba TT.java imprime 't1 mostrado: true' y luego 'tras setTooltip(nuevo): t1.isShowing=true'. El efecto con ratón real no se puede probar en el sandbox.

### I13 · visual · baja
**Contraste bajo: el hint de la cabecera (3,15:1) y los textos de ejemplo de los campos (3,18:1)**

- Evidencia: Medido sobre el render: «Chat: :ep all · :ip · …» #6b675f sobre #181819 = 3,15:1; prompts (#6b675f sobre #171717) = 3,18:1. El resto del texto pequeño está en 4,2-5,6:1 (subtítulo 5,18; card-title 4,49; hints 4,6-4,7; «Stack tile» 4,22).
- Arreglo propuesto: CSS al final: .gp-header .gp-hint,.gp-header .gp-hint .gp-icon{-fx-text-fill:#8d887e !important;} .gp-search,.gp-root .text-field{-fx-prompt-text-fill:#8a857b;}
- Riesgo: Muy bajo.
- Cómo verificar: Hecho: render antes y después (mod1_inicio.png) y medición de píxeles. Sin cliente: rr.sh.

### I14 · visual · baja
**El combo editable de Looks (temaCombo) pinta una caja dentro de otra y se ve distinto del resto de combos**

- Evidencia: La regla .gp-root .text-field (fondo rgba oscuro, radio 9, padding 7 10) también se aplica al TextField interno del combo editable: aparece un rectángulo más oscuro anidado con borde recto antes de la flecha (z_combo_editable.png).
- Arreglo propuesto: CSS al final: .gp-root .combo-box .text-field,.gp-root .combo-box .text-field:focused{-fx-background-color:transparent;-fx-border-color:transparent;-fx-background-insets:0;-fx-background-radius:0;-fx-padding:7 4 7 8;-fx-effect:null;}
- Riesgo: Muy bajo (solo afecta a combos editables).
- Cómo verificar: Hecho: z_combo_pair.png (antes a la izquierda y después a la derecha, misma altura que los demás combos).

### I15 · limpieza · media
**styles.css son 5 capas de «premium» que se pisan: el 47 % de las declaraciones sobran y el efecto de brillo dorado nunca se ve**

- Evidencia: 419 reglas y 1.338 declaraciones; 631 son pisadas por otra con el mismo selector y propiedad (hasta 6 veces: .gp-header background, .gp-btn-primary…). 473 colores literales (183 distintos) frente a 106 usos de tokens -gp-*; radios 5/6/7/8/9/10/11/14/16. Clases sin uso: gp-btn, gp-btn-danger, gp-progress-text, gp-step. El «vidrio» del fondo (498-504) lista tres radial-gradient y ÚLTIMO #0b0b0c: en JavaFX la primera capa se pinta debajo y las siguientes encima, así que el color sólido tapa los brillos (prueba BG.java: píxel #0b0b0c en todo el panel). Todo aquello son bytes y confusión que ya produjeron bugs (I04, I09).
- Arreglo propuesto: Usar el styles.css deduplicado: .../tr/audit/interfaz/ui_dedup/styles.css (23.0 KB, 200 reglas, pixel-idéntico) o ui_final/styles.css (incluye además los arreglos I04, I05, I09, I13, I14 y la ScrollPane). Se regenera con dedup_css.py: borra una declaración solo si el mismo selector la repite después con la misma importancia. Opcional: borrar las tres capas radial-gradient muertas (no cambia ningún píxel) y centralizar radios en 3 valores (tarjeta 16, control 9, píldora 7).
- Riesgo: Bajo: transformación mecánica. No cubre efectos que dependan del orden entre selectores distintos con la misma especificidad, aunque la prueba de píxeles no los muestra. Probar en la ventana real: Windows con JavaFX 8 o 11 podría diferir.
- Cómo verificar: Hecho: 9 escenarios (primera ejecución, demo con focus forzado, Extras, vista previa, diálogo, hover/pressed/focus en 11 controles) pixel-idénticos entre original y deduplicado (cmp/*.png). Control negativo: cambiar #d4b572 por #d4b573 sí se detecta (bbox 33,51-766,624). Sin cliente: rr.sh + comparación con PIL.

### I16 · rendimiento · baja
**La sombra gaussiana de 20 px en cada tarjeta duplica el coste de cada actualización con render por software**

- Evidencia: Banco con prism.order=sw: actualizar el progreso y la etiqueta de estado y repintar toda la escena cuesta 111-113 ms con las sombras y 39-42 ms sin ellas (≈2,7x). Quitando solo la de .gp-card: 48-58 ms. Durante una importación el progreso, la etiqueta y el log cambian muy a menudo dentro de tarjetas con sombra. En GPU (D3D) el coste será menor: no medido.
- Arreglo propuesto: CSS al final: .gp-card { -fx-effect: null; }. Es casi invisible porque la sombra negra cae sobre un fondo casi negro.
- Riesgo: Muy bajo. Diferencia máxima de 4/255 en 1.113 de 765.072 píxeles.
- Cómo verificar: Hecho: banco en RenderReal (bench=1) y diferencia de píxeles medida. Sin cliente: rr.sh bench=1.

### I17 · ux · baja
**Enter no hace nada en los campos de texto principales (nombre de la copia, nombre del pixel art, número de sala, reglas); la lista de presets solo se elige con doble clic**

- Evidencia: Ningún TextField del FXML tiene onAction. En Inicio hay que escribir el nombre de la copia y coger el ratón para pulsar «Copiar». GPresets.java enlaza presetListView solo a setOnMouseClicked (doble clic): sin teclado.
- Arreglo propuesto: gpresets.fxml:58 onAction="#copyBtnClick"; pixelart.fxml:58 onAction="#createClick"; sniper.fxml:16 onAction="#startClick"; extras.fxml: rulePacketField, ruleFromField y ruleToField onAction="#addRuleClick" (los handlers existen con firma ActionEvent). Opcional: presetListView.setOnKeyPressed con Enter que reutilice la lógica del doble clic.
- Riesgo: Bajo: Enter dispara exactamente lo mismo que el botón.
- Cómo verificar: Revisión estática del FXML. Sin cliente: compilar y comprobar que los handlers resuelven al cargar el FXML.

### I18 · funcion · baja
**Textos de la interfaz: referencias a pantallas que ya no existen, inglés mezclado y tildes perdidas**

- Evidencia: (a) Pantallas inexistentes: «revisa la pestaña Avanzado» (SimpleFlow:95); «reemplázalos en Configuración del preset» (GPresets:885, GPresetImporter:1822; esa tarjeta está oculta); «revisa los indicadores de ESTADO» (GPresets:895; las 6 píldoras están ocultas); «usa Crear diagnóstico ahora» (GPresets:966; el botón se llama «Diagnóstico»). (b) Inglés en el REGISTRO: «Leaving room..» (296), «Couldn't open the presets folder» (712), «Couldn't open the preset in a text editor» (724), «No preset chosen or furnidata not ready» (677), «Can't autodonate…» (683, 687). (c) Sin tildes y visibles: Extras:56 («quien»), que sustituye al «quién» del FXML en cuanto arranca el refresco, Extras:334, 464, 609; SpeedSetting:41 «Rapida (puede faltar alguno)» (opción de la lista Velocidad); RoomSniper:152, 356, 361.
- Arreglo propuesto: Ajustar las cadenas: «la sección Extras/Inicio…» o quitar la referencia; traducir los 6 mensajes; poner tildes (Rápida, Todavía, traducción, número, quién…).
- Riesgo: Bajo: solo texto. Cuidar que ningún código compare esas cadenas: SimpleFlow usa FREE_HINT y BLOCKED con regex sobre mensajes que sí deben seguir iguales.
- Cómo verificar: Revisión por grep. Sin cliente: grep de cadenas y compilar.

### I19 · limpieza · baja · sin verificar
**La interfaz real se arma a retales: pestañas construidas y quitadas después, widgets inyectados en caliente y ~20 nodos ocultos; fuentes cargadas tarde**

- Evidencia: (1) HideTabs quita «Pixel art» y «Sala llena» 0,5 s después de construirlas: sus controladores siguen vivos (SniperController tiene un Timeline de 300 ms). Si el usuario quisiera recuperarlas no hay forma desde la UI. Render con hide=false: all_tab1.png y all_tab2.png. Sus tarjetas heredan por texto los iconos de copiar y pegar (PremiumLook.forCardTitle 268-273: «PASO 1 · TU IMAGEN» sale con el icono de copiar). (2) SpeedSetting sustituye el slider «Velocidad» (y oculta «lento») por un combo (Segura, Normal, Rápida); el FXML del slider es código muerto. (3) Nodos ocultos que mantiene el FXML: 6 píldoras, loadInvBtn, abortBtn, tarjeta «USAR UN FURNI…», homeProgressLbl/Bar y los controles de Autodonar. En hoteles game-s2 los de Autodonar sí aparecen y sin estilo (botón «Autodonar furni» con el borde por defecto del tema, texto «Todos los de la exportacion»; real_s2.png). (4) PremiumLook carga Inter y Lucide al aplicar (hilo con sondeo de 400 ms): si la ventana ya está visible, los anchos se calculan con la fuente de reserva y salen botones cortados («Vista pr…», «Carp…», «Ed…»; reproducido en el arnés con early=false).
- Arreglo propuesto: (a) Cargar las fuentes en GPresetsLauncher.initialize(stage), antes de mostrar, con PremiumLook.load. (b) Poner styleClass="gp-btn-sm" en selfDonateBtn y radios de Autodonar con texto acentuado. (c) Si se quiere simplificar: mover el slider a un combo en el FXML y quitar la inyección de SpeedSetting.attach. (d) Decidir con el usuario si Pixel art y Sala llena vuelven a un menú «Más»; si no, quitarlas del FXML (cargando solo los controladores que sean necesarios).
- Riesgo: Medio para (c) y (d), porque tocan inicialización y controladores. Bajo para (a) y (b).
- Cómo verificar: (4) reproducido en el arnés con early=false (primer render, 'Vista pr…'). El momento exacto en que G-Earth muestra la ventana respecto a PremiumLook.init no está comprobado. Sin cliente: rr.sh early=false.

### I20 · ux · baja
**El resultado de una importación («Cancelado: faltan furni», «Listo, con omisiones», «Error inesperado») se pinta en el texto más tenue de la pantalla**

- Evidencia: progressLbl comparte estilo .gp-hint (#8d887e, 11 px) con «En espera», a la derecha junto a la barra. Los mensajes críticos que escribe GPresetImporter (líneas 416, 712, 1345, 1535, 1659) son los menos visibles; el texto grande de la izquierda (homeStatusLbl) muestra otra cosa. En reposo hay además tres elementos permanentes de ruido: «En espera», barra al 0 % y «0%». Captura prog_err.png.
- Arreglo propuesto: En updateProgress: añadir/quitar la clase gp-warn si el texto empieza por «Cancelado», «Error» o contiene «omisiones» y gp-good si d == 1.0 y «Listo…». Opcional: ocultar progressLbl, la barra y el % cuando d == 0 (visibleProperty ligado a progress>0).
- Riesgo: Bajo: gp-warn y gp-good ya existen en el CSS (#d9a15a y #9fc9a8).
- Cómo verificar: Hecho (parcial): render del estado actual (prog_err.png). El arreglo en sí no se prototipó. Sin cliente: rr.sh demo=errprog.

## FUNCIONES

Qué hace hoy el programa y cómo trabaja el usuario: genera presets JSON con wiredtool y Claude, los copia a presets/, los elige en Inicio, pega y prueba en el hotel (el .md de cada preset lista lo "NO probado"). Presets reales: 63 a 482 wireds por preset, la demo tiene 450 furnis y 402 wireds, y la lista completa 2.244 furnis. La interfaz real solo muestra Inicio y Extras, porque HideTabs (iniciado en RoomCrop.java:96) quita Pixel art y Sala llena. La ventana no es redimensionable (ThemedExtensionFormCreator.createForm hace setResizable(false) y sizeToScene).

Hallazgos verificados que sustentan las ideas:
1. PresetValidator solo corre al elegir el preset (src/GPresets.java:396). No corre en la recarga en caliente (GPresets.java:826-835) ni en «Recargar» (:729-744). Solo escribe en el REGISTRO y descarta las líneas "info" (sin confirmar).
2. Sobre la demo da 1 error y 12 avisos. 10 de ellos son "wf_glassdoor/wf_game_upcounter2 sin wired", probables falsos positivos: el .md del preset dice "No hay furnis de wired sin entrada".
3. Un .json con BOM no carga: PresetConfigUtils.loadPresetOrThrow falla con "A JSONObject text must begin with '{'" (reproducido en t/Bom.java). La vista previa sí quita el BOM (SalaRenderer.java:111).
4. El constructor de PresetConfig llama a WallItems.setPending (PresetConfig.java:67). Cargar con loadPreset* cualquier preset que no sea el elegido pisa los furnis de pared pendientes. Todas las ideas usan JSON crudo o FloorState, nunca PresetConfig.
5. Al terminar un pegado, la línea de estado la pisan "Diagnostico guardado…" (GPresetImporter.java:1860) y el aviso de :sugerir. SimpleFlow.onChatInfo termina siempre en homeInfo(string) (:454).
6. La vista previa no se entera de que el JSON cambió (no hay Timeline ni vigilante).

Medidas hechas offline, con prototipos en scratchpad/tr/audit/funciones/t/ y maquetas en .../shots/: validador 3-84 ms sobre los 6 JSON del repo; render de la vista previa 3-119 ms (sin sprites); PresetConfig + toJsonObject de la lista completa 45-140 ms.

Ranking: 1 tarjeta del preset, 2 soltar .json, 3 tiempo estimado, 4 qué cambió al recargar, 5 último pegado, 6 vista previa viva, 7 comparar sala con preset. Ninguna añade comandos ni susurros. Cada una usa como mucho un control visible, y cuatro de ellas ninguno. Dos mejoras grandes (actualizar solo los wireds cambiados y completar lo que faltó) envían paquetes y quedan en decisiones.

### Idea 1 · Tarjeta del preset: revisión viva, inventario de wired y «por confirmar» [S]
- Problema: La revisión del preset solo corre al elegirlo y solo escribe hasta 12 líneas en el REGISTRO, donde se pierden en cuanto entran otras líneas. No se repite cuando el vigilante recarga el archivo ni con «Recargar», que es justo el ciclo regenerar y reprobar. Las líneas «sin confirmar» (reloj [30,1,1], -401, wf_cnd_actor_in_group) se descartan, aunque CLAUDE.md pide avisar si se usan. La etiqueta homePresetInfoLbl solo da conteos (GPresets.java:571-597). Medido con los presets reales: demo 1 error + 12 avisos + 3 info; lista completa 1 error + 16 avisos + 4 info; Recompensas 8 avisos. Diez de los doce avisos de la demo son wf_glassdoor x8 y wf_game_upcounter2 x2 «sin wired», probables falsos positivos.
- Dónde: Inicio, PASO 2, junto a homePresetInfoLbl. Se envuelve la etiqueta en un HBox y se añade UNA píldora gp-pill gp-pill-sm a su derecha, con las clases lblred/lblorange/lblgrey/lblgreen que ya existen en styles.css. El detalle va en el tooltip. Maquetas renderizadas: shots/mock_a_roja.png y shots/mock_b_verde.png.
- Implementación: 1) En PresetValidator, añadir una función pura resumir(JSONObject, List<String[]>) que agrupa las líneas «esta en furni pero no en wired» por clase, cuenta error/aviso/info, arma la píldora y calcula el inventario por sección (wired.triggers, selectors, conditions, effects, addons, variables). 2) En GPresets.revisarPreset (src/GPresets.java:570), tras validar en el hilo PresetReview, hacer Platform.runLater y fijar texto, clase de color y tooltip de un nuevo Label homePresetCheckLbl (gpresets.fxml, con fx:id); el REGISTRO queda solo para errores. 3) Llamarlo también en la rama de éxito de checkPresetFolder (tras importer.setPresetConfig, GPresets.java:827) y en reloadPresetsClick (:734). Si la recarga falla (failedModified, :834), píldora roja «No se puede leer» con el motivo en el tooltip. 4) Añadir wf_glassdoor y wf_game_upcounter a SIN_ENTRADA en PresetValidator.java y en wiredtool/conocimiento.py, solo tras confirmarlo con el usuario. Leer siempre el JSON crudo con Files.readAllBytes (quitando el BOM) y nunca con PresetConfigUtils.loadPreset*, por el efecto sobre WallItems.
- Riesgo: Bajo. Es solo lectura y corre en hilo aparte. El único cambio de estructura es el HBox en el FXML, que conserva el fx:id. El riesgo real es la fatiga de píldora: si siempre sale naranja por falsos positivos, se ignora (de ahí el paso 4 y el agrupado). No cambia ninguna importación.
- Verificación offline: Hecho: PresetValidator compilado contra org.json del jar y ejecutado sobre los 6 JSON de /home/user/wired (t/RunVal.java). El prototipo t/proto/Proto.java produce píldora roja «1 error · 12 avisos», inventario «402 wireds: 49 causantes · 37 selectores · 65 condiciones · 215 efectos · 24 extras · 12 variables» y un tooltip de 8 líneas en vez de 16, todo en 84 ms. Falta hacer: pruebas unitarias de resumir con JSON sintéticos (vacío, solo info, avisos, errores, JSON roto); render con tr/ui/render.sh de los 4 colores y de «No se puede leer»; compilar con --release 8. En el hotel solo queda comprobar que wf_glassdoor y wf_game_upcounter2 no necesitan entrada de wired.

### Idea 2 · Soltar un .json en la ventana para agregarlo (y sin BOM) [S]
- Problema: Los presets llegan de fuera (generados y descargados), y hoy hay que abrir «Carpeta», copiar el archivo y esperar a «Recargar» o al vigilante (2 sondeos de 2 s). Además, un .json guardado con BOM (Bloc de notas «UTF-8 con BOM», PowerShell 5 con -Encoding UTF8) no carga: loadPresetOrThrow (PresetConfigUtils.java:98-105) usa readAllLines y JSONObject, y falla con «A JSONObject text must begin with '{' at 1 [character 2 line 1]» (reproducido en t/Bom.java). La vista previa en cambio sí quita el BOM (SalaRenderer.java:111). La lista de presets vacía no dice qué hacer. El patrón de arrastrar y soltar ya existe en PixelArtController.java:126-127, pero esa pestaña está oculta.
- Dónde: Sin controles nuevos. La ventana entera acepta soltar, enganchado a la Scene de presetListView, y el texto de la lista vacía hace de pista (placeholder de ListView, maqueta en shots/mock_lista_vacia.png). El resultado sale en el REGISTRO.
- Implementación: Clase pura PresetFiles.agregar(Path origen, Path carpeta), prototipo en t/drop/PresetFiles.java. Comprueba la extensión y el nombre (misma regex del exportador, [^<>:"/\\|?*]*), lee los bytes, quita el BOM, parsea el JSON y exige furni[], wired{} y bindings[] (lo que pide el constructor de PresetConfig). Si el preset ya existe y es distinto, mueve el viejo a .json.bak (misma política que PresetConfigUtils.savePreset:76) y escribe vía .tmp más move; si es idéntico, no hace nada. En GPresets.initExtension (tras :310) fijar setOnDragOver/setOnDragDropped en presetListView.getScene(). Al terminar, updateInstalledPresets() y selectPresetLater(nombre), como PixelArtController.java:487. Si reemplaza el preset elegido, el vigilante lo recarga solo (la idea 4 dirá qué cambió). NO construir PresetConfig para validar: WallItems.setPending (PresetConfig.java:67) pisaría los furnis de pared pendientes. El arreglo de raíz del BOM en loadPresetOrThrow conviene coordinarlo con quien audite infra.
- Riesgo: Bajo-medio. Escribe en la carpeta de presets, pero solo con un gesto explícito y conservando el .bak. En Windows, nombres que solo difieren en mayúsculas apuntan al mismo archivo, caso cubierto por la comparación de bytes. Soltar archivos no se puede probar en modo headless.
- Verificación offline: Hecho: el prototipo corrió con carpetas temporales y dio BOM → nuevo, mismo archivo → igual, versión nueva → reemplazado + .bak creado, JSON roto / sin claves / .txt → error con motivo, y la carga posterior con el mismo código de loadPresetOrThrow OK. Falta hacer: pruebas unitarias equivalentes y render del placeholder (hecho en la maqueta). El gesto de soltar no se prueba sin ventana real; se cubre revisando el código contra PixelArtController.

### Idea 3 · Tiempo estimado (antes y durante el pegado) y avance más calmado [S]
- Problema: Los pegados son largos y no dicen cuánto falta. Estimación a partir de las pausas fijas del código, sin medir en el hotel: la demo (450 furnis, 402 wireds) tarda unos 6,3 min y la lista completa (2.244 furnis, 482 wireds) unos 15-19 min como cota alta. Las pausas son dropFurni 150 ms (230 ms con BC), moveFurni 60+40+60 ms por apilable y saveWired mínimo 300 ms más la confirmación. Utils.sleep suma el valor de la barra Velocidad (confirmado en el bytecode). Esa barra no dice qué cuesta en tiempo. progress() (GPresetImporter.java:177-217) solo da «Fase (n/total)». Además, cada 50 elementos escribe una línea NARANJA, el color de aviso, en el registro (:1507 y :1392), así que un pegado normal parece lleno de avisos.
- Dónde: Franja inferior existente: texto de progressLbl con el tiempo restante (maqueta shots/mock_eta_franja.png, cabe sin mover nada). Tooltip del botón «Pegar en esta sala» y tooltip de la barra Velocidad con el tiempo estimado. Si aparece, el diálogo askPaste de SimpleFlow (:101-160) añade una línea al cabecero.
- Implementación: Clase pura EtaPegado sin dependencias. a) estimarMs(furniAColocar, apilables, wireds, extraMs, bc), con las constantes del código. Si en Diagnosticos/historial.tsv hay una importación anterior del mismo preset con duración, usar ese tiempo real; para eso se añade « seg=%d» al final de string5 (GPresetImporter.java:1855), que es la columna «detalle» y no rompe el TSV. b) restante(fase, n, total, ahora): tasa medida de la fase en curso (se reinicia cuando cambia el texto de fase en progress()), más las fases siguientes escaladas por tasa medida / tasa nominal; se oculta si faltan menos de 20 s o n < 5. Engancharlo en GPresetImporter.progress (devuelve el sufijo y se lo pasa a extension.updateProgress) y quitar las líneas periódicas naranja (:1392, :1507), o pasarlas a gris, porque la barra ya lo muestra.
- Riesgo: Bajo, solo texto. No cambia el ritmo de envío ni el orden de nada. El tiempo puede errar si el hotel va lento, por eso se mide la tasa real y se muestra con «~».
- Verificación offline: Hecho: prototipo en t/proto/Proto.java. Con la demo y barra 22 da 6,33 min; 150 de 402 wireds en 120 s da «~3 min». Falta hacer: pruebas unitarias con secuencias simuladas de progreso (rápidas, lentas, cambio de fase, n < 5) y render de la franja (hecho). En el hotel solo se puede validar comparando con la fila TOTAL del diagnóstico real; no se pudo medir aquí.

### Idea 4 · Qué cambió al recargar un preset (una línea, comparación por contenido) [S]
- Problema: El vigilante (GPresets.java:773-840) recarga cuando cambia la fecha del archivo y solo dice «recargado porque cambió el archivo (N furnis, WxH)» (src/GPresets.java:898). El usuario no sabe si es la versión nueva (por ejemplo si soltó la _demo en lugar de la completa) ni qué tocó el generador. La herramienta wiredtool comparar existe, pero solo en Python y fuera del programa.
- Dónde: La línea verde ya existente del REGISTRO y el tooltip de la píldora de la idea 1. Sin controles.
- Implementación: Clase pura PresetDiff.resumen(JSONObject viejo, JSONObject nuevo), que porta wiredtool/comparar.py. Clave de furni: clase + x + y + z·1000 + rotación + estado. Wired: clase@x,y,z con firma options|config|userSources|furniSources|delay|nº de items. Variables por nombre. Esto no depende de los ids, que cambian entre generaciones. Guardar en GPresets el JSON crudo de la última lectura (ya lo lee revisarPreset) y llamar a la función en la rama de recarga del vigilante, antes de setPresetConfig (GPresets.java:826-827). El detalle va al tooltip, con las primeras 6 diferencias y sus coordenadas. Es además el primer ladrillo de la decisión A (actualizar solo los wireds cambiados).
- Riesgo: Bajo. Un furni movido sale como −1/+1 (aproximado), y se puede afinar después. Usa JSON crudo, no PresetConfig, para no tocar WallItems.
- Verificación offline: Hecho: el prototipo en t/proto/Proto.java sobre una variante de la demo (1 texto de wired cambiado, 1 furni movido, 1 nuevo) da «+2 furnis, -1 furnis, 1 wireds cambiados» en 82 ms. Falta hacer: pruebas unitarias con pares de JSON, y comprobar paridad con `python -m wiredtool comparar` sobre los JSON reales.

### Idea 5 · Resumen de la última importación por preset (persistente y legible) [M]
- Problema: Al terminar, finishReport (GPresetImporter.java:1306-1362) manda un susurro cortado a 240 caracteres y homeInfo. Enseguida lo pisan «Diagnostico guardado en la carpeta Diagnosticos (…)» (:1860) y el aviso de :sugerir 1,5 s después de FurniMemory. Lo que no se colocó solo queda en un diag_*.txt escrito para «pásale esto a Claude» (máximo 60, Diagnostics.java:392). Al día siguiente no se sabe si el preset elegido se pegó completo.
- Dónde: Tooltip de la píldora (idea 1) y tooltip de cada elemento de la lista de presets. Segunda línea de homePresetInfoLbl. Texto final de la franja inferior: «Listo: 2.201/2.244 furnis · 118/120 wireds» en vez de «Listo, con omisiones (ver el registro)» (:1345).
- Implementación: No guarda nada nuevo: ya existe Diagnosticos/historial.tsv (Diagnostics.java:265-270). Columnas: fecha, preset, tipo, resultado, detalle = «modo=ALL furni=2201/2244 wired=118/120 fallos_wired=2 rechazados=0», archivo; no se poda. Clase pura ResumenImportacion.leer(File tsv, String preset): última fila con tipo=importacion y ese preset. Si el archivo diag existe, reutilizar FurniMemory.parseFailed (FurniMemory.java:109) para listar las clases que faltan. Llamarla desde selectPreset en hilo aparte y al terminar la importación. En finishReport, reemplazar el texto fijo por los conteos (colocados = ids presentes en realFurniIdMap). Con la idea 3 se añade seg= para mostrar la duración.
- Riesgo: Bajo-medio. Depende del formato del TSV, así que se parsea de forma defensiva y, si no se entiende, no se muestra nada. Un preset renombrado queda sin datos. Una línea de estado más clara no cambia el flujo de importación.
- Verificación offline: Falta hacer: pruebas unitarias del parser con TSV sintéticos generados con el mismo formato que Diagnostics.writeReport, más filas corruptas, archivo ausente y varios presets mezclados; render de la tarjeta (hecho en la maqueta de la idea 1). No hecho: el flujo completo necesita el hotel, y aquí solo se verificó leyendo el código que el TSV se escribe en cada importación (string5 no es nulo).

### Idea 6 · Vista previa viva: se redibuja sola y muestra la baldosa bajo el cursor [M]
- Problema: VistaPreviaVentana solo dibuja al abrir, al elegir o al cambiar una casilla: recargarLista() solo se llama en abrir() y no hay Timeline ni vigilante. Tras soltar o regenerar un preset hay que volver a elegirlo. Además, las notas de prueba citan pilas por coordenadas («(2,54)» y «(35,51)» en LISTA_COMPRA_INDIVIDUAL.md; el validador también dice «pila (50,58)»), pero la imagen de la sala (51×61 baldosas, 3256×1844 px) no muestra coordenadas.
- Dónde: La ventana «Vista previa» que ya existe. Sin controles nuevos: se redibuja sola y la línea de estado (o un tooltip) sigue al cursor.
- Implementación: a) Un Timeline de 1,5 s en VistaPreviaVentana que compara lastModified+length de presets/<nombreActual>.json y llama a redibujar(). El turno AtomicInteger de dibujar() ya descarta resultados viejos y los iconos que faltan quedan en la caché estática ICONOS (SalaRenderer.java:~273), así que no hay redescargas. b) SalaRenderer.Resultado expone el desplazamiento y la escala de render() (28−d, 28−d2, d8) y las alturas (Sala.altura). Un onMouseMoved del ImageView invierte la proyección (x−y = sx/32, x+y = (sy+32·h)/16, con h la altura del suelo) y lista los furnis de esa baldosa, ordenados por z, con la abreviatura de su sección de wired.
- Riesgo: Bajo, solo lectura. La inversa es exacta en salas planas, y la del usuario es plana (el modelo solo tiene '0' y 'x'). Con desniveles hay ambigüedad por oclusión (46 de 576 baldosas en una sala escalonada simulada) y se resuelve eligiendo la más alta.
- Verificación offline: Hecho: render de los 6 JSON reales con SalaRenderer sin sprites, sin excepciones y en 3-119 ms (t/RenderTime.java). Prueba numérica de la inversa: 576 de 576 baldosas en sala plana, 530 de 576 en escalonada (los fallos son oclusión). Falta hacer: pruebas unitarias de cambio(File, firma) y de la inversa con ida y vuelta por baldosa. El Timeline y el mouse-move no se prueban en headless; se cubren con el render y la revisión del código. El refresco automático (a) es S por sí solo; la lectura de coordenadas (b) es la parte M.

### Idea 7 · Comparar la sala con el preset (solo lectura) [M]
- Problema: Tras un pegado con omisiones, o después de jugar (jugadores o staff mueven cosas), no hay forma de saber qué distingue la sala del preset. «¿Tengo los furnis?» (GPresets.java:653-690) solo mira el inventario, y el emparejado sala–preset solo existe dentro del modo Wired y de forma privada (detectExistingRoot, GPresetImporter.java:1952; findRoomFurni, :2081; applyWiredOnlyFilter, :2037).
- Dónde: Menú contextual (clic derecho) en la lista de presets: «Comparar con la sala». Resultado en el REGISTRO, una línea más hasta 6 de detalle. Sin controles visibles.
- Implementación: Clase pura SalaVsPreset.comparar(List<Item> preset, List<Item> sala, rx, ry, rz), prototipo en t/cmp/SalaVsPreset.java. Los items del preset salen del JSON crudo (clase → typeId con FurniDataTools.getFloorTypeId) y los de la sala de FloorState.getAllFurni() (typeId, tile, z, facing). Origen: 0,0 si el preset trae floorPlan (se pegó con piso); si no, la posición que devuelve detectExistingRoot, que habría que extraer como función pura (hoy es privada y usa FloorState), o exportOrigin. No envía paquetes: usa el estado que G-Presets ya sigue.
- Riesgo: Medio-bajo. El origen es la parte frágil: si se pegó en otra posición, hay que detectarlo, y para pilas con muchas alturas hay que comparar z con tolerancia. Extraer detectExistingRoot a una función pura sin cambiar su resultado es el único toque al importador.
- Verificación offline: Hecho: el prototipo corrió sobre lista_compra_individual_8.json (2.244 furnis) con una sala simulada de 12 quitados y 3 movidos y devolvió «2229/2244 en su sitio, 3 movidos, 12 faltan {…}» en 57 ms. Falta hacer: pruebas unitarias con salas sintéticas (vacía, idéntica, con extras, con pilas, con otro origen). No se puede probar la fuente real de la sala sin el hotel.
