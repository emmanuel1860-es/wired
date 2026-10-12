#!/bin/bash
# Uso: render.sh <carpeta_ui> <archivo.fxml> <salida.png> [ancho alto] [pestana]
D="$(cd "$(dirname "$0")" && pwd)"
[ -f "$D/rcls/Render.class" ] || javac -nowarn --module-path /usr/share/openjfx/lib --add-modules javafx.controls,javafx.fxml,javafx.swing -d "$D/rcls" "$D/Render.java"
exec xvfb-run -a -s "-screen 0 1600x1200x24" java -Dprism.order=sw -Dfile.encoding=UTF-8 --module-path /usr/share/openjfx/lib --add-modules javafx.controls,javafx.fxml,javafx.swing -cp "$D/rcls" Render "$@"
