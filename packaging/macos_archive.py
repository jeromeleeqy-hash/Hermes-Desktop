"""Write a Finder-installable ZIP containing exactly one macOS app bundle.

Keep release notes inside the bundle before signing, or ship them separately.
With a '*.app.zip' filename, multiple root items can cause Archive Utility to
create an enclosing '*.app' directory that is not itself a valid app bundle.
"""
import os
from pathlib import Path, PurePosixPath
import plistlib
import stat
import zipfile


def verify_app_archive(archive):
    """Check ZIP layout and the launch entry; does not run macOS/Gatekeeper."""
    with zipfile.ZipFile(archive) as z:
        names = z.namelist()
        if not names or len(names) != len(set(names)):
            raise ValueError("App ZIP is empty or contains duplicate entries")
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or "\\" in name:
                raise ValueError("Unsafe app ZIP path: " + name)
        roots = {PurePosixPath(name).parts[0] for name in names}
        if len(roots) != 1 or not next(iter(roots)).endswith(".app"):
            raise ValueError("App ZIP must contain exactly one top-level .app and no sibling files")
        root = next(iter(roots))
        contents = root + "/Contents/"
        if contents + "Info.plist" not in names:
            raise ValueError("Outer .app is not a bundle: missing Contents/Info.plist")
        metadata = plistlib.loads(z.read(contents + "Info.plist"))
        executable = metadata.get("CFBundleExecutable", "")
        if not executable or PurePosixPath(executable).name != executable or "\\" in executable:
            raise ValueError("Invalid CFBundleExecutable")
        if metadata.get("CFBundlePackageType") != "APPL":
            raise ValueError("Expected an APPL bundle")
        entry_name = contents + "MacOS/" + executable
        if entry_name not in names:
            raise ValueError("Missing bundle executable: " + entry_name)
        entry = z.getinfo(entry_name)
        mode = entry.external_attr >> 16
        if entry.create_system != 3 or not stat.S_ISREG(mode) or not mode & 0o111:
            raise ValueError("Bundle executable must retain its Unix execute permission")
        for info in z.infolist():
            mode = info.external_attr >> 16
            if stat.S_ISLNK(mode):
                target = PurePosixPath(z.read(info).decode("utf-8"))
                parts = list(PurePosixPath(info.filename).parent.parts)
                if target.is_absolute():
                    raise ValueError("Absolute symlink in app ZIP")
                for part in target.parts:
                    if part == "..":
                        if len(parts) <= 1:
                            raise ValueError("Symlink escapes app bundle")
                        parts.pop()
                    elif part != ".":
                        parts.append(part)
        bad = z.testzip()
        if bad is not None:
            raise ValueError("ZIP CRC mismatch: " + bad)
        return {"root": root, "entries": len(names), "executable": entry_name,
                "version": metadata.get("CFBundleShortVersionString")}


def write_app_archive(app, destination):
    """Archive app bytes, permissions and symlinks without adding sibling files."""
    app, destination = Path(app), Path(destination)
    if not app.is_dir() or app.is_symlink() or app.suffix != ".app":
        raise ValueError("Expected a real .app directory")
    if destination.resolve().is_relative_to(app.resolve()):
        raise ValueError("Archive output must be outside the app bundle")
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        # Explicit directory records preserve grouping and directory permissions.
        for path in [app, *sorted(app.rglob("*"))]:
            name = path.relative_to(app.parent).as_posix()
            if path.is_symlink():
                info = zipfile.ZipInfo(name)
                info.create_system = 3
                info.external_attr = (stat.S_IFLNK | 0o777) << 16
                z.writestr(info, os.readlink(path))
            elif path.is_file() or path.is_dir():
                z.write(path, name)
    return verify_app_archive(destination)
