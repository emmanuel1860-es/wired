"""Compara dos presets por contenido (no por id, que cambia al importar)."""
import collections, json
from .io import indices

def _clave_furni(f):
    l = f['location']; return (f['className'], l['x'], l['y'], round(l['z'], 3), f.get('rotation', 0), f.get('state'))

def _firma_wired(d):
    fid, wm = indices(d); out = {}
    for wid, (sec, x) in wm.items():
        f = fid[wid]; l = f['location']
        k = (f['className'], l['x'], l['y'], round(l['z'], 3))
        out[k] = json.dumps([x.get('options'), x.get('config'), x.get('userSources'), x.get('furniSources'), x.get('delay'), len(x.get('items', []))], ensure_ascii=False)
    return out

def comparar(a, b):
    res = []
    ca = collections.Counter(_clave_furni(f) for f in a['furni'])
    cb = collections.Counter(_clave_furni(f) for f in b['furni'])
    solo_a, solo_b = ca - cb, cb - ca
    por_clase = lambda c: collections.Counter(k[0] for k in c.elements())
    for cl, n in por_clase(solo_a).items(): res.append(('quitado', f'{cl} x{n}'))
    for cl, n in por_clase(solo_b).items(): res.append(('añadido', f'{cl} x{n}'))
    wa, wb = _firma_wired(a), _firma_wired(b)
    for k in wa.keys() - wb.keys(): res.append(('wired quitado', f'{k[0]} en ({k[1]},{k[2]},{k[3]})'))
    for k in wb.keys() - wa.keys(): res.append(('wired añadido', f'{k[0]} en ({k[1]},{k[2]},{k[3]})'))
    for k in wa.keys() & wb.keys():
        if wa[k] != wb[k]: res.append(('wired cambiado', f'{k[0]} en ({k[1]},{k[2]},{k[3]})'))
    va = {x['config'] for x in a['wired']['variables']}; vb = {x['config'] for x in b['wired']['variables']}
    for v in va - vb: res.append(('variable quitada', v))
    for v in vb - va: res.append(('variable añadida', v))
    return res
