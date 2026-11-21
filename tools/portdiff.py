"""Diff the bytecode of a mod class between two jars (nested jars searched too).
Usage: portdiff.py OLD.jar NEW.jar some.pkg.Class [methodName ...]
Prints the methods' javap -c bodies side by side as a unified diff (or "same")."""
import difflib, io, os, re, subprocess, sys, tempfile, zipfile

def find_class(jar_bytes, path):
    z = zipfile.ZipFile(io.BytesIO(jar_bytes))
    if path in z.namelist():
        return z.read(path)
    for n in z.namelist():
        if n.endswith('.jar'):
            hit = find_class(z.read(n), path)
            if hit:
                return hit
    return None

def javap(cls_bytes):
    with tempfile.NamedTemporaryFile(suffix='.class', delete=False) as f:
        f.write(cls_bytes)
    try:
        return subprocess.run(['javap', '-c', '-p', f.name], capture_output=True, text=True).stdout
    finally:
        os.unlink(f.name)

def methods(text, names):
    out, cur, keep = {}, None, False
    for line in text.splitlines():
        m = re.match(r'^  \S.*?(\S+)\((.*)\);?$', line)
        if m and not line.startswith('    '):
            cur = line.strip()
            keep = not names or m.group(1).split('.')[-1] in names
            if keep:
                out[cur] = []
            continue
        if keep and cur:
            # drop constant-pool indices so pure renumbering doesn't show as a change
            out[cur].append(re.sub(r'#\d+(, \d+)?', '#', re.sub(r'^\s*\d+: ', '', line)))
    return out

old, new, cls = sys.argv[1], sys.argv[2], sys.argv[3]
names = set(sys.argv[4:])
path = cls.replace('.', '/') + '.class'
a = find_class(open(old, 'rb').read(), path)
b = find_class(open(new, 'rb').read(), path)
if a is None or b is None:
    print(f'{cls}: missing in {"old" if a is None else "new"} jar'); sys.exit()
ma, mb = methods(javap(a), names), methods(javap(b), names)
for sig in sorted(set(ma) | set(mb)):
    if sig not in mb:
        print('GONE  ', sig); continue
    if sig not in ma:
        print('NEW   ', sig); continue
    d = list(difflib.unified_diff(ma[sig], mb[sig], lineterm='', n=1))
    print(('same  ' if not d else 'CHANGED ') + sig)
    for l in d[2:60]:
        print('    ' + l)
