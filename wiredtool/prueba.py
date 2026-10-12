"""Prueba rápida: python3 -m wiredtool.prueba"""
import json, os, tempfile
from .generar import generar
from .validar import validar
from .comparar import comparar
from .io import cargar

def main():
    aqui = os.path.dirname(__file__)
    spec = json.load(open(os.path.join(aqui, 'ejemplos', 'tienda.json'), encoding='utf-8'))
    d = generar(spec)
    errores = [m for n, m in validar(d) if n == 'error']
    assert not errores, errores
    assert any(x['config'] == ':vida' for x in d['wired']['triggers'])
    d2 = generar(spec); d2['furni'][-1]['location']['x'] += 1
    assert comparar(d, d2), 'comparar no vio el cambio'
    assert not comparar(d, generar(spec)), 'dos generaciones iguales no deben diferir'
    # mensaje con acento queda doble codificado
    spec['pilas'][1]['efectos'][2]['texto'] = 'Compraste una vida extra: ¡gracias!'
    msgs = [x['config'] for x in generar(spec)['wired']['effects'] if 'gracias' in x['config']]
    assert msgs and 'Â¡' in msgs[0], msgs
    print('ok')
main()
