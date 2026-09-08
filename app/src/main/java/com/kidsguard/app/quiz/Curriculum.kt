package com.kidsguard.app.quiz

import com.kidsguard.app.data.Difficulty
import com.kidsguard.app.data.QUESTIONS_PER_GRADE
import com.kidsguard.app.data.Subject

/**
 * 人教版教学顺序表。
 *
 * 背景：原来三个生成器都是 `index % 5` 轮换题型，孩子做第 1 题是加法、
 * 第 2 题是减法、第 3 题是比大小……知识点乱跳，跟课本对不上。
 *
 * 这里把每个年级的 600 道题切成若干**单元**，单元顺序照人教版课本排，
 * 于是「练习模式从头往后做」= 「跟着课本学」。
 *
 * 难度分两级：
 * 1. 单元之间递进 —— 上学期的单元在前，下学期在后
 * 2. 单元内部爬坡 —— 每个单元最后 1/4 的题自动抬一档难度
 *
 * 重要：本文件**只决定"出哪道题"**，不出题面。所有题目仍由原来的
 * MathGenerator / ChineseGenerator / EnglishGenerator 生成，所以不存在改错答案的风险。
 */

/** 一个学习单元 */
private data class UnitBlock(
    val name: String,
    val difficulty: Difficulty,
    val topics: List<Int>,
    val count: Int,
    /** 英语专用：本单元考哪几组词（下标指向 EnglishGenerator 的 GROUPS）；其它科目留空 */
    val groups: List<Int> = emptyList()
)

/** 600 题中的一题：属于哪个单元、什么难度、哪种题型 */
data class Slot(
    val unit: String,
    val difficulty: Difficulty,
    val type: Int,
    val groups: List<Int> = emptyList()
)

// ==================== 单元顺序表 ====================

// ---------- 数学：人教版 一年级（上/下）----------
private val MATH_G1 = listOf(
    UnitBlock("准备课：数一数与比多少", Difficulty.EASY, listOf(2, 4), 30),
    UnitBlock("1~5 的认识和加减法", Difficulty.EASY, listOf(0, 1, 3), 60),
    UnitBlock("6~10 的认识和加减法", Difficulty.EASY, listOf(0, 1, 3, 4), 60),
    UnitBlock("11~20 各数的认识", Difficulty.EASY, listOf(4, 2), 50),
    UnitBlock("20 以内的进位加法", Difficulty.MEDIUM, listOf(0, 3), 70),
    UnitBlock("20 以内的退位减法", Difficulty.MEDIUM, listOf(1, 4), 70),
    UnitBlock("100 以内数的认识", Difficulty.HARD, listOf(1, 0), 80),
    UnitBlock("100 以内的加减法", Difficulty.HARD, listOf(0, 2, 3), 100),
    UnitBlock("连加连减与应用题", Difficulty.MEDIUM, listOf(2, 4), 40),
    UnitBlock("期末综合复习", Difficulty.HARD, listOf(4, 2, 0), 40)
)

// ---------- 数学：人教版 二年级（上/下）----------
private val MATH_G2 = listOf(
    UnitBlock("100 以内的加减法（二）", Difficulty.EASY, listOf(0, 1, 2, 3), 80),
    UnitBlock("表内乘法（一）", Difficulty.MEDIUM, listOf(0, 2, 3), 90),
    UnitBlock("表内乘法（二）", Difficulty.MEDIUM, listOf(0, 4, 3), 80),
    UnitBlock("观察图形与角的初步认识", Difficulty.EASY, listOf(3, 4), 40),
    UnitBlock("表内除法（一）", Difficulty.MEDIUM, listOf(1, 2), 90),
    UnitBlock("表内除法（二）", Difficulty.MEDIUM, listOf(1, 3, 4), 70),
    UnitBlock("有余数的除法", Difficulty.HARD, listOf(1, 3), 50),
    UnitBlock("混合运算", Difficulty.HARD, listOf(2, 3, 0), 50),
    UnitBlock("长度单位与测量", Difficulty.HARD, listOf(4), 30),
    UnitBlock("期末综合复习", Difficulty.HARD, listOf(0, 2), 20)
)

// ---------- 数学：人教版 三年级（上/下）----------
private val MATH_G3 = listOf(
    UnitBlock("万以内的加减法", Difficulty.EASY, listOf(0, 1, 4), 90),
    UnitBlock("多位数乘一位数", Difficulty.EASY, listOf(2, 3), 80),
    UnitBlock("倍的认识", Difficulty.MEDIUM, listOf(3), 40),
    UnitBlock("长方形与正方形（周长）", Difficulty.MEDIUM, listOf(4), 60),
    UnitBlock("分数的初步认识", Difficulty.MEDIUM, listOf(2), 60),
    UnitBlock("除数是一位数的除法", Difficulty.MEDIUM, listOf(1, 0), 90),
    UnitBlock("混合运算与括号", Difficulty.HARD, listOf(0, 1, 4), 80),
    UnitBlock("面积", Difficulty.HARD, listOf(2), 50),
    UnitBlock("分数的大小比较", Difficulty.HARD, listOf(3), 30),
    UnitBlock("期末综合复习", Difficulty.HARD, listOf(4, 0, 2), 20)
)

