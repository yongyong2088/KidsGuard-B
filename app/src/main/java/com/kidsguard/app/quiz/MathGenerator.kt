package com.kidsguard.app.quiz

import com.kidsguard.app.data.Difficulty
import com.kidsguard.app.data.Question
import kotlin.random.Random

/**
 * 数学题生成器（一年级 / 二年级 / 三年级，各 600 题）。
 *
 * 关键实现：题目由 (年级, 题号) 通过固定种子生成 —— 第 121 题永远是同一道。
 * 这样孩子能感觉到「我在往前推进」，同时磁盘上不需要存 1800 道题。
 *
 * 难度分档：简单 0-199 / 中等 200-399 / 困难 400-599，每档 5 种题型轮换。
 */
object MathGenerator {

    fun generate(grade: Int, index: Int): Question {
        val slot = slotFor(grade, index)
        val rnd = Random(seed(grade, index))
        val built = when (grade) {
            1 -> grade1(slot.difficulty, slot.type, rnd, index)
            2 -> grade2(slot.difficulty, slot.type, rnd, index)
            else -> grade3(slot.difficulty, slot.type, rnd, index)
        }
        // 难度以「单元教学顺序」为准，而不是题号落在哪一段
        return built.copy(difficulty = slot.difficulty.idx)
    }

    /** 查这题属于哪个单元、什么难度、哪种题型（见 Curriculum.kt 的人教版顺序表） */
    private fun slotFor(grade: Int, index: Int): Slot =
        Curriculum.mathSlots(grade).getOrNull(index)
            ?: Slot("期末综合复习", Difficulty.HARD, index % 5)

    /**
     * 生成种子。关键：加了一项「日期」。
     * 同一 (年级, 题号) 在「同一天」内题目固定（孩子能感到往前推进），
     * 但跨天后种子变化 → 同一位置生成出全新的题，实现「每天自动换新题」。
     * 全程不存盘、不联网。
     */
    private fun seed(grade: Int, index: Int): Int {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        return (grade * 1_000_003L + index * 7_919L + day * 2_654_537L + 17L).toInt()
    }

    // ==================== 一年级 ====================

