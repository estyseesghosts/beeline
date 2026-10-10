#!/usr/bin/env python3
r"""
adb_control.py

Cross-platform wrapper for common ADB UI actions.

Examples:
  python adb_control.py devices
  python adb_control.py tap 500 1200
  python adb_control.py swipe 500 1500 500 400 --duration 300
  python adb_control.py text "hello world"
  python adb_control.py back
  python adb_control.py home
  python adb_control.py info

Select a device:
  python adb_control.py -s emulator-5554 tap 500 1200
"""

from __future__ import annotations

import argparse
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
from typing import Optional, Sequence

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


class AdbError(RuntimeError):
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
    *,
    timeout: float,
    capture: bool = False,
) -> subprocess.CompletedProcess[str]:
    cmd = adb_prefix(adb, serial) + list(parts)
    try:
        return subprocess.run(
            cmd,
            check=True,
            text=True,
            capture_output=capture,
            timeout=timeout,
        )
    except FileNotFoundError as exc:
        raise AdbError(f"adb not found: {adb}") from exc
    except subprocess.TimeoutExpired as exc:
        raise AdbError(f"adb timed out after {timeout:g}s") from exc
    except subprocess.CalledProcessError as exc:
        detail = (exc.stderr or exc.stdout or "").strip()
        raise AdbError(detail or f"adb failed with exit code {exc.returncode}") from exc


def send_text(args: argparse.Namespace, value: str) -> None:
    # adb shell input text has awkward escaping rules for punctuation.
    # Keep this helper intentionally conservative so agents do not silently
    # type the wrong string.
    if not SAFE_TEXT.fullmatch(value):
        raise AdbError(
            "text contains unsupported characters; use simple ASCII text only"
        )
    encoded = value.replace(" ", "%s")
    run_adb(
        args.adb,
        args.serial,
        ["shell", "input", "text", encoded],
        timeout=args.timeout,
    )


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Fast ADB input/control wrapper.")
    p.add_argument("--adb", default="adb", help="adb executable or full path")
    p.add_argument("-s", "--serial", help="ADB serial, e.g. emulator-5554")
    p.add_argument("--timeout", type=float, default=10.0)

    sub = p.add_subparsers(dest="command", required=True)

    sub.add_parser("devices", help="List connected devices")

    q = sub.add_parser("tap", help="Tap screen coordinate")
    q.add_argument("x", type=int)
    q.add_argument("y", type=int)

    q = sub.add_parser("swipe", help="Swipe between coordinates")
    q.add_argument("x1", type=int)
    q.add_argument("y1", type=int)
    q.add_argument("x2", type=int)
    q.add_argument("y2", type=int)
    q.add_argument("--duration", type=int, default=300, help="milliseconds")

    q = sub.add_parser("text", help="Type conservative ASCII text")
    q.add_argument("value")

    q = sub.add_parser("key", help="Send an Android keyevent")
    q.add_argument("keycode", help="Example: KEYCODE_BACK or 4")

    sub.add_parser("back")
    sub.add_parser("home")
    sub.add_parser("enter")
    sub.add_parser("wake")
    sub.add_parser("info", help="Print basic device/display information")

    return p


def main() -> int:
    args = build_parser().parse_args()
    args.adb = resolve_adb(args.adb)

    try:
        if args.command == "devices":
            cp = run_adb(
                args.adb, None, ["devices", "-l"],
                timeout=args.timeout, capture=True
            )
            print(cp.stdout, end="")
            return 0

        if args.command == "tap":
            parts = ["shell", "input", "tap", str(args.x), str(args.y)]

        elif args.command == "swipe":
            if args.duration < 0:
                raise AdbError("--duration must be >= 0")
            parts = [
                "shell", "input", "swipe",
                str(args.x1), str(args.y1),
                str(args.x2), str(args.y2),
                str(args.duration),
            ]

        elif args.command == "text":
            send_text(args, args.value)
            return 0

        elif args.command == "key":
            parts = ["shell", "input", "keyevent", args.keycode]

        elif args.command == "back":
            parts = ["shell", "input", "keyevent", "KEYCODE_BACK"]

        elif args.command == "home":
            parts = ["shell", "input", "keyevent", "KEYCODE_HOME"]

        elif args.command == "enter":
            parts = ["shell", "input", "keyevent", "KEYCODE_ENTER"]

        elif args.command == "wake":
            parts = ["shell", "input", "keyevent", "KEYCODE_WAKEUP"]

        elif args.command == "info":
            queries = [
                ("serial", ["get-serialno"]),
                ("model", ["shell", "getprop", "ro.product.model"]),
                ("android", ["shell", "getprop", "ro.build.version.release"]),
                ("sdk", ["shell", "getprop", "ro.build.version.sdk"]),
                ("size", ["shell", "wm", "size"]),
                ("density", ["shell", "wm", "density"]),
            ]
            for label, command in queries:
                cp = run_adb(
                    args.adb, args.serial, command,
                    timeout=args.timeout, capture=True
                )
                print(f"{label}: {cp.stdout.strip()}")
            return 0

        else:
            raise AdbError(f"unsupported command: {args.command}")

        run_adb(args.adb, args.serial, parts, timeout=args.timeout)
        return 0

    except AdbError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
