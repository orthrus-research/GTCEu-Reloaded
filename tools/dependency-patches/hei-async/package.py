"""Package the one-class repair; fail if it differs from the qualified bytes."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--original', required=True, type=Path)
p.add_argument('--output', required=True, type=Path)
a = p.parse_args()
root = Path(__file__).resolve().parent
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
record = lambda path: {'path': str(path.resolve()), 'sha256': digest(path), 'bytes': path.stat().st_size}
if digest(a.original) != 'c9b812ac3396f3628ca314f10dd775e0f0098cdbaec56be13b4a4f57b70c4443':
    raise ValueError('Unrecognized upstream HEI bytes')
a.output.mkdir(parents=True, exist_ok=False)
target = a.output/'jei-4.36.1-async-completion.1.jar'
entry = 'mezz/jei/search/AsyncPrefixedSearchable.class'
with zipfile.ZipFile(root/'build/hei-async-repair.jar') as repair:
    if [n for n in repair.namelist() if n.endswith('.class')] != [entry]:
        raise ValueError('Unexpected repair class inventory')
    replacement = repair.read(entry)
with zipfile.ZipFile(a.original) as original, zipfile.ZipFile(target,'x') as output:
    if any(n.upper().endswith(('.SF','.RSA','.DSA')) for n in original.namelist()):
        raise ValueError('Signed upstream input requires separate handling')
    for item in original.infolist():
        output.writestr(item, replacement if item.filename == entry else original.read(item.filename))
with zipfile.ZipFile(a.original) as original, zipfile.ZipFile(target) as output:
    changed = [n for n in original.namelist() if original.read(n) != output.read(n)]
    if original.namelist() != output.namelist() or changed != [entry]:
        raise ValueError('Repair changed unrelated entries')
if digest(target) != 'af38fd9a883f629a4be16a3eac14b95ec5d9b3bb91802b994b8cc7ae19361d6d':
    raise ValueError('Rebuilt repair differs from qualified bytes; preserve output and requalify')
(a.output/'receipt.json').write_text(json.dumps({'original':record(a.original),'adapted':record(target),
    'changed_entries':changed,'sources':[record(f) for f in sorted(root.rglob('*')) if f.is_file() and 'build' not in f.relative_to(root).parts]},indent=2))
print(json.dumps(record(target)))
