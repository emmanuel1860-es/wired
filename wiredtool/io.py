import json

def cargar(ruta):
    with open(ruta, encoding='utf-8') as f:
        d = json.load(f)
    d.setdefault('wired', {})
    for k in ('triggers', 'selectors', 'conditions', 'effects', 'addons', 'variables'):
        d['wired'].setdefault(k, [])
    d['wired'].setdefault('variables_map', {})
    d.setdefault('furni', []); d.setdefault('bindings', []); d.setdefault('wallFurni', [])
    return d

def guardar(d, ruta):
    with open(ruta, 'w', encoding='utf-8') as f:
        json.dump(d, f, ensure_ascii=False, indent=1)

def indices(d):
    """furni por id y wired por wiredId -> (seccion, wired)."""
    fid = {f['id']: f for f in d['furni']}
    w = {}
    for sec in ('triggers', 'selectors', 'conditions', 'effects', 'addons', 'variables'):
        for x in d['wired'][sec]:
            w[x['wiredId']] = (sec, x)
    return fid, w
