package com.kidsguard.app.quiz

import com.kidsguard.app.data.Difficulty
import com.kidsguard.app.data.Question
import kotlin.random.Random

/**
 * 语文题生成器（一年级 / 二年级 / 三年级，各 600 题）。
 *
 * 与数学不同，语文没法纯算法生成，靠「结构化字库 + 模板」：
 * 拼音表、反义词表、古诗库、成语库等数据表决定题目内容上限。
 *
 * 当前内置约 200 条素材，通过题型轮换 + 干扰项组合可覆盖 1800 题，
 * 但同一素材会以不同干扰项重复出现。想做到 600 题完全不重复，
 * 扩充下面的 *_BANK 即可，不需要改任何生成逻辑。
 */
object ChineseGenerator {

    fun generate(grade: Int, index: Int): Question {
        val r = Random(seed(grade, index))
        val type = index % 5
        return when (grade) {
            1 -> grade1(Difficulty.of(index), type, r, index)
            2 -> grade2(Difficulty.of(index), type, r, index)
            else -> grade3(Difficulty.of(index), type, r, index)
        }
    }

    /**
     * 生成种子。加了一项「日期」：同一 (年级, 题号) 当天内题目固定，
     * 跨天自动变成新题，实现「每天自动换新题」（不存盘、不联网）。
     */
    private fun seed(g: Int, i: Int): Int {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        return (g * 31_337L + i * 5_771L + day * 1_029_987L + 101L).toInt()
    }

    // ==================== 一年级 ====================

    private fun grade1(d: Difficulty, type: Int, r: Random, idx: Int): Question = when (d) {
        Difficulty.EASY -> when (type) {
            0 -> { // 拼音选字
                val (zi, py) = pick(PINYIN, r)
                q("「$zi」的拼音是？", py, PINYIN.map { it.second }, r, idx)
            }
            1 -> { // 反义词
                val (a, b) = pick(ANTONYM, r)
                q("「$a」的反义词是？", b, ANTONYM.flatMap { listOf(it.first, it.second) }, r, idx)
            }
            2 -> { // 量词
                val (noun, mw) = pick(MEASURE, r)
                q("一（ ）$noun，括号里填什么？", mw, MEASURE.map { it.second }, r, idx)
            }
            3 -> { // 加一笔变新字
                val (from, to) = pick(ADD_STROKE, r)
                q("「$from」加一笔是什么字？", to, ADD_STROKE.map { it.second }, r, idx)
            }
            else -> { // 古诗填空
                val (line, ans) = pick(POEM, r)
                q(line, ans, POEM.map { it.second }, r, idx)
            }
        }
        Difficulty.MEDIUM -> when (type) {
            0 -> {
                val (zi, py) = pick(PINYIN, r)
                q("拼音「$py」是哪个字？", zi, PINYIN.map { it.first }, r, idx)
            }
            1 -> {
                val (a, b) = pick(ANTONYM, r)
                q("「$b」的反义词是？", a, ANTONYM.flatMap { listOf(it.first, it.second) }, r, idx)
            }
            2 -> {
                val (noun, mw) = pick(MEASURE, r)
                q("下面哪个量词可以搭配「$noun」？", mw, MEASURE.map { it.second }, r, idx)
            }
            3 -> {
                val (line, ans) = pick(POEM, r)
                q(line, ans, POEM.map { it.second }, r, idx)
            }
            else -> { // 归类：选出不同类的一项（选项固定，保证答案唯一）
                val odd = pick(CATEGORY, r)
                qq("下面哪个不是同一类？", odd.odd, odd.items.split("、"), idx)
            }
        }
        Difficulty.HARD -> when (type) {
            0 -> {
                val (line, ans) = pick(POEM, r)
                q(line, ans, POEM.map { it.second }, r, idx)
            }
            1 -> { // 词语搭配
                val (a, b) = pick(COLLOCATION, r)
                q("「$a」可以和下面哪个词搭配？", b, COLLOCATION.map { it.second }, r, idx)
            }
            2 -> {
                val (a, b) = pick(SYNONYM, r)
                q("「$a」的近义词是？", b, SYNONYM.flatMap { listOf(it.first, it.second) }, r, idx)
            }
            3 -> {
                val (noun, mw) = pick(MEASURE, r)
                q("一（ ）$noun，括号里填什么？", mw, MEASURE.map { it.second }, r, idx)
            }
            else -> { // 句子补充
                val (line, ans) = pick(POEM, r)
                q(line, ans, POEM.map { it.second }, r, idx)
            }
        }
    }

