#!/usr/bin/env python3
r"""
adb_flow.py

Batch several UI actions into ONE adb shell call, then optionally take ONE
direct screenshot. This reduces repeated ADB startup/round-trip overhead.

PowerShell example:
  python adb_flow.py `
    --step "tap 500 1200" `
    --step "wait 200" `
    --step "back" `
    --screenshot after.png

CMD example:
  python adb_flow.py --step "tap 500 1200" --step "wait 200" --step "back" --screenshot after.png
  python adb_flow.py --step "tap 500 1200" --screenshot after.png --display 4619827259835644672  # cover
  python adb_flow.py --step "tap 500 1200" --screenshot after.png --display 4619827551948147201  # active

Supported steps:
  tap X Y
  swipe X1 Y1 X2 Y2 [DURATION_MS]
  key KEYCODE
  back
  home
  enter
  wait MILLISECONDS
  text SIMPLE_ASCII_TEXT
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Optional

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
SAFE_TEXT = re.compile(r"^[A-Za-z0-9 _.,@:/+\-=!?()]*$")


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


class FlowError(RuntimeError):
    pass


def adb_prefix(adb: str, serial: Optional[str]) -> list[str]:
    cmd = [adb]
    if serial:
        cmd += ["-s", serial]
    return cmd


def nonnegative_int(value: str, name: str) -> int:
    try:
        result = int(value)
    except ValueError as exc:
        raise FlowError(f"{name} must be an integer: {value}") from exc

    if result < 0:
        raise FlowError(f"{name} must be >= 0")

    return result


def compile_step(step: str) -> str:
    parts = step.strip().split()
    if not parts:
        raise FlowError("empty --step")

    op = parts[0].lower()

    if op == "tap" and len(parts) == 3:
        x = nonnegative_int(parts[1], "x")
        y = nonnegative_int(parts[2], "y")
        return f"input tap {x} {y}"

    if op == "swipe" and len(parts) in (5, 6):
        x1 = nonnegative_int(parts[1], "x1")
        y1 = nonnegative_int(parts[2], "y1")
        x2 = nonnegative_int(parts[3], "x2")
        y2 = nonnegative_int(parts[4], "y2")
        duration = (
            nonnegative_int(parts[5], "duration")
            if len(parts) == 6
            else 300
        )
        return f"input swipe {x1} {y1} {x2} {y2} {duration}"

    if op == "key" and len(parts) == 2:
        key = parts[1]
        if not all(c.isalnum() or c == "_" for c in key):
            raise FlowError(f"invalid keycode: {key}")
        return f"input keyevent {key}"

    if op == "back" and len(parts) == 1:
        return "input keyevent KEYCODE_BACK"

    if op == "home" and len(parts) == 1:
        return "input keyevent KEYCODE_HOME"

    if op == "enter" and len(parts) == 1:
        return "input keyevent KEYCODE_ENTER"

    if op == "wait" and len(parts) == 2:
        ms = nonnegative_int(parts[1], "milliseconds")
        return f"sleep {ms / 1000:.3f}"

    if op == "text" and len(parts) >= 2:
        value = step.strip()[len(parts[0]):].lstrip()
        if not SAFE_TEXT.fullmatch(value):
            raise FlowError(
                "text contains unsupported characters; "
                "use simple ASCII text only"
            )
        encoded = value.replace(" ", "%s")
        return f"input text {encoded}"

    raise FlowError(f"invalid step: {step!r}")


def run_flow(
    adb: str,
    serial: Optional[str],
    remote_script: str,
    timeout: float,
) -> None:
    cmd = adb_prefix(adb, serial) + ["shell", remote_script]

    try:
        cp = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            timeout=timeout,
        )
    except FileNotFoundError as exc:
        raise FlowError(f"adb not found: {adb}") from exc
    except subprocess.TimeoutExpired as exc:
        raise FlowError(f"flow timed out after {timeout:g}s") from exc

    if cp.returncode != 0:
        detail = cp.stderr.decode(errors="replace").strip()
        raise FlowError(
            detail or f"adb failed with exit code {cp.returncode}"
        )


def take_screenshot(
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

    try:
        screencap = ["exec-out", "screencap"]
        if display is not None:
            screencap += ["-d", display]
        cmd = adb_prefix(adb, serial) + screencap + ["-p"]

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
            raise FlowError(f"adb not found: {adb}") from exc
        except subprocess.TimeoutExpired as exc:
            raise FlowError(
                f"screenshot timed out after {timeout:g}s"
            ) from exc

        if cp.returncode != 0:
            detail = cp.stderr.decode(errors="replace").strip()
            raise FlowError(
                detail or f"adb failed with exit code {cp.returncode}"
            )

        data = temp.read_bytes()
        signature_offset = data.find(PNG_SIGNATURE)
        if signature_offset < 0:
            raise FlowError("screencap output was not PNG")
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
        description="Batch ADB UI actions into one shell call."
    )
    p.add_argument("--adb", default="adb")
    p.add_argument("-s", "--serial")
    p.add_argument("--display")
    p.add_argument(
        "--step",
        action="append",
        required=True,
        help='Example: --step "tap 500 1200"',
    )
    p.add_argument("--screenshot", type=Path)
    p.add_argument("--timeout", type=float, default=30.0)
    args = p.parse_args()
    adb = resolve_adb(args.adb)

    try:
        commands = [compile_step(step) for step in args.step]
        run_flow(
            adb,
            args.serial,
            "; ".join(commands),
            args.timeout,
        )

        if args.screenshot:
            path = take_screenshot(
                adb,
                args.serial,
                args.screenshot,
                args.timeout,
                args.display,
            )
            print(path)

        return 0

    except FlowError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
