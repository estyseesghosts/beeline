#!/usr/bin/env python3
r"""
adb_screenshot.py

Capture a PNG directly to the host with:
  adb exec-out screencap -p

This avoids /sdcard and adb pull.

Examples:
  python adb_screenshot.py
  python adb_screenshot.py screen.png
  python adb_screenshot.py -s emulator-5554 C:\temp\screen.png
  python adb_screenshot.py --display 4619827259835644672 screen.png  # cover
  python adb_screenshot.py --display 4619827551948147201 screen.png  # active
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from typing import Optional

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def resolve_adb(explicit: str) -> str:
    if explicit != "adb":
        return explicit
    # Prefer PATH; this machine keeps platform-tools outside PATH for the agent shells.
    return (
        shutil.which("adb")
        or shutil.which("adb.exe")
        or os.environ.get("ADB")
        or (
            r"C:\Users\julie\Documents\platform-tools\adb.exe"
            if Path(r"C:\Users\julie\Documents\platform-tools\adb.exe").exists()
            else "adb"
        )
    )


class ScreenshotError(RuntimeError):
    pass


def adb_prefix(adb: str, serial: Optional[str]) -> list[str]:
    cmd = [adb]
    if serial:
        cmd += ["-s", serial]
    return cmd


def capture(
    adb: str,
    serial: Optional[str],
    output: Path,
    timeout: float,
    display: Optional[str] = None,
) -> Path:
    output = output.expanduser().resolve()
    output.parent.mkdir(parents=True, exist_ok=True)

    fd, temp_name = tempfile.mkstemp(
        prefix=output.name + ".",
        suffix=".tmp",
        dir=str(output.parent),
    )
    os.close(fd)
    temp = Path(temp_name)

    screencap = ["exec-out", "screencap"]
    if display is not None:
        screencap += ["-d", display]
    cmd = adb_prefix(adb, serial) + screencap + ["-p"]

    try:
        try:
            with temp.open("wb") as fh:
                cp = subprocess.run(
                    cmd,
                    stdout=fh,
                    stderr=subprocess.PIPE,
                    check=False,
                    timeout=timeout,
                )
        except FileNotFoundError as exc:
            raise ScreenshotError(f"adb not found: {adb}") from exc
        except subprocess.TimeoutExpired as exc:
            raise ScreenshotError(f"screenshot timed out after {timeout:g}s") from exc

        if cp.returncode != 0:
            detail = cp.stderr.decode(errors="replace").strip()
            raise ScreenshotError(
                detail or f"adb failed with exit code {cp.returncode}"
            )

        data = temp.read_bytes()
        signature_offset = data.find(PNG_SIGNATURE)
        if signature_offset < 0:
            if len(data) <= len(PNG_SIGNATURE):
                raise ScreenshotError("empty screenshot stream")
            raise ScreenshotError("screencap output was not PNG")
        # Multi-display emulators can prefix screencap warnings before the PNG.
        if signature_offset:
            temp.write_bytes(data[signature_offset:])

        os.replace(temp, output)
        return output

    finally:
        try:
            temp.unlink()
        except FileNotFoundError:
            pass


def main() -> int:
    p = argparse.ArgumentParser(
        description="Capture Android screenshot directly to the host."
    )
    p.add_argument("output", nargs="?", type=Path, default=Path("screen.png"))
    p.add_argument("--adb", default="adb")
    p.add_argument("-s", "--serial")
    p.add_argument("--display")
    p.add_argument("--timeout", type=float, default=15.0)
    args = p.parse_args()

    try:
        output = capture(
            resolve_adb(args.adb), args.serial, args.output, args.timeout,
            args.display,
        )
    except ScreenshotError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
