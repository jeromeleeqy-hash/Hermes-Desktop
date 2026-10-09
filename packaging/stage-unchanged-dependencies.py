#!/usr/bin/env python3
"""Reuse pinned, verified platform dependencies when the dependency declaration is unchanged."""
import argparse,hashlib,json,zipfile
from pathlib import Path

def sha(data):return hashlib.sha256(data).hexdigest()
def dependencies(text):return text[text.index('dependencies {'):text.index('compose.desktop {')]

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base',type=Path,required=True);p.add_argument('--base-sha256',required=True)
    p.add_argument('--source-base',type=Path,required=True);p.add_argument('--app-jar',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();project=Path(__file__).resolve().parent.parent
    assert sha(a.base.read_bytes())==a.base_sha256,'Base archive mismatch'
    with zipfile.ZipFile(a.source_base) as source:
        build=next(n for n in source.namelist() if n.count('/')==1 and n.endswith('/build.gradle.kts'))
        assert dependencies(source.read(build).decode())==dependencies((project/'build.gradle.kts').read_text()),'Dependencies changed: run platform Gradle staging instead'
    with zipfile.ZipFile(a.base) as base:
        manifests=[n for n in base.namelist() if n.endswith('/manifest.json') and (n.count('/')==1 or '/Contents/Resources/Release/' in n)]
        assert len(manifests)==1
        manifest=json.loads(base.read(manifests[0]));a.output.mkdir(parents=True,exist_ok=True)
        assert not list(a.output.iterdir()),'Use an empty staging directory'
        for row in manifest['jars']:
            name=row['name'];assert Path(name).name==name
            if name=='hermes-desktop.jar':continue
            candidates=[n for n in base.namelist() if n.endswith('/app/'+name)]
            assert len(candidates)==1,name
            data=base.read(candidates[0]);assert sha(data)==row['sha256'],name
            (a.output/name).write_bytes(data)
        app=a.app_jar.read_bytes();(a.output/'hermes-desktop.jar').write_bytes(app)
    result={'platform':manifest['platform'],'base':a.base.name,'base_sha256':a.base_sha256,
        'dependencies':'Verified against original release manifest; dependency declarations unchanged',
        'application_sha256':sha(app),'jars':len(list(a.output.glob('*.jar')))}
    (a.output.parent/'staging-verification.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
if __name__=='__main__':main()
