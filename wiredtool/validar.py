"""Validación estática de un preset. Devuelve lista de (nivel, mensaje): error | aviso | info."""
import re, collections
from .io import indices
from .conocimiento import (SECCION_PREFIJO, SIN_ENTRADA, SIN_CONFIRMAR, doble_codificado, tiene_no_ascii)

def validar(d):
    res = []
    add = lambda n, m: res.append((n, m))
    fid, wmap = indices(d)
    ids = [f['id'] for f in d['furni']]
    for i, n in collections.Counter(ids).items():
        if n > 1: add('error', f'id de furni repetido: {i}')
    # --- wired <-> furni ---
    for sec, pref in SECCION_PREFIJO.items():
        for x in d['wired'][sec]:
            wid = x.get('wiredId')
            f = fid.get(wid)
            if f is None:
                add('error', f'{sec}: wiredId {wid} no apunta a ningún furni'); continue
            if not f['className'].startswith(pref):
                add('error', f'{sec}: wiredId {wid} es {f["className"]}, se esperaba {pref}*')
            if sec in ('conditions',) and 'quantifier' not in x:
                add('aviso', f'condición {wid} ({f["className"]}) sin "quantifier"')
            if sec == 'effects' and 'delay' not in x:
                add('aviso', f'efecto {wid} ({f["className"]}) sin "delay"')
            for it in list(x.get('items', [])) + list(x.get('secondItems', [])):
                if it not in fid: add('error', f'{f["className"]} {wid}: item {it} no existe en furni')
    cubiertos = {x['wiredId'] for s in SECCION_PREFIJO for x in d['wired'][s]}
    for f in d['furni']:
        c = f['className']
        if c.startswith('wf_') and f['id'] not in cubiertos and not c.startswith(SIN_ENTRADA):
            add('aviso', f'{c} (id {f["id"]}) está en furni pero no en wired: hay que configurarlo a mano')
    # --- variables ---
    definidas = collections.defaultdict(list)
    for x in d['wired']['variables']:
        definidas[x.get('config', '')].append(x)
    for nombre, lst in definidas.items():
        if len(lst) > 1: add('error', f'variable "{nombre}" definida {len(lst)} veces en el preset')
    ids_def = {x.get('variableId') for x in d['wired']['variables'] if x.get('variableId')}
    vmap = set(d['wired'].get('variables_map', {}).values())
    conocidos = ids_def | vmap
    sin_def = collections.defaultdict(list)
    for sec in ('conditions', 'effects', 'addons', 'selectors', 'triggers'):
        for x in d['wired'][sec]:
            cls = fid.get(x.get('wiredId'), {}).get('className', '?')
            for v in x.get('variableIds', []):
                if v in ('', '0', 'n') or re.fullmatch(r'-\d+', v) or v.startswith('~'): continue
                if v.startswith('s') and re.match(r's(\d+)-', v):
                    padre = re.match(r's(\d+)-', v).group(1)
                    if padre not in conocidos: add('aviso', f'{cls} {x["wiredId"]}: subvariable {v!r} de una variable no definida aquí')
                    continue
                if v not in conocidos: sin_def[v].append(x['wiredId'])
    for v, lst in sin_def.items():
        add('aviso', f'variable {v} usada en {len(lst)} wired pero no definida en este preset (¿ya existe en la sala destino?)')
    # --- antenas ---
    envia, recibe = collections.Counter(), collections.Counter()
    for sec in ('effects', 'triggers'):
        for x in d['wired'][sec]:
            c = fid.get(x.get('wiredId'), {}).get('className', '')
            if c in ('wf_act_send_signal', 'wf_act_neg_send_signal'):
                for it in x.get('items', []): envia[it] += 1
            if c == 'wf_trg_recv_signal':
                for it in x.get('items', []): recibe[it] += 1
    for a in set(envia) | set(recibe):
        if a in recibe and a not in envia: add('aviso', f'antena {a}: tiene receptor pero ningún emisor en este preset (¿lo envía tu juego?)')
        if a in envia and a not in recibe: add('aviso', f'antena {a}: se envía señal pero nadie la recibe')
    # --- pilas por baldosa ---
    pilas = collections.defaultdict(list)
    for f in d['furni']:
        if f['id'] in wmap:
            l = f['location']; pilas[(l['x'], l['y'])].append((l['z'], f['id'], wmap[f['id']][0], f['className']))
    for pos, lst in pilas.items():
        lst.sort()
        zs = [round(z, 3) for z, *_ in lst]
        for z, n in collections.Counter(zs).items():
            if n > 1: add('error', f'pila {pos}: dos wireds a la misma altura z={z}')
        if all(e[2] in ('variables',) or (e[2] == 'addons' and e[3] == 'wf_xtra_var_time_util') for e in lst): continue
        tr = [e for e in lst if e[2] == 'triggers']
        if not tr: add('aviso', f'pila {pos}: no tiene trigger')
        elif lst[0][2] != 'triggers': add('aviso', f'pila {pos}: el trigger no está abajo del todo')
        efs = [(wmap[i][1], c) for z, i, s, c in lst if s == 'effects']
        sin_delay = [x for x, c in efs if x.get('delay', 0) == 0 and c in ('wf_act_change_var_val', 'wf_act_give_var', 'wf_act_remove_var')]
        usa_orden = any(s == 'addons' and c == 'wf_xtra_exec_in_order' for z, i, s, c in lst)
        vars_ = collections.Counter(tuple(x['variableIds'][:1]) for x in sin_delay)
        if not usa_orden and any(n > 1 for n in vars_.values()):
            add('aviso', f'pila {pos}: varios efectos sin retardo escriben la misma variable y no hay exec_in_order (el orden no está garantizado)')
    # --- textos ---
    for sec in ('effects', 'addons', 'triggers'):
        for x in d['wired'][sec]:
            t = x.get('config', '')
            if isinstance(t, str) and tiene_no_ascii(t) and not doble_codificado(t):
                add('aviso', f'texto con acento sin doble codificación (wiredId {x["wiredId"]}): {t[:50]!r}')
    # --- sin confirmar ---
    vistos = collections.defaultdict(set)
    for sec in SECCION_PREFIJO:
        for x in d['wired'][sec]:
            c = fid.get(x.get('wiredId'), {}).get('className', '')
            for desc, fn in SIN_CONFIRMAR:
                try:
                    if fn(c, x): vistos[desc].add(x['wiredId'])
                except Exception: pass
    for desc, s in vistos.items():
        add('info', f'sin confirmar: {desc} (en {len(s)} wired)')
    # --- bindings ---
    for b in d.get('bindings', []):
        for k in ('wiredId', 'furniId'):
            if k in b and b[k] not in fid: add('error', f'binding con {k} {b[k]} inexistente')
    return res

def resumen(res):
    c = collections.Counter(n for n, _ in res)
    return f"{c['error']} errores, {c['aviso']} avisos, {c['info']} notas"
