package com.kidsguard.app.quiz

import com.kidsguard.app.data.Difficulty
import com.kidsguard.app.data.Question
import kotlin.random.Random

/**
 * 英语题生成器（一年级 / 二年级 / 三年级，各 600 题）。
 *
 * 依赖分类词库（16 组 × 4 词）和常用句型库。
 * 一年级只用前 6 组基础词，二年级加 6 组，三年级开放全部并加入句型题。
 * 扩充 GROUPS 和 SENTENCES 即可增加题量，不需要改生成逻辑。
 */
object EnglishGenerator {

    fun generate(grade: Int, index: Int): Question {
        val slot = Curriculum.englishSlots(grade).getOrNull(index)
            ?: Slot("Revision", Difficulty.HARD, index % 5)
        val r = Random(seed(grade, index))
        // 学到哪个单元就只考那个单元的单词，不再从整个年级词库里乱抽
        val words = if (slot.groups.isEmpty()) wordsFor(grade) else wordsFor(slot.groups)
        val built = when (grade) {
            1 -> grade1(slot.difficulty, slot.type, r, index, words)
            2 -> grade2(slot.difficulty, slot.type, r, index, words)
            else -> grade3(slot.difficulty, slot.type, r, index, words)
        }
        return built.copy(difficulty = slot.difficulty.idx)
    }

    /**
     * 生成种子。加了一项「日期」：同一 (年级, 题号) 当天内题目固定，
     * 跨天自动变成新题，实现「每天自动换新题」（不存盘、不联网）。
     */
    private fun seed(g: Int, i: Int): Int {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        return (g * 77_777L + i * 3_331L + day * 1_234_567L + 7L).toInt()
    }

    /** 没指定单元词表时的兜底范围：一年级前 6 组，二年级 4~11 组，三年级全部 */
    private fun wordsFor(grade: Int): List<Pair<String, String>> = wordsFor(
        when (grade) {
            1 -> (0..5).toList()
            2 -> (4..11).toList()
            else -> (0..15).toList()
        }
    )

    /** 按 GROUPS 的下标取词表；下标越界或为空时退回全量词库 */
    private fun wordsFor(groups: List<Int>): List<Pair<String, String>> =
        groups.filter { it in GROUPS.indices }
            .flatMap { GROUPS[it].second }
            .ifEmpty { GROUPS.flatMap { it.second } }

    // ==================== 一年级 ====================

    private fun grade1(d: Difficulty, type: Int, r: Random, idx: Int, w: List<Pair<String, String>>): Question =
        when (d) {
            Difficulty.EASY -> when (type) {
                0 -> cnToEn(w, r, idx)
                1 -> enToCn(w, r, idx)
                2 -> fillLetter(w, r, idx)
                else -> oddOneOut(1, r, idx)
            }
            Difficulty.MEDIUM -> when (type) {
                0 -> enToCn(w, r, idx)
                1 -> cnToEn(w, r, idx)
                2 -> fillLetter(w, r, idx)
                3 -> oddOneOut(1, r, idx)
                else -> firstLetter(w, r, idx)
            }
            Difficulty.HARD -> when (type) {
                0 -> cnToEn(w, r, idx)
                1 -> enToCn(w, r, idx)
                2 -> fillLetter(w, r, idx)
                3 -> oddOneOut(1, r, idx)
                else -> firstLetter(w, r, idx)
            }
        }

    // ==================== 二年级 ====================

    private fun grade2(d: Difficulty, type: Int, r: Random, idx: Int, w: List<Pair<String, String>>): Question =
        when (d) {
            Difficulty.EASY -> when (type) {
                0 -> cnToEn(w, r, idx)
                1 -> enToCn(w, r, idx)
                2 -> fillLetter(w, r, idx)
                3 -> oddOneOut(2, r, idx)
                else -> firstLetter(w, r, idx)
            }
            Difficulty.MEDIUM -> when (type) {
                0 -> enToCn(w, r, idx)
                1 -> cnToEn(w, r, idx)
                2 -> fillLetter(w, r, idx)
                3 -> oddOneOut(2, r, idx)
                else -> sentence(r, idx)
            }
            Difficulty.HARD -> when (type) {
                0 -> cnToEn(w, r, idx)
                1 -> enToCn(w, r, idx)
                2 -> fillLetter(w, r, idx)
                3 -> oddOneOut(2, r, idx)
                else -> sentence(r, idx)
            }
        }

