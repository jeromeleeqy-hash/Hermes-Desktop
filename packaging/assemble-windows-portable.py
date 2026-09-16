#!/usr/bin/env python3
"""Combine Windows staging with a verified, complete desktop runtime.

Example:
  python packaging/assemble-windows-portable.py --app build/windows-staging/app \
    --runtime build/windows-runtime \
    --output Hermes-Windows-1.8.3-portable.zip

Build the runtime with build-windows-runtime.py from an official Windows JDK.
Legacy --jre/--sha256 input remains supported only if its actual module image is complete.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import struct
import tempfile
import zipfile
from windows_runtime import validate_runtime_image

VERSION = '1.8.4'


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def assert_x64_pe(data: bytes) -> None:
    assert data[:2] == b'MZ', 'Not a Windows executable'
    pe = struct.unpack_from('<I', data, 0x3C)[0]
    assert data[pe:pe + 4] == b'PE\0\0', 'Invalid PE signature'
    assert struct.unpack_from('<H', data, pe + 4)[0] == 0x8664, 'Expected Windows x64'


def safe_parts(name: str) -> tuple[str, ...]:
    p = PurePosixPath(name)
    assert not p.is_absolute() and '..' not in p.parts and '\\' not in name
    return p.parts


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', type=Path, required=True)
    runtime = parser.add_mutually_exclusive_group(required=True)
    runtime.add_argument('--runtime', type=Path)
    runtime.add_argument('--jre', type=Path)
    parser.add_argument('--sha256')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    project = Path(__file__).resolve().parent.parent
    jars = sorted(args.app.glob('*.jar'))
    names = {p.name for p in jars}
    assert 'hermes-desktop.jar' in names, 'Application JAR is missing'
    if args.jre:
        assert args.sha256 and digest(args.jre) == args.sha256.lower(), 'Official runtime checksum does not match'
    current_app = project / 'build/libs/HermesDesktop.jar'
    if current_app.is_file():
        assert digest(current_app) == digest(args.app/'hermes-desktop.jar'), 'Windows staging is stale'
    assert not any('-linux' in n or '-mac-' in n or '-macos' in n for n in names), 'Foreign platform library'
    assert not any(n.startswith(('junit-', 'mockito-', 'mockwebserver-')) for n in names), 'Test dependency in app'
    for module in ('base', 'graphics', 'controls', 'media', 'web', 'swing'):
        assert f'javafx-{module}-21.0.8-win.jar' in names, f'Windows JavaFX {module} is missing'
    native = next((p for p in jars if p.name.startswith('skiko-awt-runtime-windows-x64-')), None)
    assert native, 'Windows Skiko runtime is missing'
    with zipfile.ZipFile(native) as z:
        dlls = [n for n in z.namelist() if n.lower().endswith('.dll')]
        assert dlls, 'No Windows Skiko native library'
        for n in dlls:
            assert_x64_pe(z.read(n))
    with zipfile.ZipFile(args.app / 'hermes-desktop.jar') as z:
        assert 'com/qingyu/hermescompanion/desktop/MainKt.class' in z.namelist()
        assert 'desktop-en.json' in z.namelist()
        assert VERSION.encode() in z.read('com/qingyu/hermescompanion/BuildConfig.class')
        assert len(z.namelist()) == len(set(z.namelist()))
        assert not any('Test.class' in n for n in z.namelist())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='hermes-portable-', dir=args.output.parent) as tmp:
        root = Path(tmp) / f'Hermes-Windows-{VERSION}'
        (root / 'app').mkdir(parents=True)
        for jar in jars:
            shutil.copy2(jar, root / 'app' / jar.name)
        if args.runtime:
            shutil.copytree(args.runtime, root/'runtime')
        else:
            with zipfile.ZipFile(args.jre) as z:
                for item in z.infolist():
                    parts = safe_parts(item.filename)
                    if len(parts) < 2 or item.is_dir():
                        continue
                    out = root / 'runtime' / Path(*parts[1:])
                    out.parent.mkdir(parents=True, exist_ok=True)
                    with z.open(item) as src, out.open('wb') as dst:
                        shutil.copyfileobj(src, dst)
        assert_x64_pe((root / 'runtime/bin/java.exe').read_bytes())
        runtime_modules = validate_runtime_image(root / 'runtime/lib/modules')
        origin = json.loads((root/'runtime/hermes-runtime.json').read_text()) if args.runtime else None
        if origin:
            assert origin['module_image_sha256'] == digest(root/'runtime/lib/modules')
        release = (root / 'runtime/release').read_text()
        assert 'OS_NAME="Windows"' in release and 'JAVA_VERSION="21.' in release
        assert (root / 'runtime/legal/java.base/LICENSE').is_file()
        shutil.copy2(project / 'build/native-launcher/Hermes.exe', root / 'Hermes.exe')
        for name in ('Start-Hermes.bat', 'Diagnose-Hermes.bat', 'Run-Hermes.bat', 'hermes.vmoptions'):
            shutil.copy2(project / 'packaging' / name, root / name)
        shutil.copy2(project / 'docs/PORTABLE-README.txt', root / '开始使用.txt')
        shutil.copy2(project / 'THIRD_PARTY_NOTICES.md', root / 'THIRD_PARTY_NOTICES.md')
        shutil.copytree(project / 'licenses', root / 'licenses')
        (root / 'docs').mkdir()
        for name in ('CHANGELOG.md', 'WINDOWS-QUICKSTART.md', 'WINDOWS-VALIDATION.md', 'RELEASE-1.8.3.md', 'RELEASE-1.8.3-UI3.md', 'STARTUP-FIX-1.8.0.md'):
            shutil.copy2(project / 'docs' / name, root / 'docs' / name)
        for preview in ('windows-1.8.0', 'windows-1.8.1-ui', 'windows-1.8.2', 'windows-1.8.3', 'desktop-1.8.3-ui3'):
            if (project/'docs/previews'/preview).is_dir(): shutil.copytree(project/'docs/previews'/preview, root/'docs/previews'/preview)
        (root / 'docs/validation').mkdir()
        for file in (project/'docs/validation').glob('*1.8.3-*'):
            if file.is_file() and file.name!='windows-1.8.3-package.json':shutil.copy2(file,root/'docs/validation'/file.name)
        # Preserve license documents as stored in upstream JARs, in addition to the JARs themselves.
        for jar in jars:
            with zipfile.ZipFile(jar) as z:
                for item in z.infolist():
                    parts = safe_parts(item.filename)
                    if item.is_dir() or not any(x in PurePosixPath(item.filename).name.upper() for x in ('LICENSE', 'NOTICE', 'COPYING')):
                        continue
                    if item.filename.endswith('.class') or item.file_size > 2 * 1024 * 1024:
                        continue
                    out = root / 'licenses/dependencies' / jar.stem / Path(*parts)
                    out.parent.mkdir(parents=True, exist_ok=True)
                    out.write_bytes(z.read(item))
        manifest = {
            'application': 'Hermes', 'version': VERSION, 'edition': 'Desktop UI revision 3', 'platform': 'windows-x64',
            'launcher': 'Hermes.exe', 'launcher_sha256': digest(root / 'Hermes.exe'),
            'runtime_archive': origin['jdk_archive'] if origin else args.jre.name,
            'runtime_sha256': origin['jdk_sha256'] if origin else args.sha256.lower(),
            'runtime_source': origin['source'] if origin else 'https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1',
            'runtime_build': origin,
            'build': '1.8.3', 'runtime_modules_verified': runtime_modules,
            'runtime_files': [{'path':f.relative_to(root/'runtime').as_posix(),'bytes':f.stat().st_size,'sha256':digest(f)} for f in sorted((root/'runtime').rglob('*')) if f.is_file()],
            'jars': [{'name': p.name, 'bytes': p.stat().st_size, 'sha256': digest(p)} for p in jars],
            'validation': 'See docs/WINDOWS-VALIDATION.md; Windows native execution not performed on the Linux build host.',
        }
        (root / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
        staged_zip = Path(tmp) / 'portable.zip'
        with zipfile.ZipFile(staged_zip, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            for p in sorted(root.rglob('*')):
                if p.is_file():
                    z.write(p, str(p.relative_to(root.parent)))
        with zipfile.ZipFile(staged_zip) as z:
            assert z.testzip() is None, 'ZIP CRC verification failed'
        staged_zip.replace(args.output)
    print(json.dumps({'file': str(args.output.resolve()), 'bytes': args.output.stat().st_size, 'sha256': digest(args.output), 'jars': len(jars)}, indent=2))


if __name__ == '__main__':
    main()