    // ==================== 二年级 ====================

    private fun grade2(d: Difficulty, type: Int, r: Random, idx: Int): Question = when (d) {
        Difficulty.EASY -> when (type) {
            0 -> {
                val (zi, py) = pick(PINYIN, r)
                q("「$zi」的拼音是？", py, PINYIN.map { it.second }, r, idx)
            }
            1 -> {
                val (a, b) = pick(ANTONYM, r)
                q("「$a」的反义词是？", b, ANTONYM.flatMap { listOf(it.first, it.second) }, r, idx)
            }
            2 -> {
                val (line, ans) = pick(POEM, r)
                q(line, ans, POEM.map { it.second }, r, idx)
            }
            3 -> {
                val (noun, mw) = pick(MEASURE, r)
                q("一（ ）$noun，括号里填什么？", mw, MEASURE.map { it.second }, r, idx)
            }
            else -> {
                val (a, b) = pick(SYNONYM, r)
                q("「$a」的近义词是？", b, SYNONYM.flatMap { listOf(it.first, it.second) }, r, idx)
            }
        }
        Difficulty.MEDIUM -> when (type) {
            0 -> { // 多音字
                val (zi, p1, p2, w1, w2) = pick(POLYPHONE, r)
                val askFirst = r.nextBoolean()
                if (askFirst) q("「$w1」中「$zi」读什么？", p1, ALL_PINYIN, r, idx)
                else q("「$w2」中「$zi」读什么？", p2, ALL_PINYIN, r, idx)
            }
            1 -> { // 成语填字
                val (body, ans) = pick(IDIOM, r)
                q("把成语补充完整：$body", ans, IDIOM.map { it.second }, r, idx)
            }
            2 -> { // 词语搭配
                val (a, b) = pick(COLLOCATION, r)
                q("「$a」可以和下面哪个词搭配？", b, COLLOCATION.map { it.second }, r, idx)
            }
            3 -> {
                val (a, b) = pick(SYNONYM, r)
                q("「$b」的近义词是？", a, SYNONYM.flatMap { listOf(it.first, it.second) }, r, idx)
            }
            else -> {
                val (line, ans) = pick(POEM, r)
                q(line, ans, POEM.map { it.second }, r, idx)
            }
        }
        Difficulty.HARD -> when (type) {
            0 -> {
                val (body, ans) = pick(IDIOM, r)
                q("把成语补充完整：$body", ans, IDIOM.map { it.second }, r, idx)
            }
            1 -> { // 成语含义
                val (idiom, meaning) = pick(IDIOM_MEANING, r)
                q("「$idiom」的意思是？", meaning, IDIOM_MEANING.map { it.second }, r, idx)
            }
            2 -> { // 修辞判断
                val (sentence, tech) = pick(RHETORIC, r)
                qq("$sentence 这句话用了什么修辞手法？", tech, listOf("比喻", "拟人", "夸张", "反问"), idx)
            }
            3 -> { // 多音字
                val (zi, p1, p2, w1, w2) = pick(POLYPHONE, r)
                if (r.nextBoolean()) q("「$w1」中「$zi」读什么？", p1, ALL_PINYIN, r, idx)
                else q("「$w2」中「$zi」读什么？", p2, ALL_PINYIN, r, idx)
            }
            else -> {
                val (line, ans) = pick(POEM, r)
                q(line, ans, POEM.map { it.second }, r, idx)
            }
        }
    }

    // ==================== 三年级 ====================

