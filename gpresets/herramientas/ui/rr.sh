#!/bin/bash
# Uso: rr.sh <ui_dir> <fxml> <out.png> [prop=valor ...]   (props: tab, w, h, report, premium, hide, theme, opacity, scale)
S=/tmp/claude-0/-home-user-wired/07f3ecc1-8f98-5c55-8b13-34fe1b96edab/scratchpad
A=$S/tr/audit/interfaz
UI=$1; FX=$2; OUT=$3; shift 3
PROPS=""
SCALE=1
for kv in "$@"; do
  k=${kv%%=*}; v=${kv#*=}
  if [ "$k" = scale ]; then SCALE=$v; else PROPS="$PROPS -D$k=$v"; fi
done
export JAVA_TOOL_OPTIONS=""
exec xvfb-run -a -s "-screen 0 1920x1400x24" java -Dprism.order=sw -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dglass.gtk.uiScale=$SCALE -Dui=$UI -Dfxml=$FX -Dout=$OUT $PROPS --add-opens javafx.graphics/javafx.scene.text=ALL-UNNAMED --add-opens javafx.graphics/com.sun.javafx.scene.text=ALL-UNNAMED --add-exports javafx.graphics/com.sun.javafx.scene.text=ALL-UNNAMED --add-exports javafx.graphics/com.sun.javafx.text=ALL-UNNAMED --module-path /usr/share/openjfx/lib --add-modules javafx.controls,javafx.fxml,javafx.swing -cp $A/rcls:$S/tr/orig.jar RenderReal
