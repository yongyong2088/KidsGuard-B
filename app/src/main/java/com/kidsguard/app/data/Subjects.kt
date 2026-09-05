package com.kidsguard.app.data

import com.kidsguard.app.R

/**
 * 学习模块的静态定义。
 *
 * 六个模块 × 三个年级 × 每年级 600 题 = 10,800 道题。
 * 这个量级不存储题目，而是用「题号 → 确定性随机种子 → 实时生成」的方式：
 * 同一个（模块, 年级, 题号）永远生成同一道题，所以孩子能感觉到「我在往前推进」，
 * 但磁盘上不需要存一万道题。
 */

/** 每个年级每个模块的题目总数 */
const val QUESTIONS_PER_GRADE = 600
/** 每个难度档的题目数：简单 0-199 / 中等 200-399 / 困难 400-599 */
const val DIFFICULTY_SPAN = 200

enum class Grade(val level: Int, val label: String) {
    G1(1, "一年级"),
    G2(2, "二年级"),
    G3(3, "三年级");

    companion object {
        fun of(level: Int): Grade = entries.firstOrNull { it.level == level } ?: G1
    }
}

enum class Difficulty(val idx: Int, val label: String) {
    EASY(0, "简单"),
    MEDIUM(1, "中等"),
    HARD(2, "困难");

    /** 本档在 600 题中的题号区间 */
    fun range(): IntRange = idx * DIFFICULTY_SPAN until (idx + 1) * DIFFICULTY_SPAN

    companion object {
        fun of(index: Int): Difficulty = when {
            index < DIFFICULTY_SPAN -> EASY
            index < DIFFICULTY_SPAN * 2 -> MEDIUM
            else -> HARD
        }

        fun ofIdx(idx: Int): Difficulty = entries.firstOrNull { it.idx == idx } ?: EASY
    }
}

enum class Subject(
    val key: String,
    val label: String,
    val emoji: String,
    val colorRes: Int,
    val bgRes: Int,
    val kind: Kind
) {
    CHINESE("chinese", "语文", "\uD83D\uDCD6", R.color.subj_chinese, R.color.subj_chinese_bg, Kind.QUIZ),
    MATH("math", "数学", "\uD83D\uDD22", R.color.subj_math, R.color.subj_math_bg, Kind.QUIZ),
    ENGLISH("english", "英语", "\uD83D\uDD24", R.color.subj_english, R.color.subj_english_bg, Kind.QUIZ),
    MUSIC("music", "音乐", "\uD83C\uDFB5", R.color.subj_music, R.color.subj_music_bg, Kind.QUIZ),
    SPORT("sport", "运动", "⚽", R.color.subj_sport, R.color.subj_sport_bg, Kind.SPORT),
    RIDDLE("riddle", "脑筋急转弯", "\uD83D\uDCA1", R.color.subj_riddle, R.color.subj_riddle_bg, Kind.QUIZ);

    /** 模块的形态：QUIZ 是答题，SPORT 是动作打卡 */
    enum class Kind { QUIZ, SPORT }

    companion object {
        fun of(key: String): Subject = entries.firstOrNull { it.key == key } ?: MATH
        val all: List<Subject> = entries.toList()
    }
}

/**
 * 运动打卡任务。
 *
 * 运动模块不做纸笔题——「跳绳标准是多少」这种选择题学不到任何东西。
 * 这里改成真实动作，孩子完成后自己确认，家长端能看到完成记录。
 */
data class SportTask(
    val id: String,
    val name: String,
    /** 具体要求和达标数量 */
    val desc: String,
    val target: Int,
    val unit: String,
    val tip: String
)

/** 学习进度：某模块某年级做完了多少题 */
data class ModuleProgress(
    val subject: Subject,
    val grade: Grade,
    /** 已完成题数，0..600 */
    val done: Int,
    /** 本档做错的题号，会插回队列重做 */
    val wrongQueue: List<Int> = emptyList()
)
