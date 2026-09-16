#!/usr/bin/env python3
"""Verify the shipped installer bytes against the complete portable payload."""
import argparse
import hashlib
import io
import json
import struct
import subprocess
import tempfile
import zipfile
from pathlib import Path, PurePosixPath
from windows_runtime import validate_runtime_bytes


def sha(data):
    return hashlib.sha256(data).hexdigest()


def pe_version(data, version):
    pe = struct.unpack_from('<I', data, 0x3c)[0]
    assert data[:2] == b'MZ' and data[pe:pe+4] == b'PE\0\0'
    offset = data.index(struct.pack('<I', 0xfeef04bd))
    _, _, ms, ls, product_ms, product_ls = struct.unpack_from('<6I', data, offset)
    expected = tuple(map(int, version.split('.'))) + (0,)
    assert (ms >> 16, ms & 65535, ls >> 16, ls & 65535) == expected
    assert (product_ms, product_ls) == (ms, ls)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--portable', type=Path, required=True)
    p.add_argument('--installer', type=Path, required=True)
    p.add_argument('--sevenzip', required=True)
    p.add_argument('--report', type=Path, required=True)
    a = p.parse_args()
    with zipfile.ZipFile(a.portable) as archive:
        assert archive.testzip() is None
        names = archive.namelist()
        assert len(names) == len(set(names))
        manifest_name = next(n for n in names if n.count('/') == 1 and n.endswith('/manifest.json'))
        prefix = manifest_name.split('/')[0] + '/'
        manifest = json.loads(archive.read(manifest_name))
        version = manifest['version']
        assert manifest['build'] == version and manifest['platform'] == 'windows-x64'
        expected = {}
        for name in names:
            assert name.startswith(prefix)
            relative = name.removeprefix(prefix)
            path = PurePosixPath(relative)
            assert not path.is_absolute() and '..' not in path.parts and '\\' not in relative
            if not name.endswith('/'):
                expected[relative] = sha(archive.read(name))
        for row in manifest['jars']:
            assert expected['app/'+row['name']] == row['sha256']
        for row in manifest['runtime_files']:
            assert expected['runtime/'+row['path']] == row['sha256']
        assert expected['Hermes.exe'] == manifest['launcher_sha256']
        modules = validate_runtime_bytes(archive.read(prefix+'runtime/lib/modules'))
        assert modules == manifest['runtime_modules_verified']
        pe_version(archive.read(prefix+'Hermes.exe'), version)
        pe_version(a.installer.read_bytes(), version)
        with zipfile.ZipFile(io.BytesIO(archive.read(prefix+'app/hermes-desktop.jar'))) as app:
            assert version.encode() in app.read('com/qingyu/hermescompanion/BuildConfig.class')
            assert b'VOICE_RELEASE' in app.read('com/qingyu/hermescompanion/desktop/FloatingGestures$Result.class')
            assert 'com/qingyu/hermescompanion/desktop/DesktopMenuHost.class' in app.namelist()
            assert b'DesktopMenuHost' in app.read('com/qingyu/hermescompanion/desktop/DesktopNotifications.class')
            assert b'DesktopMenuHost' in app.read('com/qingyu/hermescompanion/desktop/DesktopFloatingHost.class')
            assert any(b'sidebar-brand' in app.read(n) for n in app.namelist()
                       if n.startswith('com/qingyu/hermescompanion/desktop/DesktopNavigationKt'))
            assert b'WindowsDesktopMenuMouse' in app.read('com/qingyu/hermescompanion/desktop/DesktopMenuHost.class')
            assert 'com/qingyu/hermescompanion/desktop/WindowsDesktopMenuMouse$Watch.class' in app.namelist()
            assert any(b'sidebar-recents' in app.read(n) for n in app.namelist()
                       if n.startswith('com/qingyu/hermescompanion/desktop/DesktopNavigationKt'))
            assert 'fonts/NotoSansCJKsc-Regular.otf' in app.namelist()
            if version == '1.8.3':
                assert 'com/qingyu/hermescompanion/desktop/AttachmentPreviewKt.class' in app.namelist()
                assert 'com/qingyu/hermescompanion/model/AgentQuestion.class' in app.namelist()
                assert b'handleServerRequest' in app.read('com/qingyu/hermescompanion/data/HermesApiClient.class')
                assert b'restorePendingAgentRequests' in app.read('com/qingyu/hermescompanion/data/HermesApiClient.class')
                assert b'javax/swing/JFrame' in app.read('com/qingyu/hermescompanion/desktop/ScreenSelection.class')
                assert b'removeKeyEventDispatcher' in app.read('com/qingyu/hermescompanion/desktop/ScreenSelection.class')

        startup = archive.read(prefix+'Run-Hermes.bat')
        assert b'2>&1' in startup and b'--describe-module jdk.unsupported.desktop' in startup
        assert b'\r\n' in startup and version.encode() in startup
    with tempfile.TemporaryDirectory(prefix='hermes-payload-check-') as temp:
        subprocess.run([a.sevenzip,'x','-y','-o'+temp,str(a.installer.resolve())], check=True, capture_output=True)
        streamed = []
        for relative, digest in expected.items():
            extracted = Path(temp)/relative
            if not extracted.is_file() or sha(extracted.read_bytes()) != digest:
                stream = subprocess.run([a.sevenzip,'x','-so',str(a.installer.resolve()),relative],check=True,capture_output=True).stdout
                assert sha(stream) == digest, relative
                streamed.append(relative)
    report = {
        'version':version, 'edition':'Maintenance release', 'matched_installer_payload_files':len(expected),
        'application_jars':len(manifest['jars']), 'runtime_files':len(manifest['runtime_files']),
        'actual_windows_runtime_modules':modules, 'launcher_and_installer_file_version':version+'.0',
        'direct_stream_checks':streamed, 'real_windows_execution':False,
        'portable':{'name':a.portable.name,'bytes':a.portable.stat().st_size,'sha256':sha(a.portable.read_bytes())},
        'installer':{'name':a.installer.name,'bytes':a.installer.stat().st_size,'sha256':sha(a.installer.read_bytes())},
    }
    a.report.parent.mkdir(parents=True,exist_ok=True)
    a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='actual_windows_runtime_modules'},ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()
