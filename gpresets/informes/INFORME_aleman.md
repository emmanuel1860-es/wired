# INFORME de (datos de aleman para Slang): de.txt + marks_de.txt

Archivos: `res/extension/tools/slang/de.txt` (392 entradas nuevas, 3 corregidas, 7 borradas; el diccionario aleman pasa de 39 a 424)
y `marks_de.txt` (753 palabras). Ningun .java tocado. Corpus: `corpus/de_pos.tsv` (362 lineas de.), `de_neg.tsv` (431, otros 8 idiomas
+ sv/da/no), `de_pos2.tsv` (142) y `de_neg2.tsv` (245) escritos despues, `de_live_cache.jsonl` (las 120 llamadas a Google).

## Auditoria de lo integrado (borrado o corregido en de.txt)
- `alter=Freund` borrado: "ein alter Thron" salia "trono de un amigo" y "dein Alter" salia "tu amigo" (medido en Google). Como vocativo Google ya lo traduce bien.
- `is`, `net`, `u`, `ok` borrados: son palabras del ingles/neerlandes (is, net, u) o no cambian nada (ok) y sumaban puntos de aleman a esos idiomas.
- `ne=nein` borrado ("ne Lampe" = "eine"), `wg` borrado (ambiguo: "wie geht's"/"wegen").
- `mega`: "sehr" -> "super" ("mega raum" salia "muy espacio"; ahora "gran salon"). `digga`: Freund -> Kumpel. `bb`: "bis bald" -> "tschuess".
- Se mantienen: lg vllt vlt kp ka hdl mfg nix iwie iwas iwann gn8 n8 hdgdl thx dnk bitte omg kb nö jo jup krass geil moin servus tschü nd nich.

## Que contiene (todo en aleman estandar y en la misma funcion gramatical)
Abreviaturas (glg ihdl jz jtz ggf bzw usw dh zb idr uvm eig ...), contracciones (gibts, gehts, "wie gehts", haste, biste, kannste, hamma,
nen/ner, aufm/vorm/mitm...), escritura sin umlaut (fuer, ueber, schoen, moechte, koennen, natuerlich, spass, strasse... ~120), dialecto
(koa, ned, nöd, isch, icke, oida, wurscht, bissl, grüezi, pfiat di), jerga (Kumpel, lowkey, boah, juhu, huhu, moinsen), anglicismos
(afk, gtg, idk, tbh, btw, np, ez, irl, pls) y Habbo (taler->Credits, nfs, wtb/wts/wtt, mod, traden, laggt, reporten, joinen, cheaten).
Precios tipo "5c"/"10k" NO se tocan (claves literales, no se pueden generalizar). Los comentarios van en linea propia: `mezclarRecurso` no admite comentarios al final de la entrada (un "alter = - # x" se leeria como valor "- # x").

## Deteccion (comando det; tune = lineas pares, hold-out = impares; estricto/flojo = % de lineas de. detectadas como de)
| conjunto | antes | despues |
|---|---|---|
| de_pos tune (181) | 41,4 / 70,7 | 76,2 / 90,6 |
| de_pos hold-out (181) | 36,5 / 70,7 | 76,8 / 91,2 |
| de_pos2 (142, escrito despues) | 50,0 / 76,1 | 89,4 / 96,5 |
| 1 palabra (25) / 2-3 (132) / 4+ (205) | 0/20, 16/57, 59/86 | 0/44, 61/86, 96/99,5 |
- Lineas alemanas que el modo flojo atribuia a OTRO idioma (se normalizaban con el diccionario equivocado): 52 -> 23 en de_pos, 22 -> 3 en de_pos2.
- Falsos positivos de en las 676 lineas de otros idiomas: estricto 1->1 y flojo 6->6 en de_neg; 1->1 y 5->5 en de_neg2 (son las MISMAS lineas que ya fallaban de
  serie: "wie heeft er hc", "zit hier niemand", "jeg er ny her"; vienen de marcas integradas como wie/er/hier). Cero lineas no alemanas cambian de normalizacion
  frente al original. Deteccion por idioma sin cambios, salvo neerlandes estricto 55->56 (el borrado de `is` le quita ruido). Coste: +0,1 ms por mensaje.
