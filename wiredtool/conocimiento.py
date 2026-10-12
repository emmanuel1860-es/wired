"""Lo confirmado y lo no confirmado, sacado de los exports del usuario (ver SKILL.md de habbo-wired)."""
import re

SECCION_PREFIJO = {
    'triggers': 'wf_trg_', 'selectors': 'wf_slc_', 'conditions': 'wf_cnd_',
    'effects': 'wf_act_', 'addons': 'wf_xtra_', 'variables': 'wf_var_',
}
# furnis wf_* que van solo en `furni` (sin entrada en `wired`)
SIN_ENTRADA = ('wf_antenna', 'wf_contract_', 'wf_storage_', 'wf_wire', 'wf_floor_switch', 'wf_button',
               'wf_firegate', 'wf_glowball', 'wf_colortile', 'wf_teamcolortile', 'wf_upcounter', 'wf_numberscreen',
               'wf_teammeter', 'wf_arrowplate', 'wf_room_linker', 'wf_blob', 'wf_conf_')

COMPARACION = {'<': 0, '=': 1, '>': 2, '!=': 4, '>=': 5}
OPERACION = {'asignar': 0, 'sumar': 1, 'restar': 2, 'dividir': 4, 'modulo': 6, 'maximo': 41, 'aleatorio': 50}
TIPO_AMBITO = {'usuario': 1, 'sala': -10, 'contexto': -20, 'furni': 0}
UNIDAD_EDAD = {'segundos': 1, 'minutos': 2, 'horas': 3, 'dias': 4, 'semanas': 5}
# subvariables de ~area_hide (kiliki.json; orden asumido)
ZONA = {'root_x': '~110', 'root_y': '~111', 'width': '~112', 'length': '~113'}
VAR_INTERNAS = {'-100': 'id furni', '-101': 'tipo furni', '-110': '@state', '-120': 'x furni', '-121': 'y furni',
                '-122': 'rotación furni', '-123': 'altitud furni', '-222': 'permisos', '-230': 'x usuario',
                '-231': 'y usuario', '-232': 'dirección', '-233': 'altitud usuario', '-240': 'handitem',
                '-270': 'id usuario', '-450': 'tipo de mensaje', '-451': 'estilo de burbuja'}

# (descripcion, funcion(clase, wired) -> bool). Todo esto se avisa como "sin confirmar".
def _opt(w, i, default=None):
    o = w.get('options') or []
    return o[i] if i < len(o) else default

SIN_CONFIRMAR = [
    ('variable -401 como "cantidad de usuarios seleccionados"', lambda c, w: '-401' in w.get('variableIds', [])),
    ('wf_cnd_actor_in_group (formato de opciones)', lambda c, w: c == 'wf_cnd_actor_in_group'),
    ('operación 3 de change_var_val (multiplicación)', lambda c, w: c == 'wf_act_change_var_val' and _opt(w, 1) == 3),
    ('reloj con opciones [30,1,1]', lambda c, w: c == 'wf_trg_clock_counter' and (w.get('options') or []) == [30, 1, 1]),
    ('unidad de edad 2/3/4 en wf_cnd_var_age_match (solo 1=segundos y 5=semanas están vistas)',
     lambda c, w: c == 'wf_cnd_var_age_match' and _opt(w, 5) in (2, 3, 4)),
    ('subvariables de ~area_hide (~110 a ~113): orden asumido', lambda c, w: any(v in ('~110', '~111', '~112', '~113') for v in w.get('variableIds', []))),
    ('wf_trg_click_user: significado de las opciones', lambda c, w: c == 'wf_trg_click_user'),
    ('@state de conf_area_hide como interruptor de ocultar', lambda c, w: c == 'wf_act_change_var_val' and '-110' in w.get('variableIds', []) and False),
]

def codigo_ok(s):
    return isinstance(s, str)

def doble_codificado(texto):
    """True si el texto con acentos parece ya doble codificado (utf8 leído como latin1)."""
    return 'Ã' in texto or 'Â' in texto

def tiene_no_ascii(texto):
    return any(ord(c) > 127 for c in texto)

def a_doble(texto):
    return texto.encode('utf-8').decode('latin-1')

def de_doble(texto):
    try: return texto.encode('latin-1').decode('utf-8')
    except Exception: return texto
