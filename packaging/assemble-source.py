#!/usr/bin/env python3
"""Package the reviewed source tree with a per-file SHA-256 inventory."""
import argparse
import hashlib
import tempfile
import zipfile
from pathlib import Path

VERSION = "1.8.4"
ROOT_FILES = {
    ".gitignore", "Build-Windows.bat", "Build-macOS.command",
    "Installer-Windows.bat", "Package-Windows.bat", "Package-macOS.command",
    "Preview-Layout.bat", "Preview-Layout.command", "README.md",
    "Run-Hermes.bat", "Run-Hermes.command", "THIRD_PARTY_NOTICES.md",
    "build.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
    "settings.gradle.kts", "开始使用.txt",
}
ROOT_DIRS = {"src", "docs", "gradle", "licenses", "packaging", ".github"}
EXCLUDED_DIRS = {".git", ".gradle", ".kotlin", "__pycache__", "build"}


def source_files(project):
    for path in sorted(project.rglob("*")):
        relative = path.relative_to(project)
        if path.is_symlink() or not path.is_file():
            continue
        if any(part in EXCLUDED_DIRS for part in relative.parts):
            continue
        if relative.parts[0] not in ROOT_DIRS and str(relative) not in ROOT_FILES:
            continue
        if path.suffix in {".pyc", ".log", ".tmp"}:
            continue
        yield path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--platform", choices=("Windows", "macOS-arm64", "Desktop"), default="Windows")
    args = parser.parse_args()
    project = Path(__file__).resolve().parent.parent
    inventory = project / "docs/SOURCE-SHA256.txt"
    files = [path for path in source_files(project) if path != inventory]
    hashes = {path.relative_to(project).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
    inventory.write_text(
        "# Hermes Desktop " + VERSION + "\n# Excludes this inventory and build/cache output.\n\n" +
        "".join(value + "  " + name + "\n" for name, value in hashes.items()), encoding="utf-8")
    files.append(inventory)
    prefix = "Hermes-" + args.platform + "-" + VERSION + "-source/"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="hermes-source-", dir=args.output.parent) as temp:
        archive = Path(temp) / "source.zip"
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as out:
            for path in files:
                out.write(path, prefix + path.relative_to(project).as_posix())
        with zipfile.ZipFile(archive) as out:
            assert out.testzip() is None
            assert len(out.namelist()) == len(set(out.namelist()))
            for name, value in hashes.items():
                assert hashlib.sha256(out.read(prefix + name)).hexdigest() == value, name
        archive.replace(args.output)
    print(f"Packaged {len(files)} source files: {args.output.resolve()}")


if __name__ == "__main__":
    main()
