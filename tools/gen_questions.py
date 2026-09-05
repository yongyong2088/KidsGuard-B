# -*- coding: utf-8 -*-
"""
学习守护 · 题库生成器

生成 6 模块 × 3 年级 × 600 题（简单/中等/困难 各 200）。
数学、英语用算法参数化生成；语文、音乐、运动、脑筋急转弯用内容库 + 模板变体。

输出：app/src/main/assets/questions/{module}_g{grade}.json
"""
import json
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "assets" / "questions"
OUT.mkdir(parents=True, exist_ok=True)

rnd = random.Random(20260903)
LEVELS = {"easy": 1, "medium": 2, "hard": 3}
PER_LEVEL = 200


# ---------------------------------------------------------------- 基础工具
def _opts(correct, distractors):
    """拼选项并打乱，保证去重"""
    ds = []
    seen = {str(correct)}
    for d in distractors:
        s = str(d)
        if s not in seen:
            seen.add(s)
            ds.append(s)
    # 不足 3 个干扰项时用数字偏移兜底
    i = 1
    while len(ds) < 3:
        for cand in (int(correct) + i if str(correct).lstrip("-").isdigit() else None,
                     int(correct) - i if str(correct).lstrip("-").isdigit() else None):
            if cand is not None and str(cand) not in seen:
                seen.add(str(cand))
                ds.append(str(cand))
                break
        i += 1
        if i > 40:
            break
    opts = [str(correct)] + ds[:3]
    rnd.shuffle(opts)
    return opts


def choice(qid, level, q, correct, distractors, tip=""):
    return {
        "id": qid, "type": "choice", "level": level, "q": q,
        "options": _opts(correct, distractors),
        "answer": str(correct), "tip": tip,
    }


def fill(qid, level, q, correct, tip=""):
    return {
        "id": qid, "type": "fill", "level": level, "q": q,
        "answer": str(correct), "tip": tip,
    }


def num_distractors(correct, n=3, spread=None):
    """围绕正确数字生成 n 个不重复的干扰项"""
    spread = spread or [1, 2, 3, 4, 5, 6, 10]
    out, seen = [], {correct}
    guard = 0
    while len(out) < n and guard < 300:
        guard += 1
        d = correct + rnd.choice(spread) * rnd.choice([1, -1])
        if d != correct and d not in seen and d >= 0:
            seen.add(d)
            out.append(d)
    k = 1
    while len(out) < n:
        v = correct + k * 3 + 1
        if v not in seen:
            seen.add(v)
            out.append(v)
        k += 1
    return out


class Bank:
    """一个模块一个年级的题桶，负责去重、计数、切分难度"""

    def __init__(self, subject, grade):
        self.subject = subject
        self.grade = grade
        self.buckets = {1: [], 2: [], 3: []}
        # 全局去重：同一模块同一年级内，600 题互不重复（跨难度档也去重）
        self.seen = set()
        self.n = 0

    def add(self, level, item):
        key = item["q"] + "|" + str(item["answer"])
        if key in self.seen:
            return False
        if len(self.buckets[level]) >= PER_LEVEL:
            return False
        self.seen.add(key)
        self.buckets[level].append(item)
        self.n += 1
        return True

    def _qid(self, level):
        return "%s-g%d-l%d-%04d" % (self.subject, self.grade, level, len(self.buckets[level]) + 1)

    def choice(self, level, q, correct, distractors, tip=""):
        return self.add(level, choice(self._qid(level), level, q, correct, distractors, tip))

    def fill(self, level, q, correct, tip=""):
        return self.add(level, fill(self._qid(level), level, q, correct, tip))

    def need(self, level):
        return PER_LEVEL - len(self.buckets[level])

    def full(self, level):
        return len(self.buckets[level]) >= PER_LEVEL

    def dump(self):
        questions = []
        for lv in (1, 2, 3):
            questions.extend(self.buckets[lv])
        # 重新编号，保证 id 连续
        for i, qq in enumerate(questions, 1):
            qq["id"] = "%s-g%d-%04d" % (self.subject, self.grade, i)
        data = {
            "subject": self.subject,
            "grade": self.grade,
            "levels": {"1": len(self.buckets[1]), "2": len(self.buckets[2]), "3": len(self.buckets[3])},
            "total": len(questions),
            "questions": questions,
        }
        path = OUT / ("%s_g%d.json" % (self.subject, self.grade))
        path.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
        return data