// ---------- 语文：人教版 一年级 ----------
private val CN_G1 = listOf(
    UnitBlock("拼音王国", Difficulty.EASY, listOf(0), 100),
    UnitBlock("字词积累：反义词", Difficulty.EASY, listOf(1), 90),
    UnitBlock("正确使用量词", Difficulty.EASY, listOf(2), 90),
    UnitBlock("汉字游戏：加一笔", Difficulty.EASY, listOf(3), 90),
    UnitBlock("古诗积累", Difficulty.EASY, listOf(4), 100),
    UnitBlock("综合练习（一）", Difficulty.MEDIUM, listOf(0, 1, 2, 3, 4), 80),
    UnitBlock("综合提升（二）", Difficulty.HARD, listOf(0, 1, 2, 3, 4), 50)
)

// ---------- 语文：人教版 二年级 ----------
private val CN_G2 = listOf(
    UnitBlock("字词基础", Difficulty.EASY, listOf(0, 1, 2, 3, 4), 120),
    UnitBlock("多音字", Difficulty.MEDIUM, listOf(0), 80),
    UnitBlock("词语搭配", Difficulty.MEDIUM, listOf(2), 80),
    UnitBlock("成语积累", Difficulty.MEDIUM, listOf(1), 90),
    UnitBlock("成语理解", Difficulty.HARD, listOf(1), 70),
    UnitBlock("修辞手法", Difficulty.HARD, listOf(2), 60),
    UnitBlock("多音字巩固", Difficulty.HARD, listOf(3), 50),
    UnitBlock("综合提升", Difficulty.HARD, listOf(0, 1, 2, 3, 4), 50)
)

// ---------- 语文：人教版 三年级 ----------
private val CN_G3 = listOf(
    UnitBlock("字词与积累", Difficulty.EASY, listOf(0, 1, 2, 3, 4), 130),
    UnitBlock("词语运用", Difficulty.MEDIUM, listOf(0, 1, 2, 3, 4), 110),
    UnitBlock("关联词", Difficulty.MEDIUM, listOf(1), 70),
    UnitBlock("古诗理解", Difficulty.HARD, listOf(3), 70),
    UnitBlock("阅读与表达", Difficulty.HARD, listOf(0, 1, 2, 4), 120),
    UnitBlock("综合拔高", Difficulty.HARD, listOf(0, 1, 2, 3, 4), 100)
)

// ---------- 英语：一年级（数字 / 颜色 / 动物 / 文具 / 家庭 / 水果）----------
private val EN_G1 = listOf(
    UnitBlock("Unit 1 Numbers", Difficulty.EASY, listOf(0, 1, 2, 3, 4), 80, listOf(0)),
    UnitBlock("Unit 2 Colours", Difficulty.EASY, listOf(0, 1, 2, 3, 4), 80, listOf(1)),
    UnitBlock("Unit 3 Animals", Difficulty.EASY, listOf(0, 1, 2, 3, 4), 80, listOf(2)),
    UnitBlock("Unit 4 Stationery", Difficulty.MEDIUM, listOf(0, 1, 2, 3, 4), 80, listOf(3)),
    UnitBlock("Unit 5 Family", Difficulty.MEDIUM, listOf(0, 1, 2, 3, 4), 80, listOf(4)),
    UnitBlock("Unit 6 Fruit", Difficulty.MEDIUM, listOf(0, 1, 2, 3, 4), 80, listOf(5)),
    UnitBlock("Revision 复习", Difficulty.HARD, listOf(0, 1, 2, 3, 4), 120, listOf(0, 1, 2, 3, 4, 5))
)

// ---------- 英语：二年级 ----------
private val EN_G2 = listOf(
    UnitBlock("Unit 1 Family", Difficulty.EASY, listOf(0, 1, 2, 3, 4), 65, listOf(4)),
    UnitBlock("Unit 2 Fruit", Difficulty.EASY, listOf(0, 1, 2, 3, 4), 65, listOf(5)),
    UnitBlock("Unit 3 Food", Difficulty.EASY, listOf(0, 1, 2, 3, 4), 65, listOf(6)),
    UnitBlock("Unit 4 Body", Difficulty.MEDIUM, listOf(0, 1, 2, 3, 4), 65, listOf(7)),
    UnitBlock("Unit 5 Clothes", Difficulty.MEDIUM, listOf(0, 1, 2, 3, 4), 65, listOf(8)),
    UnitBlock("Unit 6 Weather", Difficulty.MEDIUM, listOf(0, 1, 2, 3, 4), 65, listOf(9)),
    UnitBlock("Unit 7 Places", Difficulty.MEDIUM, listOf(0, 1, 2, 3, 4), 65, listOf(10)),
    UnitBlock("Unit 8 Actions", Difficulty.HARD, listOf(0, 1, 2, 3, 4), 65, listOf(11)),
    UnitBlock("Revision 复习", Difficulty.HARD, listOf(0, 1, 2, 3, 4), 80, listOf(4, 5, 6, 7, 8, 9, 10, 11))
)

