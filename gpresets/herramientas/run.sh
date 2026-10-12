#!/bin/bash
# Uso: run.sh <carpeta_de_clases> < entrada.tsv      (ej: run.sh base_cls)
D="$(cd "$(dirname "$0")" && pwd)"; C="${1:-$D/base_cls}"; shift 2>/dev/null
FX=$(ls /usr/share/openjfx/lib/*.jar | tr '\n' ':')
exec java -Dfile.encoding=UTF-8 -cp "$C:$D/orig.jar:$FX" extension.tools.Banco "$@"
