# G-Presets mejorado

Cambios sobre G-Presets 1.5.4-beta-10. Jar: `GPresets_mejorado.jar` (se entrega aparte; no está en git).
Nada de esto se probó dentro de G-Earth ni en el hotel: solo compilación, `-Xverify:all`, carga de clases, pruebas offline y traducciones reales con Google desde un sandbox.

## Qué trae
1. **Avisos restaurados.** Se quitó el filtro de susurros: "Colocando…", "Configurando…", "Importando…" salen como en el original.
2. **Revisión silenciosa del preset** (`PresetValidator`): al elegir un preset lo revisa; solo escribe en el registro y susurra únicamente si hay errores.
3. **Traductor (pestaña Extras)**
   - **Detección de idioma** nueva (`Slang`): puntuación por palabra repartida entre idiomas, marcas ortográficas, umbrales prudentes. Falsos "es mi idioma" en 9 idiomas: 0 en los corpus de prueba (antes 0,5-0,8 %); acierto estricto 36→78 % en el corpus propio.
   - **Alemán**: diccionario de abreviaturas/jerga 39→424 entradas (`res/.../de.txt`) y 753 palabras de detección. **Inglés**: +551 entradas, 20 integradas dudosas borradas, +553 palabras.
   - **Lo que escribes → alemán** (`EstiloDe`): convierte el "Sie/Ihnen/Ihr" que suele sacar Google a "du" cuando no es ambiguo, tono de chat, arregla precios rotos ("30 °C"→"30c"). Archivo editable `Memoria/aleman_chat.txt`.
   - **Lo que escribes → inglés**: `NivelB2` corregido (`permit=let`, a/an, apóstrofos que formaban otra palabra…). **Nuevo "Inglés C2"** (`NivelC2`, ~700 reglas, `Memoria/ingles_c2.txt`): aparece como entrada en la lista "Traducir lo que yo digo a…", justo debajo de "Inglés". Respeta el límite de 100 caracteres del chat.
   - **Ritmo de escritura** (`Escritura`): lo que escribes sale tras una espera según su largo (~15 caracteres/s, 350 ms de pausa, máximo 3,5 s, descontando lo que ya tardó Google). Ajustable o apagable en `Memoria/escritura.txt`.
4. El contador "Entiende N modismos" de la interfaz refleja ahora los diccionarios nuevos.

## Qué NO está hecho (y por qué)
- **Mejoras de importación/exportación/interfaz de la auditoría**: no se aplicaron. Los agentes que las implementaban se cortaron por el límite semanal de uso. La auditoría completa (≈85 hallazgos verificados, con evidencia y arreglo propuesto) está en `informes/AUDITORIA.md`. Los más valiosos: excepciones en escuchas que matan la extensión, esperas de 5 s por wired con `"0"` (≈8,5 min en la demo), furnis en x=0/y=0 sin altura del preset, z truncada 1 centésima, el importador no se entera de que sales de la sala.
- **Tubería del traductor** (proteger precios/emoticonos, idioma de origen, cola con orden): no entregada. `Traductor`/`ChatTraductor` solo llevan los enganches de C2 y escritura.
- **Nivel C2 sin segunda revisión**: la primera revisión halló 18 defectos (inversión de "I'll give you X for Y", "lo siento" de pésame, negaciones…). La versión incluida los corrige (comprobado con las reproducciones del revisor y 65 salidas reales de Google), pero la revisión de esa versión no llegó a ejecutarse.
- Alemán, inglés y C2 no los revisó ningún hablante nativo.

## Archivos
- `src/`: código nuevo propio (`EstiloDe`, `NivelC2`, `Escritura`). `PresetValidator.java`.
- `res/extension/tools/slang/`: diccionarios y palabras de detección (`de`, `en`, `marks_de`, `marks_en`).
- `informes/*.patch`: cambios sobre el descompilado CFR crudo de `Slang`, `NivelB2`, `Traductor`, `ChatTraductor`, `ExtrasController`, `GPresets`. `informes/INFORME_*.md`: informes de cada pieza con sus medidas.

## Reconstruir
```
java -jar cfr.jar GPresets.jar --outputdir src
# quitar restos de CFR ((Object), '?[]', try/finally raros) y aplicar los .patch; copiar src/ y res/ de aquí
javac -encoding UTF-8 --release 8 -cp GPresets.jar:<javafx> -d cls <fuentes>
cd cls && jar uf ../GPresets_mejorado.jar $(find . -type f)
```
