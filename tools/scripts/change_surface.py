#!/usr/bin/env python3
"""Summarize a Git diff and find likely source/resource/test neighbors for the changed files."""
from pathlib import Path
import argparse, subprocess, re
from collections import defaultdict
from _common import iter_files, read_text, rel

SRC_SUFFIX={'.kt','.kts','.java','.xml','.gradle','.toml','.properties'}

def git(root,*args):
    p=subprocess.run(['git',*args],cwd=root,text=True,capture_output=True)
    if p.returncode: raise SystemExit(p.stderr.strip() or 'git command failed')
    return p.stdout

def names_from_file(path,text):
    names=set(re.findall(r'\b(?:class|interface|object|fun|typealias)\s+([A-Za-z_]\w*)',text))
    names.add(path.stem)
    return {n for n in names if len(n)>=4}

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('root',nargs='?',default='.')
    ap.add_argument('--base',default=None,help='compare BASE...HEAD instead of working tree/index')
    ap.add_argument('--max-neighbors',type=int,default=8)
    a=ap.parse_args(); root=Path(a.root).resolve()
    if a.base:
        changed=[x for x in git(root,'diff','--name-only',f'{a.base}...HEAD').splitlines() if x]
    else:
        unst=[x for x in git(root,'diff','--name-only').splitlines() if x]
        staged=[x for x in git(root,'diff','--cached','--name-only').splitlines() if x]
        changed=list(dict.fromkeys(unst+staged))
    print('CHANGED FILES:')
    if not changed: print('  (none)'); return
    for x in changed: print('  '+x)
    targets=[]
    for x in changed:
        p=root/x
        if p.exists() and p.suffix in SRC_SUFFIX:
            targets.append((x,names_from_file(p,read_text(p))))
    corpus=[]
    for p in iter_files(root,SRC_SUFFIX):
        rp=rel(p,root)
        if rp in changed: continue
        corpus.append((rp,read_text(p)))
    print('\nLIKELY NEIGHBORS:')
    for file,names in targets:
        scores=[]
        for rp,text in corpus:
            score=sum(len(re.findall(r'\b'+re.escape(n)+r'\b',text)) for n in names)
            if score: scores.append((score,rp))
        scores.sort(reverse=True)
        print(f'\n  {file}')
        if names: print('    symbols: '+', '.join(sorted(names)[:20]))
        for score,rp in scores[:a.max_neighbors]: print(f'    {score:3}  {rp}')
    print('\nReview neighbors as candidates only; textual coupling is not proof of runtime dependency.')
if __name__=='__main__': main()