// ---------- 英语：三年级 ----------
private val EN_G3 = listOf(
    UnitBlock("Unit 1 基础词汇", Difficulty.EASY, listOf(0, 1, 2, 3, 4), 90, listOf(0, 1, 2, 3)),
    UnitBlock("Unit 2 生活词汇", Difficulty.EASY, listOf(0, 1, 2, 3, 4), 90, listOf(4, 5, 6, 7)),
    UnitBlock("Unit 3 拓展词汇", Difficulty.MEDIUM, listOf(0, 1, 2, 3, 4), 90, listOf(8, 9, 10, 11)),
    UnitBlock("Unit 4 应用词汇", Difficulty.MEDIUM, listOf(0, 1, 2, 3, 4), 90, listOf(12, 13, 14, 15)),
    UnitBlock("Unit 5 词汇综合", Difficulty.HARD, listOf(0, 1, 2, 3, 4), 120, listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)),
    UnitBlock("Unit 6 常用句型", Difficulty.HARD, listOf(0, 1, 2, 3, 4), 120, listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))
)

// ==================== 展开 ====================

object Curriculum {

    private val MATH_PLAN: Map<Int, List<Slot>> =
        mapOf(1 to expand(MATH_G1), 2 to expand(MATH_G2), 3 to expand(MATH_G3))
    private val CHINESE_PLAN: Map<Int, List<Slot>> =
        mapOf(1 to expand(CN_G1), 2 to expand(CN_G2), 3 to expand(CN_G3))
    private val ENGLISH_PLAN: Map<Int, List<Slot>> =
        mapOf(1 to expand(EN_G1), 2 to expand(EN_G2), 3 to expand(EN_G3))

    fun mathSlots(grade: Int): List<Slot> = MATH_PLAN[grade] ?: MATH_PLAN.getValue(1)
    fun chineseSlots(grade: Int): List<Slot> = CHINESE_PLAN[grade] ?: CHINESE_PLAN.getValue(1)
    fun englishSlots(grade: Int): List<Slot> = ENGLISH_PLAN[grade] ?: ENGLISH_PLAN.getValue(1)

    /**
     * 某个模块某年级、第 index 题属于哪个单元。
     * 用于在首页和练习页显示「第几单元 · 学什么」，让家长一眼看出孩子学到哪了。
     * 音乐和脑筋急转弯不按单元排，返回空串（界面上不显示这一行）。
     */
    fun unitOf(subject: Subject, grade: Int, index: Int): String {
        val slots = when (subject) {
            Subject.MATH -> mathSlots(grade)
            Subject.CHINESE -> chineseSlots(grade)
            Subject.ENGLISH -> englishSlots(grade)
            else -> return ""
        }
        if (slots.isEmpty()) return ""
        return slots[index.coerceIn(0, slots.lastIndex)].unit
    }

    /**
     * 把单元展开成 600 个题位。
     * 单元内的知识点循环出现（不是学一遍就丢），最后 1/4 抬一档难度做爬坡。
     */
    private fun expand(blocks: List<UnitBlock>): List<Slot> {
        val out = ArrayList<Slot>(QUESTIONS_PER_GRADE)
        for (b in blocks) {
            for (i in 0 until b.count) {
                val rise = i >= b.count - b.count / 4
                val d = if (rise) {
                    Difficulty.ofIdx((b.difficulty.idx + 1).coerceAtMost(2))
                } else b.difficulty
                out.add(Slot(b.name, d, b.topics[i % b.topics.size], b.groups))
            }
        }
        return normalize(out)
    }

    /**
     * 校正到恰好 600 题。
     * 万一以后有人改单元题数导致总数不对，这里补齐或截断，绝不让调用方索引越界。
     */
    private fun normalize(out: MutableList<Slot>): List<Slot> {
        if (out.isEmpty()) out.add(Slot("综合练习", Difficulty.EASY, 0))
        val base = out.toList()
        var k = 0
        while (out.size < QUESTIONS_PER_GRADE) {
            out.add(base[k % base.size])
            k++
        }
        return out.take(QUESTIONS_PER_GRADE)
    }
}
