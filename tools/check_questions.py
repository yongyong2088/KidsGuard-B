# -*- coding: utf-8 -*-
"""题库体检：选项数量、重复、答案有效性、空题干、难度分档"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
QDIR = ROOT / "app" / "src" / "main" / "assets" / "questions"

problems = []
stats = {}


def check(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    key = "%s_g%d" % (data["subject"], data["grade"])
    qs = data["questions"]
    ids = set()
    texts = set()
    lvc = {1: 0, 2: 0, 3: 0}
    for q in qs:
        where = "%s[%s]" % (key, q["id"])
        if q["id"] in ids:
            problems.append("%s id 重复" % where)
        ids.add(q["id"])
        if not q.get("q", "").strip():
            problems.append("%s 题干为空" % where)
        if not str(q.get("answer", "")).strip():
            problems.append("%s 答案为空" % where)
        # 题干 + 答案 完全一致才算重复（同题干不同答案是合法的题型变体）
        t = (q["q"].strip(), str(q["answer"]).strip())
        if t in texts:
            problems.append("%s 题目重复：%s" % (where, t[0][:40]))
        texts.add(t)
        lvc[q["level"]] = lvc.get(q["level"], 0) + 1
        if q["type"] == "choice":
            opts = q.get("options", [])
            # 判断题（对/错）天然只有两个选项，不视为问题
            is_judge = set(opts) == {"对", "错"}
            if len(opts) < 3 and not is_judge:
                problems.append("%s 选项不足（%d 个）：%s" % (where, len(opts), t[0][:40]))
            if len(set(opts)) != len(opts):
                problems.append("%s 选项重复：%s" % (where, opts))
            if q["answer"] not in opts:
                problems.append("%s 正确答案不在选项里：%s / %s" % (where, q["answer"], opts))
        elif q["type"] != "fill":
            problems.append("%s 未知题型 %s" % (where, q["type"]))
    stats[key] = (len(qs), lvc)


def main():
    files = sorted(QDIR.glob("*.json"))
    if not files:
        print("没有找到题库文件")
        return
    for f in files:
        check(f)
    print("%-14s %6s   %s" % ("题库", "总题量", "简单/中等/困难"))
    print("-" * 52)
    total = 0
    for k in sorted(stats):
        n, lvc = stats[k]
        total += n
        flag = "" if (lvc.get(1) == 200 and lvc.get(2) == 200 and lvc.get(3) == 200) else "  ← 未满"
        print("%-14s %6d   %3d / %3d / %3d%s" % (k, n, lvc.get(1, 0), lvc.get(2, 0), lvc.get(3, 0), flag))
    print("-" * 52)
    print("合计 %d 题" % total)
    if problems:
        print("\n发现 %d 个问题：" % len(problems))
        for p in problems[:40]:
            print("  -", p)
        if len(problems) > 40:
            print("  ...还有 %d 个" % (len(problems) - 40))
        sys.exit(1)
    else:
        print("\n体检通过：无重复、无空题、选项完整、答案有效")


if __name__ == "__main__":
    main()
