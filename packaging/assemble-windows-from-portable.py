#!/usr/bin/env python3
"""Reuse a verified Windows Java runtime; replace every application dependency from fresh Windows staging."""
import argparse
import hashlib
import json
import struct
import tempfile
import zipfile
from pathlib import Path, PurePosixPath
from windows_runtime import validate_runtime_image, validate_runtime_bytes

VERSION = "1.8.4"

def digest(value):
    return hashlib.sha256(value).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app", type=Path, required=True)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--base-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--runtime", type=Path, help="Verified, freshly linked Windows runtime")
    args = parser.parse_args()
    project = Path(__file__).resolve().parent.parent
    assert digest(args.base.read_bytes()) == args.base_sha256, "Base archive checksum mismatch"
    jars = sorted(args.app.glob("*.jar"))
    new_names = {p.name for p in jars}
    assert "hermes-desktop.jar" in new_names
    current_app = project / "build/libs/HermesDesktop.jar"
    if current_app.is_file():
        assert digest(current_app.read_bytes()) == digest((args.app / "hermes-desktop.jar").read_bytes()), "Windows staging is stale; run stageWindowsApplication again after the last compilation"
    assert not any(any(token in n for token in ("-linux", "-macos", "-mac-", "mockito", "mockwebserver", "junit")) for n in new_names)
    with zipfile.ZipFile(args.base) as base:
        assert base.testzip() is None, "Base archive CRC failure"
        manifest_path = next(n for n in base.namelist() if n.count("/") == 1 and n.endswith("/manifest.json"))
        prefix = manifest_path.split("/")[0] + "/"
        runtime_modules = validate_runtime_image(args.runtime / 'lib/modules') if args.runtime else validate_runtime_bytes(base.read(prefix + 'runtime/lib/modules'))
        manifest = json.loads(base.read(manifest_path))
        assert manifest["platform"] == "windows-x64"
        old_names = {n.removeprefix(prefix + "app/") for n in base.namelist() if n.startswith(prefix + "app/") and n.endswith(".jar")}
        dependency_changes = {
            "added": sorted(new_names-old_names), "removed": sorted(old_names-new_names),
            "updated": [jar.name for jar in jars if jar.name in old_names and digest(jar.read_bytes()) != digest(base.read(prefix+"app/"+jar.name))],
        }
        with zipfile.ZipFile(args.app / "hermes-desktop.jar") as app:
            assert app.testzip() is None
            assert VERSION.encode() in app.read("com/qingyu/hermescompanion/BuildConfig.class")
            assert len(app.namelist()) == len(set(app.namelist())), "Duplicate application entry"
            for required in ("desktop/ChatComposerKt.class", "desktop/ModelPickerKt.class", "desktop/DesktopWindowChromeKt.class", "data/ModelCatalogParserKt.class", "desktop/MarkdownWorkspaceKt.class", "desktop/DesktopMenusKt.class", "desktop/WindowsWindowFrame.class", "desktop/CaptionClickTracker.class"):
                assert "com/qingyu/hermescompanion/" + required in app.namelist()
            for required in ("DesktopCompanion.class", "DesktopFloatingHost.class", "FloatingQuickPanelKt.class", "NativeDocumentContainerKt.class", "CompatibleDocumentKt.class"):
                assert "com/qingyu/hermescompanion/desktop/" + required in app.namelist()
            assert len([name for name in app.namelist() if name.startswith("icons/") and name.endswith(".svg")]) == 170
            assert not any(n.endswith("Test.class") for n in app.namelist())
        java = (args.runtime / 'bin/java.exe').read_bytes() if args.runtime else base.read(prefix + "runtime/bin/java.exe")
        pe = struct.unpack_from("<I", java, 0x3c)[0]
        assert java[:2] == b"MZ" and java[pe:pe+4] == b"PE\0\0"
        assert struct.unpack_from("<H", java, pe+4)[0] == 0x8664
        release = (args.runtime / 'release').read_text() if args.runtime else base.read(prefix + "runtime/release").decode()
        assert 'OS_NAME="Windows"' in release and 'JAVA_VERSION="21.' in release
        assert prefix + "runtime/legal/java.base/LICENSE" in base.namelist()
        destination = "Hermes-Windows-" + VERSION + "/"
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="hermes-release-", dir=args.output.parent) as temp:
            staged = Path(temp) / "portable.zip"
            with zipfile.ZipFile(staged, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as out:
                for name in base.namelist():
                    if not name.startswith(prefix):
                        continue
                    relative = name.removeprefix(prefix)
                    path = PurePosixPath(relative)
                    assert not path.is_absolute() and ".." not in path.parts and "\\" not in relative
                    if args.runtime and relative.startswith('runtime/'):
                        continue
                    if not name.endswith("/") and relative.startswith(("runtime/", "licenses/")) and not (project / relative).is_file():
                        out.writestr(destination + relative, base.read(name))
                if args.runtime:
                    for file in sorted(args.runtime.rglob('*')):
                        if file.is_file():out.write(file, destination + 'runtime/' + file.relative_to(args.runtime).as_posix())
                for file in sorted((project/"licenses").rglob("*")):
                    if file.is_file():out.write(file,destination+file.relative_to(project).as_posix())
                launcher=project/"build/native-launcher/Hermes.exe"
                assert launcher.read_bytes()[:2]==b"MZ", "Build native launcher first"
                out.write(launcher,destination+"Hermes.exe")
                for jar in jars:
                    out.write(jar, destination + "app/" + jar.name)
                for name in ("Start-Hermes.bat", "Diagnose-Hermes.bat", "Run-Hermes.bat", "hermes.vmoptions"):
                    out.write(project / "packaging" / name, destination + name)
                out.write(project / "docs/PORTABLE-README.txt", destination + "开始使用.txt")
                out.write(project / "THIRD_PARTY_NOTICES.md", destination + "THIRD_PARTY_NOTICES.md")
                for name in ("CHANGELOG.md", "WINDOWS-QUICKSTART.md", "WINDOWS-VALIDATION.md", "RELEASE-1.8.4.md", "STARTUP-FIX-1.8.0.md"):
                    out.write(project / "docs" / name, destination + "docs/" + name)
                for file in sorted((project / "docs/previews/windows-1.8.0").glob("*.png")):
                    out.write(file, destination + "docs/previews/windows-1.8.0/" + file.name)
                for file in sorted((project / "docs/validation").glob("windows-1.8.*-*")):
                    out.write(file, destination + "docs/validation/" + file.name)
                manifest.update({
                    "version": VERSION,
                    "build": "1.8.4",
                    "launcher": "Hermes.exe",
                    "launcher_sha256": digest(launcher.read_bytes()),
                    "runtime_reused_from": None if args.runtime else args.base.name,
                    "runtime_build": json.loads((args.runtime / 'hermes-runtime.json').read_text()) if args.runtime else None,
                    "runtime_modules_verified": runtime_modules,
                    "runtime_files": [{"path":f.relative_to(args.runtime).as_posix(),"bytes":f.stat().st_size,"sha256":digest(f.read_bytes())} for f in sorted(args.runtime.rglob('*')) if f.is_file()] if args.runtime else [],
                    "application_dependencies": "All replaced from Gradle Windows staging",
                    "dependency_changes": dependency_changes,
                    "runtime_base_archive_sha256": args.base_sha256,
                    "jars": [{"name": p.name, "bytes": p.stat().st_size, "sha256": digest(p.read_bytes())} for p in jars],
                    "validation": "Linux JVM, HTTP/WebSocket and Compose interaction tests. Windows native runtime execution not performed. See docs/WINDOWS-VALIDATION.md."
                })
                if args.runtime:
                    origin = manifest['runtime_build']
                    manifest.update(runtime_archive=origin['jdk_archive'], runtime_sha256=origin['jdk_sha256'],
                                    runtime_source=origin['source'], runtime_base_archive_sha256=None,
                                    application_base_archive_sha256=args.base_sha256)
                out.writestr(destination + "manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
            with zipfile.ZipFile(staged) as out:
                assert out.testzip() is None
                for jar in jars:
                    assert digest(out.read(destination + "app/" + jar.name)) == digest(jar.read_bytes())
            staged.replace(args.output)
    print(json.dumps({"file": str(args.output.resolve()), "bytes": args.output.stat().st_size, "sha256": digest(args.output.read_bytes()), "jars": len(jars)}, indent=2))

if __name__ == "__main__":
    main()
