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
import com.kidsguard.app.data.Difficulty
import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.data.Question
import com.kidsguard.app.data.QuestionRepository
import com.kidsguard.app.data.Subject
import com.kidsguard.app.data.ThemeManager
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

        findViewById<TextView>(R.id.tvTitle).text = subject.label
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        if (subject.kind == Subject.Kind.SPORT) {
            setupSport()
        } else {
            setupQuiz()
        }
    }

    // ---------- 题目模式 ----------

    private fun setupQuiz() {
        val tabs = listOf(
            R.id.btnEasy to Difficulty.EASY,
            R.id.btnMedium to Difficulty.MEDIUM,
            R.id.btnHard to Difficulty.HARD
        )
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
        tip.visibility = View.GONE
        submit.text = getString(R.string.lock_btn_submit)

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

        if (correct) {
            prefs.addProgress(subject, prefs.grade)
            prefs.answeredToday = prefs.answeredToday + 1
            prefs.addStars(1)
        }
        posInBlock = (posInBlock + 1) % 200
        fb.postDelayed({ showQuizQuestion() }, if (correct) 600 else 1400)
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

    companion object {
        const val EXTRA_SUBJECT = "extra_subject"
    }
}
