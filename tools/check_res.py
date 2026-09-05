#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KidsGuard 资源静态一致性校验：
扫描 res/ 与 java/kt 源码，确认所有 R.string/@string、R.drawable/@drawable、
R.color/@color、R.style/@style、R.array/@array、?attr/* 引用都有对应定义。
缺失的引用会在云端编译时直接报错，所以这里提前拦住。
"""
import os, re, sys, glob
from xml.etree import ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main")
RES = os.path.join(APP, "res")
SRC = os.path.join(APP, "java")

NS = "{http://schemas.android.com/apk/res/android}"

defined = {
    "string": set(), "drawable": set(), "color": set(),
    "style": set(), "array": set(), "attr": set(), "mipmap": set(),
    "dimen": set(), "plurals": set(), "integer": set(),
}

def add(t, name):
    if name:
        defined[t].add(name)

# 1) parse res/values/*.xml
for f in glob.glob(os.path.join(RES, "values", "*.xml")):
    try:
        tree = ET.parse(f)
    except ET.ParseError as e:
        print(f"[PARSE ERROR] {f}: {e}")
        continue
    root = tree.getroot()
    for el in root.iter():
        tag = el.tag
        name = el.get("name")
        if not name:
            continue
        if tag == "string":
            add("string", name)
        elif tag in ("string-array", "array", "integer-array"):
            add("array", name)
        elif tag == "color":
            add("color", name)
        elif tag == "style":
            add("style", name)
        elif tag == "attr":
            add("attr", name)
        elif tag == "dimen":
            add("dimen", name)
        elif tag == "plurals":
            add("plurals", name)
        elif tag == "integer":
            add("integer", name)
        elif tag == "item" and el.get(NS + "type") == "id":
            pass  # id declarations handled below

# 2) drawable / mipmap from filenames (any res subdir starting with drawable or mipmap)
for d in glob.glob(os.path.join(RES, "*")):
    if not os.path.isdir(d):
        continue
    base = os.path.basename(d)
    if base.startswith("drawable"):
        kind = "drawable"
    elif base.startswith("mipmap"):
        kind = "mipmap"
    else:
        continue
    for fn in os.listdir(d):
        nm, ext = os.path.splitext(fn)
        if ext in (".xml", ".png", ".jpg", ".webp", ".9.png"):
            add(kind, nm)

# 3) collect @+id declarations across layouts (for R.id checks)
id_decls = set()
for f in glob.glob(os.path.join(RES, "**", "*.xml"), recursive=True):
    txt = open(f, encoding="utf-8", errors="ignore").read()
    for m in re.findall(r'@\+id/([\w.]+)', txt):
        id_decls.add(m)

# 4) collect references
ref_patterns = {
    "string": re.compile(r'@string/([\w.]+)'),
    "drawable": re.compile(r'@drawable/([\w.]+)'),
    "color": re.compile(r'@color/([\w.]+)'),
    "style": re.compile(r'@style/([\w.]+)'),
    "array": re.compile(r'@array/([\w.]+)'),
    "mipmap": re.compile(r'@mipmap/([\w.]+)'),
    "dimen": re.compile(r'@dimen/([\w.]+)'),
    "plurals": re.compile(r'@plurals/([\w.]+)'),
}
# R.xxx.yyy in kotlin/java
code_ref = {
    "string": re.compile(r'R\.string\.([A-Za-z_]\w*)'),
    "drawable": re.compile(r'R\.drawable\.([A-Za-z_]\w*)'),
    "color": re.compile(r'R\.color\.([A-Za-z_]\w*)'),
    "style": re.compile(r'R\.style\.([A-Za-z_]\w*)'),
    "array": re.compile(r'R\.array\.([A-Za-z_]\w*)'),
    "mipmap": re.compile(r'R\.mipmap\.([A-Za-z_]\w*)'),
    "id": re.compile(r'R\.id\.([A-Za-z_]\w*)'),
    "layout": re.compile(r'R\.layout\.([A-Za-z_]\w*)'),
}
# layouts defined
layout_names = set()
for f in glob.glob(os.path.join(RES, "layout", "*.xml")):
    layout_names.add(os.path.splitext(os.path.basename(f))[0])

problems = []
files_scanned = 0

def scan_text(text, fname, is_code):
    # strip XML comments so placeholders in comments don't false-flag
    text = re.sub(r'<!--.*?-->', '', text, flags=re.S)
    for t, pat in (ref_patterns if not is_code else {}).items():
        for m in pat.findall(text):
            if m not in defined[t]:
                problems.append(f"{fname}: missing @{t}/{m}")
    if is_code:
        # ignore framework refs like android.R.string.cancel
        code_ref_safe = {
            "string": re.compile(r'(?<!android\.)R\.string\.([A-Za-z_]\w*)'),
            "drawable": re.compile(r'(?<!android\.)R\.drawable\.([A-Za-z_]\w*)'),
            "color": re.compile(r'(?<!android\.)R\.color\.([A-Za-z_]\w*)'),
            "style": re.compile(r'(?<!android\.)R\.style\.([A-Za-z_]\w*)'),
            "array": re.compile(r'(?<!android\.)R\.array\.([A-Za-z_]\w*)'),
            "mipmap": re.compile(r'(?<!android\.)R\.mipmap\.([A-Za-z_]\w*)'),
            "id": re.compile(r'(?<!android\.)R\.id\.([A-Za-z_]\w*)'),
            "layout": re.compile(r'(?<!android\.)R\.layout\.([A-Za-z_]\w*)'),
        }
        for t, pat in code_ref_safe.items():
            for m in pat.findall(text):
                if t == "id":
                    if m not in id_decls:
                        problems.append(f"{fname}: R.id.{m} not declared as @+id anywhere")
                elif t == "layout":
                    if m not in layout_names:
                        problems.append(f"{fname}: R.layout.{m} missing layout file")
                elif t == "style":
                    # Android maps dotted XML style names to underscored R constants
                    if m not in defined["style"] and m.replace("_", ".") not in defined["style"]:
                        problems.append(f"{fname}: R.style.{m} not defined")
                else:
                    if m not in defined[t]:
                        problems.append(f"{fname}: R.{t}.{m} not defined")
    # ?attr references (xml and code), ignoring comment placeholders
    for m in re.findall(r'\?attr/([\w.]+)', text):
        if m not in defined["attr"]:
            problems.append(f"{fname}: ?attr/{m} not defined in attrs.xml")

# scan xml (layouts, values, xml dirs)
for f in glob.glob(os.path.join(RES, "**", "*.xml"), recursive=True):
    scan_text(open(f, encoding="utf-8", errors="ignore").read(), f, False)
    files_scanned += 1

# scan kotlin/java
for f in glob.glob(os.path.join(SRC, "**", "*.kt"), recursive=True) + \
         glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
    scan_text(open(f, encoding="utf-8", errors="ignore").read(), f, True)
    files_scanned += 1

print(f"Scanned {files_scanned} files.")
print(f"Defined: string={len(defined['string'])} drawable={len(defined['drawable'])} "
      f"color={len(defined['color'])} style={len(defined['style'])} "
      f"array={len(defined['array'])} mipmap={len(defined['mipmap'])} attr={len(defined['attr'])}")
if problems:
    print(f"\n!!! {len(problems)} PROBLEM(S) FOUND:")
    for p in sorted(set(problems)):
        print("  -", p)
    sys.exit(1)
else:
    print("\nOK: all R.* / @* / ?attr references resolve. Project is reference-consistent.")
