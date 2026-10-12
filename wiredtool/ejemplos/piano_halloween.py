#!/usr/bin/env python3
"""Genera un preset de piano (tabla de notas) en el MISMO formato que piano_100ms.json.

Formato (analizado de piano_100ms.json): cada nota/acorde es un bloque `room_invisible_block4` en (0,0,0)
con variables de furni {order, notas}. `order` = instante en unidades de 100 ms; `notas` = tecla 1..37.
Los bloques con el mismo `order` suenan a la vez (acorde, hasta 3). No hay wireds en el archivo: la logica que
lee la tabla vive en la sala. variables_map reutiliza los ids del piano original (notas/order/nextplay).

Supuesto SIN CONFIRMAR: tecla 1 = Do3 (MIDI 48), tecla 37 = Do6 (MIDI 84), cromatica. Cambiar BASE_MIDI si el piano real
empieza en otra nota.

Notacion de una cancion: "A5:2 G5:2 (D3 A3):8 R:4 C#4:6" -> nota:duracion en ticks (la duracion es el avance hasta la
siguiente), parentesis = acorde, R = silencio. Altura en notacion cientifica (C4 = Do central), # y b permitidos.
Uso: python3 piano_halloween.py salida.json
"""
import json, re, sys

BASE_MIDI = 48          # tecla 1 = Do3
TECLAS = 37
IDS = {"nextplay": "244585", "order": "244584", "notas": "244583"}   # los del piano original
SEMIT = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}

def tecla(nombre):
    m = re.fullmatch(r"([A-G])([#b]?)(-?\d)", nombre)
    if not m:
        raise ValueError("nota no valida: " + nombre)
    midi = 12 * (int(m.group(3)) + 1) + SEMIT[m.group(1)] + {"#": 1, "b": -1, "": 0}[m.group(2)]
    n = midi - BASE_MIDI + 1
    if not 1 <= n <= TECLAS:
        raise ValueError("%s fuera del teclado (tecla %d, rango 1..%d)" % (nombre, n, TECLAS))
    return n

def parsear(texto, inicio):
    """-> lista de (order, [teclas])"""
    t, ev = inicio, []
    for tok in re.findall(r"\([^)]*\):\d+|\S+", texto):
        nota, dur = tok.rsplit(":", 1)
        dur = int(dur)
        if nota != "R":
            notas = nota.strip("()").split()
            if len(notas) > 3:
                raise ValueError("un acorde admite como mucho 3 notas: " + tok)
            ev.append((t, sorted(tecla(n) for n in notas)))
        t += dur
    return ev

CANCIONES = [
    # (nombre, order inicial, notacion)
    ("Toccata y fuga en Re menor (Bach), tema inicial simplificado", 1,
     "A5:2 G5:2 A5:12 R:4 G5:2 F5:2 E5:2 D5:2 C#5:4 D5:16 R:4 "
     "A4:2 G4:2 A4:12 R:4 G4:2 F4:2 E4:2 D4:2 C#4:4 D4:16 R:4 "
     "A3:2 G3:2 A3:12 R:4 G3:2 F3:2 E3:2 D3:2 C#3:4 D3:16 R:6 "
     "(D3 A3 D4):14 (D3 A3 F4):14 (D3 A3 D4):30"),
    ("Dies irae (canto gregoriano, motivo de Halloween), en Re dorico", 1001,
     "(D3 A3 F4):5 E4:5 F4:5 D4:5 E4:5 C4:5 D4:10 R:5 "
     "(D3 A3 F4):5 E4:5 F4:5 D4:5 E4:5 C4:5 D4:10 R:5 "
     "(D3 A3 F5):5 E5:5 F5:5 D5:5 E5:5 C5:5 D5:20 R:5 "
     "(D3 A3 D4):30"),
]

def construir():
    eventos = []
    for _, ini, txt in CANCIONES:
        eventos += parsear(txt, ini)
    furni, i = [], 0
    for order, teclas in sorted(eventos):
        for n in teclas:
            furni.append({
                "id": i + 1, "name": "room_invisible_block4[%d]" % i, "className": "room_invisible_block4",
                "rotation": 0, "location": {"x": 0, "y": 0, "z": 0}, "variables": {"order": order, "notas": n}})
            i += 1
    return {"wired": {"triggers": [], "effects": [], "conditions": [], "selectors": [], "addons": [],
                      "variables": [], "variables_map": dict(IDS)},
            "bindings": [], "furni": furni}

if __name__ == "__main__":
    d = construir()
    out = sys.argv[1] if len(sys.argv) > 1 else "Piano Halloween.json"
    with open(out, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
    print(out, len(d["furni"]), "bloques")