    private fun grade3(d: Difficulty, type: Int, r: Random, idx: Int): Question = when (d) {
        Difficulty.EASY -> when (type) {
            0 -> {
                val (a, b) = pick(SYNONYM, r)
                q("「$a」的近义词是？", b, SYNONYM.flatMap { listOf(it.first, it.second) }, r, idx)
            }
            1 -> {
                val (body, ans) = pick(IDIOM, r)
                q("把成语补充完整：$body", ans, IDIOM.map { it.second }, r, idx)
            }
            2 -> {
                val (line, ans) = pick(POEM, r)
                q(line, ans, POEM.map { it.second }, r, idx)
            }
            3 -> {
                val (a, b) = pick(ANTONYM, r)
                q("「$a」的反义词是？", b, ANTONYM.flatMap { listOf(it.first, it.second) }, r, idx)
            }
            else -> {
                val (zi, p1, p2, w1, w2) = pick(POLYPHONE, r)
                if (r.nextBoolean()) q("「$w1」中「$zi」读什么？", p1, ALL_PINYIN, r, idx)
                else q("「$w2」中「$zi」读什么？", p2, ALL_PINYIN, r, idx)
            }
        }
        Difficulty.MEDIUM -> when (type) {
            0 -> {
                val (idiom, meaning) = pick(IDIOM_MEANING, r)
                q("「$idiom」的意思是？", meaning, IDIOM_MEANING.map { it.second }, r, idx)
            }
            1 -> { // 关联词
                val (body, ans) = pick(CONJUNCTION, r)
                q("$body 括号里填哪个关联词？", ans, CONJUNCTION.map { it.second }, r, idx)
            }
            2 -> {
                val (sentence, tech) = pick(RHETORIC, r)
                qq("$sentence 用了什么修辞手法？", tech, listOf("比喻", "拟人", "夸张", "反问"), idx)
            }
            3 -> {
                val (body, ans) = pick(IDIOM, r)
                q("把成语补充完整：$body", ans, IDIOM.map { it.second }, r, idx)
            }
            else -> {
                val (line, ans) = pick(POEM, r)
                q(line, ans, POEM.map { it.second }, r, idx)
            }
        }
        Difficulty.HARD -> when (type) {
            0 -> {
                val (idiom, meaning) = pick(IDIOM_MEANING, r)
                q("「$idiom」的意思是？", meaning, IDIOM_MEANING.map { it.second }, r, idx)
            }
            1 -> {
                val (body, ans) = pick(CONJUNCTION, r)
                q("$body 括号里填哪个关联词？", ans, CONJUNCTION.map { it.second }, r, idx)
            }
            2 -> {
                val (sentence, tech) = pick(RHETORIC, r)
                qq("$sentence 用了什么修辞手法？", tech, listOf("比喻", "拟人", "夸张", "反问"), idx)
            }
            3 -> { // 古诗理解
                val (q1, a1) = pick(POEM_MEANING, r)
                q(q1, a1, POEM_MEANING.map { it.second }, r, idx)
            }
            else -> {
                val (body, ans) = pick(IDIOM, r)
                q("把成语补充完整：$body", ans, IDIOM.map { it.second }, r, idx)
            }
        }
    }

    // ==================== 工具 ====================

    private fun <T> pick(list: List<T>, r: Random): T = list[r.nextInt(list.size)]

    private fun q(text: String, answer: String, pool: List<String>, r: Random, idx: Int): Question =
        Question(
            id = "cn_${idx}_${text.hashCode()}",
            text = text,
            answer = answer,
            subject = "chinese",
            index = idx,
            difficulty = Difficulty.of(idx).idx,
            options = options4(answer, pool, r)
        )

    private fun qq(text: String, answer: String, fixed: List<String>, idx: Int): Question =
        Question(
            id = "cn_${idx}_${text.hashCode()}",
            text = text,
            answer = answer,
            subject = "chinese",
            index = idx,
            difficulty = Difficulty.of(idx).idx,
            options = fixed.shuffled()
        )

    /** 正确答案 + 从同素材池里取 3 个不重复的干扰项 */
    private fun options4(correct: String, pool: List<String>, r: Random): List<String> {
        val set = LinkedHashSet<String>()
        set.add(correct)
        var guard = 0
        while (set.size < 4 && guard++ < 80) {
            val c = pool[r.nextInt(pool.size)]
            if (c != correct) set.add(c)
        }
        var k = 1
        while (set.size < 4) set.add("$correct$k")
        return set.toList().shuffled(r)
    }

    // ==================== 素材库（扩充这里即可加题量） ====================

