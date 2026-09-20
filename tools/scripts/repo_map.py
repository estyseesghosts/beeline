#!/usr/bin/env python3
"""Map an Android/Kotlin repository: modules, packages, source sets, declarations, and large files."""
from pathlib import Path
import argparse, json, re
from collections import Counter, defaultdict
from _common import iter_files, read_text, rel, code_loc

DECL_RE = re.compile(r'^\s*(?:(?:public|private|internal|protected|open|abstract|sealed|data|enum|value|annotation|fun)\s+)*(class|interface|object|fun|typealias)\s+([A-Za-z_]\w*)', re.M)
PACKAGE_RE = re.compile(r'^\s*package\s+([\w.]+)', re.M)
ANNOT_RE = re.compile(r'^\s*@([A-Za-z_]\w*)', re.M)

def classify(path: Path, text: str):
    s = path.as_posix().lower()
    tags=[]
    if '/src/test/' in s: tags.append('unit-test')
    if '/src/androidtest/' in s: tags.append('instrumented-test')
    if '/src/main/' in s: tags.append('main')
    if '@composable' in text.lower(): tags.append('compose')
    if re.search(r'\bclass\s+\w*ViewModel\b', text): tags.append('viewmodel')
    if re.search(r'\b(interface|class)\s+\w*(Api|Service)\b', text): tags.append('api')
    if re.search(r'\b(class|interface)\s+\w*Repository\b', text): tags.append('repository')
    if 'room' in text.lower() or '@dao' in text.lower(): tags.append('persistence')
    return tags

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('root', nargs='?', default='.')
    ap.add_argument('--top', type=int, default=40, help='largest Kotlin/Java files to show')
    ap.add_argument('--json', action='store_true')
    a=ap.parse_args(); root=Path(a.root).resolve()
    build_files=list(iter_files(root, {'.gradle','.kts','settings.gradle','settings.gradle.kts'}))
    module_dirs=set()
    for p in build_files:
        if p.name in ('build.gradle','build.gradle.kts') and p.parent != root:
            module_dirs.add(rel(p.parent,root))
    rows=[]; packages=Counter(); tags=Counter(); decls=Counter(); source_sets=Counter()
    for p in iter_files(root,{'.kt','.kts','.java'}):
        text=read_text(p); rp=rel(p,root); loc=code_loc(text)
        pkg=(PACKAGE_RE.search(text).group(1) if PACKAGE_RE.search(text) else '')
        if pkg: packages[pkg]+=1
        for kind,name in DECL_RE.findall(text): decls[kind]+=1
        ftags=classify(p,text)
        for t in ftags: tags[t]+=1
        m=re.search(r'/src/([^/]+)/', '/'+rp)
        if m: source_sets[m.group(1)] += 1
        rows.append({'path':rp,'loc':loc,'package':pkg,'tags':ftags,'declarations':len(DECL_RE.findall(text))})
    data={'root':str(root),'modules':sorted(module_dirs),'source_sets':dict(source_sets),'tags':dict(tags),'declarations':dict(decls),'packages':packages.most_common(30),'largest_files':sorted(rows,key=lambda x:x['loc'],reverse=True)[:a.top]}
    if a.json:
        print(json.dumps(data,indent=2)); return
    print(f'ROOT: {root}')
    print('\nMODULES:'); [print('  '+x) for x in data['modules']] or print('  (single/root module or not detected)')
    print('\nSOURCE SETS:'); print('  '+', '.join(f'{k}={v}' for k,v in sorted(source_sets.items())))
    print('\nARCHITECTURE TAGS:'); print('  '+', '.join(f'{k}={v}' for k,v in sorted(tags.items())))
    print('\nTOP PACKAGES:'); [print(f'  {n:4}  {p}') for p,n in data['packages'][:20]]
    print('\nLARGEST KOTLIN/JAVA FILES:')
    for r in data['largest_files']:
        print(f"  {r['loc']:5}  decl={r['declarations']:3}  {r['path']}  [{','.join(r['tags'])}]")
if __name__=='__main__': main()
