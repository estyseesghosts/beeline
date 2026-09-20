#!/usr/bin/env python3
"""Find declarations/references for a symbol with concise context across Beeline source/resources."""
from pathlib import Path
import argparse, re
from _common import iter_files, read_text, rel

SUFFIXES={'.kt','.kts','.java','.xml','.gradle','.toml','.md','.json','.properties'}

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('symbol')
    ap.add_argument('root',nargs='?',default='.')
    ap.add_argument('-C','--context',type=int,default=1)
    ap.add_argument('--max-results',type=int,default=200)
    ap.add_argument('--regex',action='store_true')
    a=ap.parse_args(); root=Path(a.root).resolve()
    pat=re.compile(a.symbol if a.regex else r'\b'+re.escape(a.symbol)+r'\b',re.I)
    found=0
    for p in iter_files(root,SUFFIXES):
        lines=read_text(p).splitlines()
        for i,line in enumerate(lines):
            if not pat.search(line): continue
            found+=1
            print(f'\n{rel(p,root)}:{i+1}')
            lo=max(0,i-a.context); hi=min(len(lines),i+a.context+1)
            for j in range(lo,hi): print(f'{j+1:6}: {lines[j]}')
            if found>=a.max_results:
                print(f'\nStopped at --max-results={a.max_results}.'); return
    print(f'\n{found} matching lines.')
if __name__=='__main__': main()
