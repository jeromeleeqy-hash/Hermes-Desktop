#!/usr/bin/env python3
"""The 1.5 icon set is imported without changing upstream Fluent paths."""
import runpy
from pathlib import Path
runpy.run_path(str(Path(__file__).with_name("import-fluent-icons.py")),run_name="__main__")
