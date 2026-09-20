#!/usr/bin/env python3
"""Heuristic Kotlin/Java function audit for long, branchy, deeply nested, or parameter-heavy functions."""
from pathlib import Path
import argparse, json, re
from _common import iter_files, read_text, rel

KT_FUN = re.compile(r'(?m)^\s*(?:@[\w.()", =:-]+\s*)*(?:(?:public|private|protected|internal|inline|tailrec|operator|infix|suspend|override|open|final|abstract|external|actual|expect)\s+)*fun\s+(?:<[^>{}]+>\s*)?(?:[\w?.<>]+\.)?([A-Za-z_]\w*)\s*\(')
JAVA_FUN = re.compile(r'(?m)^\s*(?:(?:public|private|protected|static|final|synchronized|native|abstract|default)\s+)*(?:<[^>]+>\s*)?[\w\[\]<>?,.@]+\s+([A-Za-z_]\w*)\s*\(')
CONTROL = re.compile(r'\b(if|for|while|when|catch|else\s+if|try)\b|&&|\|\|')

def masked(text):
    # Preserve newlines/brace positions while hiding comments and strings.
    out=list(text); i=0; n=len(out); state='code'
    while i<n:
        c=out[i]; nxt=out[i+1] if i+1<n else ''
        if state=='code':
            if c=='/' and nxt=='/': out[i]=out[i+1]=' '; i+=2; state='line'; continue
            if c=='/' and nxt=='*': out[i]=out[i+1]=' '; i+=2; state='block'; continue
            if text.startswith('"""',i): out[i:i+3]=[' ',' ',' ']; i+=3; state='triple'; continue
            if c=='"': out[i]=' '; i+=1; state='string'; continue
            if c=="'": out[i]=' '; i+=1; state='char'; continue
        elif state=='line':
            if c=='\n': state='code'
            else: out[i]=' '
        elif state=='block':
            if c=='*' and nxt=='/': out[i]=out[i+1]=' '; i+=2; state='code'; continue
            if c!='\n': out[i]=' '
        elif state=='triple':
            if text.startswith('"""',i): out[i:i+3]=[' ',' ',' ']; i+=3; state='code'; continue
            if c!='\n': out[i]=' '
        elif state in ('string','char'):
            quote='"' if state=='string' else "'"
            if c=='\\': out[i]=' '; i+=1; 
            if i<n and out[i] != '\n': out[i]=' '
            if c==quote: state='code'
        i+=1
    return ''.join(out)

def paren_end(s,start):
    depth=0
    for i in range(start,len(s)):
        if s[i]=='(': depth+=1
        elif s[i]==')':
            depth-=1
            if depth==0:return i
    return None

def body_span(s, sig_end):
    i=sig_end+1
    # Kotlin expression body: count until newline; block body: brace matching.
    while i<len(s) and s[i].isspace(): i+=1
    # Skip return type / where / throws clauses. Permit line breaks before the body.
    limit=min(len(s), i+800)
    while i<limit and s[i] not in '{=;': i+=1
    if i>=limit or s[i]==';': return None
    if s[i]=='=':
        j=s.find('\n',i); return (i, len(s) if j<0 else j)
    depth=0
    for j in range(i,len(s)):
        if s[j]=='{': depth+=1
        elif s[j]=='}':
            depth-=1
            if depth==0:return (i,j+1)
    return None

def count_params(sig):
    inner=sig[1:-1].strip()
    if not inner:return 0
    depth=0; count=1
    for c in inner:
        if c in '(<[{': depth+=1
        elif c in ')>]}': depth=max(0,depth-1)
        elif c==',' and depth==0: count+=1
    return count

def max_nesting(body):
    depth=0; maxd=0
    for c in body:
        if c=='{': depth+=1; maxd=max(maxd,depth)
        elif c=='}': depth=max(0,depth-1)
    return max(0,maxd-1)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('root', nargs='?', default='.')
    ap.add_argument('--max-lines',type=int,default=80)
    ap.add_argument('--max-params',type=int,default=7)
    ap.add_argument('--max-nesting',type=int,default=5)
    ap.add_argument('--max-branches',type=int,default=16)
    ap.add_argument('--all',action='store_true',help='show all functions, not only threshold hits')
    ap.add_argument('--json',action='store_true')
    a=ap.parse_args(); root=Path(a.root).resolve(); rows=[]
    for p in iter_files(root,{'.kt','.kts','.java'}):
        raw=read_text(p); s=masked(raw); regex=KT_FUN if p.suffix in ('.kt','.kts') else JAVA_FUN
        for m in regex.finditer(s):
            po=s.find('(',m.end()-1); pe=paren_end(s,po)
            if pe is None: continue
            span=body_span(s,pe)
            if not span: continue
            b0,b1=span; body=s[b0:b1]
            start_line=s.count('\n',0,m.start())+1; end_line=s.count('\n',0,b1)+1
            params=count_params(s[po:pe+1]); branches=len(CONTROL.findall(body)); nesting=max_nesting(body); lines=end_line-start_line+1
            flags=[]
            if lines>a.max_lines: flags.append('lines')
            if params>a.max_params: flags.append('params')
            if nesting>a.max_nesting: flags.append('nesting')
            if branches>a.max_branches: flags.append('branches')
            if flags or a.all:
                rows.append({'file':rel(p,root),'name':m.group(1),'start':start_line,'end':end_line,'lines':lines,'params':params,'nesting':nesting,'branches':branches,'flags':flags})
    rows.sort(key=lambda r:(len(r['flags']),r['lines'],r['branches'],r['nesting']),reverse=True)
    if a.json: print(json.dumps(rows,indent=2)); return
    if not rows: print('No functions matched the selected thresholds.'); return
    print('flags       lines params nest branches  location')
    for r in rows:
        print(f"{','.join(r['flags']) or '-':11} {r['lines']:5} {r['params']:6} {r['nesting']:4} {r['branches']:8}  {r['file']}:{r['start']}  {r['name']}")
    print('\nHeuristic only: investigate responsibility/cohesion before splitting anything.')
if __name__=='__main__': main()