- Mensajes de 1 palabra siguen sin llegar a "estricto" (hace falta >=4 puntos; no es dato, es umbral de Slang.java).
- marks_de: se descartaron por chocar con otros idiomas (con listas de palabras nl/en/es/pt/fr/it/tr/fi/sv/da/no): niemand, moderator, waren, diamanten,
  handel, melden, gewonnen, verloren, beste, geld, oma/opa, uns, total, warm, leer, paar, ding... Quedan con riesgo bajo: viel (abreviatura finesa), rein (fr), taler (danes).

## Traduccion de->es con Google real (120 de 120 llamadas, una linea por llamada, sl=auto como en produccion)
Se compararon 32 lineas del hold-out (original / normalizada / normalizada por el codigo original) + 20 sondas. Juicio mio, no de nativo.
- MEJORA clara: kp (sin traducir -> "no tengo idea"), lg ("LG de Berlin" -> "Saludos cordiales"), gn8, hdgdl, "hdl bb" (sin traducir), afk (decia "desconcertado"),
  hamma ("hamm perdio" -> "perdimos"), laggt (detectado como ingles -> "entrecortado"), lowkey ("Discreto" -> "de alguna manera"), huhu ("jaja"), mod -> Moderator,
  taler -> Credits ("taleros" -> "creditos"), "haste mal nen taler" (Google lo creia danes: "prisa mal nen taler"), "moin digga" (lo creia noruego: "menos digga"),
  "i hab koa zeit" (lo creia tet: "Tengo koa zeit" -> "no tengo tiempo"), "ez gewonnen" -> "leicht gewonnen" ("ganado facilmente"), bb, zb, wars, traden.
- EMPATE (Google ya lo sabe): sry, bock, adden, gehts, gibts, tschau, bzw, kannste, dh, evtl, ggf, geil, krass, mega, wurscht, pennen, moechte/schoen/ueber (umlaut), kein ding.
- EMPEORA, y por eso se RETIRARON del archivo: rofl->haha (cambia el sentido), scam/scammer/gescammt->betrug (Google decia "estafo", mejor), brb ("volvere pronto, me ire"),
  noob->Anfaenger, mahlzeit->guten Appetit, "alter schwede"->Mensch, y las frases "ne,"/"alter,..." (empate, mas riesgo). Tambien se quitaron sry/bock/adden/joa (sin ganancia).
- Hallazgo: en frases cortas lo que mas rompe es que Google (sl=auto) cree que el aleman coloquial es otro idioma; normalizar a aleman estandar lo arregla.
  Sugerencia para el integrador (no es mio): si Slang.detect devuelve "de", pasar sl=de a Google; hoy Traductor.traducirSinMemoria siempre pide "auto".

## Riesgos y limites
- Los corpus los escribi yo (no son chats reales); tune/hold-out comparten autor y mis marcas salen de conocimiento del idioma, no de las lineas, pero hay sesgo posible.
  de_neg2 destapo una colision ("beste" es neerlandes): se arreglo y las cifras son posteriores.
- Las claves tambien suman 1 punto de deteccion: las que coinciden con el diccionario ingles (afk gtg idk tbh btw np irl pls gz nfs wtb wts wtt mod lowkey) suman tambien a "de"
  en textos ingleses; no hubo cambios de resultado en 140 lineas inglesas, pero es la fuente de ruido a vigilar si se amplia.
- Danes/noruego/sueco: "taler" (dict) y "hallo" etc. pueden dar "de" flojo en esos idiomas (no son hoteles de Habbo). "gerne/billig/klar/gar/hör" se quitaron por eso sin perder recall.
- "taler -> Credits" asume que el usuario recibe en espanol/ingles/portugues (creditos); en otros destinos Google lo adapta.
- Con el umbral actual de Slang.java, 12 de 362 lineas alemanas (3 de 142) no se normalizan porque otro idioma empata ("des is mir wurscht", "haste mal ne minute").

## No verificado
- Jar real en G-Earth (solo harness con curl, no HttpURLConnection); el camino de salida (Extras.onSendChat, EstiloDe) no se probo con aleman.
- Destinos distintos de es; otras longitudes de mensaje; revision por un hablante nativo de dialecto suizo/austriaco (koa, wos, isch, nöd, heast, leiwand, ois: segun mi conocimiento).
- Que la moneda de habbo.de se llame "Taler" y que "Mod"/"Wired" se escriban asi en ese hotel: supuesto del encargo, no comprobado.