    private fun grade1(d: Difficulty, type: Int, r: Random, idx: Int): Question = when (d) {
        Difficulty.EASY -> when (type) {
            0 -> { // 10 以内加法
                val a = r.nextInt(9) + 1
                val b = r.nextInt(10 - a) + 1
                q("$a + $b = ?", a + b, r, 3, idx)
            }
            1 -> { // 10 以内减法
                val a = r.nextInt(9) + 2
                val b = r.nextInt(a - 1) + 1
                q("$a − $b = ?", a - b, r, 3, idx)
            }
            2 -> { // 比大小
                val a = r.nextInt(10) + 1
                val b = r.nextInt(10) + 1
                val ans = if (a > b) ">" else if (a < b) "<" else "="
                qq("$a ○ $b，○ 里填什么？", ans, listOf(">", "<", "="), idx)
            }
            3 -> { // 填未知数
                val a = r.nextInt(8) + 1
                val sum = a + r.nextInt(9 - a) + 1
                q("$a + ( ) = $sum，括号里填几？", sum - a, r, 3, idx)
            }
            else -> { // 相邻数
                val a = r.nextInt(18) + 2
                q("$a 的前面一个数是几？", a - 1, r, 2, idx)
            }
        }
        Difficulty.MEDIUM -> when (type) {
            0 -> { // 20 以内进位加
                val a = r.nextInt(8) + 5
                val b = r.nextInt(9) + 4
                q("$a + $b = ?", a + b, r, 4, idx)
            }
            1 -> { // 20 以内退位减
                val a = r.nextInt(9) + 11
                val b = r.nextInt(a - 10) + 3
                q("$a − $b = ?", a - b, r, 4, idx)
            }
            2 -> { // 连加连减
                val a = r.nextInt(6) + 3
                val b = r.nextInt(5) + 2
                val c = r.nextInt(5) + 1
                if (r.nextBoolean()) q("$a + $b + $c = ?", a + b + c, r, 4, idx)
                else {
                    val s = a + b + c
                    q("$s − $b − $c = ?", s - b - c, r, 4, idx)
                }
            }
            3 -> { // 20 以内比大小
                val a = r.nextInt(20) + 1
                val b = r.nextInt(20) + 1
                val ans = if (a > b) ">" else if (a < b) "<" else "="
                qq("$a ○ $b，○ 里填什么？", ans, listOf(">", "<", "="), idx)
            }
            else -> { // 应用题
                val a = r.nextInt(8) + 3
                val b = r.nextInt(7) + 2
                if (r.nextBoolean())
                    q("小明有 $a 颗糖，妈妈又给他 $b 颗，现在一共有几颗？", a + b, r, 3, idx)
                else {
                    val s = a + b + 4
                    q("树上有 $s 只小鸟，飞走了 $b 只，还剩几只？", s - b, r, 3, idx)
                }
            }
        }
        Difficulty.HARD -> when (type) {
            0 -> { // 100 以内加减
                val a = r.nextInt(60) + 20
                val b = r.nextInt(30) + 5
                if (r.nextBoolean()) q("$a + $b = ?", a + b, r, 6, idx)
                else q("${a + b} − $b = ?", a, r, 6, idx)
            }
            1 -> { // 整十数
                val a = (r.nextInt(8) + 1) * 10
                val b = (r.nextInt(8) + 1) * 10
                if (r.nextBoolean()) q("$a + $b = ?", a + b, r, 10, idx)
                else q("${a + b} − $a = ?", b, r, 10, idx)
            }
            2 -> { // 加减混合
                val a = r.nextInt(40) + 20
                val b = r.nextInt(15) + 3
                val c = r.nextInt(12) + 2
                q("$a + $b − $c = ?", a + b - c, r, 5, idx)
            }
            3 -> { // 填未知数
                val a = r.nextInt(50) + 20
                val sum = a + r.nextInt(30) + 5
                q("( ) + $a = $sum，括号里填几？", sum - a, r, 6, idx)
            }
            else -> { // 应用题（两步）
                val a = r.nextInt(9) + 4
                val b = r.nextInt(8) + 3
                val c = r.nextInt(6) + 2
                q("小红第一天看 $a 页书，第二天看 $b 页，第三天看 $c 页，三天一共看几页？",
                    a + b + c, r, 5, idx)
            }
        }
    }

    // ==================== 二年级 ====================

