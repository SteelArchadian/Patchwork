"""Patchwork's private work inbox (this PC only; nothing here ships in the mod).

Scans every Obelisk pack's logs and crash reports for things Patchwork should act on:
  switchedoff  a module turned itself off because its target mod updated ("[Patchwork] x: SWITCHED OFF: ...")
  mixinfail    one of Patchwork's mixins failed to apply
  crash        a crash report whose stack runs through Patchwork's code
New items go to C:/mcw/patchbox/inbox/INBOX.md (open list) and are remembered in seen.json, so each one is
reported once. Usage:
  inbox.py            scan, print NEW items only (empty output = nothing to do); for the Overseer's checks
  inbox.py --list     print every open item
  inbox.py --done KEY mark an item handled (removes it from INBOX.md)
"""
import glob, json, os, re, sys, time

INSTANCES = 'D:/Modding/Minecraft/Obelisk/Instances'
BOX = 'C:/mcw/patchbox/inbox'
SEEN = os.path.join(BOX, 'seen.json')
OPEN = os.path.join(BOX, 'INBOX.md')

SWITCHED = re.compile(r'\[Patchwork\] (\w+): SWITCHED OFF: (.+?) is now (\S+) but this fix was written for (.+?)\. ')
MIXINFAIL = re.compile(r'Mixin \[patchwork\.mixins\.json:([\w.]+)[^\]]*\].*?(FAILED|failed)')
# a frame in our own classes or in a method a Patchwork mixin added; the "{pl:mixin:APP:patchwork.mixins.json:..}"
# tags only say a class we patch was on the stack, which is true of half the game
OURS = re.compile(r'^\s*at \S*(dev\.chill\.patchwork\.|patchwork\$)', re.M)


def load(path, default):
    try:
        with open(path, encoding='utf-8') as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def scan():
    items = {}
    for mc in sorted(glob.glob(INSTANCES + '/*/minecraft')):
        pack = os.path.basename(os.path.dirname(mc))
        for log in ('logs/latest.log', 'logs/debug.log'):
            try:
                text = open(os.path.join(mc, log), encoding='utf-8', errors='replace').read()
            except OSError:
                continue
            for m in SWITCHED.finditer(text):
                module, mod, found, expected = m.groups()
                items[f'switchedoff:{module}:{found}'] = (
                    f'{pack}: module {module} off, {mod} is now {found} (fix written for {expected})')
            for m in MIXINFAIL.finditer(text):
                items[f'mixinfail:{m.group(1)}:{pack}'] = f'{pack}: mixin {m.group(1)} failed to apply ({log})'
        for crash in glob.glob(os.path.join(mc, 'crash-reports', '*.txt')):
            try:
                head = open(crash, encoding='utf-8', errors='replace').read(200_000)
            except OSError:
                continue
            # only the stack above "-- System Details --": the mod list there always names Patchwork
            stack = head.split('-- System Details --')[0]
            if OURS.search(stack):
                desc = next((l.strip() for l in stack.splitlines() if l.startswith('Description:')), '')
                items[f'crash:{pack}:{os.path.basename(crash)}'] = f'{pack}: crash through Patchwork code, {desc} ({crash})'
    return items


def write_open(open_items):
    os.makedirs(BOX, exist_ok=True)
    lines = ['# Patchwork inbox (private, this PC only)', '',
             'Open items for the Patchwork chat. Handle, then `python -I C:/mcw/patchbox/tools/inbox.py --done KEY`.', '']
    for key, (when, text) in sorted(open_items.items()):
        lines.append(f'- `{key}` ({when}): {text}')
    with open(OPEN, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')


def main():
    state = load(SEEN, {'seen': [], 'open': {}})
    seen, open_items = set(state['seen']), state['open']
    if len(sys.argv) > 2 and sys.argv[1] == '--done':
        for key in sys.argv[2:]:
            open_items.pop(key, None)
    elif len(sys.argv) > 1 and sys.argv[1] == '--list':
        for key, (when, text) in sorted(open_items.items()):
            print(f'{key}  {text}')
        return
    else:
        now = time.strftime('%Y-%m-%d %H:%M')
        for key, text in scan().items():
            if key not in seen:
                seen.add(key)
                open_items[key] = [now, text]
                print(f'NEW {key}  {text}')
    os.makedirs(BOX, exist_ok=True)
    with open(SEEN, 'w', encoding='utf-8') as f:
        json.dump({'seen': sorted(seen), 'open': open_items}, f, indent=1)
    write_open(open_items)


if __name__ == '__main__':
    main()