    private val PINYIN = listOf(
        "大" to "dà", "小" to "xiǎo", "上" to "shàng", "下" to "xià",
        "中" to "zhōng", "人" to "rén", "口" to "kǒu", "手" to "shǒu",
        "目" to "mù", "日" to "rì", "月" to "yuè", "火" to "huǒ",
        "水" to "shuǐ", "山" to "shān", "石" to "shí", "田" to "tián",
        "土" to "tǔ", "木" to "mù", "禾" to "hé", "竹" to "zhú",
        "马" to "mǎ", "鸟" to "niǎo", "鱼" to "yú", "虫" to "chóng",
        "牛" to "niú", "羊" to "yáng", "花" to "huā", "草" to "cǎo",
        "树" to "shù", "云" to "yún", "雨" to "yǔ", "风" to "fēng",
        "雪" to "xuě", "白" to "bái", "黑" to "hēi", "红" to "hóng",
        "绿" to "lǜ", "黄" to "huáng", "蓝" to "lán", "高" to "gāo",
        "长" to "cháng", "多" to "duō", "少" to "shǎo", "前" to "qián",
        "后" to "hòu", "左" to "zuǒ", "右" to "yòu", "东" to "dōng",
        "西" to "xī", "南" to "nán", "北" to "běi", "早" to "zǎo",
        "晚" to "wǎn", "春" to "chūn", "夏" to "xià", "秋" to "qiū",
        "冬" to "dōng", "爸" to "bà", "妈" to "mā", "家" to "jiā",
        "书" to "shū", "门" to "mén", "车" to "chē", "心" to "xīn",
        "头" to "tóu", "耳" to "ěr", "足" to "zú", "牙" to "yá"
    )

    private val ANTONYM = listOf(
        "大" to "小", "上" to "下", "多" to "少", "前" to "后",
        "左" to "右", "高" to "矮", "长" to "短", "黑" to "白",
        "早" to "晚", "冷" to "热", "快" to "慢", "新" to "旧",
        "远" to "近", "深" to "浅", "粗" to "细", "胖" to "瘦",
        "哭" to "笑", "真" to "假", "好" to "坏", "忙" to "闲",
        "开" to "关", "来" to "去", "买" to "卖", "进" to "出"
    )

    private val SYNONYM = listOf(
        "美丽" to "漂亮", "高兴" to "快乐", "著名" to "有名", "特别" to "非常",
        "立刻" to "马上", "喜欢" to "喜爱", "安静" to "宁静", "明亮" to "光亮",
        "温暖" to "暖和", "寒冷" to "严寒", "容易" to "简单", "困难" to "艰难",
        "勇敢" to "英勇", "聪明" to "机智", "美丽" to "好看", "帮助" to "帮忙",
        "立刻" to "立即", "十分" to "非常", "连忙" to "赶紧", "经常" to "常常"
    )

    private val MEASURE = listOf(
        "牛" to "头", "马" to "匹", "鱼" to "条", "花" to "朵",
        "书" to "本", "笔" to "支", "鸟" to "只", "树" to "棵",
        "桥" to "座", "船" to "艘", "车" to "辆", "画" to "幅",
        "歌" to "首", "云" to "朵", "山" to "座", "河" to "条",
        "衣服" to "件", "鞋" to "双", "表" to "块", "灯" to "盏"
    )

    private val ADD_STROKE = listOf(
        "日" to "白", "木" to "本", "大" to "天", "十" to "土",
        "人" to "大", "口" to "日", "一" to "二", "二" to "三",
        "王" to "主", "了" to "子", "乌" to "鸟", "云" to "去"
    )

    /** 归类题：items 里前三个同类，odd 是不同类的那一个 */
    private val CATEGORY = listOf(
        Odd("江、河、海、鸟", "鸟"),
        Odd("树、花、草、鱼", "鱼"),
        Odd("跑、跳、走、山", "山"),
        Odd("红、绿、蓝、书", "书"),
        Odd("猫、狗、兔、桌", "桌"),
        Odd("春、夏、秋、风", "风"),
        Odd("爸、妈、爷、门", "门"),
        Odd("笔、书、纸、云", "云"),
        Odd("米饭、面条、包子、铅笔", "铅笔"),
        Odd("苹果、香蕉、西瓜、石头", "石头")
    )
    private data class Odd(val items: String, val odd: String)