    private fun grade2(d: Difficulty, type: Int, r: Random, idx: Int): Question = when (d) {
        Difficulty.EASY -> when (type) {
            0 -> {
                val a = r.nextInt(50) + 25
                val b = r.nextInt(40) + 8
                q("$a + $b = ?", a + b, r, 8, idx)
            }
            1 -> {
                val a = r.nextInt(50) + 30
                val b = r.nextInt(a - 10) + 5
                q("$a − $b = ?", a - b, r, 8, idx)
            }
            2 -> {
                val a = r.nextInt(30) + 10
                val b = r.nextInt(20) + 5
                val c = r.nextInt(15) + 3
                q("$a + $b − $c = ?", a + b - c, r, 7, idx)
            }
            3 -> {
                val a = r.nextInt(90) + 10
                val b = r.nextInt(90) + 10
                val ans = if (a > b) ">" else if (a < b) "<" else "="
                qq("$a ○ $b，○ 里填什么？", ans, listOf(">", "<", "="), idx)
            }
            else -> {
                val a = r.nextInt(8) + 2
                val b = r.nextInt(8) + 2
                val c = r.nextInt(20) + 5
                q("$a × $b + $c = ?", a * b + c, r, 8, idx)
            }
        }
        Difficulty.MEDIUM -> when (type) {
            0 -> { // 表内乘法
                val a = r.nextInt(8) + 2
                val b = r.nextInt(8) + 2
                q("$a × $b = ?", a * b, r, 6, idx)
            }
            1 -> { // 表内除法
                val b = r.nextInt(8) + 2
                val c = r.nextInt(8) + 2
                q("${b * c} ÷ $b = ?", c, r, 4, idx)
            }
            2 -> { // 填乘数
                val a = r.nextInt(7) + 2
                val c = r.nextInt(8) + 2
                q("$a × ( ) = ${a * c}，括号里填几？", c, r, 3, idx)
            }
            3 -> { // 乘法比大小
                val a = r.nextInt(8) + 2
                val b = r.nextInt(8) + 2
                val c = a * b
                val d = r.nextInt(8) + 2
                val e = r.nextInt(8) + 2
                val f = d * e
                val ans = if (c > f) ">" else if (c < f) "<" else "="
                qq("$a × $b ○ $d × $e，○ 里填什么？", ans, listOf(">", "<", "="), idx)
            }
            else -> { // 乘法应用题
                val a = r.nextInt(7) + 3
                val b = r.nextInt(6) + 2
                q("一盒有 $a 支铅笔，$b 盒一共有几支？", a * b, r, 8, idx)
            }
        }
        Difficulty.HARD -> when (type) {
            0 -> { // 两步应用题
                val a = r.nextInt(7) + 4
                val b = r.nextInt(6) + 3
                val c = r.nextInt(5) + 2
                q("班级有 $a 排座位，每排坐 ${b + c} 人，其中每排男生 $b 人，女生一共有几人？",
                    a * c, r, 10, idx)
            }
            1 -> { // 带余除法
                val d = r.nextInt(6) + 3
                val q = r.nextInt(6) + 2
                val rem = r.nextInt(d - 1) + 1
                val a = d * q + rem
                q("$a ÷ $d = $q …… ( )，余数是几？", rem, r, 2, idx)
            }
            2 -> { // 乘减混合
                val a = r.nextInt(8) + 3
                val b = r.nextInt(8) + 3
                val c = r.nextInt(20) + 5
                q("$a × $b − $c = ?", a * b - c, r, 9, idx)
            }
            3 -> { // 除加混合
                val b = r.nextInt(7) + 2
                val c = r.nextInt(8) + 2
                val d = r.nextInt(30) + 10
                q("${b * c} ÷ $b + $d = ?", c + d, r, 8, idx)
            }
            else -> { // 单位换算
                val m = r.nextInt(8) + 2
                if (r.nextBoolean()) q("$m 米 = ( ) 厘米", m * 100, r, 100, idx)
                else q("${m * 100} 厘米 = ( ) 米", m, r, 1, idx)
            }
        }
    }

    // ==================== 三年级 ====================