# ==================================================================== 数学
def gen_math(grade):
    b = Bank("math", grade)

    def addsub(lo, hi, level, cnt, sub=True, fill_ratio=0.35):
        """加减法：一部分选择、一部分填空"""
        made = 0
        guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            a = rnd.randint(lo, hi)
            bb = rnd.randint(lo, hi)
            if sub and rnd.random() < 0.5:
                x, y = max(a, bb), min(a, bb)
                if x - y < 0:
                    continue
                q, ans, op = "%d − %d = ?" % (x, y), x - y, "减"
            else:
                x, y = a, bb
                q, ans, op = "%d + %d = ?" % (x, y), x + y, "加"
            if rnd.random() < fill_ratio:
                ok = b.fill(level, q, ans, tip="%d %s %d 等于 %d" % (x, op, y, ans))
            else:
                ok = b.choice(level, q, ans, num_distractors(ans),
                              tip="%d %s %d 等于 %d" % (x, op, y, ans))
            if ok:
                made += 1

    def muldiv(tables, level, cnt, div=True):
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            a = rnd.choice(tables)
            bb = rnd.randint(2, 9)
            if div and rnd.random() < 0.45:
                q, ans, op = "%d ÷ %d = ?" % (a * bb, a), bb, "除以"
            else:
                q, ans, op = "%d × %d = ?" % (a, bb), a * bb, "乘"
            ok = b.choice(level, q, ans, num_distractors(ans, spread=[1, 2, 3, 6, 9, 12]),
                          tip="%s口诀：%d %s %d = %d" % (op, a, "×" if op == "乘" else "÷", bb, ans))
            if ok:
                made += 1

    def compare(lo, hi, level, cnt):
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            a = rnd.randint(lo, hi)
            bb = rnd.randint(lo, hi)
            if a == bb:
                continue
            sign = ">" if a > bb else "<"
            q = "%d ○ %d，○ 里应填？" % (a, bb)
            ok = b.choice(level, q, sign, [">", "<", "="] if sign != "=" else [">", "<"],
                          tip="%d 比 %d %s，所以填 %s" % (a, bb, "大" if a > bb else "小", sign))
            if ok:
                made += 1

    def blank(lo, hi, level, cnt, ops=("+", "−")):
        """填空：( ) + 3 = 8 或 7 − ( ) = 2"""
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            op = rnd.choice(ops)
            a = rnd.randint(lo, hi)
            bb = rnd.randint(lo, hi)
            if op == "+":
                q, ans = "( ) + %d = %d" % (a, a + bb), bb
                tip = "%d + %d = %d，括号里是 %d" % (bb, a, a + bb, bb)
            else:
                if a - bb < 0:
                    continue
                q, ans = "%d − ( ) = %d" % (a, a - bb), bb
                tip = "%d − %d = %d，括号里是 %d" % (a, bb, a - bb, bb)
            ok = b.fill(level, q, ans, tip=tip)
            if ok:
                made += 1

    def pattern(level, cnt, step_hi=5, length=4, hard=False):
        """找规律填数"""
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            start = rnd.randint(1, 30)
            step = rnd.randint(2, step_hi)
            seq = [start + step * i for i in range(length)]
            if hard and rnd.random() < 0.4:  # 倍数规律
                start = rnd.randint(2, 6)
                step = rnd.choice([2, 3])
                seq = [start * (step ** i) for i in range(length)]
                if seq[-1] > 500:
                    continue
            hide = length - 1
            shown = [str(x) for x in seq]
            ans = seq[hide]
            shown[hide] = "( )"
            q = "找规律填数：%s" % "、".join(shown)
            ok = b.fill(level, q, ans, tip="每次多 %d，所以是 %d" % (step if not hard else step, ans))
            if ok:
                made += 1

    def money(level, cnt):
        """人民币：元角分"""
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            kind = rnd.randint(1, 3)
            if kind == 1:
                y, j = rnd.randint(1, 20), rnd.randint(1, 9)
                q, ans = "%d 元 %d 角 = ( ) 角" % (y, j), y * 10 + j
                tip = "1 元 = 10 角，%d 元 = %d 角，再加 %d 角是 %d 角" % (y, y * 10, j, ans)
            elif kind == 2:
                j = rnd.randint(20, 99)
                q, ans = "%d 角 = ( ) 元 ( ) 角" % (j, ), "%d元%d角" % (j // 10, j % 10)
                tip = "10 角 = 1 元，%d 角是 %d 元 %d 角" % (j, j // 10, j % 10)
            else:
                a = rnd.randint(1, 9) * 10
                bb = rnd.randint(1, 8) * 10
                q, ans = "买东西花了 %d 元，付了 %d 元，应找回 ( ) 元" % (a, a + bb), bb
                tip = "%d − %d = %d 元" % (a + bb, a, bb)
            ok = b.fill(level, q, ans, tip=tip)
            if ok:
                made += 1

    def word_problem(templates, level, cnt):
        made = guard = 0
        while made < cnt and guard < cnt * 80:
            guard += 1
            t = rnd.choice(templates)
            q, ans, tip = t()
            if b.fill(level, q, ans, tip=tip):
                made += 1

    def unit(level, cnt):
        """长度 / 重量 / 时间单位换算"""
        made = guard = 0
        conv = [
            ("1 米 = ( ) 厘米", 100, "1 米 = 100 厘米"),
            ("1 分米 = ( ) 厘米", 10, "1 分米 = 10 厘米"),
            ("1 时 = ( ) 分", 60, "1 时 = 60 分"),
            ("1 分 = ( ) 秒", 60, "1 分 = 60 秒"),
            ("1 千克 = ( ) 克", 1000, "1 千克 = 1000 克"),
            ("1 米 = ( ) 分米", 10, "1 米 = 10 分米"),
        ]
        while made < cnt and guard < cnt * 40:
            guard += 1
            base, val, tip = rnd.choice(conv)
            if rnd.random() < 0.5 and val >= 60:
                n = rnd.randint(2, 9)
                if val == 100:
                    q, ans = "%d 米 = ( ) 厘米" % n, n * 100
                    tip = "%d × 100 = %d 厘米" % (n, n * 100)
                elif val == 60:
                    q, ans = "%d 时 = ( ) 分" % n, n * 60
                    tip = "%d × 60 = %d 分" % (n, n * 60)
                elif val == 1000:
                    q, ans = "%d 千克 = ( ) 克" % n, n * 1000
                    tip = "%d × 1000 = %d 克" % (n, n * 1000)
                else:
                    q, ans, tip = base, val, tip
            else:
                q, ans, tip = base, val, tip
            if b.fill(level, q, ans, tip=tip):
                made += 1

    def geometry(level, cnt):
        made = guard = 0
        facts = [
            ("长方形有几条边？", 4, [3, 4, 5, 6]),
            ("正方形有几条边？", 4, [3, 4, 5, 6]),
            ("三角形有几条边？", 3, [2, 3, 4, 5]),
            ("一个正方体有几个面？", 6, [4, 5, 6, 8]),
            ("红领巾是什么形状？", "三角形", ["长方形", "正方形", "圆形", "三角形"]),
            ("硬币的正面是什么形状？", "圆形", ["圆形", "方形", "三角形", "椭圆形"]),
            ("数学书的封面通常是什么形状？", "长方形", ["长方形", "正方形", "圆形", "三角形"]),
            ("钟表的表盘通常是什么形状？", "圆形", ["圆形", "方形", "三角形", "五角形"]),
            ("长方形有几个直角？", 4, [2, 3, 4, 6]),
            ("平行四边形有几组对边平行？", 2, [1, 2, 3, 4]),
        ]
        while made < cnt and guard < cnt * 40:
            guard += 1
            q, ans, opts = rnd.choice(facts)
            ds = [o for o in opts if str(o) != str(ans)]
            if b.choice(level, q, ans, ds, tip="正确答案是 %s" % ans):
                made += 1

    def area_perimeter(level, cnt):
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            w, h = rnd.randint(2, 20), rnd.randint(2, 20)
            if rnd.random() < 0.5:
                q = "长方形长 %d 厘米、宽 %d 厘米，面积是 ( ) 平方厘米" % (h, w)
                ans = w * h
                tip = "面积 = 长 × 宽 = %d × %d = %d" % (h, w, ans)
            else:
                q = "长方形长 %d 厘米、宽 %d 厘米，周长是 ( ) 厘米" % (h, w)
                ans = 2 * (w + h)
                tip = "周长 = (长 + 宽) × 2 = (%d + %d) × 2 = %d" % (h, w, ans)
            if b.fill(level, q, ans, tip=tip):
                made += 1

    def fraction(level, cnt):
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            d = rnd.randint(5, 9)  # 分母至少 5，保证能凑够 3 个干扰项
            a = rnd.randint(1, d - 2)
            bb = rnd.randint(1, d - a - 1)
            if rnd.random() < 0.5:
                q = r"%d/%d + %d/%d = ?" % (a, d, bb, d)
                ans = "%d/%d" % (a + bb, d)
                tip = "分母不变，分子相加：%d + %d = %d" % (a, bb, a + bb)
                ok = b.fill(level, q, ans, tip=tip)
            else:
                c = rnd.randint(1, d - 1)
                if c == a:
                    continue
                q = r"%d/%d 和 %d/%d 哪个大？" % (a, d, c, d)
                big = r"%d/%d" % (a, d) if a > c else r"%d/%d" % (c, d)
                small = r"%d/%d" % (c, d) if a > c else r"%d/%d" % (a, d)
                others = [r"%d/%d" % (x, d) for x in range(1, d) if x not in (a, c)]
                rnd.shuffle(others)
                ok = b.choice(level, q, big, [small] + others[:2], tip="分母相同，分子大的分数更大")
            if ok:
                made += 1

    # ---------------- 按年级编排 ----------------
    if grade == 1:
        addsub(1, 9, 1, 70, sub=True, fill_ratio=0.3)
        compare(1, 20, 1, 40)
        blank(1, 9, 1, 40)
        geometry(1, 30)
        pattern(1, 20, step_hi=3)

        addsub(5, 20, 2, 65, sub=True, fill_ratio=0.3)
        blank(3, 15, 2, 40)
        compare(5, 30, 2, 30)
        money(2, 35)
        pattern(2, 30, step_hi=5)

        addsub(10, 50, 3, 60, sub=True, fill_ratio=0.35)
        word_problem([
            lambda: (lambda n, m: ("小明有 %d 支铅笔，用了 %d 支，还剩 ( ) 支" % (n, m), n - m,
                                   "%d − %d = %d" % (n, m, n - m)))(rnd.randint(11, 40), rnd.randint(2, 10)),
            lambda: (lambda n, m: ("树上有 %d 只鸟，又飞来 %d 只，一共 ( ) 只" % (n, m), n + m,
                                   "%d + %d = %d" % (n, m, n + m)))(rnd.randint(10, 40), rnd.randint(3, 20)),
            lambda: (lambda n, m: ("妈妈买了 %d 个苹果，吃掉 %d 个，还剩 ( ) 个" % (n, m), n - m,
                                   "%d − %d = %d" % (n, m, n - m)))(rnd.randint(12, 45), rnd.randint(3, 15)),
            lambda: (lambda n: ("一排有 %d 个小朋友，一共有 2 排，共 ( ) 个" % n, n * 2,
                                "%d × 2 = %d" % (n, n * 2)))(rnd.randint(5, 20)),
        ], 3, 45)
        pattern(3, 40, step_hi=6)
        money(3, 30)
        unit(3, 25)

    elif grade == 2:
        addsub(10, 99, 1, 60, sub=True, fill_ratio=0.3)
        muldiv([2, 3, 4, 5], 1, 60, div=False)
        blank(5, 40, 1, 40)
        compare(10, 99, 1, 25)
        geometry(1, 15)

        muldiv([2, 3, 4, 5, 6, 7, 8, 9], 2, 70, div=True)
        addsub(20, 99, 2, 40, sub=True, fill_ratio=0.35)
        unit(2, 40)
        pattern(2, 25, step_hi=8)
        money(2, 25)

        addsub(50, 500, 3, 45, sub=True, fill_ratio=0.4)
        word_problem([
            lambda: (lambda n, m: ("每盒有 %d 块糖，%d 盒一共 ( ) 块" % (n, m), n * m,
                                   "%d × %d = %d" % (n, m, n * m)))(rnd.randint(3, 9), rnd.randint(3, 9)),
            lambda: (lambda n, m: ("有 %d 个苹果，平均分成 %d 盘，每盘 ( ) 个" % (n * m, m), n,
                                   "%d ÷ %d = %d" % (n * m, m, n)))(rnd.randint(2, 9), rnd.randint(2, 9)),
            lambda: (lambda n, m: ("一本书 %d 页，看了 %d 页，还有 ( ) 页没看" % (n, m), n - m,
                                   "%d − %d = %d" % (n, m, n - m)))(rnd.randint(60, 300), rnd.randint(10, 55)),
            lambda: (lambda n, m, k: ("车上原有 %d 人，下去 %d 人，又上来 %d 人，现在 ( ) 人" % (n, m, k),
                                      n - m + k, "%d − %d + %d = %d" % (n, m, k, n - m + k)))(
                rnd.randint(20, 60), rnd.randint(5, 20), rnd.randint(3, 25)),
        ], 3, 55)
        pattern(3, 40, step_hi=12, hard=True)
        muldiv([6, 7, 8, 9], 3, 35, div=True)
        unit(3, 25)

    else:  # grade 3
        addsub(100, 999, 1, 55, sub=True, fill_ratio=0.35)
        muldiv([2, 3, 4, 5, 6, 7], 1, 55, div=True)
        blank(10, 99, 1, 45)
        unit(1, 30)
        compare(100, 999, 1, 15)

        m = 0
        while m < 55:  # 两位数乘一位数 / 两位数乘两位数（简单）
            a = rnd.randint(11, 99)
            bb = rnd.randint(2, 9)
            if b.fill(2, "%d × %d = ?" % (a, bb), a * bb, tip="%d × %d = %d" % (a, bb, a * bb)):
                m += 1
        m = 0
        while m < 45:  # 除法
            bb = rnd.randint(2, 9)
            c = rnd.randint(11, 99)
            if b.fill(2, "%d ÷ %d = ?" % (bb * c, bb), c, tip="%d ÷ %d = %d" % (bb * c, bb, c)):
                m += 1
        area_perimeter(2, 45)
        fraction(2, 35)
        unit(2, 20)

        m = 0
        while m < 45:  # 两位数乘两位数
            a = rnd.randint(11, 99)
            bb = rnd.randint(11, 50)
            if b.fill(3, "%d × %d = ?" % (a, bb), a * bb, tip="%d × %d = %d" % (a, bb, a * bb)):
                m += 1
        m = 0
        while m < 40:  # 三位数除以一位数
            bb = rnd.randint(3, 9)
            c = rnd.randint(101, 400)
            if b.fill(3, "%d ÷ %d = ?" % (bb * c, bb), c, tip="%d ÷ %d = %d" % (bb * c, bb, c)):
                m += 1
        word_problem([
            lambda: (lambda n, m: ("果园有 %d 棵苹果树，梨树是苹果树的 %d 倍，梨树 ( ) 棵" % (n, m), n * m,
                                   "%d × %d = %d" % (n, m, n * m)))(rnd.randint(12, 99), rnd.randint(2, 9)),
            lambda: (lambda n, m: ("一件衣服 %d 元，买 %d 件要 ( ) 元" % (n, m), n * m,
                                   "%d × %d = %d" % (n, m, n * m)))(rnd.randint(15, 199), rnd.randint(2, 9)),
            lambda: (lambda n, m: ("一条路长 %d 米，已经修了 %d 米，还剩 ( ) 米" % (n, m), n - m,
                                   "%d − %d = %d" % (n, m, n - m)))(rnd.randint(300, 999), rnd.randint(50, 290)),
            lambda: (lambda n, m: ("三年级有 %d 人，每 %d 人一组，可以分 ( ) 组" % (n * m, m), n,
                                   "%d ÷ %d = %d" % (n * m, m, n)))(rnd.randint(4, 30), rnd.randint(3, 9)),
        ], 3, 45)
        area_perimeter(3, 40)
        fraction(3, 30)

    for lv in (1, 2, 3):
        if not b.full(lv):
            math_extra(b, grade, lv, b.need(lv))

    return b.dump()


# ==================================================================== 英语
VOCAB = {
    1: [  # 一年级：颜色、数字、动物、水果、文具、身体、家庭
        ("red", "红色"), ("blue", "蓝色"), ("yellow", "黄色"), ("green", "绿色"),
        ("black", "黑色"), ("white", "白色"), ("orange", "橙色"), ("pink", "粉色"),
        ("one", "一"), ("two", "二"), ("three", "三"), ("four", "四"), ("five", "五"),
        ("six", "六"), ("seven", "七"), ("eight", "八"), ("nine", "九"), ("ten", "十"),
        ("cat", "猫"), ("dog", "狗"), ("pig", "猪"), ("duck", "鸭子"), ("bird", "鸟"),
        ("fish", "鱼"), ("rabbit", "兔子"), ("monkey", "猴子"), ("tiger", "老虎"),
        ("apple", "苹果"), ("banana", "香蕉"), ("pear", "梨"), ("tangerine", "橘子"),
        ("pen", "钢笔"), ("pencil", "铅笔"), ("book", "书"), ("bag", "书包"),
        ("ruler", "尺子"), ("eraser", "橡皮"), ("desk", "课桌"), ("chair", "椅子"),
        ("eye", "眼睛"), ("ear", "耳朵"), ("nose", "鼻子"), ("mouth", "嘴巴"),
        ("hand", "手"), ("foot", "脚"), ("head", "头"), ("face", "脸"),
        ("father", "爸爸"), ("mother", "妈妈"), ("teacher", "老师"), ("friend", "朋友"),
        ("boy", "男孩"), ("girl", "女孩"), ("name", "名字"), ("school", "学校"),
        ("sun", "太阳"), ("moon", "月亮"), ("star", "星星"), ("tree", "树"),
        ("water", "水"), ("milk", "牛奶"), ("egg", "鸡蛋"), ("cake", "蛋糕"),
        ("car", "汽车"), ("bus", "公共汽车"), ("bike", "自行车"), ("ball", "球"),
        ("door", "门"), ("window", "窗户"), ("bed", "床"), ("clock", "钟"),
    ],
    2: [  # 二年级：食物、衣物、天气、交通、房间、动作
        ("rice", "米饭"), ("noodles", "面条"), ("bread", "面包"), ("soup", "汤"),
        ("meat", "肉"), ("juice", "果汁"), ("tea", "茶"), ("coffee", "咖啡"),
        ("coat", "外套"), ("shirt", "衬衫"), ("shoes", "鞋"), ("hat", "帽子"),
        ("socks", "袜子"), ("dress", "连衣裙"), ("pants", "裤子"), ("gloves", "手套"),
        ("sunny", "晴朗的"), ("rainy", "下雨的"), ("windy", "有风的"), ("cloudy", "多云的"),
        ("snowy", "下雪的"), ("hot", "热的"), ("cold", "冷的"), ("warm", "温暖的"),
        ("train", "火车"), ("plane", "飞机"), ("ship", "轮船"), ("taxi", "出租车"),
        ("bedroom", "卧室"), ("kitchen", "厨房"), ("bathroom", "卫生间"), ("garden", "花园"),
        ("run", "跑"), ("jump", "跳"), ("swim", "游泳"), ("sing", "唱歌"),
        ("dance", "跳舞"), ("draw", "画画"), ("read", "读"), ("write", "写"),
        ("eleven", "十一"), ("twelve", "十二"), ("thirteen", "十三"), ("fifteen", "十五"),
        ("twenty", "二十"), ("thirty", "三十"), ("fifty", "五十"), ("hundred", "一百"),
        ("cow", "奶牛"), ("horse", "马"), ("sheep", "绵羊"), ("chicken", "小鸡"),
        ("flower", "花"), ("grass", "草"), ("leaf", "叶子"), ("river", "河流"),
        ("spring", "春天"), ("summer", "夏天"), ("autumn", "秋天"), ("winter", "冬天"),
        ("happy", "开心的"), ("sad", "伤心的"), ("big", "大的"), ("small", "小的"),
        ("long", "长的"), ("short", "短的"), ("new", "新的"), ("old", "旧的"),
        ("Monday", "星期一"), ("Friday", "星期五"), ("Sunday", "星期日"), ("today", "今天"),
    ],
    3: [  # 三年级：星期、月份、学科、职业、地点、形容词、动词短语
        ("Tuesday", "星期二"), ("Wednesday", "星期三"), ("Thursday", "星期四"),
        ("Saturday", "星期六"), ("week", "星期"), ("month", "月份"), ("year", "年"),
        ("January", "一月"), ("March", "三月"), ("May", "五月"), ("July", "七月"),
        ("October", "十月"), ("December", "十二月"), ("birthday", "生日"),
        ("Chinese", "语文"), ("maths", "数学"), ("science", "科学"), ("music", "音乐"),
        ("art", "美术"), ("history", "历史"), ("lesson", "课"), ("homework", "作业"),
        ("doctor", "医生"), ("nurse", "护士"), ("driver", "司机"), ("farmer", "农民"),
        ("cook", "厨师"), ("police", "警察"), ("worker", "工人"), ("singer", "歌手"),
        ("hospital", "医院"), ("library", "图书馆"), ("museum", "博物馆"), ("park", "公园"),
        ("zoo", "动物园"), ("market", "市场"), ("airport", "机场"), ("station", "车站"),
        ("beautiful", "美丽的"), ("clever", "聪明的"), ("quiet", "安静的"), ("busy", "忙碌的"),
        ("tired", "累的"), ("hungry", "饿的"), ("thirsty", "渴的"), ("strong", "强壮的"),
        ("get up", "起床"), ("go home", "回家"), ("have lunch", "吃午饭"), ("play football", "踢足球"),
        ("watch TV", "看电视"), ("listen to", "听"), ("look for", "寻找"), ("put on", "穿上"),
        ("answer", "回答"), ("question", "问题"), ("story", "故事"), ("letter", "信"),
        ("picture", "图画"), ("animal", "动物"), ("country", "国家"), ("city", "城市"),
        ("weather", "天气"), ("season", "季节"), ("minute", "分钟"), ("hour", "小时"),
        ("easy", "容易的"), ("difficult", "困难的"), ("early", "早的"), ("late", "迟的"),
    ],
}

SENTENCES = {
    1: [
        ("Hello!", "你好！", ["再见！", "谢谢！", "对不起！"]),
        ("Good morning!", "早上好！", ["晚上好！", "再见！", "谢谢！"]),
        ("Thank you!", "谢谢你！", ["对不起！", "你好！", "再见！"]),
        ("How are you?", "你好吗？", ["你几岁？", "你是谁？", "你在哪？"]),
        ("What's your name?", "你叫什么名字？", ["你几岁了？", "这是什么？", "你在哪里？"]),
        ("Goodbye!", "再见！", ["你好！", "谢谢！", "对不起！"]),
        ("I'm sorry.", "对不起。", ["谢谢你。", "早上好。", "没关系。"]),
        ("This is a cat.", "这是一只猫。", ["这是一只狗。", "这是一本书。", "这是一辆车。"]),
    ],
    2: [
        ("How old are you?", "你几岁了？", ["你好吗？", "你叫什么？", "这是哪里？"]),
        ("It's time for school.", "该上学了。", ["该睡觉了。", "该吃饭了。", "该回家了。"]),
        ("I like apples.", "我喜欢苹果。", ["我有苹果。", "这是苹果。", "我想要苹果。"]),
        ("Let's go home.", "我们回家吧。", ["我们去学校。", "我们吃饭吧。", "我们睡觉吧。"]),
        ("What colour is it?", "它是什么颜色？", ["它是多大？", "它是谁的？", "它在哪里？"]),
        ("Can you swim?", "你会游泳吗？", ["你在游泳吗？", "你喜欢游泳吗？", "你去游泳吗？"]),
        ("It's sunny today.", "今天天晴。", ["今天下雨。", "今天很冷。", "今天有风。"]),
        ("I have two books.", "我有两本书。", ["我想要两本书。", "这里有两本书。", "我买了两本书。"]),
    ],
    3: [
        ("What day is it today?", "今天星期几？", ["今天几号？", "今天什么天气？", "现在几点？"]),
        ("I get up at seven.", "我七点起床。", ["我七点睡觉。", "我七点回家。", "我七点吃饭。"]),
        ("She is a doctor.", "她是一名医生。", ["她是一名老师。", "她是一名护士。", "她是一名司机。"]),
        ("We have maths on Monday.", "我们星期一有数学课。", ["我们星期一有语文课。", "我们星期一去公园。", "我们星期一考试。"]),
        ("It's time to have lunch.", "该吃午饭了。", ["该起床了。", "该上学了。", "该写作业了。"]),
        ("He is taller than me.", "他比我高。", ["他比我矮。", "他和我一样高。", "他最高。"]),
        ("There is a book on the desk.", "桌上有一本书。", ["桌上有两本书。", "桌上没有书。", "书在桌子里。"]),
        ("I went to the zoo yesterday.", "我昨天去了动物园。", ["我明天去动物园。", "我常去动物园。", "我不去动物园。"]),
    ],
}


# ---------- 补充题型：基础题型组合空间用尽后，用这些把每档补满 200 ----------
GROUPS = {
    "动物": ["cat", "dog", "pig", "duck", "bird", "fish", "rabbit", "monkey", "tiger", "cow", "horse", "sheep", "chicken"],
    "水果": ["apple", "banana", "pear", "tangerine"],
    "颜色": ["red", "blue", "yellow", "green", "black", "white", "pink", "orange"],
    "数字": ["one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"],
    "身体部位": ["eye", "ear", "nose", "mouth", "hand", "foot", "head", "face"],
    "学习用品": ["pen", "pencil", "book", "bag", "ruler", "eraser", "desk", "chair"],
    "人物": ["father", "mother", "teacher", "friend", "boy", "girl"],
    "食物饮料": ["rice", "noodles", "bread", "soup", "meat", "juice", "milk", "egg", "cake", "water"],
    "衣物": ["coat", "shirt", "shoes", "hat", "socks", "dress", "pants", "gloves"],
    "天气": ["sunny", "rainy", "windy", "cloudy", "snowy", "hot", "cold", "warm"],
    "交通工具": ["car", "bus", "bike", "train", "plane", "ship", "taxi"],
    "季节": ["spring", "summer", "autumn", "winter"],
    "星期": ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"],
    "职业": ["doctor", "nurse", "driver", "farmer", "cook", "police", "worker", "singer"],
    "学科": ["Chinese", "maths", "science", "music", "art", "history"],
    "场所": ["hospital", "library", "museum", "park", "zoo", "market", "airport", "station", "school", "garden"],
}


def _build_en2cn():
    m = {}
    for ws in VOCAB.values():
        for en, cn in ws:
            m.setdefault(en.replace("2", ""), cn)
    return m


EN2CN = _build_en2cn()

DIALOGUES = [
    ("— How are you?", "I'm fine, thank you.", ["I'm nine.", "My name is Tom.", "It's a cat."]),
    ("— Thank you!", "You're welcome.", ["Goodbye!", "Good morning!", "I'm sorry."]),
    ("— Good morning!", "Good morning!", ["Good night!", "Thank you!", "See you tomorrow!"]),
    ("— What's your name?", "My name is Tom.", ["I'm nine.", "It's red.", "I'm fine."]),
    ("— Nice to meet you.", "Nice to meet you, too.", ["Goodbye!", "Thank you!", "You're welcome."]),
    ("— How old are you?", "I'm nine.", ["My name is Tom.", "It's a book.", "I'm fine."]),
    ("— Can you swim?", "Yes, I can.", ["Yes, it is.", "It's a fish.", "I'm nine."]),
    ("— Let's go to school.", "OK, let's go.", ["Good night!", "You're welcome.", "I'm sorry."]),
    ("— Goodbye!", "Bye! See you.", ["Good morning!", "Thank you!", "How are you?"]),
    ("— I'm sorry.", "That's OK.", ["Good morning!", "Nice to meet you.", "You're welcome."]),
    ("— Happy birthday!", "Thank you!", ["Goodbye!", "I'm nine.", "You're welcome."]),
    ("— What colour is it?", "It's red.", ["It's a cat.", "I'm fine.", "My name is Tom."]),
]

BE_VERB = [
    ("I ___ a student.", "am", ["is", "are", "be"]),
    ("He ___ my father.", "is", ["am", "are", "be"]),
    ("She ___ my sister.", "is", ["am", "are", "be"]),
    ("They ___ good friends.", "are", ["am", "is", "be"]),
    ("We ___ in Class One.", "are", ["am", "is", "be"]),
    ("It ___ a cat.", "is", ["am", "are", "be"]),
    ("You ___ my teacher.", "are", ["am", "is", "be"]),
    ("My name ___ Tom.", "is", ["am", "are", "be"]),
]

PRONOUN = [
    ("This is my mother. ___ is a teacher.", "She", ["He", "It", "They"]),
    ("Tom is a boy. ___ is nine.", "He", ["She", "It", "They"]),
    ("I have a cat. ___ is white.", "It", ["He", "She", "They"]),
    ("My father is a doctor. ___ works in a hospital.", "He", ["She", "It", "They"]),
    ("Amy is my friend. ___ likes singing.", "She", ["He", "It", "They"]),
    ("Look at the birds. ___ are flying.", "They", ["He", "She", "It"]),
]

PREPOSITION = [
    ("The book is ___ the desk.", "on", ["in", "under", "at"]),
    ("The cat is ___ the box.", "in", ["on", "under", "at"]),
    ("The ball is ___ the chair.", "under", ["on", "in", "at"]),
    ("The pen is ___ my bag.", "in", ["on", "under", "at"]),
    ("There is a picture ___ the wall.", "on", ["in", "under", "at"]),
    ("The shoes are ___ the bed.", "under", ["on", "in", "at"]),
]

OPPOSITES_EN = [
    ("big", "small"), ("small", "big"), ("long", "short"), ("short", "long"),
    ("new", "old"), ("old", "new"), ("hot", "cold"), ("cold", "hot"),
    ("happy", "sad"), ("sad", "happy"), ("early", "late"), ("late", "early"),
    ("easy", "difficult"), ("difficult", "easy"), ("strong", "weak"), ("weak", "strong"),
]

NUM_WORDS = {"one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7,
             "eight": 8, "nine": 9, "ten": 10, "eleven": 11, "twelve": 12, "thirteen": 13,
             "fifteen": 15, "twenty": 20, "thirty": 30, "fifty": 50, "hundred": 100}
REV_NUM = {v: k for k, v in NUM_WORDS.items()}


def eng_extra(b, level, budget):
    """用更多样的题型把某一档补满"""
    guard = 0
    limit = budget * 150
    while not b.full(level) and guard < limit:
        guard += 1
        kind = guard % 7
        ok = False
        if kind == 0:
            ga, gb = rnd.sample(list(GROUPS.keys()), 2)
            three = rnd.sample(GROUPS[ga], 3)
            one = rnd.choice(GROUPS[gb])
            if one not in three:
                stem = rnd.choice([
                    "选出不同类的单词",
                    "下面哪个单词和其他三个不是一类？",
                    "找出不属于同一类的那一个",
                    "圈出不同类的单词",
                ])
                ok = b.choice(level, stem, one, three,
                              tip="%s 属于「%s」，其余三个属于「%s」" % (EN2CN.get(one, one), gb, ga))
        elif kind == 1:
            q, ans, wrong = rnd.choice(DIALOGUES)
            ok = b.choice(level, "选择合适的答语：%s" % q, ans, wrong, tip="%s → %s" % (q, ans))
        elif kind == 2:
            q, ans, wrong = rnd.choice(BE_VERB)
            ok = b.choice(level, "选择正确的 be 动词：%s" % q, ans, wrong, tip="正确答案是 %s" % ans)
        elif kind == 3:
            q, ans, wrong = rnd.choice(PRONOUN)
            ok = b.choice(level, "选择正确的代词：%s" % q, ans, wrong, tip="正确答案是 %s" % ans)
        elif kind == 4:
            q, ans, wrong = rnd.choice(PREPOSITION)
            ok = b.choice(level, "选择正确的介词：%s" % q, ans, wrong, tip="正确答案是 %s" % ans)
        elif kind == 5:
            en, cn = rnd.choice(OPPOSITES_EN)
            pool = [v for _, v in OPPOSITES_EN if v != cn]
            rnd.shuffle(pool)
            ok = b.choice(level, "%s（%s）的反义词是？" % (en, EN2CN.get(en, en)), cn, pool[:3],
                          tip="%s 的反义词是 %s" % (en, cn))
        else:
            a = rnd.choice(list(NUM_WORDS.items()))
            bb = rnd.choice(list(NUM_WORDS.items()))
            s = a[1] + bb[1]
            if s in REV_NUM:
                ok = b.fill(level, "%s + %s = ?（用英文单词回答）" % (a[0], bb[0]), REV_NUM[s],
                            tip="%d + %d = %d，写作 %s" % (a[1], bb[1], s, REV_NUM[s]))


def math_extra(b, grade, level, budget):
    """数学补题：数的组成、相邻数、排序、时间、排队问题、判断"""
    guard = 0
    limit = budget * 200
    while not b.full(level) and guard < limit:
        guard += 1
        kind = guard % 6
        ok = False
        if kind == 0:  # 数的组成
            if grade == 1:
                n = rnd.randint(11, 99)
                ans = "%d个十和%d个一" % (n // 10, n % 10)
                ok = b.fill(level, "%d 里面有 ( ) 个十和 ( ) 个一" % n, ans,
                            tip="%d 的十位是 %d，个位是 %d" % (n, n // 10, n % 10))
            else:
                n = rnd.randint(101, 999)
                ans = "%d个百%d个十%d个一" % (n // 100, (n % 100) // 10, n % 10)
                ok = b.fill(level, "%d 里面有 ( ) 个百、( ) 个十和 ( ) 个一" % n, ans,
                            tip="%d 的数位：百位 %d、十位 %d、个位 %d" % (n, n // 100, (n % 100) // 10, n % 10))
        elif kind == 1:  # 相邻数 / 最大最小
            if rnd.random() < 0.5:
                n = rnd.randint(10, 998)
                ok = b.fill(level, "与 %d 相邻的两个数是 ( ) 和 ( )" % n, "%d和%d" % (n - 1, n + 1),
                            tip="前一个数是 %d，后一个数是 %d" % (n - 1, n + 1))
            else:
                a, bb, c = rnd.sample(range(1, 1000), 3)
                ok = b.fill(level, "%d、%d、%d 中最大的数是 ( )" % (a, bb, c), max(a, bb, c),
                            tip="三个数比较，最大的是 %d" % max(a, bb, c))
        elif kind == 2:  # 排序
            hi = 100 if grade < 3 else 1000
            nums = rnd.sample(range(1, hi), 3)
            ans = "、".join(str(x) for x in sorted(nums))
            ok = b.fill(level, "把 %s 按从小到大的顺序排列" % "、".join(str(x) for x in nums), ans,
                        tip="从小到大：%s" % ans)
        elif kind == 3:  # 时间
            h = rnd.randint(1, 12)
            m = rnd.choice([0, 5, 10, 15, 20, 25, 30, 35, 40, 45])
            if rnd.random() < 0.5:
                ok = b.fill(level, "%d 时过 %d 分是 ( )（写成 几时几分）" % (h, m), "%d时%d分" % (h, m),
                            tip="时针过了 %d，分针走了 %d 分" % (h, m))
            else:
                dur = rnd.randint(5, 50)
                total = h * 60 + m + dur
                hh = (total // 60) % 12 or 12
                ok = b.fill(level, "从 %d 时 %d 分开始，过了 %d 分钟，是 ( )（写成 几时几分）" % (h, m, dur),
                            "%d时%d分" % (hh, total % 60), tip="分钟相加，满 60 进 1 小时")
        elif kind == 4:  # 排队 / 间隔
            if rnd.random() < 0.5:
                a, bb = rnd.randint(3, 20), rnd.randint(2, 15)
                ok = b.fill(level, "小朋友排队，从前数小明是第 %d 个，从后数是第 %d 个，一共 ( ) 人" % (a, bb),
                            a + bb - 1, tip="小明被数了两次：%d + %d − 1 = %d" % (a, bb, a + bb - 1))
            else:
                a = rnd.randint(2, 15)
                ok = b.fill(level, "一根绳子剪 %d 次，能剪成 ( ) 段" % a, a + 1,
                            tip="段数比剪的次数多 1：%d + 1 = %d" % (a, a + 1))
        else:  # 填符号 / 判断对错
            a, bb = rnd.randint(2, 50), rnd.randint(1, 30)
            if rnd.random() < 0.5:
                ok = b.fill(level, "在 ○ 里填上 + 或 −：%d ○ %d = %d" % (a, bb, a + bb), "+",
                            tip="%d + %d = %d" % (a, bb, a + bb))
            else:
                s = a + bb
                if rnd.random() < 0.5:
                    shown, ans = s, "对"
                else:
                    shown, ans = s + rnd.choice([1, 2, 10, -1]), "错"
                ok = b.choice(level, "判断：%d + %d = %d" % (a, bb, shown), ans, ["对", "错"],
                              tip="%d + %d = %d" % (a, bb, s))


def gen_english(grade):
    b = Bank("english", grade)
    words = VOCAB[grade]
    sents = SENTENCES[grade]

    def cn2en(level, cnt):
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            en, cn = rnd.choice(words)
            others = [w[0] for w in rnd.sample(words, 4) if w[0] != en][:3]
            if len(others) < 3:
                continue
            if b.choice(level, "「%s」的英文是？" % cn, en, others, tip="%s = %s" % (cn, en)):
                made += 1

    def en2cn(level, cnt):
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            en, cn = rnd.choice(words)
            others = [w[1] for w in rnd.sample(words, 4) if w[1] != cn][:3]
            if len(others) < 3:
                continue
            if b.choice(level, "%s 的中文意思是？" % en, cn, others, tip="%s = %s" % (en, cn)):
                made += 1

    def spelling(level, cnt):
        """补全单词：a_ple → apple"""
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            en, cn = rnd.choice(words)
            en = en.replace("2", "")
            if len(en) < 3:
                continue
            pos = rnd.randint(1, len(en) - 1)
            shown = en[:pos] + "_" + en[pos + 1:]
            if b.fill(level, "补全单词（%s）：%s" % (cn, shown), en, tip="%s 拼写为 %s" % (cn, en)):
                made += 1

    def first_letter(level, cnt):
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            en, cn = rnd.choice(words)
            en = en.replace("2", "")
            q = "「%s」的英文单词以哪个字母开头？" % cn
            others = [c for c in "abcdefghijklmnopqrstuvwxyz" if c != en[0]]
            rnd.shuffle(others)
            if b.choice(level, q, en[0].upper(), [o.upper() for o in others[:3]],
                        tip="%s = %s，首字母是 %s" % (cn, en, en[0].upper())):
                made += 1

    def sentence(level, cnt):
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            en, cn, wrong = rnd.choice(sents)
            if b.choice(level, "%s 的意思是？" % en, cn, wrong, tip="%s 意思是「%s」" % (en, cn)):
                made += 1

    def sentence_back(level, cnt):
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            en, cn, wrong = rnd.choice(sents)
            pool = [s[0] for s in sents if s[0] != en][:3]
            if len(pool) < 3:
                pool = wrong
            if b.choice(level, "「%s」用英语怎么说？" % cn, en, pool, tip="「%s」= %s" % (cn, en)):
                made += 1

    def letters(level, cnt):
        """字母顺序 / 大小写，主要一年级"""
        made = guard = 0
        upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        while made < cnt and guard < cnt * 60:
            guard += 1
            kind = rnd.randint(1, 3)
            if kind == 1:
                i = rnd.randint(0, 23)
                q = "按字母表顺序，%s 后面是？" % upper[i]
                if b.choice(level, q, upper[i + 1], [upper[i], upper[(i + 2) % 26], upper[(i + 3) % 26]],
                            tip="字母表顺序：%s → %s" % (upper[i], upper[i + 1])):
                    made += 1
            elif kind == 2:
                i = rnd.randint(1, 25)
                q = "按字母表顺序，%s 前面是？" % upper[i]
                if b.choice(level, q, upper[i - 1], [upper[i], upper[(i + 1) % 26], upper[(i + 2) % 26]],
                            tip="字母表顺序：%s → %s" % (upper[i - 1], upper[i])):
                    made += 1
            else:
                c = rnd.choice(upper)
                q = "字母 %s 的小写是？" % c
                if b.choice(level, q, c.lower(), [c, c.lower() + c.lower(), rnd.choice(upper).lower()],
                            tip="%s 的小写是 %s" % (c, c.lower())):
                    made += 1

    def plural(level, cnt):
        """名词复数，主要三年级"""
        pairs = [("book", "books"), ("pen", "pens"), ("cat", "cats"), ("dog", "dogs"),
                 ("box", "boxes"), ("bus", "buses"), ("baby", "babies"), ("city", "cities"),
                 ("leaf", "leaves"), ("knife", "knives"), ("child", "children"), ("foot", "feet"),
                 ("tooth", "teeth"), ("man", "men"), ("woman", "women"), ("sheep", "sheep")]
        made = guard = 0
        while made < cnt and guard < cnt * 60:
            guard += 1
            s, p = rnd.choice(pairs)
            others = [x[1] for x in rnd.sample(pairs, 4) if x[1] != p][:3]
            if len(others) < 3:
                continue
            if b.choice(level, "%s 的复数形式是？" % s, p, others, tip="%s 的复数是 %s" % (s, p)):
                made += 1

    if grade == 1:
        cn2en(1, 55); en2cn(1, 55); letters(1, 60); first_letter(1, 30)
        cn2en(2, 50); en2cn(2, 50); sentence(2, 50); spelling(2, 50)
        cn2en(3, 45); en2cn(3, 45); sentence(3, 45); sentence_back(3, 35); spelling(3, 30)
    elif grade == 2:
        cn2en(1, 50); en2cn(1, 50); first_letter(1, 40); sentence(1, 60)
        cn2en(2, 45); en2cn(2, 45); spelling(2, 55); sentence(2, 55)
        cn2en(3, 40); en2cn(3, 40); sentence_back(3, 45); spelling(3, 45); plural(3, 30)
    else:
        cn2en(1, 45); en2cn(1, 45); sentence(1, 55); sentence_back(1, 55)
        cn2en(2, 40); en2cn(2, 40); spelling(2, 55); sentence_back(2, 65)
        cn2en(3, 35); en2cn(3, 35); spelling(3, 45); plural(3, 50); sentence_back(3, 35)

    for lv in (1, 2, 3):
        if not b.full(lv):
            eng_extra(b, lv, b.need(lv))

    return b.dump()


# ==================================================================== 主流程
GENERATORS = {"math": gen_math, "english": gen_english}


def main():
    only = sys.argv[1:] if len(sys.argv) > 1 else list(GENERATORS.keys())
    summary = []
    for subj, fn in GENERATORS.items():
        if subj not in only:
            continue
        for g in (1, 2, 3):
            data = fn(g)
            summary.append((subj, g, data["total"], data["levels"]))
            print("%-8s g%d  总 %4d  (简单 %3d / 中等 %3d / 困难 %3d)" % (
                subj, g, data["total"],
                data["levels"]["1"], data["levels"]["2"], data["levels"]["3"]))
    tot = sum(s[2] for s in summary)
    print("-" * 60)
    print("合计 %d 题" % tot)


if __name__ == "__main__":
    main()
