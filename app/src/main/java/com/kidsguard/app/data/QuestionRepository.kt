package com.kidsguard.app.data

import android.content.Context
import com.kidsguard.app.quiz.ChineseGenerator
import com.kidsguard.app.quiz.EnglishGenerator
import com.kidsguard.app.quiz.MathGenerator
import com.kidsguard.app.quiz.SportTaskBank
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

/**
 * 题库统一入口。
 *
 * 题目不存储、不联网，全部现场生成：
 * - 数学 / 语文 / 英语：由 (年级, 题号) 确定性生成，第 121 题永远是第 121 题
 * - 运动：动作打卡任务，不是题目
 * - 音乐 / 脑筋急转弯：内置题库（当前为占位量，需扩充，见 FILE_TODO 注释）
 * - 家长自定义题：存在本地，弹窗时优先出现
 */
class QuestionRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("kidsguard_questions", Context.MODE_PRIVATE)

    /**
     * 取指定位置的题目。
     * @param subject 模块
     * @param grade 年级 1/2/3
     * @param index 题号 0..599
     */
    fun questionAt(subject: Subject, grade: Int, index: Int): Question = shuffleOptions(
        when (subject) {
            Subject.MATH -> MathGenerator.generate(grade, index)
            Subject.CHINESE -> ChineseGenerator.generate(grade, index)
            Subject.ENGLISH -> EnglishGenerator.generate(grade, index)
            Subject.MUSIC -> musicQuestion(index)
            Subject.RIDDLE -> riddleQuestion(index)
            Subject.SPORT -> throw IllegalArgumentException("运动模块是动作打卡，没有题目")
        }
    )

    /**
     * 把四选一的选项再洗一次牌。
     *
     * 为什么必须有这一步：所有生成器为了「第 121 题永远是第 121 题」，都用固定种子，
     * 于是同一道题的正确答案**永远停在第 2 个按钮**——孩子只要背位置就能蒙对。
     * 这里在出题出口用无种子的 [kotlin.random.Random] 再洗一次，
     * 保证同一道题每次出现，正确选项的位置都不一样。
     *
     * 只动展示顺序，不动答案本身，所以判分逻辑（比对文本）完全不受影响。
     */
    private fun shuffleOptions(q: Question): Question =
        if (q.options.size > 1) q.copy(options = q.options.shuffled()) else q

    /** 运动模块取任务 */
    fun sportTask(grade: Int, index: Int): SportTask = SportTaskBank.taskFor(grade, index)

    /**
     * 弹窗取题。
     *
     * 与练习模式不同，弹窗不必按顺序推进，所以在难度档内随机取题。
     * @param stage 1 = 超出额度阶段（中等档），2 = 加密阶段（困难档）
     */
    fun nextForLock(prefs: PrefsManager, stage: Int): Question {
        val custom = getCustomQuestions()
        if (custom.isNotEmpty() && Random.nextInt(100) < 40) {
            return custom[Random.nextInt(custom.size)]
        }
        val subject = pickSubject(prefs)
        val grade = prefs.grade

        // 学到哪考到哪：以孩子在练习模式里的进度为中心出题。
        // 这样弹窗永远不会蹦出还没学到的单元，也不会反复考最前面的重复内容。
        // 阶段 2（累计超时后的加密阶段）把范围往前推一点、放宽一点，让孩子有「再往前够一够」的感觉。
        val done = prefs.getProgress(subject, grade).coerceIn(0, QUESTIONS_PER_GRADE - 1)
        val center = (done + if (stage >= 2) 30 else 0).coerceAtMost(QUESTIONS_PER_GRADE - 1)
        val radius = if (stage >= 2) 40 else 25
        val lo = (center - radius).coerceAtLeast(0)
        val hi = (center + radius).coerceAtMost(QUESTIONS_PER_GRADE - 1)
        val index = lo + Random.nextInt(hi - lo + 1)

        return questionAt(subject, grade, index)
    }

    /**
     * 弹窗出题科目。
     * 家长可以在家长端指定 math / chinese / english；设为 mixed 则三科轮换。
     * 运动是打卡不是答题，永远不会出现在弹窗里。
     */
    private fun pickSubject(prefs: PrefsManager): Subject {
        val configured = prefs.lockSubject
        if (configured != "mixed") {
            val s = Subject.of(configured)
            if (s != Subject.SPORT) return s
        }
        val pool = listOf(Subject.MATH, Subject.CHINESE, Subject.ENGLISH)
        return pool[Random.nextInt(pool.size)]
    }

    // ---------- 家长自定义题 ----------

    fun getCustomQuestions(): List<Question> {
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Question>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Question(
                        id = o.getString("id"),
                        text = o.getString("text"),
                        answer = o.getString("answer"),
                        subject = o.optString("subject", "custom"),
                        difficulty = o.optInt("difficulty", 1),
                        isCustom = true
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addCustomQuestion(text: String, answer: String) {
        val list = getCustomQuestions().toMutableList()
        list.add(
            Question(
                id = UUID.randomUUID().toString(),
                text = text,
                answer = answer,
                subject = "custom",
                difficulty = 1,
                isCustom = true
            )
        )
        saveCustom(list)
    }

    fun removeCustomQuestion(id: String) {
        saveCustom(getCustomQuestions().filter { it.id != id })
    }

    private fun saveCustom(list: List<Question>) {
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("text", it.text)
            o.put("answer", it.answer)
            o.put("subject", it.subject)
            o.put("difficulty", it.difficulty)
            arr.put(o)
        }
        prefs.edit().putString(KEY_CUSTOM, arr.toString()).apply()
    }

    // ---------- 音乐 / 脑筋急转弯（待扩充） ----------

    /**
     * FILE_TODO：这两个模块目前是占位内容（各 40 条），要铺满每年级 600 题需要扩充下面的题库。
     * 脑筋急转弯无法算法生成，只能人工收集；音乐需要音频资源才能做听音题。
     * 扩充方式：往 MUSIC_BANK / RIDDLE_BANK 里加条目即可，生成逻辑不用动。
     */
    private fun musicQuestion(index: Int): Question {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        // 按日期轮换题库起点，实现「每天自动换新题」
        val (text, answer) = MUSIC_BANK[(index + day) % MUSIC_BANK.size]
        return Question(
            id = "music_$index",
            text = text,
            answer = answer,
            subject = "music",
            index = index,
            difficulty = Difficulty.of(index).idx,
            options = options4(answer, MUSIC_BANK.map { it.second }, Random(index + day * 13))
        )
    }

    private fun riddleQuestion(index: Int): Question {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        // 按日期轮换题库起点，实现「每天自动换新题」
        val (text, answer) = RIDDLE_BANK[(index + day) % RIDDLE_BANK.size]
        return Question(
            id = "riddle_$index",
            text = text,
            answer = answer,
            subject = "riddle",
            index = index,
            difficulty = Difficulty.of(index).idx,
            options = options4(answer, RIDDLE_BANK.map { it.second }, Random(index + day * 13))
        )
    }

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

    companion object {
        private const val KEY_CUSTOM = "custom_questions"

        private val MUSIC_BANK = listOf(
            "钢琴一般有多少个琴键？" to "88个",
            "下面哪个是打击乐器？" to "鼓",
            "do re mi 一共有几个基本音级？" to "7个",
            "唱歌时打拍子通常用什么？" to "手",
            "简谱中 1 唱作什么？" to "do",
            "下面哪个是弦乐器？" to "小提琴",
            "《小星星》的第一句是？" to "一闪一闪亮晶晶",
            "四分音符唱几拍？" to "1拍",
            "下面哪个是中国民族乐器？" to "二胡",
            "音乐中声音的大小叫什么？" to "音量",
            "声音的高低叫什么？" to "音高",
            "全音符唱几拍？" to "4拍",
            "下面哪个是铜管乐器？" to "小号",
            "简谱中 0 表示什么？" to "休止符",
            "《两只老虎》是哪个国家的儿歌？" to "法国",
            "笛子是用什么发声的？" to "空气柱振动",
            "下面哪个乐器用弓拉？" to "二胡",
            "合唱时大家要保持什么一致？" to "节奏",
            "音乐中反复记号的作用是？" to "重复演奏",
            "《生日快乐歌》通常在什么时候唱？" to "过生日时",
            "五线谱有几条线？" to "5条",
            "高音谱号又叫什么？" to "G谱号",
            "下面哪种声音最高？" to "小鸟叫",
            "拍号 2/4 表示每小节有几拍？" to "2拍",
            "下面哪个是键盘乐器？" to "电子琴",
            "音乐课上打节拍的乐器叫什么？" to "节拍器",
            "《茉莉花》是哪个国家的民歌？" to "中国",
            "升记号在简谱中写作什么？" to "#",
            "下面哪个乐器的声音最低沉？" to "大鼓",
            "唱歌时正确的姿势是？" to "站直放松",
            "下面属于吹奏乐器的是？" to "口琴",
            "《欢乐颂》的作曲家是？" to "贝多芬",
            "音乐中快板表示什么？" to "速度很快",
            "简谱中数字下面加一条线表示？" to "时值减半",
            "下面哪个不是乐器？" to "铅笔",
            "大提琴有几根弦？" to "4根",
            "歌曲中的「副歌」通常在哪？" to "高潮部分",
            "指挥的作用是？" to "统一节奏",
            "下面哪个是木管乐器？" to "长笛",
            "人声一般分成几个声部？" to "4个"
        )

        private val RIDDLE_BANK = listOf(
            "什么东西越洗越脏？" to "水",
            "什么东西有头无脚？" to "砖头",
            "什么车寸步难行？" to "风车",
            "什么书买不到？" to "遗书",
            "什么水永远用不完？" to "薪水",
            "什么房子不能住人？" to "蜂房",
            "什么门永远关不上？" to "球门",
            "什么伞下雨不能用？" to "降落伞",
            "什么鱼不能吃？" to "木鱼",
            "什么牛不吃草？" to "蜗牛",
            "什么马不会跑？" to "木马",
            "什么床不能睡觉？" to "车床",
            "什么球不能踢？" to "地球",
            "什么灯不能照明？" to "红绿灯",
            "什么帽不能戴？" to "螺帽",
            "什么笔不能写字？" to "电笔",
            "什么河没有水？" to "银河",
            "什么海没有水？" to "辞海",
            "什么布剪不断？" to "瀑布",
            "什么路不能走？" to "电路",
            "小明把硬币扔进海里会怎样？" to "沉下去",
            "什么东西你只能用左手拿，不能用右手拿？" to "右手",
            "一个人在沙滩上走，为什么回头看不到脚印？" to "他在倒着走",
            "什么东西越切越大？" to "洞",
            "什么桌子不能吃饭？" to "电脑桌面",
            "一年之中哪个月最短？" to "二月",
            "什么鸡没有翅膀？" to "田鸡",
            "什么虎不吃人？" to "壁虎",
            "什么羊不吃草？" to "羊毛衫",
            "世界上什么最大？" to "眼皮",
            "一斤铁和一斤棉花哪个重？" to "一样重",
            "什么水果最爱说话？" to "芒果",
            "什么样的路不能走？" to "思路",
            "什么人一年只工作一天？" to "圣诞老人",
            "什么蛋不能吃？" to "混蛋",
            "什么树永远不落叶？" to "画上的树",
            "什么水不能喝？" to "墨水",
            "什么样的锁没有钥匙能打开？" to "密码锁",
            "什么车最慢？" to "堵车时的车",
            "什么东西天天从你身边走过你却抓不住？" to "时间",
            "什么动物最没有方向感？" to "麋鹿",
            "什么书谁也没看见过？" to "天书"
        )
    }
}