    private fun grade3(d: Difficulty, type: Int, r: Random, idx: Int): Question = when (d) {
        Difficulty.EASY -> when (type) {
            0 -> {
                val a = r.nextInt(800) + 200
                val b = r.nextInt(500) + 100
                q("$a + $b = ?", a + b, r, 50, idx)
            }
            1 -> {
                val a = r.nextInt(800) + 300
                val b = r.nextInt(a - 200) + 100
                q("$a − $b = ?", a - b, r, 50, idx)
            }
            2 -> { // 多位数乘一位数（简单）
                val a = r.nextInt(80) + 20
                val b = r.nextInt(7) + 2
                q("$a × $b = ?", a * b, r, 20, idx)
            }
            3 -> { // 整十整百乘
                val a = (r.nextInt(8) + 2) * 10
                val b = r.nextInt(8) + 2
                q("$a × $b = ?", a * b, r, 30, idx)
            }
            else -> { // 表内除巩固
                val b = r.nextInt(8) + 3
                val c = r.nextInt(8) + 3
                q("${b * c} ÷ $b = ?", c, r, 3, idx)
            }
        }
        Difficulty.MEDIUM -> when (type) {
            0 -> { // 三位数乘一位数
                val a = r.nextInt(400) + 120
                val b = r.nextInt(7) + 3
                q("$a × $b = ?", a * b, r, 60, idx)
            }
            1 -> { // 除数是一位数
                val b = r.nextInt(6) + 3
                val c = r.nextInt(60) + 20
                q("${b * c} ÷ $b = ?", c, r, 15, idx)
            }
            2 -> { // 同分母分数加法
                val den = r.nextInt(6) + 3
                val n1 = r.nextInt(den - 2) + 1
                val n2 = r.nextInt(den - n1) + 1
                qq("$n1/$den + $n2/$den = ?", "${n1 + n2}/$den",
                    listOf("${n1 + n2}/$den", "${n1 + n2}/${den * 2}", "${n1 + n2 + 1}/$den", "${n1 + n2 - 1}/$den"), idx)
            }
            3 -> { // 倍数问题
                val a = r.nextInt(30) + 10
                val k = r.nextInt(5) + 2
                q("$a 的 $k 倍是多少？", a * k, r, 30, idx)
            }
            else -> { // 周长
                val a = r.nextInt(30) + 10
                val b = r.nextInt(20) + 5
                q("一个长方形长 $a 厘米、宽 $b 厘米，周长是多少厘米？", (a + b) * 2, r, 20, idx)
            }
        }
        Difficulty.HARD -> when (type) {
            0 -> { // 先乘除后加减
                val a = r.nextInt(8) + 3
                val b = r.nextInt(8) + 3
                val c = r.nextInt(40) + 10
                q("$a × $b + $c = ?", a * b + c, r, 25, idx)
            }
            1 -> { // 带括号
                val a = r.nextInt(30) + 20
                val b = r.nextInt(15) + 5
                val c = r.nextInt(6) + 2
                q("($a + $b) × $c = ?", (a + b) * c, r, 60, idx)
            }
            2 -> { // 面积
                val a = r.nextInt(20) + 5
                val b = r.nextInt(15) + 4
                q("一个长方形长 $a 厘米、宽 $b 厘米，面积是多少平方厘米？", a * b, r, 40, idx)
            }
            3 -> { // 分数比大小
                val den = r.nextInt(6) + 4
                val n1 = r.nextInt(den - 2) + 1
                val n2 = n1 + 1
                qq("$n1/$den ○ $n2/$den，○ 里填什么？", "<", listOf(">", "<", "="), idx)
            }
            else -> { // 两步应用题
                val a = r.nextInt(30) + 20
                val b = r.nextInt(20) + 10
                val c = r.nextInt(6) + 2
                q("商店进了 $c 箱苹果，每箱 $a 个，卖出 $b 个后还剩几个？", a * c - b, r, 40, idx)
            }
        }
    }

    // ==================== 工具 ====================

    private fun q(text: String, answer: Int, r: Random, spread: Int, idx: Int): Question {
        val ans = answer.toString()
        return Question(
            id = "math_${idx}_${text.hashCode()}",
            text = text,
            answer = ans,
            subject = "math",
            grade = 0,
            index = idx,
            difficulty = Difficulty.of(idx).idx,
            options = numericOptions(answer, r, spread)
        )
    }

    /** 固定选项的题目（比大小、分数等） */
    private fun qq(text: String, answer: String, options: List<String>, idx: Int): Question =
        Question(
            id = "math_${idx}_${text.hashCode()}",
            text = text,
            answer = answer,
            subject = "math",
            grade = 0,
            index = idx,
            difficulty = Difficulty.of(idx).idx,
            options = options.shuffled()
        )

    /** 生成 4 个数字选项：正确答案 + 3 个附近的干扰项 */
    private fun numericOptions(correct: Int, r: Random, spread: Int): List<String> {
        val set = LinkedHashSet<Int>()
        set.add(correct)
        var guard = 0
        while (set.size < 4 && guard++ < 80) {
            var d = r.nextInt(spread * 2 + 1) - spread
            if (d == 0) d = 1
            val v = correct + d
            if (v >= 0) set.add(v)
        }
        var k = 1
        while (set.size < 4) {
            set.add(correct + k * (spread + 2))
            k++
        }
        return set.toList().map { it.toString() }
    }
}
