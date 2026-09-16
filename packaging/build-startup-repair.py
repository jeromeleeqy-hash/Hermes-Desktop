#!/usr/bin/env python3
"""Build a small repair installer and overlay ZIP from the verified fixed portable distribution."""
import argparse
import hashlib
import json
import os
import subprocess
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILES = {'Hermes.exe', 'Start-Hermes.bat', 'Diagnose-Hermes.bat', 'Run-Hermes.bat', 'hermes.vmoptions'}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--portable', type=Path, required=True)
    p.add_argument('--sha256', required=True)
    p.add_argument('--makensis', required=True)
    p.add_argument('--nsis-dir', required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--zip', type=Path, required=True)
    a = p.parse_args()
    assert sha(a.portable.read_bytes()) == a.sha256
    a.output = a.output.resolve(); a.zip = a.zip.resolve()
    a.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(a.portable) as z, tempfile.TemporaryDirectory(prefix='hermes-startup-repair-', dir=a.output.parent) as temp:
        prefix = 'Hermes-Windows-1.8.0/'
        manifest = json.loads(z.read(prefix + 'manifest.json'))
        assert manifest['build'] == '1.8.0+startup.1'
        payload = Path(temp) / 'payload'; payload.mkdir()
        contents = {}
        for name in z.namelist():
            relative = name.removeprefix(prefix)
            if name.startswith(prefix) and (relative in FILES or relative.startswith('runtime/')) and not name.endswith('/'):
                path = payload / relative; path.parent.mkdir(parents=True, exist_ok=True)
                contents[relative] = z.read(name); path.write_bytes(contents[relative])
        for row in manifest['runtime_files']:
            assert sha((payload / 'runtime' / row['path']).read_bytes()) == row['sha256']
        assert sha((payload / 'Hermes.exe').read_bytes()) == manifest['launcher_sha256']
        env = os.environ.copy(); env['NSISDIR'] = str(Path(a.nsis_dir).resolve())
        flag = '/' if os.name == 'nt' else '-'
        staged = Path(temp) / a.output.name
        subprocess.run([a.makensis, flag+'V2', flag+'WX', flag+'DOUTPUT='+str(staged), flag+'DPAYLOAD='+str(payload),
                        flag+'DICON='+str(ROOT / 'packaging/Hermes.ico'), str(ROOT / 'packaging/Hermes-Startup-Repair.nsi')], env=env, check=True)
        staged.replace(a.output)
        contents['修复说明.txt'] = (ROOT / 'docs/STARTUP-FIX-README.txt').read_bytes()
        contents['startup-fix.ini'] = b'[Hermes]\r\nBuild=1.8.0+startup.1\r\n'
        with zipfile.ZipFile(a.zip, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as patch:
            for name, data in sorted(contents.items()): patch.writestr(name, data)
        with zipfile.ZipFile(a.zip) as patch:
            assert patch.testzip() is None
            for name, data in contents.items(): assert sha(patch.read(name)) == sha(data), name
    print(json.dumps([{'file':str(f),'bytes':f.stat().st_size,'sha256':sha(f.read_bytes())} for f in (a.output,a.zip)], indent=2))


if __name__ == '__main__':
    main()
