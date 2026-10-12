import json, collections
OUT = '/home/user/wired/Recompensas por tiempo.json'
def T(s): return s.encode('utf-8').decode('latin-1')
# ids de variables que YA existen en la sala (picnic.json)
AURA, VIP, AZAR_C, DIF_C, AZAR_F, DIF_F, PREMIO_C, HAY_CRED = '244353', '244359', '244362', '244363', '244360', '244361', '244364', '244627'
CRED_ENTREGADO, FURNI_ENTREGADO = '95001', '95002'          # nuevas (variables de sala)
BASE, ESCALA = 50, 1000                                      # chance por ronda = (aura + BASE) / ESCALA
RONDAS, ESPACIO = 10, 12                                     # 10 rondas, una cada 6 s (12 x 0,5 s)
furni, W = [], {k: [] for k in ('triggers', 'selectors', 'conditions', 'effects', 'addons', 'variables')}
nid = [0]; cnt = collections.Counter()
def new_furni(cls, x, y, z, state=None):
    nid[0] += 1; n = cnt[cls]; cnt[cls] += 1
    d = dict(rotation=0, name=f'{cls}[{n}]', className=cls, location=dict(x=x, y=y, z=round(z, 3)), id=nid[0])
    if state is not None: d['state'] = state
    furni.append(d); return nid[0]
class Stack:
    def __init__(self, x, y): self.x, self.y, self.z = x, y, 0.0
    def a(self, cls, sec, **kw):
        i = new_furni(cls, self.x, self.y, self.z); self.z += 0.65
        d = dict(secondItems=[], furniSources=[], wiredId=i, userSources=[], variableIds=[], options=[], config='', items=[])
        d.update(kw); W[sec].append(d); return i
    def periodic(self, n): return self.a('wf_trg_periodically', 'triggers', options=[n])
    def recv(self, ant): return self.a('wf_trg_recv_signal', 'triggers', furniSources=[100], items=[ant])
    def vip_users(self): return self.a('wf_slc_users_with_var', 'selectors', options=[2, 0, 0, 0, -10, 0], userSources=[0], furniSources=[100], variableIds=[VIP, '0'])
    def todos(self): return self.a('wf_slc_users_bytype', 'selectors', options=[1])
    def sala(self, var, comp, val): return self.a('wf_cnd_var_val_match', 'conditions', options=[-10, comp, 0, 0, val, -10, 0], userSources=[0, 200], furniSources=[0, 200], variableIds=[var, '0'], quantifier=0)
    def menor(self, azar, dif): return self.a('wf_cnd_var_val_match', 'conditions', options=[1, 0, 1, 0, 0, 1, 0], userSources=[201, 201], furniSources=[0, 200], variableIds=[azar, dif], quantifier=0)
    def limite(self, n, s): return self.a('wf_xtra_execution_limit', 'addons', options=[n, s])
    def nombre(self): return self.a('wf_xtra_text_output_username', 'addons', options=[0], userSources=[201], config='name')
    def azar(self, var, dl): return self.a('wf_act_change_var_val', 'effects', options=[1, 50, 0, 0, ESCALA, -10, 0], userSources=[200, 200], furniSources=[0, 200], variableIds=[var, '0'], delay=dl)
    def copia(self, dst, src, dl): return self.a('wf_act_change_var_val', 'effects', options=[1, 0, 1, 0, 0, 1, 0], userSources=[200, 200], furniSources=[0, 200], variableIds=[dst, src], delay=dl)
    def suma(self, var, v, dl, src=200): return self.a('wf_act_change_var_val', 'effects', options=[1, 1, 0, 0, v, -10, 0], userSources=[src, 200], furniSources=[0, 200], variableIds=[var, '0'], delay=dl)
    def set_sala(self, var, v, dl=0): return self.a('wf_act_change_var_val', 'effects', options=[-10, 0, 0, 0, v, -10, 0], userSources=[0, 200], furniSources=[0, 200], variableIds=[var, '0'], delay=dl)
    def set_ctx(self, var, v, dl=0): return self.a('wf_act_change_var_val', 'effects', options=[-20, 0, 0, 0, v, -10, 0], userSources=[0, 200], furniSources=[0, 200], variableIds=[var, '0'], delay=dl)
    def senal_usuarios(self, ant, dl): return self.a('wf_act_send_signal', 'effects', options=[0, 1], userSources=[200], furniSources=[100, 200], items=[ant], delay=dl)
    def senal(self, ant, dl): return self.a('wf_act_send_signal', 'effects', options=[0, 0], userSources=[200], furniSources=[100, 200], items=[ant], delay=dl)
    def msg(self, txt, dl, sala=False): return self.a('wf_act_show_message', 'effects', options=[1 if sala else 0, 222, -1], userSources=[201 if not sala else 0], config=T(txt), delay=dl)

