#!/bin/bash
# Uso: build.sh <carpeta_src> <carpeta_clases_salida> [live]
#   live = engancha CurlShim en Traductor.get (solo pruebas, porque Google bloquea a Java en este sandbox).
D="$(cd "$(dirname "$0")" && pwd)"; SRC="$1"; OUT="$2"; LIVE="$3"
[ -d "$SRC" ] || { echo "falta carpeta src: $SRC"; exit 2; }
FX=$(ls /usr/share/openjfx/lib/*.jar | tr '\n' ':')
TMP="$(mktemp -d)"; cp -r "$SRC"/. "$TMP"/
if [ "$LIVE" = "live" ]; then
  python3 - "$TMP/extension/tools/Traductor.java" <<'PY' || exit 3
import sys
p=sys.argv[1]; s=open(p,encoding='utf-8').read()
sig='private static String get(String string, int n) throws Exception {'
assert sig in s, 'no encuentro la firma de Traductor.get: adapta build.sh'
s=s.replace(sig, sig+'\n        if (true) return CurlShim.get(string, n);',1)
open(p,'w',encoding='utf-8').write(s)
PY
fi
rm -rf "$OUT"; mkdir -p "$OUT"
BANCO="$D/harness/Banco.java"; [ -f "$SRC/../harness/Banco.java" ] && BANCO="$SRC/../harness/Banco.java"   # cada agente puede tener su Banco ampliado en work/<id>/harness
javac -encoding UTF-8 --release 8 -nowarn -cp "$D/orig.jar:$FX" -d "$OUT" $(find "$TMP" -name '*.java') "$BANCO" "$D/harness/CurlShim.java" 2>&1 | grep -v -E '^(Picked up|Note:)'
# recursos (si los hay) junto a las clases
[ -d "$SRC/../res" ] && cp -r "$SRC/../res"/. "$OUT"/
rm -rf "$TMP"
test -f "$OUT/extension/tools/Banco.class" && echo "OK -> $OUT"
