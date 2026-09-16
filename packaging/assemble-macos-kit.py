#!/usr/bin/env python3
"""Create a verified Apple Silicon kit. The actual app/DMG is built on macOS."""
import argparse, hashlib, json, shutil, struct, tarfile, tempfile, zipfile
from pathlib import Path

VERSION='1.8.4'
ROOT=Path(__file__).resolve().parent.parent

def digest(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def arm64_macho(data):
    if data[:4]==b'\xcf\xfa\xed\xfe':return struct.unpack_from('<I',data,4)[0]==0x100000c
    if data[:4] in (b'\xca\xfe\xba\xbe',b'\xca\xfe\xba\xbf'):
        count=struct.unpack_from('>I',data,4)[0];step=32 if data[:4]==b'\xca\xfe\xba\xbf' else 20
        return any(struct.unpack_from('>I',data,8+i*step)[0]==0x100000c for i in range(count))
    return False

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app',type=Path,required=True)
    parser.add_argument('--jdk',type=Path,required=True)
    parser.add_argument('--origin',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    origin=json.loads(args.origin.read_text());assert digest(args.jdk)==origin['sha256']
    assert origin['version']=='21.0.12.1+1'
    with tarfile.open(args.jdk) as tar:
        names=tar.getnames()
        home='jdk-21.0.12.1+1/Contents/Home/'
        release=tar.extractfile(home+'release').read().decode()
        assert 'OS_NAME="Darwin"' in release and 'OS_ARCH="aarch64"' in release
        assert arm64_macho(tar.extractfile(home+'bin/java').read())
        assert arm64_macho(tar.extractfile(home+'bin/jpackage').read())
        assert home+'jmods/jdk.unsupported.desktop.jmod' in names
    jars=sorted(args.app.glob('*.jar'));names={p.name for p in jars}
    assert 'hermes-desktop.jar' in names
    assert not any(any(s in n for s in ('-linux','-win','-macos-x64','junit-','mockito-','mockwebserver-')) for n in names)
    assert digest(args.app/'hermes-desktop.jar')==digest(ROOT/'build/libs/HermesDesktop.jar')
    native_files=[];upstream_extra_files=[]
    for module in ('base','graphics','controls','media','web','swing'):
        assert f'javafx-{module}-21.0.8-mac-aarch64.jar' in names
    assert any(n.startswith('skiko-awt-runtime-macos-arm64-') for n in names)
    for jar in jars:
        if jar.name.startswith(('javafx-','skiko-awt-runtime-')):
            with zipfile.ZipFile(jar) as z:
                for name in z.namelist():
                    if name.endswith(('.dylib','.jnilib')):
                        # Upstream Skiko 0.9.37.4 also ships its x64 sibling in the ARM64 JAR.
                        # Library.findAndLoad selects the resource by host OS/architecture.
                        # Keep the authentic artifact and require the actual arm64 resource below.
                        if jar.name.startswith('skiko-awt-runtime-macos-arm64-') and name=='libskiko-macos-x64.dylib':
                            data=z.read(name)
                            assert data[:4]==b'\xcf\xfa\xed\xfe' and struct.unpack_from('<I',data,4)[0]==0x1000007
                            upstream_extra_files.append(jar.name+':'+name)
                            continue
                        assert arm64_macho(z.read(name)),(jar.name,name)
                        native_files.append(jar.name+':'+name)
    assert native_files
    assert any(x.endswith(':libskiko-macos-arm64.dylib') for x in native_files)
    with zipfile.ZipFile(args.app/'hermes-desktop.jar') as z:
        assert VERSION.encode() in z.read('com/qingyu/hermescompanion/BuildConfig.class')
        for name in ('desktop/MacApplicationHooks','desktop/MacWindowChrome','desktop/MacStatusIcon','desktop/MacFloatingMenu','desktop/MacScreenAccess','desktop/AttachmentPreviewKt','model/AgentQuestion'):
            assert 'com/qingyu/hermescompanion/'+name+'.class' in z.namelist()
    report={'revision':'Desktop capabilities 1.8.4','version':VERSION,'target':'macos-arm64','user_system':'macOS 27','jdk':origin,
            'jars':len(jars),'verified_arm64_native_files':native_files,'unused_upstream_x64_resources':upstream_extra_files,
            'application_sha256':digest(args.app/'hermes-desktop.jar'),
            'macos_execution':False,'app_and_dmg_generated':False,
            'note':'The kit validates payload and architecture on Linux. Run Build-Mac-App.command on the target Mac to create and verify the actual app and DMG.'}
    report_file=ROOT/'docs/validation/macos-1.8.4-package.json'
    report_file.parent.mkdir(parents=True,exist_ok=True);report_file.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='hermes-mac-kit-',dir=args.output.parent) as temp:
        kit=Path(temp)/f'Hermes-macOS-arm64-{VERSION}-BuildKit';kit.mkdir()
        shutil.copytree(args.app,kit/'app')
        (kit/'toolchain').mkdir();shutil.copy2(args.jdk,kit/'toolchain/jdk-macos-arm64.tar.gz')
        shutil.copy2(args.origin,kit/'toolchain/JDK-ORIGIN.json')
        (kit/'packaging').mkdir()
        for name in ('Hermes.icns','NativeSpeech.swift','SpeechInfo.plist'):shutil.copy2(ROOT/'packaging'/name,kit/'packaging'/name)
        shutil.copy2(ROOT/'packaging/macos/Build-Mac-App.command',kit/'Build-Mac-App.command')
        shutil.copy2(ROOT/'docs/BUILD-1.8.4.md',kit/'先看这里.md')
        shutil.copy2(ROOT/'THIRD_PARTY_NOTICES.md',kit/'THIRD_PARTY_NOTICES.md')
        shutil.copy2(ROOT/'docs/RELEASE-1.8.4.md',kit/'本次更新.md')
        shutil.copytree(ROOT/'licenses',kit/'licenses')
        (kit/'validation').mkdir()
        for name in ('macos-1.8.4-package.json','desktop-1.8.4-validation.json'):
            if (ROOT/'docs/validation'/name).is_file():shutil.copy2(ROOT/'docs/validation'/name,kit/'validation'/name)
        inventory=[]
        for path in sorted(kit.rglob('*')):
            if path.is_file():inventory.append((path.relative_to(kit).as_posix(),digest(path)))
        (kit/'PAYLOAD-SHA256.txt').write_text(''.join(f'{sha}  {name}\n' for name,sha in inventory))
        archive=Path(temp)/'kit.zip'
        with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=4) as z:
            for path in sorted(kit.rglob('*')):
                if path.is_file():z.write(path,kit.name+'/'+path.relative_to(kit).as_posix())
        with zipfile.ZipFile(archive) as z:
            assert z.testzip() is None
            for name,sha in inventory:assert hashlib.sha256(z.read(kit.name+'/'+name)).hexdigest()==sha,name
        archive.replace(args.output)
    report['kit']={'file':args.output.name,'bytes':args.output.stat().st_size,'sha256':digest(args.output)}
    report_file.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
