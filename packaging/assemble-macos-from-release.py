#!/usr/bin/env python3
"""Build an ad-hoc signed Apple Silicon app from a verified release and fresh Mac staging.

The release contributes only the native launcher, runtime and unchanged system helper.
All application JARs are replaced. This cross-host build does not claim native execution.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import plistlib
import re
import shutil
import struct
import subprocess
import tempfile
import zipfile
from macos_signature import verify_adhoc
from macos_archive import write_app_archive

VERSION = "2.0.5"
ROOT = Path(__file__).resolve().parent.parent


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base", type=Path, required=True)
    p.add_argument("--base-sha256", required=True)
    p.add_argument("--app", type=Path, required=True)
    p.add_argument("--sevenzip", type=Path, help="Required only for DMG bases")
    p.add_argument("--rcodesign", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    assert sha(a.base) == a.base_sha256.lower(), "Base release digest mismatch"
    jars = sorted(a.app.glob("*.jar"))
    names = {x.name for x in jars}
    assert "hermes-desktop.jar" in names
    assert "sherpa-onnx-native-lib-osx-aarch64-v1.13.5.jar" in names
    assert any(x.startswith("skiko-awt-runtime-macos-arm64-") for x in names)
    assert not any(any(t in n for t in ("-linux", "-win", "osx-x64", "junit-", "mockito-", "mockwebserver-")) for n in names)
    assert sha(a.app / "hermes-desktop.jar") == sha(ROOT / "build/libs/HermesDesktop.jar"), "Stale Mac staging"
    with zipfile.ZipFile(a.app / "hermes-desktop.jar") as z:
        assert VERSION.encode() in z.read("com/qingyu/hermescompanion/BuildConfig.class")
        assert "today/hermes-today-writer.py" in z.namelist()
    a.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="hermes-macos-", dir=a.output.parent) as tmp:
        tmp = Path(tmp)
        if zipfile.is_zipfile(a.base):
            from macos_archive import verify_app_archive
            verify_app_archive(a.base)
            with zipfile.ZipFile(a.base) as archive:
                for entry in archive.infolist():
                    target = tmp / "base" / entry.filename
                    mode = entry.external_attr >> 16
                    if entry.is_dir():
                        target.mkdir(parents=True, exist_ok=True)
                    else:
                        target.parent.mkdir(parents=True, exist_ok=True)
                        if mode & 0o170000 == 0o120000:
                            target.symlink_to(archive.read(entry).decode())
                        else:
                            with archive.open(entry) as src, target.open("wb") as dst:
                                shutil.copyfileobj(src, dst)
                            target.chmod(mode & 0o777 or 0o644)
        else:
            assert a.sevenzip, "DMG bases require --sevenzip"
            subprocess.run([str(a.sevenzip.resolve()), "x", "-y", "-o" + str(tmp / "base"), str(a.base.resolve())], check=True, stdout=subprocess.DEVNULL)
        candidates = list((tmp / "base").rglob("Hermes.app"))
        assert len(candidates) == 1
        app = tmp / "Hermes.app"
        shutil.copytree(candidates[0], app, symlinks=True)
        contents = app / "Contents"
        application = contents / "app"
        launcher = contents / "MacOS/Hermes"
        head = launcher.read_bytes()[:8]
        assert head[:4] == b"\xcf\xfa\xed\xfe" and struct.unpack("<I", head[4:])[0] == 0x100000c, "Expected ARM64 Mach-O"
        native_source = {str(x.relative_to(app)): sha(x) for x in app.rglob("*") if x.is_file() and x.suffix != ".jar"}
        for old in application.glob("*.jar"):
            old.unlink()
        for jar in jars:
            shutil.copy2(jar, application / jar.name)
        cfg = application / "Hermes.cfg"
        options = re.sub(r"-Djpackage\.app-version=[^\r\n]+", "-Djpackage.app-version=" + VERSION, cfg.read_text().split("[JavaOptions]", 1)[1])
        cfg.write_text("[Application]\napp.mainclass=com.qingyu.hermescompanion.desktop.MainKt\n" +
                       "".join("app.classpath=$APPDIR/" + jar.name + "\n" for jar in jars) + "\n[JavaOptions]" + options)
        plist = contents / "Info.plist"
        data = plistlib.loads(plist.read_bytes())
        assert data["CFBundleIdentifier"] == "com.qingyu.hermes.desktop"
        data.update(CFBundleVersion=VERSION, CFBundleShortVersionString=VERSION, HermesBuildRevision="desktop-" + VERSION)
        plist.write_bytes(plistlib.dumps(data, sort_keys=False))
        state = application / ".jpackage.xml"
        if state.exists():
            state.write_text(re.sub(r"<app-version>[^<]+</app-version>", "<app-version>" + VERSION + "</app-version>", state.read_text()))
        # DMG extraction on non-macOS can lose mode bits. Restore native executability.
        for file in app.rglob("*"):
            if not file.is_file() or file.is_symlink():
                continue
            with file.open("rb") as stream:
                magic = stream.read(4)
            if magic in (b"\xcf\xfa\xed\xfe", b"\xfe\xed\xfa\xcf", b"\xca\xfe\xba\xbe", b"\xbe\xba\xfe\xca") or file.name == "jspawnhelper":
                file.chmod(0o755)
        docs = contents / "Resources/Release"
        docs.mkdir(parents=True, exist_ok=True)
        for name in ("RELEASE-2.0.5.md", "BUILD-2.0.5.md", "ACCEPTANCE-2.0.5.md"):
            shutil.copy2(ROOT / "docs" / name, docs / name)
        shutil.copy2(ROOT / "THIRD_PARTY_NOTICES.md", docs / "THIRD_PARTY_NOTICES.md")
        shutil.copytree(ROOT / "licenses", docs / "licenses", dirs_exist_ok=True)
        for jar in jars:
            with zipfile.ZipFile(jar) as z:
                for entry in z.infolist():
                    path = PurePosixPath(entry.filename)
                    if entry.is_dir() or not any(n in path.name.upper() for n in ("LICENSE", "NOTICE", "COPYING")):
                        continue
                    assert not path.is_absolute() and ".." not in path.parts and "\\" not in entry.filename
                    if entry.file_size > 2_000_000 or entry.filename.endswith(".class"):
                        continue
                    target = docs / "licenses/dependencies" / jar.stem / entry.filename
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(z.read(entry))
        manifest = {
            "version": VERSION, "platform": "macos-arm64", "base_release": a.base.name,
            "base_release_sha256": a.base_sha256, "reused_native_files_before_resigning": native_source,
            "application_dependencies": "Application rebuilt; unchanged platform dependencies verified against the supplied base release manifest",
            "jars": [{"name": j.name, "bytes": j.stat().st_size, "sha256": sha(j)} for j in jars],
            "validation": "Cross-host resource and architecture verification; native macOS execution not performed.",
            "signature": "ad-hoc; not Apple Developer ID or notarized",
        }
        (docs / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
        subprocess.run([str(a.rcodesign.resolve()), "sign", "--timestamp-url", "none", str(app)], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        # rcodesign 0.29 verify incorrectly treats absent ad-hoc CMS as an error.
        # Independently verify all code pages and the app's Info.plist/resource slots.
        signatures = {}
        for binary in app.rglob("*"):
            if not binary.is_file() or binary.is_symlink():
                continue
            with binary.open("rb") as stream:
                magic = stream.read(4)
            if magic not in (b"\xcf\xfa\xed\xfe", b"\xca\xfe\xba\xbe", b"\xca\xfe\xba\xbf"):
                continue
            # Java .class files share the universal Mach-O magic; only native paths apply.
            if binary.suffix == ".class":
                continue
            special = {1: plist.read_bytes(), 3: (contents / "_CodeSignature/CodeResources").read_bytes()} if binary == launcher else {}
            signatures[str(binary.relative_to(app))] = verify_adhoc(binary.read_bytes(), special)
        assert str(launcher.relative_to(app)) in signatures
        (a.output.parent / (a.output.stem + "-signatures.json")).write_text(json.dumps(signatures, indent=2) + "\n")
        # Verify every sealed regular resource, including app JARs, against CodeResources.
        seal = plistlib.loads((contents / "_CodeSignature/CodeResources").read_bytes())
        for name, entry in seal["files2"].items():
            target = contents / name
            if isinstance(entry, dict) and "hash2" in entry:
                assert hashlib.sha256(target.read_bytes()).digest() == entry["hash2"], "Invalid resource seal: " + name
        for jar in jars:
            assert sha(application / jar.name) == sha(jar)
            assert "app/" + jar.name in seal["files2"]
        assert os.access(launcher, os.X_OK)
        staged = tmp / "application.zip"
        # A second root item makes Archive Utility create an enclosing directory.
        # For *.app.zip that directory looks like an app but has no Contents.
        # Acceptance notes already live inside the signed Resources/Release tree.
        write_app_archive(app, staged)
        staged.replace(a.output)
        (a.output.parent / (a.output.stem + "-manifest.json")).write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"file": str(a.output.resolve()), "bytes": a.output.stat().st_size, "sha256": sha(a.output)}, indent=2))


if __name__ == "__main__":
    main()
