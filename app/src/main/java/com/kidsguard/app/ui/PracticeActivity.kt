package com.kidsguard.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kidsguard.app.R
import com.kidsguard.app.data.DIFFICULTY_SPAN
import com.kidsguard.app.data.Difficulty
import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.data.Question
import com.kidsguard.app.data.QuestionRepository
import com.kidsguard.app.data.Subject
import com.kidsguard.app.data.ThemeManager
import com.kidsguard.app.quiz.Curriculum
import com.kidsguard.app.quiz.EnglishExamples
import com.kidsguard.app.quiz.EnglishSpeech
import kotlin.random.Random

/**
 * 练习 / 运动打卡界面。
 *
 * - 题目类模块（语文/数学/英语/音乐/脑筋急转弯）：选难度档 → 答题，进度自动保存
 * - 运动模块：展示一个真实动作任务，孩子完成后点「完成了」记录一次
 */
class PracticeActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var repo: QuestionRepository
    private lateinit var subject: Subject

    private var difficulty: Difficulty = Difficulty.EASY
    private var posInBlock = 0          // 当前难度档内做到第几题（0..199）
    private var current: Question? = null
    private var sportIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice)

        prefs = PrefsManager(this)
        repo = QuestionRepository(this)
        subject = Subject.of(intent.getStringExtra(EXTRA_SUBJECT).orEmpty())

        // 英语 / 数学模块要读单词或题面，先把 TTS 引擎拉起来（异步初始化，不阻塞 UI）
        if (subject == Subject.ENGLISH || subject == Subject.MATH) EnglishSpeech.init(this)

        findViewById<TextView>(R.id.tvTitle).text = subject.label
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        if (subject.kind == Subject.Kind.SPORT) {
            setupSport()
        } else {
            setupQuiz()
        }
    }

    override fun onDestroy() {
        // 释放 TTS 引擎；非英语模块 init 没被调用过，shutdown 内部幂等
        EnglishSpeech.shutdown()
        super.onDestroy()
    }

    // ---------- 题目模式 ----------

    private fun setupQuiz() {
        val tabs = listOf(
            R.id.btnEasy to Difficulty.EASY,
            R.id.btnMedium to Difficulty.MEDIUM,
            R.id.btnHard to Difficulty.HARD
        )

        // 从上次做到哪儿接着往后做，而不是每次进来都从第一题重来。
        // 这样「一直往下做」就等同于「跟着人教版课本往前学」，难度也自然跟着爬坡。
        val done = prefs.getProgress(subject, prefs.grade)
        difficulty = Difficulty.of(done)
        posInBlock = done % DIFFICULTY_SPAN

        tabs.forEach { (id, diff) ->
            findViewById<Button>(id).setOnClickListener {
                difficulty = diff
                posInBlock = 0
                highlightDiff()
                showQuizQuestion()
            }
        }
        highlightDiff()
        showQuizQuestion()
    }

    private fun highlightDiff() {
        val tabs = mapOf(R.id.btnEasy to Difficulty.EASY, R.id.btnMedium to Difficulty.MEDIUM, R.id.btnHard to Difficulty.HARD)
        val brand = ThemeManager.brand(this)
        tabs.forEach { (id, diff) ->
            val btn = findViewById<Button>(id)
            val active = diff == difficulty
            btn.setBackgroundColor(if (active) brand else android.graphics.Color.parseColor("#EDEDEA"))
            btn.setTextColor(if (active) android.graphics.Color.WHITE else getColor(R.color.text_sub))
        }
    }

    private fun showQuizQuestion() {
        val grade = prefs.grade
        val index = difficulty.range().first + posInBlock
        val q = repo.questionAt(subject, grade, index)
        current = q

        val tvQ = findViewById<TextView>(R.id.tvQuestion)
        val et = findViewById<EditText>(R.id.etAnswer)
        val tip = findViewById<TextView>(R.id.tvTip)
        val opts = findViewById<LinearLayout>(R.id.optionsContainer)
        val fb = findViewById<TextView>(R.id.tvFeedback)
        val submit = findViewById<Button>(R.id.btnSubmit)

        tvQ.text = q.text
        fb.text = ""
        val example = findViewById<TextView>(R.id.tvExample)
        example.text = ""
        example.visibility = View.GONE
        tip.visibility = View.GONE
        submit.text = getString(R.string.lock_btn_submit)

        // 英语题型：answer 含英文时显示「听发音」按钮（enToCn 的 answer 是中文就不显示）
        val speakBtn = findViewById<View>(R.id.btnSpeak)
        val speakable = subject == Subject.ENGLISH && containsEnglish(q.answer)
        if (speakable) {
            speakBtn.visibility = View.VISIBLE
            speakBtn.setOnClickListener {
                if (EnglishSpeech.available()) {
                    EnglishSpeech.speak(q.answer)
                } else {
                    Toast.makeText(this, R.string.practice_speak_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
            // 自动朗读：题面显示完延迟 350ms 念一遍 answer，让小孩先听再选
            // 不可用时 speak 内部静默跳过（v10 兜底），按钮仍可手动再听
            if (EnglishSpeech.available()) {
                speakBtn.postDelayed({ EnglishSpeech.speak(q.answer) }, 350)
            }
        } else {
            speakBtn.visibility = View.GONE
        }

        // 数学题型：题面显示完自动用中文 TTS 念一遍（"七加八等于多少"）
        // 一年级小孩不认字，听到声音比看 "7 + 8 = ?" 友好
        if (subject == Subject.MATH && EnglishSpeech.available()) {
            findViewById<View>(R.id.tvQuestion).postDelayed({
                EnglishSpeech.speakZh(q.text)
            }, 350)
        }

        if (q.hasOptions) {
            et.visibility = View.GONE
            opts.visibility = View.VISIBLE
            opts.removeAllViews()
            for (opt in q.options) {
                val b = Button(this).apply {
                    text = opt
                    textSize = 17f
                    setBackgroundResource(R.drawable.btn_cartoon_option)
                    setTextColor(getColor(R.color.text_main))
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
                    layoutParams = lp
                    setOnClickListener { onSubmit(opt) }
                }
                opts.addView(b)
            }
        } else {
            opts.visibility = View.GONE
            opts.removeAllViews()
            et.visibility = View.VISIBLE
            et.text?.clear()
            et.requestFocus()
        }
        updateProgress()
    }

    private fun onSubmit(picked: String? = null) {
        val q = current ?: return
        val input = picked ?: findViewById<EditText>(R.id.etAnswer).text?.toString().orEmpty()
        if (picked == null && input.isBlank()) {
            Toast.makeText(this, R.string.lock_empty_answer, Toast.LENGTH_SHORT).show()
            return
        }
        val correct = normalize(input) == normalize(q.answer)
        val fb = findViewById<TextView>(R.id.tvFeedback)
        fb.text = if (correct) getString(R.string.lock_correct) else getString(R.string.lock_wrong, q.answer)
        fb.setTextColor(getColor(if (correct) R.color.learning else R.color.danger))

        // 英语题答对时，若该单词在例句库里：显示例句 + TTS 朗读整个英文例句
        // 答错不显示例句（避免给答案提示），但会朗读一次 answer 单词让孩子记住发音
        val example = findViewById<TextView>(R.id.tvExample)
        if (correct && subject == Subject.ENGLISH) {
            val ex = EnglishExamples.lookup(q.answer)
            if (ex != null) {
                example.text = getString(R.string.practice_example_tpl, ex.first, ex.second)
                example.visibility = View.VISIBLE
                // 朗读整个英文例句，让孩子在语境里再听一次
                // 设备无声时弹一次 Toast，避免孩子以为按钮坏了
                if (EnglishSpeech.available()) {
                    EnglishSpeech.speak(ex.first)
                } else {
                    Toast.makeText(this, R.string.practice_speak_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        } else if (!correct && subject == Subject.ENGLISH && containsEnglish(q.answer)) {
            // 答错时朗读一次正确单词（不展示例句），让答错也变成学习机会
            // 不可用时静默跳过（v10 兜底）
            if (EnglishSpeech.available()) {
                EnglishSpeech.speak(q.answer)
            }
        }

        // 数学题：答对 / 答错时如果答案是纯数字，用中文 TTS 朗读答案（让答对更爽、答错也学一次）
        if (subject == Subject.MATH && isPureNumber(q.answer) && EnglishSpeech.available()) {
            EnglishSpeech.speakZh(q.answer)
        }

        if (correct) {
            prefs.addProgress(subject, prefs.grade)
            prefs.answeredToday = prefs.answeredToday + 1
            prefs.addStars(1)
        }
        posInBlock = (posInBlock + 1) % 200
        // 节奏调整：
        // - 答对 + 有例句：2200ms（让 TTS 读完整个例句）
        // - 答对 + 无例句：600ms（保持原节奏）
        // - 答错 + 英语题型：1800ms（让 TTS 读完正确答案单词，给孩子再听一次的机会）
        // - 答错 + 数学题型：1800ms（同上，让 TTS 读答案数字）
        // - 答错 + 其他题型：1400ms（保持原节奏）
        val spokenOnWrong = !correct && subject == Subject.ENGLISH && containsEnglish(q.answer)
        val spokenOnMathWrong = !correct && subject == Subject.MATH && isPureNumber(q.answer)
        val nextDelay = when {
            correct && example.visibility == View.VISIBLE -> 2200L
            correct -> 600L
            spokenOnWrong -> 1800L
            spokenOnMathWrong -> 1800L
            else -> 1400L
        }
        fb.postDelayed({ showQuizQuestion() }, nextDelay)
    }

    private fun updateProgress() {
        val done = prefs.getProgress(subject, prefs.grade)
        findViewById<TextView>(R.id.tvProgress).text =
            getString(R.string.practice_progress, subject.label, done)
    }

    // ---------- 运动模式 ----------

    private fun setupSport() {
        findViewById<LinearLayout>(R.id.difficultyRow).visibility = View.GONE
        sportIndex = Random.nextInt(600)
        showSportTask()
        findViewById<Button>(R.id.btnSubmit).setOnClickListener { onSportDone() }
    }

    private fun showSportTask() {
        val task = repo.sportTask(prefs.grade, sportIndex)
        findViewById<TextView>(R.id.tvQuestion).text = "${task.name}\n${task.desc}"
        val tip = findViewById<TextView>(R.id.tvTip)
        tip.text = getString(R.string.practice_sport_tip, task.tip)
        tip.visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.optionsContainer).visibility = View.GONE
        findViewById<EditText>(R.id.etAnswer).visibility = View.GONE
        findViewById<TextView>(R.id.tvFeedback).text = ""
        findViewById<Button>(R.id.btnSubmit).text = getString(R.string.practice_sport_done)
        findViewById<TextView>(R.id.tvProgress).text =
            getString(R.string.practice_sport_count, prefs.sportDoneToday)
    }

    private fun onSportDone() {
        prefs.sportDoneToday = prefs.sportDoneToday + 1
        prefs.addStars(2)
        Toast.makeText(this, R.string.practice_sport_cheer, Toast.LENGTH_SHORT).show()
        sportIndex = (sportIndex + 37) % 600   // 换一个动作，避免连续重复
        showSportTask()
    }

    private fun normalize(s: String): String = s.trim().replace(" ", "").lowercase()

    /** 判断字符串是否含英文字母（用于决定英语题是否值得朗读） */
    private fun containsEnglish(s: String): Boolean = s.any { it in 'a'..'z' || it in 'A'..'Z' }

    /** 判断字符串是否是纯数字（用于数学题答案朗读：可能含正负号、小数点，这里宽松处理） */
    private fun isPureNumber(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty()) return false
        // 允许首字符为 - 或 +，其余为数字或小数点
        var i = 0
        if (t[0] == '-' || t[0] == '+') i = 1
        if (i >= t.length) return false
        var hasDigit = false
        var hasDot = false
        while (i < t.length) {
            val c = t[i]
            if (c.isDigit()) hasDigit = true
            else if (c == '.' && !hasDot) hasDot = true
            else return false
            i++
        }
        return hasDigit
    }

    companion object {
        const val EXTRA_SUBJECT = "extra_subject"
    }
}
