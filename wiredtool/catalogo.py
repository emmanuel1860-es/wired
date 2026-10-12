"""Catálogo de formatos vistos en uno o varios exports."""
import json, collections
from .io import cargar, indices
from .conocimiento import SECCION_PREFIJO

def construir(rutas):
    cat = collections.defaultdict(lambda: dict(secciones=set(), n=0, opciones=collections.Counter(),
                                              userSources=collections.Counter(), furniSources=collections.Counter(),
                                              delays=collections.Counter(), ejemplos_config=[], archivos=set()))
    variables, subvars = {}, collections.Counter()
    for r in rutas:
        d = cargar(r); fid, _ = indices(d)
        for sec in SECCION_PREFIJO:
            for x in d['wired'][sec]:
                c = fid.get(x['wiredId'], {}).get('className')
                if not c: continue
                e = cat[c]; e['secciones'].add(sec); e['n'] += 1; e['archivos'].add(r.split('/')[-1])
                e['opciones'][json.dumps(x.get('options', []))] += 1
                e['userSources'][json.dumps(x.get('userSources', []))] += 1
                e['furniSources'][json.dumps(x.get('furniSources', []))] += 1
                if 'delay' in x: e['delays'][x['delay']] += 1
                t = x.get('config', '')
                if t and len(e['ejemplos_config']) < 3 and t not in e['ejemplos_config']: e['ejemplos_config'].append(t[:80])
                for v in x.get('variableIds', []):
                    if v.startswith(('s', '~')) and len(v) > 3: subvars[v] += 1
        for x in d['wired']['variables']:
            variables[x['config'] or x['variableId']] = dict(tipo=fid[x['wiredId']]['className'], opciones=x['options'], id=x.get('variableId'))
    out = {}
    for c, e in sorted(cat.items()):
        out[c] = dict(secciones=sorted(e['secciones']), veces=e['n'],
                      opciones=[(json.loads(k), n) for k, n in e['opciones'].most_common(8)],
                      userSources=[(json.loads(k), n) for k, n in e['userSources'].most_common(4)],
                      furniSources=[(json.loads(k), n) for k, n in e['furniSources'].most_common(4)],
                      delays=dict(e['delays'].most_common(6)), ejemplos_config=e['ejemplos_config'], archivos=sorted(e['archivos']))
    return dict(wireds=out, variables=variables, subvariables=dict(subvars))

def imprimir(cat, buscar=None):
    for c, e in cat['wireds'].items():
        if buscar and buscar not in c: continue
        print(f"{c}  ({e['veces']}x)  {','.join(e['secciones'])}")
        print('   opciones:', '; '.join(f'{o} x{n}' for o, n in e['opciones'][:5]))
        print('   userSources:', e['userSources'][:3], ' furniSources:', e['furniSources'][:3])
        if e['ejemplos_config']: print('   config:', e['ejemplos_config'])
    if not buscar:
        print('\nvariables:', len(cat['variables']), ' subvariables vistas:', list(cat['subvariables'])[:6])
