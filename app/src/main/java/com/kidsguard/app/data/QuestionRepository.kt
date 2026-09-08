package com.kidsguard.app.data

import android.content.Context
import com.kidsguard.app.quiz.ChineseGenerator
import com.kidsguard.app.quiz.EnglishGenerator
import com.kidsguard.app.quiz.MathGenerator
import com.kidsguard.app.quiz.MusicBank
import com.kidsguard.app.quiz.RiddleBank
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
    /**
     * 洗牌用的随机源。
     * 显式用 java.util.Random（按系统时间播种），不依赖默认随机源，
     * 确保每道题每次出现时正确选项的落点都不一样。
     */
    private val shuffleRandom = Random(System.nanoTime())

    private fun shuffleOptions(q: Question): Question =
        if (q.options.size > 1) q.copy(options = q.options.shuffled(shuffleRandom)) else q

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
     * 扩充方式：往 MusicBank / RiddleBank 里加条目即可，生成逻辑不用动。
     */
    private fun musicQuestion(index: Int): Question {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        // 按日期轮换题库起点，实现「每天自动换新题」
        val (text, answer) = MusicBank.items[(index + day) % MusicBank.items.size]
        return Question(
            id = "music_$index",
            text = text,
            answer = answer,
            subject = "music",
            index = index,
            difficulty = Difficulty.of(index).idx,
            options = options4(answer, MusicBank.items.map { it.second }, Random(index + day * 13))
        )
    }

    private fun riddleQuestion(index: Int): Question {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        // 按日期轮换题库起点，实现「每天自动换新题」
        val (text, answer) = RiddleBank.items[(index + day) % RiddleBank.items.size]
        return Question(
            id = "riddle_$index",
            text = text,
            answer = answer,
            subject = "riddle",
            index = index,
            difficulty = Difficulty.of(index).idx,
            options = options4(answer, RiddleBank.items.map { it.second }, Random(index + day * 13))
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

    }
}
