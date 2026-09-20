#!/usr/bin/env python3
"""Heuristic god-file audit for Kotlin/Java source files."""
from pathlib import Path
import argparse, json, re
from _common import iter_files, read_text, rel, code_loc

FUN=re.compile(r'(?m)^\s*(?:@[\w.()", =:-]+\s*)*(?:(?:public|private|protected|internal|inline|tailrec|operator|infix|suspend|override|open|final|abstract)\s+)*fun\s+[^\n{=]*')
TYPE=re.compile(r'(?m)^\s*(?:(?:public|private|protected|internal|open|abstract|sealed|data|enum|value|annotation)\s+)*(class|interface|object)\s+([A-Za-z_]\w*)')
IMPORT=re.compile(r'(?m)^\s*import\s+')
COMPOSABLE=re.compile(r'(?m)^\s*@Composable\b')
PUBLIC_DECL=re.compile(r'(?m)^\s*(?!private\b)(?!internal\b)(?!protected\b)(?:public\s+)?(?:class|interface|object|fun|val|var|typealias)\b')

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('root',nargs='?',default='.')
    ap.add_argument('--max-lines',type=int,default=500)
    ap.add_argument('--max-functions',type=int,default=25)
    ap.add_argument('--max-types',type=int,default=8)
    ap.add_argument('--max-public',type=int,default=20)
    ap.add_argument('--max-imports',type=int,default=45)
    ap.add_argument('--top',type=int,default=100)
    ap.add_argument('--all',action='store_true')
    ap.add_argument('--json',action='store_true')
    a=ap.parse_args(); root=Path(a.root).resolve(); rows=[]
    for p in iter_files(root,{'.kt','.kts','.java'}):
        t=read_text(p); loc=code_loc(t); funcs=len(FUN.findall(t)); types=len(TYPE.findall(t)); pubs=len(PUBLIC_DECL.findall(t)); imports=len(IMPORT.findall(t)); comps=len(COMPOSABLE.findall(t))
        flags=[]
        if loc>a.max_lines: flags.append('lines')
        if funcs>a.max_functions: flags.append('functions')
        if types>a.max_types: flags.append('types')
        if pubs>a.max_public: flags.append('public')
        if imports>a.max_imports: flags.append('imports')
        # Score intentionally combines multiple signals; no single threshold means "god file".
        score=(loc/max(a.max_lines,1))+(funcs/max(a.max_functions,1))+(types/max(a.max_types,1))+(pubs/max(a.max_public,1))+(imports/max(a.max_imports,1))
        if flags or a.all: rows.append({'file':rel(p,root),'loc':loc,'functions':funcs,'types':types,'public':pubs,'imports':imports,'composables':comps,'score':round(score,2),'flags':flags})
    rows.sort(key=lambda r:(len(r['flags']),r['score'],r['loc']),reverse=True); rows=rows[:a.top]
    if a.json: print(json.dumps(rows,indent=2)); return
    if not rows: print('No files matched the selected thresholds.'); return
    print('flags                 score   loc funcs types public imports comp  file')
    for r in rows:
        print(f"{','.join(r['flags']) or '-':21} {r['score']:5.2f} {r['loc']:5} {r['functions']:5} {r['types']:5} {r['public']:6} {r['imports']:7} {r['composables']:4}  {r['file']}")
    print('\nThreshold hits are review candidates, not automatic refactor instructions.')
if __name__=='__main__': main()
