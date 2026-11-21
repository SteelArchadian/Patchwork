"""Pin every mod jar a Patchwork module is version-gated to, so Obelisk's Update all can't switch modules off.

Reads target mod ids from Module.java, finds the jar (or the jar that nests it) in each pack, and merges the file
names into <instance>/minecraft/.obelisk/pins.json (backup kept next to it). Rerun after adding a module.
"""
import io, json, re, shutil, sys, time, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INSTANCES = Path("D:/Modding/Minecraft/Obelisk/Instances")
PACKS = ["Foundation", "Odyssey", "Test Dummy"]
EXTRA = {"structblend"}  # openclstructblend also requires StructBlend's own classes
SKIP = {"minecraft", "neoforge"}

src = (ROOT / "src/main/java/dev/chill/patchwork/Module.java").read_text(encoding="utf-8")
targets = set(re.findall(r'^    [A-Z_0-9]+\("[a-z0-9]+", "([a-z0-9_]+)", Set\.of', src, re.M)) - SKIP | EXTRA


def mod_ids(data, nested=True):
    ids = set()
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        names = set(z.namelist())
        for meta in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
            if meta in names:
                # Only [[mods]] blocks: [[dependencies]] blocks name other mods' ids too.
                text = z.read(meta).decode("utf-8", "replace")
                for block in re.split(r"^\s*\[\[", text, flags=re.M)[1:]:
                    if block.startswith("mods]]"):
                        ids |= set(re.findall(r'modId\s*=\s*"([^"]+)"', block))
        if "fabric.mod.json" in names:
            try:
                ids.add(json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"))["id"])
            except Exception:
                pass
        for n in names if nested else ():
            if n.endswith(".jar") and (n.startswith("META-INF/jarjar/") or n.startswith("META-INF/jars/")):
                try:
                    ids |= mod_ids(z.read(n))
                except Exception:
                    pass
    return ids


def project_keys(mods_dir):
    """File name -> "modrinth:<id>" / "curseforge:<id>" from the pack's mods/.index/*.pw.toml (a key survives renames)."""
    keys = {}
    for toml in (mods_dir / ".index").glob("*.pw.toml"):
        text = toml.read_text(encoding="utf-8", errors="replace")
        name = re.search(r"^filename\s*=\s*['\"]([^'\"]+)", text, re.M)
        mr = re.search(r"\[update\.modrinth\][^\[]*?mod-id\s*=\s*['\"]([^'\"]+)", text, re.S)
        cf = re.search(r"\[update\.curseforge\][^\[]*?project-id\s*=\s*['\"]?(\d+)", text, re.S)
        if name and (mr or cf):
            keys[name.group(1)] = f"modrinth:{mr.group(1)}" if mr else f"curseforge:{cf.group(1)}"
    return keys


# Restored jars often have no index entry of their own: find their key by mod id from any pack's index.
KEY_BY_ID = {}
for pack in PACKS:
    mods_dir = INSTANCES / pack / "minecraft" / "mods"
    for name, key in project_keys(mods_dir).items():
        if (mods_dir / name).exists():
            for mod_id in mod_ids((mods_dir / name).read_bytes(), nested=False):
                KEY_BY_ID.setdefault(mod_id, key)


def key_for(jar, keys):
    if jar.name in keys:
        return keys[jar.name]
    own = sorted(mod_ids(jar.read_bytes(), nested=False))
    return next((KEY_BY_ID[i] for i in own if i in KEY_BY_ID), jar.name)


dry = "--dry" in sys.argv
for pack in PACKS:
    mc = INSTANCES / pack / "minecraft"
    keys = project_keys(mc / "mods")
    pins = []
    for jar in sorted((mc / "mods").glob("*.jar")):
        try:
            hit = mod_ids(jar.read_bytes()) & targets
        except Exception:
            continue
        if hit:
            pin = key_for(jar, keys)
            pins.append(pin)
            print(f"{pack}: {pin} {jar.name} ({', '.join(sorted(hit))})")
    pin_file = mc / ".obelisk" / "pins.json"
    old = json.loads(pin_file.read_text(encoding="utf-8")) if pin_file.exists() else []
    # File-name pins (this script's first version) go stale on rename; project keys replace them where known.
    old = [p for p in old if not (p.endswith(".jar") and p not in pins)]
    merged = old + [p for p in pins if p not in old]
    if dry or merged == old:
        continue
    if pin_file.exists():
        shutil.copy2(pin_file, pin_file.with_name(f"pins.json.bak-{int(time.time())}"))
    pin_file.write_text(json.dumps(merged, indent=2) + "\n", encoding="utf-8")
    print(f"{pack}: wrote {len(merged)} pins ({len(merged) - len(old)} new)")
