# Herramientas de reconstrucción de G-Presets (copiadas de la sesión del 12/10/2026)

Esperan un espacio de trabajo `tr/` con `orig.jar` (el GPresets.jar original) y las fuentes en `src/` (clases tocadas) y `res/` (recursos), JavaFX (`apt-get install openjfx xvfb`) y CFR.
- `fp.py`: prueba de fidelidad por método contra el bytecode original (`python3 fp.py orig.jar clases_nuevas --all`).
- `gpbuild.sh <src> <out>`: compila solo las clases tocadas contra el jar. `build.sh <src> <out> [live]`: igual + banco de pruebas del traductor; `live` usa `curl` porque Google bloquea a `HttpURLConnection` desde el sandbox. `run.sh`, `harness/`: banco de pruebas del traductor (`det`, `norm`, `b2`, `tr`…). `assemble.sh`: arma el jar final.
- `ui/render.sh` + `Render.java`: renderiza un FXML a PNG con `xvfb-run` (sin controladores). `ui/rr.sh` + `RenderReal.java`: arranque real (tema, CSS, PremiumLook, HideTabs); ajustar la ruta `S=` dentro de `rr.sh`.
Rutas absolutas del sandbox dentro de algunos scripts: adaptarlas.
