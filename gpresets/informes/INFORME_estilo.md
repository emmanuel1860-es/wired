# INFORME estilo: capa de estilo SALIENTE (NivelB2 para ingles, EstiloDe para aleman)

Archivos: `src/extension/tools/NivelB2.java` (reescrito), `EstiloDe.java` (de esqueleto a real), `harness/Banco.java` (+comando `de` y variable BANCO_DE),
`harness/Fuzz.java`, `bugs_en.py`, `check_de_pos.py`, `ref/*.txt` (copia legible de los dos archivos que crea el codigo). Firmas sin cambios:
`static String aplicar(String)`, `static File archivo()`, hook `archivoPrueba` y `main`. Solo ASCII (`\uXXXX`), Java 8, sin tocar Traductor/Extras. Sin UI, sin comandos, sin avisos.
Corpus en `corpus/estilo_*` (es_tune 30, es_hold 30, es_hold2 6, es_probe 18, formal_pos 66, formal_neg 24, formal_more 50, en_adversarial, live_cache, tablas_antes_despues).
Google real: 150/150 llamadas (60 tune, 23 sondas, 60 hold-out, 6 hold-out 2, 1 prueba), una por una, con curl.

## INGLES (NivelB2): lo que estaba roto, medido (38 casos, antes 6/38 bien, ahora 38/38)
- `permit = let` rompia el sustantivo ("a permit" -> "a let") y el verbo ("let me to enter"): ahora `permit = allow`, solo si es verbo; `a permit` queda.
- Sustantivo/verbo: "an attempt" -> "an try", "your purchase" -> "your buy", desire, permit. Ahora purchase/permit/attempt/desire (+ -s) solo se cambian en contexto de verbo.
- a/an tras sustituir ("an try"): se corrige el articulo segun el sonido (probado con entradas propias: `an apple`->`a pear`, `a hour`->`a minute`).
- Participios: "was selected" -> "was chose", "has demonstrated" -> "has showed": ahora chosen/shown tras have/be y tras the/a ("the selected item"). "was received" ya no da "was got".
- `however/therefore/thus/certainly/currently...` solo al EMPEZAR frase y con coma ("This is, however, wrong" ya no sale "this is, but, wrong"; "I'll sure not do it" desaparece).
- `immediately = right away` (y no si va "after/before..."), `additional = extra` ("an more item"), `in addition to = besides` ("also to the lamp"), `sufficient`, `inform/provide` (a frases: "keep me told").
- gonna/wanna: "I'm gonna the store", "we're gonna Paris" -> solo si lo que sigue es un verbo de una lista (go, buy, play...). "wanna" nunca ante articulo/Mayuscula.
- URLs, dominios, e-mails, @nombres, #tags y :comandos ya no se tocan ("http://buy.example.com/try" salia de purchase.example.com/attempt). MAYUSCULAS no se tocan ("dont TOUCH").
- Punto final: ya no se come "p.m.", "etc.", "Mr."; "..." y "?" siguen intactos; "No." se queda en "no". `Mr. Smith` ya no pasa a `mr.`.
- Minuscula inicial: antes bajaba TODO ("pablo is here"); ahora solo si la primera palabra es de funcion/interjeccion (lista de ~325: hello, thanks, what...). Nombres y verbos raros conservan mayuscula.
- Contracciones: "I will not" daba "I'll not" si se quitaba el parche; "What is it?" -> "what's it?"; "Let us know" -> "let's know". Corregidos; "will not" ya no pierde la mayuscula.
- Azar: `new Random(hashCode)` da la MISMA primera moneda a textos casi iguales (medido: 99,3 % de coincidencia en mensajes consecutivos; tasa de gonna entre 47 % y 72 % en vez de 60 %).
  Ahora semilla mezclada (SplitMix): 52,7 % y tasas 59-61 / 35 / 30 %. Sigue siendo determinista por mensaje (el mismo texto da el mismo estilo).
- Sospechosos que NO eran bug: "I'll -> ill", "we'll -> well", "he'll -> hell", "she'll -> shell", "it's -> its", "I'd -> id" no se generan nunca (72 variantes). Solo salen
  dont, cant, wont, im, youre, theyre, thats, whats, theres, heres, lets: grafias normales de chat; se mantienen (30 % por mensaje).
- Nuevo (Habbo y chat): `living room = room` (Google traduce "sala" asi), "Ha ha ha" -> "Hahaha", hello->hi, goodbye->bye, "Thank you (very much)" -> "thanks (a lot)" solo al empezar frase.
- Compatibilidad: formato intacto (activo, estilo_chat, `a = b`). Archivo v1 SIN tocar (hash SHA-256) -> se sustituye por el v2 (`# version = 2`). Archivo v1 que el usuario ya cambio ->
  se respeta; solo se arreglan en memoria las entradas viejas que rompian (permit=let, additional=more, immediately=right now) y se descartan inform/provide/concerning/as well as.
  Probado: v1 intacto, v1 con CRLF+BOM, v1 modificado, archivo minimo, basura, activo=no. Los guardas por palabra se aplican tambien a archivos viejos.

