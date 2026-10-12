#!/usr/bin/env python3
"""Convierte un archivo MIDI en un preset de piano (tabla de notas, formato de piano_100ms.json).

Uso: python3 midi_a_piano.py cancion.mid salida.json [--inicio 1] [--base 48] [--transponer N] [--pistas 0,1] [--tick 0.1]
  --inicio     order de la primera nota (pon 1001 para una segunda cancion en la misma tabla)
  --base       nota MIDI de la tecla 1 (48 = Do3, supuesto sin confirmar; ver skill habbo-wired seccion 12)
  --transponer semitonos a desplazar toda la cancion (por defecto se elige el desplazamiento de octavas que mas notas deja dentro)
  --pistas     indices de pista a usar (por defecto todas menos la percusion, canal 10)
  --tick       resolucion en segundos (0.1 = 100 ms, como el piano original)
Las notas que aun caen fuera de las 37 teclas se pliegan por octavas. Cada instante admite 3 notas como mucho:
se conservan la mas aguda (melodia), la mas grave (bajo) y la mas fuerte de las restantes.
Solo lee MIDI; no incluye ninguna cancion."""
import json, struct, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from piano_halloween import IDS, TECLAS

def leer_midi(ruta):
    d = open(ruta, 'rb').read()
    if d[:4] != b'MThd':
        raise ValueError("no es un MIDI (falta MThd)")
    fmt, ntrk, div = struct.unpack('>HHH', d[8:14])
    if div & 0x8000:
        raise ValueError("division SMPTE no soportada")
    pos, pistas = 14, []
    for _ in range(ntrk):
        while d[pos:pos + 4] != b'MTrk':
            pos += 8 + struct.unpack('>I', d[pos + 4:pos + 8])[0]
        n = struct.unpack('>I', d[pos + 4:pos + 8])[0]
        pistas.append(d[pos + 8:pos + 8 + n]); pos += 8 + n
    return div, pistas

def eventos(pista):
    """-> lista de (tick_abs, tipo, datos) con tipo 'tempo' | 'on' | 'off'"""
    i, t, estado, out = 0, 0, 0, []
    def vlq():
        nonlocal i
        v = 0
        while True:
            b = pista[i]; i += 1; v = (v << 7) | (b & 0x7F)
            if not b & 0x80: return v
    while i < len(pista):
        t += vlq()
        b = pista[i]
        if b == 0xFF:
            tipo = pista[i + 1]; i += 2; n = vlq(); datos = pista[i:i + n]; i += n
            if tipo == 0x51: out.append((t, 'tempo', int.from_bytes(datos, 'big')))
            if tipo == 0x2F: break
        elif b in (0xF0, 0xF7):
            i += 1; n = vlq(); i += n
        else:
            if b & 0x80: estado = b; i += 1
            cmd, canal = estado & 0xF0, estado & 0x0F
            if cmd in (0xC0, 0xD0): i += 1
            else:
                a, v = pista[i], pista[i + 1]; i += 2
                if cmd == 0x90 and v > 0: out.append((t, 'on', (canal, a, v)))
                elif cmd in (0x80, 0x90): out.append((t, 'off', (canal, a, 0)))
    return out

def convertir(ruta, inicio=1, base=48, transponer=None, pistas_sel=None, tick=0.1):
    div, pistas = leer_midi(ruta)
    todos = [(i, e) for i, p in enumerate(pistas) for e in eventos(p)]
    tempos = sorted((t, v) for _, (t, k, v) in todos if k == 'tempo') or [(0, 500000)]
    def segundos(tk):
        s, ult, mu = 0.0, 0, 500000
        for t, v in tempos:
            if t >= tk: break
            s += (t - ult) * mu / div / 1e6; ult, mu = t, v
        return s + (tk - ult) * mu / div / 1e6
    notas = [(segundos(t), a, v) for i, (t, k, (c, a, v)) in [(i, (t, k, d)) for i, (t, k, d) in todos if k == 'on']
             if c != 9 and (pistas_sel is None or i in pistas_sel)]
    if not notas: raise ValueError("el MIDI no tiene notas utilizables")
    if transponer is None:
        # desplazamiento en octavas que deja mas notas dentro del teclado
        mejor = max(range(-60, 61, 12), key=lambda s: sum(base <= a + s < base + TECLAS for _, a, _ in notas))
        transponer = mejor
    t0 = min(s for s, _, _ in notas)
    por_tick = {}
    for s, a, v in notas:
        a += transponer
        while a < base: a += 12
        while a >= base + TECLAS: a -= 12
        por_tick.setdefault(int(round((s - t0) / tick)), {})[a - base + 1] = max(v, por_tick.get(int(round((s - t0) / tick)), {}).get(a - base + 1, 0))
    furni, i = [], 0
    recortados = 0
    for k in sorted(por_tick):
        ns = por_tick[k]
        if len(ns) > 3:
            recortados += 1
            alta, baja = max(ns), min(ns)
            resto = [n for n in ns if n not in (alta, baja)]
            ns = sorted({alta, baja, max(resto, key=lambda n: ns[n])})
        for n in sorted(ns):
            furni.append({"id": i + 1, "name": "room_invisible_block4[%d]" % i, "className": "room_invisible_block4",
                          "rotation": 0, "location": {"x": 0, "y": 0, "z": 0},
                          "variables": {"order": inicio + k, "notas": n}})
            i += 1
    preset = {"wired": {"triggers": [], "effects": [], "conditions": [], "selectors": [], "addons": [],
                        "variables": [], "variables_map": dict(IDS)}, "bindings": [], "furni": furni}
    info = {"bloques": len(furni), "instantes": len(por_tick), "segundos": round(max(por_tick) * tick, 1),
            "transposicion": transponer, "instantes_recortados_a_3": recortados}
    return preset, info

if __name__ == "__main__":
    a = sys.argv[1:]
    if len(a) < 2: sys.exit(__doc__)
    opt = {}
    for k, v in zip(a[2::2], a[3::2]): opt[k.lstrip('-')] = v
    pr, info = convertir(a[0], int(opt.get('inicio', 1)), int(opt.get('base', 48)),
                         int(opt['transponer']) if 'transponer' in opt else None,
                         set(map(int, opt['pistas'].split(','))) if 'pistas' in opt else None, float(opt.get('tick', 0.1)))
    json.dump(pr, open(a[1], 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
    print(a[1], info)
