"""Regression checks for the Finder 'damaged or incomplete' packaging defect."""
from pathlib import Path
import plistlib
import stat
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "packaging"))
from macos_archive import verify_app_archive, write_app_archive


class MacArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.app = self.root / "Hermes.app"
        self.launcher = self.app / "Contents/MacOS/Hermes"
        self.launcher.parent.mkdir(parents=True)
        self.launcher.write_bytes(b"launch fixture")
        self.launcher.chmod(0o755)
        self.plist = self.app / "Contents/Info.plist"
        self.plist.write_bytes(plistlib.dumps({
            "CFBundleExecutable": "Hermes", "CFBundlePackageType": "APPL",
            "CFBundleShortVersionString": "2.0.0"}))
        self.archive = self.root / "Hermes-macOS-arm64-2.0.0.app.zip"

    def test_single_bundle_preserves_bytes_permissions_and_internal_symlinks(self):
        link = self.launcher.parent / "Alias"
        link.symlink_to("Hermes")
        result = write_app_archive(self.app, self.archive)
        self.assertEqual(result["root"], "Hermes.app")
        with zipfile.ZipFile(self.archive) as z:
            self.assertIn("Hermes.app/", z.namelist())
            self.assertEqual(z.read(result["executable"]), self.launcher.read_bytes())
            self.assertEqual(z.getinfo(result["executable"]).external_attr >> 16 & 0o777, 0o755)
            entry = z.getinfo("Hermes.app/Contents/MacOS/Alias")
            self.assertTrue(stat.S_ISLNK(entry.external_attr >> 16))
            self.assertEqual(z.read(entry), b"Hermes")

    def test_original_app_plus_sibling_readme_is_rejected(self):
        write_app_archive(self.app, self.archive)
        with zipfile.ZipFile(self.archive, "a") as z:
            z.writestr("验收说明.md", "instructions")
        with self.assertRaisesRegex(ValueError, "exactly one top-level"):
            verify_app_archive(self.archive)

    def test_invalid_outer_app_with_nested_real_app_is_rejected(self):
        with zipfile.ZipFile(self.archive, "w") as z:
            z.write(self.plist, "Outer.app/Hermes.app/Contents/Info.plist")
        with self.assertRaisesRegex(ValueError, "Outer .app is not a bundle"):
            verify_app_archive(self.archive)

    def test_missing_executable_is_rejected(self):
        self.launcher.unlink()
        with self.assertRaisesRegex(ValueError, "Missing bundle executable"):
            write_app_archive(self.app, self.archive)

    def test_lost_execute_permission_is_rejected(self):
        self.launcher.chmod(0o644)
        with self.assertRaisesRegex(ValueError, "execute permission"):
            write_app_archive(self.app, self.archive)

    def test_wrong_plist_executable_is_rejected(self):
        metadata = plistlib.loads(self.plist.read_bytes())
        metadata["CFBundleExecutable"] = "WrongName"
        self.plist.write_bytes(plistlib.dumps(metadata))
        with self.assertRaisesRegex(ValueError, "Missing bundle executable"):
            write_app_archive(self.app, self.archive)

    def test_escaping_symlink_is_rejected(self):
        (self.app / "outside").symlink_to("../outside")
        with self.assertRaisesRegex(ValueError, "Symlink escapes"):
            write_app_archive(self.app, self.archive)


if __name__ == "__main__":
    unittest.main()
