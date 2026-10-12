import argparse, json, sys
from . import __version__
from .io import cargar, guardar
from .validar import validar, resumen
from .catalogo import construir, imprimir
from .comparar import comparar
from .plano import texto, png
from .generar import generar, ErrorSpec

def main(argv=None):
    p = argparse.ArgumentParser(prog='wiredtool', description='Generador y validador de presets de wired (solo archivos JSON).')
    p.add_argument('--version', action='version', version=__version__)
    sp = p.add_subparsers(dest='cmd', required=True)
    a = sp.add_parser('validar', help='revisa un preset antes de importarlo'); a.add_argument('preset'); a.add_argument('--solo-errores', action='store_true')
    a = sp.add_parser('catalogo', help='formatos de wired vistos en tus exports'); a.add_argument('exports', nargs='+'); a.add_argument('--buscar'); a.add_argument('-o', '--salida')
    a = sp.add_parser('comparar', help='diferencias entre dos presets'); a.add_argument('a'); a.add_argument('b')
    a = sp.add_parser('plano', help='plano de huellas del preset'); a.add_argument('preset'); a.add_argument('-o', '--png')
    a = sp.add_parser('generar', help='crea un preset desde una descripción JSON'); a.add_argument('descripcion'); a.add_argument('-o', '--salida', required=True)
    args = p.parse_args(argv)
    if args.cmd == 'validar':
        res = validar(cargar(args.preset))
        for n, m in res:
            if args.solo_errores and n != 'error': continue
            print(f'[{n}] {m}')
        print('\n' + resumen(res)); return 1 if any(n == 'error' for n, _ in res) else 0
    if args.cmd == 'catalogo':
        cat = construir(args.exports)
        if args.salida: json.dump(cat, open(args.salida, 'w', encoding='utf-8'), ensure_ascii=False, indent=1); print('guardado', args.salida)
        else: imprimir(cat, args.buscar)
        return 0
    if args.cmd == 'comparar':
        res = comparar(cargar(args.a), cargar(args.b))
        for n, m in sorted(res): print(f'{n}: {m}')
        print(f'\n{len(res)} diferencias'); return 0
    if args.cmd == 'plano':
        d = cargar(args.preset)
        if args.png:
            print('guardado ' + args.png if png(d, args.png) else 'matplotlib no está instalado; uso el plano de texto')
        print(texto(d)); return 0
    if args.cmd == 'generar':
        try: d = generar(json.load(open(args.descripcion, encoding='utf-8')))
        except ErrorSpec as e: print('error en la descripción:', e); return 2
        guardar(d, args.salida)
        res = validar(d)
        for n, m in res:
            if n != 'info': print(f'[{n}] {m}')
        print(f'generado {args.salida}: {len(d["furni"])} furnis. ' + resumen(res)); return 1 if any(n == 'error' for n, _ in res) else 0
