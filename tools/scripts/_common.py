from __future__ import annotations
from pathlib import Path
import os

DEFAULT_EXCLUDES = {
    '.git', '.gradle', '.idea', 'build', 'out', 'node_modules', '.kotlin',
    '.cxx', '.externalNativeBuild', 'captures', 'generated'
}

def iter_files(root: Path, suffixes=None, extra_excludes=()):
    excludes = DEFAULT_EXCLUDES | set(extra_excludes)
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in excludes and not d.startswith('.tmp')]
        base = Path(dirpath)
        for name in filenames:
            p = base / name
            if suffixes is None or p.suffix.lower() in suffixes or p.name in suffixes:
                yield p

def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding='utf-8', errors='ignore')
    except OSError:
        return ''

def rel(path: Path, root: Path) -> str:
    try: return path.relative_to(root).as_posix()
    except ValueError: return path.as_posix()

def code_loc(text: str) -> int:
    return sum(1 for line in text.splitlines() if line.strip() and not line.lstrip().startswith('//'))