    // ==================== 三年级 ====================

    private fun grade3(d: Difficulty, type: Int, r: Random, idx: Int, w: List<Pair<String, String>>): Question =
        when (d) {
            Difficulty.EASY -> when (type) {
                0 -> cnToEn(w, r, idx)
                1 -> enToCn(w, r, idx)
                2 -> fillLetter(w, r, idx)
                3 -> oddOneOut(3, r, idx)
                else -> sentence(r, idx)
            }
            Difficulty.MEDIUM -> when (type) {
                0 -> enToCn(w, r, idx)
                1 -> cnToEn(w, r, idx)
                2 -> fillLetter(w, r, idx)
                3 -> oddOneOut(3, r, idx)
                else -> sentence(r, idx)
            }
            Difficulty.HARD -> when (type) {
                0 -> cnToEn(w, r, idx)
                1 -> enToCn(w, r, idx)
                2 -> fillLetter(w, r, idx)
                3 -> sentence(r, idx)
                else -> oddOneOut(3, r, idx)
            }
        }

    // ==================== 题型 ====================

    private fun cnToEn(words: List<Pair<String, String>>, r: Random, idx: Int): Question {
        val (en, cn) = words[r.nextInt(words.size)]
        val pool = words.map { it.first }
        return make("「$cn」的英文是？", en, pool, r, idx)
    }

    private fun enToCn(words: List<Pair<String, String>>, r: Random, idx: Int): Question {
        val (en, cn) = words[r.nextInt(words.size)]
        val pool = words.map { it.second }
        return make("「$en」的中文意思是？", cn, pool, r, idx)
    }

    /** 补全单词：ap_le，挖掉一个字母 */
    private fun fillLetter(words: List<Pair<String, String>>, r: Random, idx: Int): Question {
        val (en, cn) = words.filter { it.first.length >= 4 }.let {
            if (it.isEmpty()) words else it
        }.let { it[r.nextInt(it.size)] }
        val pos = 1 + r.nextInt(en.length - 2)
        val missing = en[pos].toString()
        val shown = en.substring(0, pos) + "_" + en.substring(pos + 1)
        val letters = ('a'..'z').map { it.toString() }
        return make("补全单词（$cn）：$shown", missing, letters, r, idx)
    }

    /** 首字母提示：猫 c___ */
    private fun firstLetter(words: List<Pair<String, String>>, r: Random, idx: Int): Question {
        val (en, cn) = words[r.nextInt(words.size)]
        val hint = en.first() + " _ _ " + "·".repeat((en.length - 1).coerceAtLeast(1))
        val pool = words.map { it.first }
        return make("「$cn」的英文是（提示 $hint）", en, pool, r, idx)
    }

    /** 选出不同类的一项 */
    private fun oddOneOut(grade: Int, r: Random, idx: Int): Question {
        val usable = when (grade) {
            1 -> GROUPS.take(6)
            2 -> GROUPS.drop(4).take(8)
            else -> GROUPS
        }
        val group = usable[r.nextInt(usable.size)]
        val others = usable.filter { it.first != group.first }
        val other = others[r.nextInt(others.size)]
        val options = group.second.take(3).map { it.first }.toMutableList()
        options.add(other.second[r.nextInt(other.second.size)].first)
        return Question(
            id = "en_${idx}_odd",
            text = "下面哪个单词不是同一类？",
            answer = options.last(),
            subject = "english",
            index = idx,
            difficulty = Difficulty.of(idx).idx,
            options = options.shuffled(r)
        )
    }

    /** 常用句型填空 */
    private fun sentence(r: Random, idx: Int): Question {
        val s = SENTENCES[r.nextInt(SENTENCES.size)]
        return Question(
            id = "en_${idx}_s",
            text = s.text,
            answer = s.answer,
            subject = "english",
            index = idx,
            difficulty = Difficulty.of(idx).idx,
            options = s.options.shuffled(r)
        )
    }