    private val COLLOCATION = listOf(
        "美丽的" to "风景", "认真地" to "学习", "飞快地" to "奔跑", "大声地" to "朗读",
        "明亮的" to "教室", "温暖的" to "阳光", "可爱的" to "小兔", "清澈的" to "河水",
        "茂密的" to "树林", "鲜艳的" to "花朵", "勤劳的" to "蜜蜂", "勇敢地" to "前进",
        "仔细地" to "观察", "慢慢地" to "行走", "高兴地" to "唱歌", "安静地" to "等待"
    )

    private val POEM = listOf(
        "「床前明月光，疑是地上霜。举头望明月，低头思____。」" to "故乡",
        "「锄禾日当午，汗滴禾下土。谁知盘中餐，粒粒皆____。」" to "辛苦",
        "「春眠不觉晓，处处闻啼鸟。夜来风雨声，花落知____。」" to "多少",
        "「白日依山尽，黄河入海流。欲穷千里目，更上一____。」" to "层楼",
        "「两个黄鹂鸣翠柳，一行白鹭上青天。窗含西岭千秋雪，门泊东吴万里____。」" to "船",
        "「碧玉妆成一树高，万条垂下绿丝绦。不知细叶谁裁出，二月春风似____。」" to "剪刀",
        "「日照香炉生紫烟，遥看瀑布挂前川。飞流直下三千尺，疑是银河落____。」" to "九天",
        "「千山鸟飞绝，万____人踪灭。」" to "径",
        "「小荷才露尖尖角，早有蜻蜓立上____。」" to "头",
        "「接天莲叶无穷碧，映日荷花别样____。」" to "红",
        "「远上寒山石径斜，白云生处有人家。停车坐爱枫林晚，霜叶红于二月____。」" to "花",
        "「一道残阳铺水中，半江瑟瑟半江____。」" to "红",
        "「横看成岭侧成峰，远近高低各不同。不识庐山真面目，只缘身在此____。」" to "山中",
        "「水光潋滟晴方好，山色空蒙雨亦奇。欲把西湖比西子，淡妆浓抹总相____。」" to "宜",
        "「李白乘舟将欲行，忽闻岸上踏歌声。桃花潭水深千尺，不及汪伦送我____。」" to "情",
        "「天门中断楚江开，碧水东流至此回。两岸青山相对出，孤帆一片日边____。」" to "来",
        "「草长莺飞二月天，拂堤杨柳醉春烟。儿童散学归来早，忙趁东风放纸____。」" to "鸢",
        "「牧童骑黄牛，歌声振林樾。意欲捕鸣蝉，忽然闭口____。」" to "立",
        "「小娃撑小艇，偷采白莲回。不解藏踪迹，浮萍一道____。」" to "开",
        "「解落三秋叶，能开二月花。过江千尺浪，入竹万竿____。」" to "斜"
    )

    private val POEM_MEANING = listOf(
        "「举头望明月，低头思故乡」表达的是什么感情？" to "思念家乡",
        "「谁知盘中餐，粒粒皆辛苦」告诉我们什么？" to "要爱惜粮食",
        "「欲穷千里目，更上一层楼」的意思是什么？" to "站得高看得远",
        "「飞流直下三千尺」描写的是什么？" to "瀑布",
        "「霜叶红于二月花」描写的是哪个季节？" to "秋天",
        "「小荷才露尖尖角」描写的是哪个季节？" to "夏天",
        "「忙趁东风放纸鸢」中的「纸鸢」是什么？" to "风筝",
        "「只缘身在此山中」的上一句是什么？" to "不识庐山真面目",
        "「碧玉妆成一树高」描写的是什么树？" to "柳树",
        "「粒粒皆辛苦」出自哪首诗？" to "悯农"
    )

    /** 多音字：字、读音1、读音2、词语1、词语2 */
    private val POLYPHONE = listOf(
        Poly("长", "cháng", "zhǎng", "长短", "长大"),
        Poly("乐", "lè", "yuè", "快乐", "音乐"),
        Poly("好", "hǎo", "hào", "好人", "爱好"),
        Poly("发", "fā", "fà", "发现", "头发"),
        Poly("只", "zhī", "zhǐ", "一只", "只有"),
        Poly("种", "zhǒng", "zhòng", "种子", "种树"),
        Poly("空", "kōng", "kòng", "天空", "空闲"),
        Poly("觉", "jué", "jiào", "感觉", "睡觉"),
        Poly("背", "bèi", "bēi", "后背", "背书包"),
        Poly("都", "dōu", "dū", "都是", "首都"),
        Poly("行", "xíng", "háng", "行走", "银行"),
        Poly("少", "shǎo", "shào", "多少", "少年"),
        Poly("教", "jiāo", "jiào", "教书", "教室"),
        Poly("为", "wéi", "wèi", "成为", "因为"),
        Poly("地", "dì", "de", "土地", "慢慢地"),
        Poly("相", "xiāng", "xiàng", "互相", "照相")
    )
    private data class Poly(val zi: String, val p1: String, val p2: String, val w1: String, val w2: String)

