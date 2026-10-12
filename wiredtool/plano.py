"""Plano de huellas de un preset (PNG si hay matplotlib, texto si no)."""
import collections
from .io import indices

def celdas(d):
    fid, wm = indices(d); c = collections.defaultdict(set)
    for f in d['furni']:
        l = f['location']
        tipo = 'wired' if f['id'] in wm or f['className'].startswith('wf_') else 'furni'
        c[(l['x'], l['y'])].add(tipo)
    return c

def texto(d):
    c = celdas(d)
    if not c: return '(vacío)'
    xs = [p[0] for p in c]; ys = [p[1] for p in c]
    filas = []
    for y in range(min(ys), max(ys) + 1):
        filas.append(''.join(('W' if 'wired' in c[(x, y)] else 'f') if (x, y) in c else '.' for x in range(min(xs), max(xs) + 1)))
    return '\n'.join(filas) + f'\n(origen {min(xs)},{min(ys)}; W = pila de wired, f = furni, . = libre; {max(xs)-min(xs)+1}x{max(ys)-min(ys)+1} baldosas)'

def png(d, ruta):
    try:
        import matplotlib; matplotlib.use('Agg')
        import matplotlib.pyplot as plt, matplotlib.patches as pa
    except ImportError:
        return False
    c = celdas(d)
    fig, ax = plt.subplots(figsize=(8, 8))
    for (x, y), t in c.items():
        ax.add_patch(pa.Rectangle((x, y), 1, 1, fc='tab:purple' if 'wired' in t else 'tab:orange', ec='k', alpha=.5))
    xs = [p[0] for p in c] or [0]; ys = [p[1] for p in c] or [0]
    ax.set_xlim(min(xs) - 1, max(xs) + 2); ax.set_ylim(max(ys) + 2, min(ys) - 1)
    ax.set_aspect('equal'); ax.grid(True, alpha=.3); fig.savefig(ruta, dpi=110); return True
