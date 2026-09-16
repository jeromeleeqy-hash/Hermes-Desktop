"""Validate the actual linked image; a publisher's release/MODULES field can list absent modules."""
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

REQUIRED = {
    'java.base', 'java.desktop', 'java.datatransfer', 'java.logging', 'java.net.http',
    'java.xml', 'jdk.unsupported', 'jdk.unsupported.desktop', 'jdk.jsobject',
    'jdk.xml.dom', 'jdk.crypto.ec', 'jdk.crypto.mscapi', 'jdk.zipfs',
}


def jimage_tool():
    home = os.environ.get('JAVA_HOME')
    candidate = Path(home) / 'bin' / ('jimage.exe' if os.name == 'nt' else 'jimage') if home else None
    return str(candidate) if candidate and candidate.is_file() else shutil.which('jimage')


def validate_runtime_image(image):
    tool = jimage_tool()
    if not tool:
        raise RuntimeError('A build-host JDK with jimage is required to verify the actual runtime modules.')
    result = subprocess.run([tool, 'list', str(image)], check=True, capture_output=True, text=True)
    modules = {line.split(': ', 1)[1].strip() for line in result.stdout.splitlines() if line.startswith('Module: ')}
    missing = REQUIRED - modules
    if missing:
        raise ValueError('Incomplete Windows runtime image: missing ' + ', '.join(sorted(missing)) +
                         '. Build the runtime from a Windows JDK using build-windows-runtime.py.')
    return sorted(modules)


def validate_runtime_bytes(data):
    with tempfile.TemporaryDirectory(prefix='hermes-module-check-') as temp:
        image = Path(temp) / 'modules'
        image.write_bytes(data)
        return validate_runtime_image(image)
