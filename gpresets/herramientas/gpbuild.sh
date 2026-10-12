#!/bin/bash
# Uso: gpbuild.sh <carpeta_src> <carpeta_clases_salida>
# Compila SOLO los .java de <carpeta_src> contra el jar original (el resto de clases sale del jar) y copia <carpeta_src>/../res a la salida.
D="$(cd "$(dirname "$0")" && pwd)"; SRC="$1"; OUT="$2"
FX=$(ls /usr/share/openjfx/lib/*.jar | tr '\n' ':')
rm -rf "$OUT"; mkdir -p "$OUT"
javac -encoding UTF-8 --release 8 -nowarn -Xmaxerrs 500 -cp "$D/orig.jar:$FX" -d "$OUT" $(find "$SRC" -name '*.java') 2>&1 | grep -v -E '^(Picked up|Note:)'
[ -d "$SRC/../res" ] && cp -r "$SRC/../res"/. "$OUT"/
find "$OUT" -name '*.class' | head -1 | grep -q . && echo "OK -> $OUT ($(find "$OUT" -name '*.class' | wc -l) clases)" || echo "FALLO: no hay clases"
