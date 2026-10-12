#!/bin/bash
# Uso: assemble.sh <salida.jar>
# Compila GPresets.java (sin filtro de susurros + revision silenciosa) + PresetValidator + clases del traductor (tr/src) y las mete en una copia del jar original.
D="$(cd "$(dirname "$0")" && pwd)"; OUTJAR="$(realpath -m "$1")"
FX=$(ls /usr/share/openjfx/lib/*.jar | tr '\n' ':')
rm -rf "$D/final_cls"; mkdir -p "$D/final_cls"
javac -encoding UTF-8 --release 8 -nowarn -cp "$D/orig.jar:$FX" -d "$D/final_cls" \
  "$D/gp/extension/GPresets.java" "$D/gp/extension/tools/PresetValidator.java" \
  $(find "$D/final/src" -name '*.java') 2>&1 | grep -v -E '^(Picked up|Note:)'
[ -f "$D/final_cls/extension/GPresets.class" ] || { echo "FALLO la compilacion"; exit 2; }
cp -r "$D/final/res"/. "$D/final_cls"/
cp "$D/orig.jar" "$OUTJAR"
( cd "$D/final_cls" && jar uf "$OUTJAR" $(find . -type f | sed 's|^\./||') )
echo "OK -> $OUTJAR  ($(unzip -l "$OUTJAR" | tail -1))"
