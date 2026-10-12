# INFORME agente "en" (datos de ingles para Slang)

Entregables (solo datos, sin tocar Java): `res/extension/tools/slang/en.txt` y `marks_en.txt`. Corpus en `corpus/en_pos.tsv` (267 lineas), `corpus/en_neg.tsv` (351), cache de Google `corpus/en_live_cache.jsonl`, scripts en `work/en/tmp/`.

## Que cambia
- `en.txt`: 551 entradas nuevas, 6 corregidas, 20 integradas borradas. Total EN fusionado: 817 (antes 286). `marks_en.txt`: 553 palabras nuevas (antes 230).
- Borradas (danan o no se pueden confirmar): `ig` (Instagram), `fu` (kung fu), `pull up` (literal), `fr` (for real / friend request / frances), `bc` (because / Builders Club), `ik` (neerlandes "yo"), `yeah`/`yea` (sarcasmo), `my b` ("my b-day"), `the goat` (animal), `nm`, `jp`, `ic` (RP), `ty!`, y sin confirmar en Habbo: `rev`, `revs`, `p2s`, `p2r`, `elim`, `gbs`. Si alguna es real en tus juegos, se repone en Memoria/modismos.txt (`en: rev = revive`).
- Corregidas: `noob/newb/nub/n00b` -> "newbie" (conserva el insulto), `smh` -> "shaking my head", `stfu` -> "shut up". `kys` y `wtf` se dejan: el sentido se conserva tal cual. `im out of` -> "I am out of" (antes "im out of money" daba "I am leaving of money").
- Nuevas: ortografia de no nativos (wut, wht, hw, wher, gud, luv, frnd, becuz...), sin apostrofo (dont, thats, theyre...; `im` NO, ver riesgos), numeros por letras con contexto (thx 4, how 2, wat 2, looking 4, come 2, 2 much), chat/juegos (ggwp, omfg, wyd rn, ngl, cba, innit, wagwan, nvm, c u, c ya), Habbo con contexto (`mm` solo en "a mm/need mm/mm pls", `pc` en "pc plz/pc for", `bc` solo en "bc i/im/u/it...", `fr` solo en "fr fr/no way fr/my fr/accept my fr", `the goat` solo en "is the goat", r8, hns, p2w, ono, creds, dmnds, tele me), siglas con puntos protegidas (r.i.p, u.s.a).
- No se tocan a proposito: hc, ltd, rares, furni, "5c" (Google los deja tal cual y un jugador de Habbo los entiende), `dias` (en espanol es "dias"), `wat/dat/dis/wen/wil/mod` como palabra suelta (chocan con nl/fr/de/es; ver abajo).

## Deteccion (det), baseline -> nuevo (corpus propio; HOLDOUT no se uso para ajustar)
| conjunto | n | estricto en | flojo en |
|---|---|---|---|
| positivo TUNING | 138 | 66,7% -> 76,1% | 97,1% -> 99,3% |
| positivo HOLDOUT | 129 | 63,6% -> 73,6% | 95,3% -> 96,9% |
Falsos positivos de ingles en negativos (otros idiomas): estricto 1/126, 0/119, 0/106 (TUNING/HOLDOUT/STRESS) igual que antes (el 1 es la frase artificial "bad hand war nur ein witz", solo palabras inglesas en aleman); flojo 5/12/26 igual que antes. Ninguna linea inglesa cae como otro idioma en estricto (0 antes y despues).
Contraste con corpus de OTROS agentes (de_neg, de_pos, c2_es: 850 lineas): 0 regresiones de deteccion estricta, 0 falsos positivos nuevos. Una primera version (con `such`, `find`, `sry`, `im`, `dat`, `mod`) rompia 5 lineas alemanas/neerlandesas y creaba 3 falsos positivos; por eso se quitaron.
El 24-26% de misses estrictos son mensajes de 1 a 3 palabras con una sola marca: no llegan a los 4 puntos que exige Slang.detect (decision de codigo, no de datos).
Normalizacion: sobre las 351 lineas negativas no cambia ni una salida respecto al baseline (0 diferencias). Sobre los 267 positivos cambian 107 lineas (baseline 93); 30 de ellas distintas del baseline.

## Prueba en vivo (119 de 120 llamadas, Google gtx via curl, en->es, texto crudo vs normalizado)
54 frases donde la normalizacion cambia el texto. Con el texto final exacto (48): mejor 26, igual 20, peor 2. Otras 6 se probaron con una variante casi igual (no el texto final exacto): 1 mejor (brb), 3 igual, 2 sin dato.
- Mejora clara (Google no entiende la sigla): c/o ("c/o 15 creds" queda sin traducir), wtt/wtb ("Wtb lampara..." -> "Quiero comprar..."), mm -> intermediario, tele -> "Teletransportame" (crudo: "telefoneame", Google lo tomo por rumano), afk ("estaba enojado" -> "lejos del teclado"), "accept my fr" ("acepta mi amigo" -> "solicitud de amistad"), "u r the goat" ("eres la cabra" -> "el mejor"), stfu, wyd rn (crudo sin traducir), ngl/tbh, ggwp, r8, sum1/evry1, m8, nvm, lemme, thx 4, "idk wat 2 do", brb, hns, cba.
- Igual: errores de ortografia que Google ya arregla (wat/dis/dat/wher/hw/luv/sory...). Por eso no hay entradas de una palabra para wat/dat/dis.
- Peor: "how old are you" limpio sale "cuantos anos tiene" (crudo "hw old r u" dio "tienes"); "to trade my ltd" sale "comercializar" donde el crudo daba "cambiar".
- Observado: con minusculas y sin signos Google devuelve minusculas sin tildes (igual en crudo). Capitalizar o poner ?/! antes de enviar mejoraria el estilo, pero es codigo.

## Riesgos y limites
- `im` -> "I am" se probo y se quito: `im` es aleman ("im Raum") y bajaba la deteccion de frases alemanas.
- Colisiones que ya existian en el EN integrado y que dejo: `wsp` (WhatsApp en es), `thx` (de, nl), `idk` (nl), `omg` (de), `u` (de), `pls`/`msg` (it), `lol` (fr), `wtf` (nl), `tele` (tele=TV en es). Solo suman 1 punto al ingles; ojo al fusionar con los datos de es/de/nl.
- Las MARCAS solo se pueden anadir, no quitar: el MARKS integrado de "en" trae palabras que son alemanas/neerlandesas (am, is, hard, bad, open, start, stop, help, game, man...). Quitarlas exige un cambio en Slang.java.
- En el EN integrado el normalizador usa la lista inglesa en mensajes no ingleses con siglas universales ("gg wp an alle" -> "good game well played an alle", "voce fala portugues" -> "voce ola portugues", "donne moi" -> "donne hei"): ya ocurria en el baseline, es de otros agentes.
- Los significados de Habbo (fr = friend request, hns, r8, p2w, tele = teleport, mm = middleman, mods) salen de mi conocimiento, no de una prueba en el hotel.

## No verificado
- Nada se probo dentro de G-Earth/Habbo; solo el harness (det/norm/tr) y Google gtx. El respaldo MyMemory/clients5 no se probo.
- Los 6 textos finales sin prueba exacta: "im going to eat be right back", "im fine thanks", "im so sorry", "see you tomorrow guys", "wat you want", "see you tomorrow tc".
- Direccion es->en (lo que escribes tu): solo se probo en->es. NivelB2 no se toco.
- Los corpus los escribi yo (no son chat real exportado); el HOLDOUT no es independiente del todo (misma mano, mismo dia). Miré su baseline una vez antes de ajustar.
