#!/usr/bin/env python3
"""Compile the native launcher or installable EXE with NSIS (Windows or Linux host).
The installer payload is taken from a verified portable ZIP, never a separate build.
"""
import argparse, hashlib, json, os, struct, subprocess, tempfile, zipfile
from pathlib import Path, PurePosixPath

ROOT=Path(__file__).resolve().parent.parent
VERSION='1.8.4'

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def check_pe(path):
    data=path.read_bytes();offset=struct.unpack_from('<I',data,0x3c)[0]
    assert data[:2]==b'MZ' and data[offset:offset+4]==b'PE\0\0'

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--makensis',default='makensis')
    parser.add_argument('--nsis-dir')
    parser.add_argument('--portable',type=Path)
    parser.add_argument('--portable-sha256')
    parser.add_argument('--launcher-only',action='store_true')
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args();env=os.environ.copy()
    if args.nsis_dir:env['NSISDIR']=str(Path(args.nsis_dir).resolve())
    args.output=args.output.resolve();args.output.parent.mkdir(exist_ok=True,parents=True)
    prefix='/' if os.name=='nt' else '-'
    def compile(script,defines):
        command=[args.makensis,prefix+'V2',prefix+'WX']+[prefix+'D'+k+'='+str(v) for k,v in defines.items()]+[str(ROOT/'packaging'/script)]
        subprocess.run(command,env=env,check=True)
    if args.launcher_only:
        compile('Hermes-Launcher.nsi',{'OUTPUT':args.output,'ICON':ROOT/'packaging/Hermes.ico'})
        check_pe(args.output)
    else:
        assert args.portable and args.portable_sha256,'Provide the verified portable archive and its checksum'
        assert sha(args.portable)==args.portable_sha256
        with tempfile.TemporaryDirectory(prefix='hermes-installer-',dir=args.output.parent) as temp:
            with zipfile.ZipFile(args.portable) as archive:
                assert archive.testzip() is None
                for name in archive.namelist():
                    path=PurePosixPath(name)
                    assert not path.is_absolute() and '..' not in path.parts and '\\' not in name
                archive.extractall(temp)
            payload=Path(temp)/('Hermes-Windows-'+VERSION)
            manifest=json.loads((payload/'manifest.json').read_text())
            assert manifest['version']==VERSION and manifest['platform']=='windows-x64'
            check_pe(payload/'Hermes.exe')
            for row in manifest['jars']:assert sha(payload/'app'/row['name'])==row['sha256']
            assert manifest.get('runtime_files'), 'Missing verified runtime inventory; use the startup-fixed portable build'
            for row in manifest['runtime_files']:assert sha(payload/'runtime'/row['path'])==row['sha256']
            staged=Path(temp)/args.output.name
            compile('Hermes-Setup.nsi',{'OUTPUT':staged,'PAYLOAD':payload,'ICON':ROOT/'packaging/Hermes.ico'})
            check_pe(staged);staged.replace(args.output)
    print(json.dumps({'file':str(args.output),'bytes':args.output.stat().st_size,'sha256':sha(args.output),'version':VERSION},indent=2))

if __name__=='__main__':main()
