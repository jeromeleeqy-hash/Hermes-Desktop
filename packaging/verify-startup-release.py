#!/usr/bin/env python3
"""Compare actual installer payloads with ZIPs, reject the original broken runtime, and confirm unchanged application JARs."""
import argparse
import hashlib
import json
import subprocess
import tempfile
import zipfile
from pathlib import Path
from windows_runtime import validate_runtime_bytes


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--release-dir', type=Path, required=True)
    p.add_argument('--base', type=Path, required=True)
    p.add_argument('--sevenzip', required=True)
    p.add_argument('--repair-only', action='store_true')
    p.add_argument('--report', type=Path, required=True)
    a = p.parse_args(); prefix = 'Hermes-Windows-1.8.0/'
    report = {'installers': {}, 'real_windows_execution': False}
    with zipfile.ZipFile(a.base) as old, zipfile.ZipFile(a.release_dir/'Hermes-Windows-1.8.0-startup-fixed-portable.zip') as fixed:
        assert fixed.testzip() is None
        try:
            validate_runtime_bytes(old.read(prefix + 'runtime/lib/modules'))
        except ValueError as e:
            assert 'jdk.unsupported.desktop' in str(e)
            report['old_runtime_rejected'] = True
        else:
            raise AssertionError('Broken runtime unexpectedly passed validation')
        modules = validate_runtime_bytes(fixed.read(prefix + 'runtime/lib/modules'))
        report['actual_windows_runtime_modules'] = modules
        expected = {n.removeprefix(prefix): fixed.read(n) for n in fixed.namelist() if n.startswith(prefix) and not n.endswith('/')}
        jars = [n for n in expected if n.startswith('app/') and n.endswith('.jar')]
        for name in jars:
            assert sha(expected[name]) == sha(old.read(prefix + name)), name
        report['unchanged_app_jars'] = len(jars)
        targets = [('Hermes-Windows-1.8.0-Startup-Repair.exe', {n:b for n,b in expected.items() if n.startswith('runtime/') or n in {'Hermes.exe','Start-Hermes.bat','Diagnose-Hermes.bat','Run-Hermes.bat','hermes.vmoptions'}})]
        if not a.repair_only:
            targets.append(('Hermes-Windows-1.8.0-startup-fixed-Setup.exe', expected))
        with zipfile.ZipFile(a.release_dir/'Hermes-Windows-1.8.0-Startup-Repair.zip') as overlay:
            assert overlay.testzip() is None
            for name, data in targets[0][1].items(): assert sha(overlay.read(name)) == sha(data)
        for name, files in targets:
            installer = a.release_dir/name
            with tempfile.TemporaryDirectory(prefix='hermes-payload-check-') as temp:
                subprocess.run([a.sevenzip,'x','-y','-o'+temp,str(installer)],check=True,capture_output=True)
                streamed = []
                for relative, data in files.items():
                    extracted = Path(temp)/relative
                    if not extracted.is_file() or sha(extracted.read_bytes()) != sha(data):
                        # Some hosts impose extraction limits; verify the archive stream directly.
                        stream = subprocess.run([a.sevenzip,'x','-so',str(installer),relative],check=True,capture_output=True).stdout
                        assert sha(stream) == sha(data), (name, relative)
                        streamed.append(relative)
                report['installers'][name] = {'sha256':sha(installer.read_bytes()),'bytes':installer.stat().st_size,
                                             'matched_payload_files':len(files),'direct_stream_checks':streamed}
    a.report.parent.mkdir(parents=True,exist_ok=True)
    a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='actual_windows_runtime_modules'},ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()