    private val ALL_PINYIN: List<String> = POLYPHONE.flatMap { listOf(it.p1, it.p2) }.distinct()

    private val IDIOM = listOf(
        "一心一（ ）" to "意", "三心二（ ）" to "意", "五光十（ ）" to "色",
        "七上八（ ）" to "下", "九牛一（ ）" to "毛", "百发百（ ）" to "中",
        "千军万（ ）" to "马", "万紫千（ ）" to "红", "春暖花（ ）" to "开",
        "鸟语花（ ）" to "香", "山清水（ ）" to "秀", "风和日（ ）" to "丽",
        "自言自（ ）" to "语", "不知不（ ）" to "觉", "天长地（ ）" to "久",
        "目不转（ ）" to "睛", "守株待（ ）" to "兔", "亡羊补（ ）" to "牢",
        "画蛇添（ ）" to "足", "井底之（ ）" to "蛙", "五湖四（ ）" to "海",
        "五颜六（ ）" to "色", "一心二（ ）" to "用"
    )

    private val IDIOM_MEANING = listOf(
        "守株待兔" to "不劳而获，死守经验",
        "亡羊补牢" to "出了问题及时补救",
        "画蛇添足" to "多此一举，反而坏事",
        "井底之蛙" to "见识短浅的人",
        "狐假虎威" to "借别人的势力吓唬人",
        "掩耳盗铃" to "自己欺骗自己",
        "刻舟求剑" to "办事不知变通",
        "揠苗助长" to "违反规律，急于求成",
        "望梅止渴" to "用空想安慰自己",
        "胸有成竹" to "做事之前已有把握",
        "一举两得" to "做一件事得到两方面的好处",
        "名副其实" to "名声或名称与实际相符",
        "川流不息" to "行人车马来往不断",
        "应有尽有" to "该有的全都有",
        "恍然大悟" to "一下子明白过来",
        "目不转睛" to "注意力高度集中"
    )

    private val CONJUNCTION = listOf(
        "（ ）天下雨，（ ）运动会改期举行。" to "因为……所以……",
        "他（ ）学习好，（ ）乐于助人。" to "不但……而且……",
        "（ ）你努力，（ ）一定会有收获。" to "只要……就……",
        "（ ）明天下雨，我们（ ）去爬山。" to "如果……就……",
        "这本书（ ）是我的，（ ）是小明的。" to "不是……而是……",
        "（ ）他生病了，（ ）没来上学。" to "因为……所以……",
        "（ ）刮风（ ）下雨，他都坚持跑步。" to "无论……都……",
        "这件衣服（ ）漂亮（ ）便宜。" to "既……又……",
        "（ ）你答应了，（ ）要做到。" to "既然……就……",
        "他（ ）聪明（ ）勤奋。" to "不但……而且……"
    )

    private val RHETORIC = listOf(
        "「月亮害羞地躲进了云层里」" to "拟人",
        "「月亮像一个大玉盘挂在天上」" to "比喻",
        "「教室里静得连一根针掉在地上都听得见」" to "夸张",
        "「小鸟在枝头唱着动听的歌」" to "拟人",
        "「他的脸红得像一个熟透的苹果」" to "比喻",
        "「难道这不是我们应该做的吗？」" to "反问",
        "「飞流直下三千尺，疑是银河落九天」" to "夸张",
        "「春风轻轻地抚摸着大地」" to "拟人",
        "「湖面平静得像一面镜子」" to "比喻",
        "「这间屋子小得转不开身」" to "夸张",
        "「字典难道不是我们无声的老师吗？」" to "反问",
        "「太阳公公露出了笑脸」" to "拟人"
    )
}
