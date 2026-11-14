"""Regenerates docs/CODE_STATS.md: lines of code per component and per language.

Run from the repo root: python3 tools/code_stats.py
Counts git-tracked text files only. Code lines exclude blank and comment lines.
"""
import subprocess,re,collections,json
files=subprocess.run(["git","ls-files"],capture_output=True,text=True).stdout.split()
LANG={"kt":"Kotlin","kts":"Kotlin (Gradle scripts)","xml":"XML","json":"JSON","sh":"Shell","bat":"Batch","toml":"TOML","properties":"Properties","pro":"ProGuard","md":"Markdown","py":"Python"}
def lang(f):
    if f.endswith("gradlew"): return "Shell"
    e=f.rsplit(".",1)[-1] if "." in f.split("/")[-1] else ""
    return LANG.get(e)
def count(f,l):
    try: lines=open(f,encoding="utf-8").read().splitlines()
    except Exception: return None
    blank=comment=code=0; inblock=False
    cstyle=l in("Kotlin","Kotlin (Gradle scripts)")
    for s in (x.strip() for x in lines):
        if not s: blank+=1; continue
        if cstyle:
            if inblock:
                comment+=1
                if "*/" in s: inblock=False
                continue
            if s.startswith("//"): comment+=1; continue
            if s.startswith("/*"):
                comment+=1
                if "*/" not in s: inblock=True
                continue
        elif l=="XML" and s.startswith("<!--"): comment+=1; continue
        elif l in("Shell","TOML","Properties","ProGuard","Python") and s.startswith("#"): comment+=1; continue
        code+=1
    return code,comment,blank
P="app/src/main/java/org/seg7/familywatchlist/"; T="app/src/test/java/org/seg7/familywatchlist/"
def comp(f,l):
    if f.startswith(P):
        r=f[len(P):]; parts=r.split("/")
        if parts[0]=="ui": return "App: UI screens and ViewModels (ui/)"
        if parts[0]=="data" and len(parts)>2:
            return {"local":"App: local database (data/local)","remote":"App: TMDB client (data/remote)","repository":"App: repositories (data/repository)","recommend":"App: recommender (data/recommend)","search":"App: search logic (data/search)","backup":"App: backup and restore (data/backup)"}.get(parts[1],"App: other data/")
        if parts[0]=="work": return "App: background work (work/)"
        if parts[0]=="di": return "App: dependency wiring (di/)"
        return "App: root and common (MainActivity, app class, BuildStats, common/)"
    if f.startswith(T): return "Tests (app/src/test)"
    if f.startswith("app/schemas/"): return "Room schema exports (generated JSON)"
    if f.startswith("app/src/main/res/"): return "Android resources (app/src/main/res)"
    if l=="Markdown": return "Docs and project notes (Markdown)"
    return "Build and tooling (Gradle, scripts, config)"
by_lang=collections.Counter(); by_comp=collections.defaultdict(lambda:[0,0,0,0]); lang_files=collections.Counter()
for f in files:
    l=lang(f)
    if not l: continue
    r=count(f,l)
    if r is None: continue
    c=comp(f,l); by_comp[c][0]+=1
    for i in range(3): by_comp[c][i+1]+=r[i]
    by_lang[l]+=r[0]; lang_files[l]+=1

