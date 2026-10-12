#!/usr/bin/env python3
"""Huella de fidelidad de una clase recompilada frente a la original.
Uso: fp.py <jar_original> <carpeta_clases_nuevas> <nombre.Completo.Clase> [--todo]
Por metodo (excepto lambdas, que se agregan) compara el multiconjunto de operandos de invoke*/get/put/new/checkcast/instanceof/ldc.
Un metodo con diferencias = el descompilador pudo haber cambiado la logica (o javac eligio otra forma): revisalo a mano."""
import subprocess, sys, re, collections, os
JAVA_OPTS = []
def javap(cp, cls):
    out = subprocess.run(['javap','-p','-c','-constants','-cp',cp,cls],capture_output=True,text=True).stdout
    return out
OPS = re.compile(r'^\s+\d+:\s+(invoke\w+|getfield|putfield|getstatic|putstatic|new|checkcast|instanceof|ldc\w*|anewarray|multianewarray)\s+#\d+(?:,\s*\d+)?\s*(?://\s*(.*))?$')
def parse(txt):
    methods = collections.OrderedDict(); cur=None
    for line in txt.splitlines():
        m = re.match(r'^  (?:[\w<>\[\],.$ ?&@]+\s)?([\w$<>]+\(.*\)|static \{\});?', line)
        if re.match(r'^  \S.*[;{]$', line) and '(' in line and not line.strip().startswith('//'):
            cur = line.strip(); methods[cur]=collections.Counter(); continue
        if re.match(r'^  static \{\};', line): cur='static{}'; methods[cur]=collections.Counter(); continue
        mo = OPS.match(line)
        if mo and cur is not None:
            op, arg = mo.group(1), (mo.group(2) or '')
            arg = re.sub(r'(Method|Field|InterfaceMethod|class|String|int|float|long|double)\s+','',arg).strip()
            arg = re.sub(r'lambda\$[\w$]+\$\d+','lambda$N',arg)
            methods[cur][op+' '+arg]+=1
    return methods
def norm(name):
    return re.sub(r'lambda\$[\w$]+\$\d+','lambda$N',name)
def agg(methods):
    res = collections.OrderedDict(); lam = collections.Counter()
    for k,c in methods.items():
        if 'lambda$' in k: lam.update(c)
        else: res[re.sub(r'\s+',' ',k)] = c
    res['<<lambdas agregadas>>']=lam
    return res
def main():
    jar, cls_dir, cls = sys.argv[1], sys.argv[2], sys.argv[3]
    if cls == '--all':
        # todas las clases (incluidas internas y anonimas) que haya en cls_dir
        tot = 0
        names = []
        for root, _, files in os.walk(cls_dir):
            for f in files:
                if f.endswith('.class'):
                    names.append(os.path.relpath(os.path.join(root, f), cls_dir)[:-6].replace(os.sep, '.'))
        for n in sorted(names):
            r = subprocess.run([sys.executable, __file__, jar, cls_dir, n] + sys.argv[4:], capture_output=True, text=True).stdout
            last = [l for l in r.splitlines() if l.startswith('RESUMEN')]
            if not last or not last[-1].startswith('RESUMEN %s: 0 ' % n):
                print(r.rstrip())
                tot += 1
        print('TOTAL: %d clases con diferencias de %d' % (tot, len(names)))
        return
    show_all = '--todo' in sys.argv
    fx = ':'.join(os.path.join('/usr/share/openjfx/lib',f) for f in os.listdir('/usr/share/openjfx/lib') if f.endswith('.jar'))
    a = agg(parse(javap(jar+':'+fx, cls)))
    b = agg(parse(javap(cls_dir+':'+jar+':'+fx, cls)))
    bad=0
    for k in a:
        ca, cb = a[k], b.get(k)
        if cb is None:
            print('FALTA metodo en la nueva:', k); bad+=1; continue
        if ca != cb:
            bad+=1
            print('DIFIERE:', k)
            for op in sorted(set(ca)|set(cb)):
                if ca[op]!=cb[op]: print('    %-90s orig=%d nueva=%d'%(op[:90],ca[op],cb[op]))
        elif show_all: print('igual  :', k)
    for k in b:
        if k not in a: print('SOBRA metodo en la nueva:', k); bad+=1
    print('RESUMEN %s: %d metodos con diferencias de %d' % (cls, bad, len(a)))
main()
