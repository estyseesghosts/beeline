#!/usr/bin/env python3
r"""
adb_inspect.py

Create a small inspection directory for an agent.

Quick mode:
  screenshot.png
  device.txt
  activity.txt

Full mode also adds:
  hierarchy.xml
  window.txt
  display.txt

Examples:
  python adb_inspect.py
  python adb_inspect.py --full
  python adb_inspect.py --out C:\temp\android-inspect --full
  python adb_inspect.py --display 4619827259835644672  # cover
  python adb_inspect.py --display 4619827551948147201  # active
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from typing import Optional, Sequence, Union

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
REMOTE_XML = "/sdcard/window_dump.xml"


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


class InspectError(RuntimeError):
    pass


def adb_prefix(adb: str, serial: Optional[str]) -> list[str]:
    cmd = [adb]
    if serial:
        cmd += ["-s", serial]
    return cmd


def run_adb(
    adb: str,
    serial: Optional[str],
    parts: Sequence[str],
    timeout: float,
    binary: bool = False,
) -> Union[str, bytes]:
    cmd = adb_prefix(adb, serial) + list(parts)
    try:
        cp = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            timeout=timeout,
        )
    except FileNotFoundError as exc:
        raise InspectError(f"adb not found: {adb}") from exc
    except subprocess.TimeoutExpired as exc:
        raise InspectError(
            f"adb timed out after {timeout:g}s: {' '.join(parts)}"
        ) from exc

    if cp.returncode != 0:
        detail = cp.stderr.decode(errors="replace").strip()
        raise InspectError(
            detail or f"adb failed with exit code {cp.returncode}"
        )

    if binary:
        return cp.stdout
    return cp.stdout.decode(errors="replace")


def take_screenshot(
    adb: str,
    serial: Optional[str],
    timeout: float,
    display: Optional[str] = None,
) -> bytes:
    parts = ["exec-out", "screencap"]
    if display is not None:
        parts += ["-d", display]
    parts += ["-p"]
    data = run_adb(
        adb, serial,
        parts,
        timeout,
        binary=True,
    )
    assert isinstance(data, bytes)
    signature_offset = data.find(PNG_SIGNATURE)
    if signature_offset < 0:
        raise InspectError("screencap output was not PNG")
    # Multi-display emulators can prefix screencap warnings before the PNG.
    return data[signature_offset:]


def device_summary(
    adb: str,
    serial: Optional[str],
    timeout: float,
) -> str:
    queries = [
        ("serial", ["get-serialno"]),
        ("model", ["shell", "getprop", "ro.product.model"]),
        ("android", ["shell", "getprop", "ro.build.version.release"]),
        ("sdk", ["shell", "getprop", "ro.build.version.sdk"]),
        ("size", ["shell", "wm", "size"]),
        ("density", ["shell", "wm", "density"]),
    ]

    lines: list[str] = []
    for label, command in queries:
        value = run_adb(adb, serial, command, timeout)
        assert isinstance(value, str)
        lines.append(f"{label}: {value.strip()}")

    return "\n".join(lines) + "\n"


def dump_hierarchy(
    adb: str,
    serial: Optional[str],
    timeout: float,
) -> str:
    # This is intentionally slower and is only used in --full mode.
    # It uses simple ADB calls, not host-shell pipes or Unix-only tools.
    run_adb(
        adb,
        serial,
        ["shell", "uiautomator", "dump", "--compressed", REMOTE_XML],
        timeout,
    )

    xml = run_adb(
        adb,
        serial,
        ["exec-out", "cat", REMOTE_XML],
        timeout,
    )
    assert isinstance(xml, str)

    try:
        run_adb(
            adb,
            serial,
            ["shell", "rm", "-f", REMOTE_XML],
            timeout,
        )
    except InspectError:
        pass

    stripped = xml.lstrip()
    if not (
        stripped.startswith("<?xml")
        or stripped.startswith("<hierarchy")
    ):
        raise InspectError("uiautomator did not produce XML")

    return xml


def main() -> int:
    p = argparse.ArgumentParser(
        description="Capture Android screenshot and useful debug state."
    )
    p.add_argument("--adb", default="adb")
    p.add_argument("-s", "--serial")
    p.add_argument("--display")
    p.add_argument(
        "--out",
        type=Path,
        default=Path(tempfile.gettempdir()) / "android-inspect",
    )
    p.add_argument("--full", action="store_true")
    p.add_argument("--timeout", type=float, default=20.0)
    args = p.parse_args()
    adb = resolve_adb(args.adb)

    out = args.out.expanduser().resolve()

    try:
        if out.exists():
            if not out.is_dir():
                raise InspectError(
                    f"output exists and is not a directory: {out}"
                )
            shutil.rmtree(out)

        out.mkdir(parents=True)

        (out / "screenshot.png").write_bytes(
            take_screenshot(adb, args.serial, args.timeout, args.display)
        )

        (out / "device.txt").write_text(
            device_summary(adb, args.serial, args.timeout),
            encoding="utf-8",
        )

        activity = run_adb(
            adb,
            args.serial,
            ["shell", "dumpsys", "activity", "activities"],
            args.timeout,
        )
        assert isinstance(activity, str)
        (out / "activity.txt").write_text(
            activity, encoding="utf-8"
        )

        if args.full:
            try:
                hierarchy = dump_hierarchy(
                    adb, args.serial, args.timeout
                )
                (out / "hierarchy.xml").write_text(
                    hierarchy, encoding="utf-8"
                )
            except InspectError as exc:
                (out / "hierarchy.error.txt").write_text(
                    str(exc), encoding="utf-8"
                )

            for filename, command in (
                ("window.txt", ["shell", "dumpsys", "window"]),
                ("display.txt", ["shell", "dumpsys", "display"]),
            ):
                try:
                    text = run_adb(
                        adb, args.serial, command, args.timeout
                    )
                    assert isinstance(text, str)
                    (out / filename).write_text(
                        text, encoding="utf-8"
                    )
                except InspectError as exc:
                    (out / (filename + ".error.txt")).write_text(
                        str(exc), encoding="utf-8"
                    )

    except InspectError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    print(out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