def var_sala(nombre, vid, z):
    i = new_furni('wf_var_room', 0, 6, z)
    W['variables'].append(dict(secondItems=[], furniSources=[], wiredId=i, userSources=[], variableIds=[], options=[10], config=nombre, items=[], variableId=vid))
var_sala('cred_entregado', CRED_ENTREGADO, 0); var_sala('furni_entregado', FURNI_ENTREGADO, 1.2)
ANT_RONDA_C = new_furni('wf_antenna2', 0, 0, 0, '0'); ANT_CAND_C = new_furni('wf_antenna2', 1, 0, 0, '0')
ANT_RONDA_F = new_furni('wf_antenna2', 2, 0, 0, '0'); ANT_CAND_F = new_furni('wf_antenna2', 3, 0, 0, '0')

# ===== CREDITO: 1c cada 30 min, un solo ganador entre los VIP, mas aura = mas chance =====
s = Stack(0, 2); s.periodic(3600); s.sala(HAY_CRED, 1, 1); s.set_sala(CRED_ENTREGADO, 0, 0)
for i in range(RONDAS): s.senal(ANT_RONDA_C, 2 + i * ESPACIO)
s = Stack(1, 2); s.recv(ANT_RONDA_C); s.vip_users(); s.sala(CRED_ENTREGADO, 1, 0)
s.azar(AZAR_C, 0); s.copia(DIF_C, AURA, 1); s.suma(DIF_C, BASE, 2); s.senal_usuarios(ANT_CAND_C, 3)
s = Stack(2, 2); s.recv(ANT_CAND_C); s.sala(CRED_ENTREGADO, 1, 0); s.menor(AZAR_C, DIF_C)
s.limite(1, 60); s.nombre()
s.set_sala(CRED_ENTREGADO, 1, 0); s.set_ctx(PREMIO_C, 1, 0)
s.a('wf_act_give_currency', 'effects', options=[0, 1, 1, 1, 1, 11], userSources=[201, 0], variableIds=[PREMIO_C], delay=1)
s.msg('@green@ ¡Ganaste 1 crédito en el sorteo VIP!', 2)
s.msg('@green@ $(name) ganó 1 crédito en el sorteo VIP (cada 30 minutos)', 2, sala=True)

# ===== FORNEY: 1 furni del cofre cada 8 min, un solo ganador (participan todos), mas aura = mas chance =====
s = Stack(0, 4); s.periodic(960); s.set_sala(FURNI_ENTREGADO, 0, 0)
for i in range(RONDAS): s.senal(ANT_RONDA_F, 2 + i * ESPACIO)
s = Stack(1, 4); s.recv(ANT_RONDA_F); s.todos(); s.sala(FURNI_ENTREGADO, 1, 0)
s.azar(AZAR_F, 0); s.copia(DIF_F, AURA, 1); s.suma(DIF_F, BASE, 2); s.senal_usuarios(ANT_CAND_F, 3)
s = Stack(2, 4); s.recv(ANT_CAND_F); s.sala(FURNI_ENTREGADO, 1, 0); s.menor(AZAR_F, DIF_F)
s.a('wf_cnd_chest_has_items', 'conditions', options=[0, 0, -20, 2], userSources=[200], furniSources=[100, 200], variableIds=['0'], quantifier=0)
s.a('wf_xtra_scan_chest_furni_by_type', 'addons', options=[0], furniSources=[0, 100], variableIds=['0'])
s.limite(1, 60); s.nombre()
s.set_sala(FURNI_ENTREGADO, 1, 0); s.suma(AURA, 25, 0, src=201)
s.a('wf_act_give_furni', 'effects', options=[0, 1, 0, -20, 1, 0], userSources=[201, 200], furniSources=[100, 200], variableIds=['0'], delay=1)
s.msg('@green@ ¡Ganaste un furni del cofre! (+25 de Aura)', 2)
s.msg('@green@ $(name) sacó el cofre y se llevó un furni (+25 de Aura)', 2, sala=True)

fid = {f['id']: f for f in furni}
for k, v in W.items():
    for x in v:
        assert x['wiredId'] in fid
        for it in x['items'] + x['secondItems']: assert it in fid
assert max(collections.Counter((f['location']['x'], f['location']['y'], f['location']['z']) for f in furni).values()) == 1
print(len(furni), {k: len(v) for k, v in W.items()})
json.dump(dict(wired=dict(W, variables_map={'cred_entregado': CRED_ENTREGADO, 'furni_entregado': FURNI_ENTREGADO}), bindings=[], furni=furni, wallFurni=[], exportOrigin=dict(x=0, y=0, floor=0)), open(OUT, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