## ALEMAN (EstiloDe): `Memoria/aleman_chat.txt` (se crea sola; activo / estilo_chat / tutear; `a = b`; `^a = b` = solo al empezar frase)
- tutear (codigo, no tabla, porque `Sie` y `sie` son distintos): Ihnen->dir, Ihr/Ihre/..->dein/deine/.. (en medio de frase); Sie->dich tras preposicion, tras ich/wir/er/man, "Sie zu sehen";
  verbo+Sie -> forma de du ("Können Sie" -> "kannst du", "Haben Sie" -> "hast du"); imperativo si no hay "?" ("Helfen Sie mir" -> "hilf mir", "Seien Sie" -> "sei"); "wenn Sie können" ->
  "wenn du kannst" (verbo final), ", Sie haben" -> ", du hast", "und Sie?" -> "und du?". ~230 verbos (irregulares con cambio de vocal, prefijos separables). NO toca: "Sie" al principio de frase
  (puede ser ella/ellos), frases con "sich", "und/oder" dentro de la subordinada, verbos desconocidos, "sie" minuscula. Cuando duda, deja la forma formal (siempre correcta).
- Tabla (54 entradas): Vielen Dank->danke, Auf Wiedersehen->tschüss, ^Guten Tag->hallo, ich habe->hab, ^ich bin->bin (no ante nombre: "ich bin Pablo" intacto), gibt es->gibts, geht es->gehts,
  wie wäre es->wie wärs, es tut mir leid->tut mir leid, ich möchte->ich will, sehr gut->super, wirklich->echt (no tras "nicht"), Entschuldigung->sorry, Wohnzimmer->Zimmer (Habbo)...
- Precios que Google rompe, reparados: "30 °C" -> "30c" (si no habla del tiempo), "50 Cent" -> "50c", "3,5.000" -> "3.5k". (Lo correcto seria enmascararlos ANTES de traducir, en Traductor; fuera de mi alcance.)
- Estilo: sin punto final (se conserva en "am 5." y "usw."); primera letra en minuscula solo si empieza por palabra de funcion o por una forma que el propio motor genero. Sustantivos y nombres intactos.
- Determinista e idempotente: 660 textos a mano y >2 millones de cadenas aleatorias (Fuzz.java, sin try/catch, por reflexion; 750 mil con el codigo final): 0 excepciones, 0 no idempotentes (el fuzz
  encontro un caso real -> la tabla se repite hasta punto fijo, max 4 rondas). URLs/@/:comandos protegidos. Nunca lanza (devuelve el texto).

## Resultados (Google real; juicio mio, no de nativo)
| medida | antes | ahora |
|---|---|---|
| 84 mensajes es->de: con Sie/Ihnen/Ihr en la salida de Google | 19 (23 %) | 0 |
| ...en el hold-out (36 mensajes, no usados para escribir reglas) | 10 | 0, todos bien conjugados |
| precios rotos / Wohnzimmer (84 mensajes) | 6 / 3 | 0 / 0 |
| suite formal->du (66 frases escritas antes del codigo; 3 expectativas corregidas despues) | 0/66 | 66/66 |
| frases con sie/Sie/Ihr que NO deben cambiar (24) | - | 0 cambios indebidos |
| en: 60 mensajes reales, distintos del NivelB2 viejo | - | 12/60 (5 de contenido: hi, thanks a lot, living room->room, Hahaha; el resto, otra moneda) |
| offline.tsv, 41 lineas b2 | - | 37 iguales; 4 distintas = 3 por la moneda nueva + additional->extra |
Ejemplos DE: "Stören Sie mich nicht" -> "stör mich nicht"; "Schreiben Sie mir, wenn Sie können" -> "schreib mir, wenn du kannst"; "Akzeptieren Sie 30 °C für die Pflanze?" -> "akzeptierst du 30c für die Pflanze?";
"Vielen Dank für Ihre Geduld" -> "danke für deine Geduld"; "Geben Sie mir Ihre Nummer und ich rufe Sie morgen an" -> "gib mir deine Nummer und ich rufe dich morgen an".

## Riesgos / NO verificado
- Aleman no revisado por un nativo; "ich will" por "ich möchte" y el "sorry" son decisiones de tono (editables en el archivo). Sin Sie al principio de frase ("Sie sind nett") se queda formal: es a proposito.
- Solo probado es->en y es->de (el usuario escribe en español). Google varia por dia; las cifras son de una sesion. Los 38 casos en y la suite formal los escribi al auditar: no son independientes
  (el hold-out de 36 mensajes si lo fue, salvo ajustes menores posteriores: lista de inicios, verbos extra, reglas R2/R3b del tuteo).
- Un archivo aleman_chat.txt guardado en ANSI (Bloc de notas viejo) rompe las vocales con tilde de las claves: no peta, pero esas entradas no casan. Sin G-Earth real: solo harness y JVM aislada (0,1 ms/mensaje).
