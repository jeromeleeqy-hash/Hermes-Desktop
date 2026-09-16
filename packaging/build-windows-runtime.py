#!/usr/bin/env python3
"""Link an x64 Windows runtime from a checksum-verified Windows JDK, including JavaFX/Swing interop."""
import argparse
import hashlib
import json
import os
import struct
import subprocess
import tempfile
import zipfile
from pathlib import Path
from windows_runtime import validate_runtime_image

# Preserve the modules of the previously shipped runtime and add the missing Swing interop module.
MODULES = '''java.se java.smartcardio jdk.accessibility jdk.charsets jdk.crypto.ec jdk.crypto.cryptoki
jdk.crypto.mscapi jdk.dynalink jdk.httpserver jdk.incubator.vector jdk.internal.vm.ci
jdk.internal.vm.compiler jdk.internal.vm.compiler.management jdk.jdwp.agent jdk.jfr
jdk.jsobject jdk.localedata jdk.management jdk.management.agent jdk.management.jfr
jdk.naming.dns jdk.naming.rmi jdk.net jdk.nio.mapmode jdk.sctp jdk.security.auth
jdk.security.jgss jdk.unsupported jdk.unsupported.desktop jdk.xml.dom jdk.zipfs'''.split()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jdk', type=Path, required=True)
    p.add_argument('--sha256', required=True)
    p.add_argument('--host-jdk', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    sha = hashlib.sha256(a.jdk.read_bytes()).hexdigest()
    assert sha == a.sha256.lower(), 'Windows JDK publisher checksum mismatch'
    assert not a.output.exists(), 'Use a new output directory'
    with zipfile.ZipFile(a.jdk) as z, tempfile.TemporaryDirectory(prefix='hermes-jmods-') as temp:
        assert z.testzip() is None
        release_name = next(n for n in z.namelist() if n.count('/') == 1 and n.endswith('/release'))
        prefix = release_name.split('/')[0] + '/'
        release = z.read(release_name).decode()
        assert 'OS_NAME="Windows"' in release and 'JAVA_VERSION="21.' in release
        java = z.read(prefix + 'bin/java.exe')
        pe = struct.unpack_from('<I', java, 0x3c)[0]
        assert java[:2] == b'MZ' and struct.unpack_from('<H', java, pe + 4)[0] == 0x8664
        for n in z.namelist():
            if n.startswith(prefix + 'jmods/') and n.endswith('.jmod'):
                (Path(temp) / Path(n).name).write_bytes(z.read(n))
        suffix = '.exe' if os.name == 'nt' else ''
        subprocess.run([str(a.host_jdk / ('bin/jlink' + suffix)), '--module-path', temp,
                        '--add-modules', ','.join(MODULES), '--output', str(a.output),
                        '--compress=zip-6', '--strip-debug', '--no-header-files', '--no-man-pages'], check=True)
        os.environ['JAVA_HOME'] = str(a.host_jdk)
        modules = validate_runtime_image(a.output / 'lib/modules')
        # jlink release omits publisher/platform metadata. Retain only accurate fields from the JDK.
        selected = ('IMPLEMENTOR=', 'IMPLEMENTOR_VERSION=', 'OS_NAME=', 'OS_ARCH=', 'SOURCE_REPO=', 'JAVA_RUNTIME_VERSION=')
        with (a.output / 'release').open('a') as f:
            f.write('\n'.join(line for line in release.splitlines() if line.startswith(selected)) + '\n')
        origin = {'jdk_archive': a.jdk.name, 'jdk_sha256': sha, 'modules': modules,
                  'source': 'https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1',
                  'module_image_sha256': hashlib.sha256((a.output / 'lib/modules').read_bytes()).hexdigest()}
        (a.output / 'hermes-runtime.json').write_text(json.dumps(origin, indent=2) + '\n')
        print(json.dumps(origin, indent=2))


if __name__ == '__main__':
    main()