    private fun make(text: String, answer: String, pool: List<String>, r: Random, idx: Int): Question {
        val set = LinkedHashSet<String>()
        set.add(answer)
        var guard = 0
        while (set.size < 4 && guard++ < 80) {
            val c = pool[r.nextInt(pool.size)]
            if (c != answer) set.add(c)
        }
        var k = 1
        while (set.size < 4) set.add("$answer$k")
        return Question(
            id = "en_${idx}_${text.hashCode()}",
            text = text,
            answer = answer,
            subject = "english",
            index = idx,
            difficulty = Difficulty.of(idx).idx,
            options = set.toList().shuffled(r)
        )
    }

    // ==================== 素材库 ====================

    private data class Sentence(val text: String, val answer: String, val options: List<String>)

    /** 分类词库：组名 → (英文, 中文) 列表 */
    private val GROUPS: List<Pair<String, List<Pair<String, String>>>> = listOf(
        "数字" to listOf("one" to "一", "two" to "二", "three" to "三", "four" to "四"),
        "颜色" to listOf("red" to "红色", "blue" to "蓝色", "green" to "绿色", "yellow" to "黄色"),
        "动物" to listOf("cat" to "猫", "dog" to "狗", "bird" to "鸟", "fish" to "鱼"),
        "文具" to listOf("book" to "书", "pen" to "钢笔", "pencil" to "铅笔", "ruler" to "尺子"),
        "家庭" to listOf("mother" to "妈妈", "father" to "爸爸", "sister" to "姐妹", "brother" to "兄弟"),
        "水果" to listOf("apple" to "苹果", "banana" to "香蕉", "orange" to "橙子", "pear" to "梨"),
        "食物" to listOf("rice" to "米饭", "bread" to "面包", "milk" to "牛奶", "egg" to "鸡蛋"),
        "身体" to listOf("hand" to "手", "head" to "头", "eye" to "眼睛", "ear" to "耳朵"),
        "衣物" to listOf("hat" to "帽子", "coat" to "外套", "shoe" to "鞋子", "sock" to "袜子"),
        "天气" to listOf("sun" to "太阳", "rain" to "雨", "snow" to "雪", "wind" to "风"),
        "地点" to listOf("school" to "学校", "park" to "公园", "zoo" to "动物园", "shop" to "商店"),
        "动作" to listOf("run" to "跑", "jump" to "跳", "swim" to "游泳", "sing" to "唱歌"),
        "学科" to listOf("math" to "数学", "art" to "美术", "music" to "音乐", "science" to "科学"),
        "职业" to listOf("teacher" to "老师", "doctor" to "医生", "driver" to "司机", "farmer" to "农民"),
        "交通" to listOf("bus" to "公共汽车", "car" to "小汽车", "bike" to "自行车", "train" to "火车"),
        "形容词" to listOf("big" to "大的", "small" to "小的", "happy" to "开心的", "tall" to "高的")
    )

    /** 常用句型：句子、答案、固定选项 */
    private val SENTENCES = listOf(
        Sentence("I ___ a student.", "am", listOf("am", "is", "are", "be")),
        Sentence("She ___ my sister.", "is", listOf("am", "is", "are", "be")),
        Sentence("They ___ good friends.", "are", listOf("am", "is", "are", "be")),
        Sentence("This ___ a book.", "is", listOf("am", "is", "are", "be")),
        Sentence("I ___ apples very much.", "like", listOf("like", "likes", "liking", "liked")),
        Sentence("___ name is Tom.", "My", listOf("My", "I", "Me", "Mine")),
        Sentence("How ___ you?", "are", listOf("am", "is", "are", "be")),
        Sentence("Good ___ , teacher!", "morning", listOf("morning", "night", "bye", "evening")),
        Sentence("I go to school ___ bus.", "by", listOf("by", "on", "in", "at")),
        Sentence("It is ___ apple.", "an", listOf("a", "an", "the", "/")),
        Sentence("___ color is it? — It's red.", "What", listOf("What", "Who", "Where", "How")),
        Sentence("The cat is ___ the table.", "on", listOf("on", "in", "at", "of"))
    )
}