import datetime
head=subprocess.run(["git","log","-1","--format=%h %cs"],capture_output=True,text=True).stdout.split()
def pct(n,t): return f"{100*n/t:.1f}%"
app=sorted([(k,v) for k,v in by_comp.items() if k.startswith("App:")],key=lambda kv:-kv[1][1])
rest=[(k,by_comp[k]) for k in ["Tests (app/src/test)","Android resources (app/src/main/res)","Build and tooling (Gradle, scripts, config)","Room schema exports (generated JSON)","Docs and project notes (Markdown)"] if k in by_comp]
def row(k,v): n=k.split(': ',1)[-1]; return f"| {n[0].upper()+n[1:]} | {v[0]} | {v[1]:,} | {v[2]:,} | {v[3]:,} |"
appsum=[sum(v[i] for _,v in app) for i in range(4)]
tot=[sum(v[i] for v in by_comp.values()) for i in range(4)]
hand={k:v for k,v in by_lang.items() if k not in("JSON","Markdown")}
ht=sum(hand.values()); at=sum(by_lang.values())
kot=by_lang.get("Kotlin",0)+by_lang.get("Kotlin (Gradle scripts)",0)
L=[]
L+=["# Code statistics","",f"A snapshot of the codebase size at commit `{head[0]}` ({head[1]}). Not documentation: just numbers.","",
"- **Code lines** exclude blank lines and comment lines (`//`, `/* */`, KDoc, `#`, `<!-- -->`).",
"- Only git-tracked text files are counted. Images and the Gradle wrapper jar are excluded.",
"- Regenerate with `python3 tools/code_stats.py` from the repo root.","",
"## Headline","",
f"| | Code lines |","|---|---:|",
f"| App code (Kotlin, `app/src/main`) | {appsum[1]:,} |",
f"| Test code (Kotlin, `app/src/test`) | {by_comp['Tests (app/src/test)'][1]:,} |",
f"| All hand-written code (app, tests, build, resources) | {ht:,} |",
f"| Everything tracked, including generated schemas and docs | {at:,} |","",
f"Tests are {by_comp['Tests (app/src/test)'][1]/appsum[1]:.2f} lines for every line of app code.","",
"## App code by component","",
"| Component | Files | Code | Comments | Blank |","|---|---:|---:|---:|---:|"]
L+=[row(k,v) for k,v in app]
L+=[f"| **App total** | **{appsum[0]}** | **{appsum[1]:,}** | **{appsum[2]:,}** | **{appsum[3]:,}** |","",
"## Everything else","","| Area | Files | Code | Comments | Blank |","|---|---:|---:|---:|---:|"]
L+=[row(k,v) for k,v in rest]
L+=[f"| **Grand total (all areas)** | **{tot[0]}** | **{tot[1]:,}** | **{tot[2]:,}** | **{tot[3]:,}** |","",
"## Languages","","### Hand-written code","",
"Excludes the generated Room schema JSON and the Markdown docs. There is no Java, C or C++ in the repo. The only Python is the stats script, `tools/code_stats.py`.","",
"| Language | Files | Code lines | Share |","|---|---:|---:|---:|"]
for k,v in sorted(hand.items(),key=lambda kv:-kv[1]): L.append(f"| {k} | {lang_files[k]} | {v:,} | {pct(v,ht)} |")
L+=[f"| **Total** | **{sum(lang_files[k] for k in hand)}** | **{ht:,}** | **100%** |","",
f"Kotlin, including the Gradle build scripts, is **{pct(kot,ht)}** of hand-written code.","",
"### Everything tracked","","| Language | Files | Code lines | Share |","|---|---:|---:|---:|"]
for k,v in sorted(by_lang.items(),key=lambda kv:-kv[1]): L.append(f"| {k} | {lang_files[k]} | {v:,} | {pct(v,at)} |")
L+=[f"| **Total** | **{sum(lang_files.values())}** | **{at:,}** | **100%** |",""]
L+=["```mermaid","pie showData title Hand-written code by language (code lines)"]
for k,v in sorted(hand.items(),key=lambda kv:-kv[1]):
    if v/ht>=0.005: L.append(f'    "{k}" : {v}')
small=[k for k,v in sorted(hand.items(),key=lambda kv:-kv[1]) if v/ht<0.005]
if small: L.append(f'    "Other ({", ".join(small)})" : {sum(hand[k] for k in small)}')
L+=["```",""]
open("docs/CODE_STATS.md","w").write("\n".join(L))
print("wrote docs/CODE_STATS.md")
