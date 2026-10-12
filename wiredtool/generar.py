"""Genera un preset a partir de una descripción JSON sencilla, usando solo formatos ya vistos en exports reales.

Ver README.md y ejemplos/tienda.json para el formato de la descripción.
"""
import collections
from .conocimiento import COMPARACION, OPERACION, TIPO_AMBITO, UNIDAD_EDAD, ZONA, a_doble

class ErrorSpec(ValueError): pass

class Preset:
    def __init__(self, spec):
        self.spec = spec
        self.furni, self.W = [], {k: [] for k in ('triggers', 'selectors', 'conditions', 'effects', 'addons', 'variables')}
        self.vmap, self.nid, self.cnt = {}, 0, collections.Counter()
        self.vkind, self.fur = {}, {}          # nombre variable -> ambito ; nombre furni -> id
        self.sigvar = 94000

    # ---- furni / variables ----
    def nuevo_furni(self, cls, x, y, z, state=None, rot=0):
        self.nid += 1; n = self.cnt[cls]; self.cnt[cls] += 1
        d = dict(rotation=rot, name=f'{cls}[{n}]', className=cls, location=dict(x=x, y=y, z=round(z, 3)), id=self.nid)
        if state is not None: d['state'] = state
        self.furni.append(d); return self.nid

    def variable(self, v, x, y, z):
        nombre, amb = v['nombre'], v.get('ambito', 'usuario')
        if amb not in ('usuario', 'sala', 'contexto'): raise ErrorSpec(f'ámbito inválido en {nombre}: {amb}')
        vid = v.get('id')
        if vid is None and amb != 'contexto':
            self.sigvar += 1; vid = self.sigvar
        if amb == 'contexto' and vid is None:
            self.sigvar += 1; vid = self.sigvar
        clase = {'usuario': 'wf_var_user', 'sala': 'wf_var_room', 'contexto': 'wf_var_context'}[amb]
        opts = {'usuario': [10, 1] if v.get('persistente') else [0, 1], 'sala': [10], 'contexto': [1]}[amb]
        i = self.nuevo_furni(clase, x, y, z)
        self.W['variables'].append(dict(secondItems=[], furniSources=[], wiredId=i, userSources=[], variableIds=[], options=opts,
                                        config=nombre, items=[], variableId='' if amb == 'contexto' else str(vid)))
        self.vmap[nombre] = str(vid); self.vkind[nombre] = amb
        if v.get('con_tiempo'):                      # complemento Utilidades de Tiempo encima (día del año + año, fecha de creación)
            j = self.nuevo_furni('wf_xtra_var_time_util', x, y, z + 1.2)
            self.W['addons'].append(dict(secondItems=[], furniSources=[], wiredId=j, userSources=[], variableIds=[], options=[1152, 1], config='', items=[]))
            if v.get('subvariable_dia_del_año'):
                self.vmap[nombre + '.dia_del_año'] = v['subvariable_dia_del_año']

    def vid(self, nombre):
        if nombre in ('@state',): return '-110'
        if nombre.lstrip('-').isdigit() or nombre.startswith(('~', 's')): return nombre
        if nombre not in self.vmap: raise ErrorSpec(f'variable no definida: {nombre}')
        return self.vmap[nombre]

    def furni_ref(self, nombre):
        if nombre not in self.fur: raise ErrorSpec(f'furni no definido: {nombre}')
        return self.fur[nombre]

    # ---- pila ----
    def pila(self, p):
        x, y, z = p['x'], p['y'], 0.0
        def add(cls, sec, **kw):
            nonlocal z
            i = self.nuevo_furni(cls, x, y, z); z += 0.65
            d = dict(secondItems=[], furniSources=[], wiredId=i, userSources=[], variableIds=[], options=[], config='', items=[])
            d.update(kw); self.W[sec].append(d); return i
        t = p['trigger']; k = t['tipo']
        if k == 'dice': add('wf_trg_says_something', 'triggers', options=[0, 1, 1], config=t['texto'])
        elif k == 'entra': add('wf_trg_enter_room', 'triggers')
        elif k == 'periodico': add('wf_trg_periodically', 'triggers', options=[t.get('cada', 2)])
        elif k == 'senal': add('wf_trg_recv_signal', 'triggers', furniSources=[100], items=[self.furni_ref(t['antena'])])
        elif k == 'pago_ok': add('wf_trg_transaction_complete', 'triggers', furniSources=[100], items=[self.furni_ref(t['contrato'])])
        elif k == 'click_usuario': add('wf_trg_click_user', 'triggers', options=[1, 0])
        else: raise ErrorSpec(f'trigger desconocido: {k}')
        for s in p.get('selectores', []):
            if s['tipo'] == 'usuarios_area': add('wf_slc_users_area', 'selectors', options=[s['x'], s['y'], s.get('ancho', 1), s.get('alto', 1)])
            elif s['tipo'] == 'furnis_por_tipo': add('wf_slc_furni_bytype', 'selectors', options=[0], furniSources=[100], items=[self.furni_ref(s['ejemplo'])])
            else: raise ErrorSpec(f'selector desconocido: {s["tipo"]}')
        for c in p.get('condiciones', []): self._cond(add, c)
        for a in p.get('addons', []): self._addon(add, a)
        for e in p.get('efectos', []): self._efecto(add, e)

    def _cond(self, add, c):
        k = c['tipo']; neg = c.get('negar', False)
        if k == 'admin': add('wf_cnd_has_var', 'conditions', options=[1], userSources=[0], furniSources=[100], variableIds=['-222'], quantifier=0)
        elif k == 'tiene_var': add('wf_cnd_neg_has_var' if neg else 'wf_cnd_has_var', 'conditions', options=[1], userSources=[0], furniSources=[100], variableIds=[self.vid(c['var'])], quantifier=0)
        elif k in ('usuario_cmp', 'sala_cmp', 'contexto_cmp'):
            amb = {'usuario_cmp': 1, 'sala_cmp': -10, 'contexto_cmp': -20}[k]
            add('wf_cnd_var_val_match', 'conditions', options=[amb, COMPARACION[c['op']], 0, 0, c['valor'], -10, 0], userSources=[0, 200], furniSources=[0, 200], variableIds=[self.vid(c['var']), '0'], quantifier=0)
        elif k == 'edad':
            add('wf_cnd_var_age_match', 'conditions', options=[1, COMPARACION[c['op']], 0, 0, c['valor'], UNIDAD_EDAD[c['unidad']]], userSources=[0], furniSources=[100], variableIds=[self.vid(c['var'])], quantifier=0)
        elif k == 'no_a_si_mismo': add('wf_cnd_not_triggerer_match', 'conditions', options=[1], userSources=[0, 11], quantifier=0)
        elif k == 'usuarios_seleccionados': add('wf_cnd_slc_quantity', 'conditions', options=[1, c.get('valor', 0), {'=': 1, '>': 2}[c.get('op', '=')]], userSources=[200], furniSources=[200], quantifier=0)
        else: raise ErrorSpec(f'condición desconocida: {k}')

    def _addon(self, add, a):
        k = a['tipo']
        if k == 'orden': add('wf_xtra_exec_in_order', 'addons')
        elif k == 'limite': add('wf_xtra_execution_limit', 'addons', options=[a.get('n', 100), a.get('s', 1)])
        elif k == 'nombre_usuario': add('wf_xtra_text_output_username', 'addons', options=[0], userSources=[11 if a.get('de') == 'clickeado' else 0], config=a['nombre'])
        elif k == 'texto_var': add('wf_xtra_text_output_variable', 'addons', options=[0, TIPO_AMBITO[self.vkind[a['var']]], 0], userSources=[0], furniSources=[100], variableIds=[self.vid(a['var'])], config=a['nombre'])
        else: raise ErrorSpec(f'addon desconocido: {k}')

    def _efecto(self, add, e):
        k, dl = e['tipo'], e.get('delay', 0)
        src = 11 if e.get('a') == 'clickeado' else 0
        if k == 'mensaje':
            add('wf_act_show_message', 'effects', options=[0, e.get('estilo', 34), -1], userSources=[src], config=a_doble(e['texto']), delay=dl)
        elif k in ('dar_var', 'quitar_var'):
            amb = TIPO_AMBITO[self.vkind[e['var']]]
            if k == 'quitar_var': add('wf_act_remove_var', 'effects', options=[1] if amb == 1 else [0], userSources=[src], furniSources=[100], variableIds=[self.vid(e['var'])], delay=dl)
            else:
                opts = [amb, 0, e.get('inicial', 0), 1 if e.get('con_tiempo') else 0]
                add('wf_act_give_var', 'effects', options=opts, userSources=[src], furniSources=[100], variableIds=[self.vid(e['var'])], delay=dl)
        elif k in ('asignar', 'sumar', 'restar'):
            amb = TIPO_AMBITO[self.vkind[e['var']]]; op = OPERACION[{'asignar': 'asignar', 'sumar': 'sumar', 'restar': 'restar'}[k]]
            add('wf_act_change_var_val', 'effects', options=[amb, op, 0, 0, e['valor'], -10], userSources=[src, 200], furniSources=[0, 200], variableIds=[self.vid(e['var']), '0'], delay=dl)
        elif k == 'estado_furni':
            add('wf_act_change_var_val', 'effects', options=[0, 0, 0, 0, e['valor'], -10], userSources=[0, 200], furniSources=[100, 200], variableIds=['-110', '0'], items=[self.furni_ref(e['furni'])], delay=dl)
        elif k == 'estado_seleccion':
            add('wf_act_change_var_val', 'effects', options=[0, 0, 0, 0, e['valor'], -10], userSources=[0, 200], furniSources=[200, 200], variableIds=['-110', '0'], delay=dl)
        elif k == 'zona':
            add('wf_act_change_var_val', 'effects', options=[0, 0, 0, 0, e['valor'], -10], userSources=[0, 200], furniSources=[100, 200], variableIds=[ZONA[e['campo']], 'n'], items=[self.furni_ref(e['furni'])], delay=dl)
        elif k == 'cobrar':
            add('wf_act_cancel_transaction', 'effects', options=[0], userSources=[0], furniSources=[100], items=[self.furni_ref(e['contrato'])], delay=0)
            add('wf_act_init_transaction', 'effects', options=[0, 10, 0, -20, 0, e['monto']], userSources=[0, 200], furniSources=[100, 101, 200], variableIds=['0', '0', '0'], items=[self.furni_ref(e['caja'])], secondItems=[self.furni_ref(e['contrato'])], delay=dl)
        elif k == 'senal':
            add('wf_act_send_signal', 'effects', options=[0, 0], userSources=[200], furniSources=[100, 200], items=[self.furni_ref(e['antena'])], delay=dl)
        else: raise ErrorSpec(f'efecto desconocido: {k}')

    def construir(self):
        s = self.spec
        zv = 0.0
        for v in s.get('variables', []):             # variables apiladas en la baldosa (0,0), 1.2 cada una (2.4 con complemento de tiempo)
            self.variable(v, 0, 0, zv); zv += 2.4 if v.get('con_tiempo') else 1.2
        for f in s.get('furnis', []):
            self.fur[f['nombre']] = self.nuevo_furni(f['clase'], f['x'], f['y'], f.get('z', 0), f.get('estado'))
        for p in s.get('pilas', []): self.pila(p)
        o = s.get('origen', {'x': 0, 'y': 0})
        return dict(wired=dict(triggers=self.W['triggers'], selectors=self.W['selectors'], conditions=self.W['conditions'],
                               effects=self.W['effects'], addons=self.W['addons'], variables=self.W['variables'], variables_map=self.vmap),
                    bindings=[], furni=self.furni, wallFurni=[], exportOrigin=dict(x=o['x'], y=o['y'], floor=0))

def generar(spec):
    return Preset(spec).construir()
