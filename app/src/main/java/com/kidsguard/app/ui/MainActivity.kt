package com.kidsguard.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kidsguard.app.R
import com.kidsguard.app.data.Grade
import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.data.QUESTIONS_PER_GRADE
import com.kidsguard.app.data.Subject
import com.kidsguard.app.data.ThemeManager
import com.kidsguard.app.monitor.AppClassifier
import com.kidsguard.app.monitor.UsageMonitorService

/**
 * 学习中心主界面。
 *
 * 六个模块卡片在这里动态生成，点卡片进入对应模块的练习。
 * 顶部四个圆点切换主题，年级条切换出题年级，守护卡展示今日娱乐额度与三段式状态。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前应用主题，否则主题不生效
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)
        // 首次启动把预置名单中已安装的应用写进配置
        AppClassifier(prefs).applyPresetsIfNeeded(this)

        bindThemeDots()
        bindGradeChips()
        bindGuardian()
        buildModuleCards()

        findViewById<View>(R.id.btnParent).setOnClickListener {
            startActivity(Intent(this, ParentActivity::class.java))
        }

        findViewById<View>(R.id.bannerStars).setOnClickListener {
            startActivity(Intent(this, RewardActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // 用量/进度可能已被其它界面改动，回到主界面时刷新
        refreshGuardian()
        buildModuleCards()
        highlightGrade()
        highlightTheme()
        refreshStarsBar()
    }

    private fun refreshStarsBar() {
        val n = prefs.stars
        findViewById<TextView>(R.id.tvStarsBar).text = if (n > 0) {
            getString(R.string.reward_cost, n)
        } else {
            "答对题目赚星星吧"
        }
    }

    // ---------- 主题 ----------

    private fun bindThemeDots() {
        mapOf(
            R.id.themeOcean to ThemeManager.OCEAN,
            R.id.themeForest to ThemeManager.FOREST,
            R.id.themeSunset to ThemeManager.SUNSET,
            R.id.themeCandy to ThemeManager.CANDY
        ).forEach { (id, key) ->
            findViewById<View>(id).setOnClickListener {
                ThemeManager.switchTo(this, key)
            }
        }
    }

    private fun highlightTheme() {
        val active = prefs.theme
        val map = mapOf(
            R.id.themeOcean to ThemeManager.OCEAN,
            R.id.themeForest to ThemeManager.FOREST,
            R.id.themeSunset to ThemeManager.SUNSET,
            R.id.themeCandy to ThemeManager.CANDY
        )
        map.forEach { (id, key) ->
            val dot = findViewById<View>(id)
            dot.alpha = if (key == active) 1f else 0.35f
        }
    }

    // ---------- 年级 ----------

    private fun bindGradeChips() {
        val chips = listOf(
            R.id.chipG1 to 1,
            R.id.chipG2 to 2,
            R.id.chipG3 to 3
        )
        chips.forEach { (id, grade) ->
            findViewById<Button>(id).setOnClickListener {
                prefs.grade = grade
                highlightGrade()
                buildModuleCards()
            }
        }
    }

    private fun highlightGrade() {
        val chips = mapOf(R.id.chipG1 to 1, R.id.chipG2 to 2, R.id.chipG3 to 3)
        val brand = ThemeManager.brand(this)
        chips.forEach { (id, grade) ->
            val btn = findViewById<Button>(id)
            val active = prefs.grade == grade
            btn.setBackgroundColor(if (active) brand else Color.parseColor("#EDEDEA"))
            btn.setTextColor(if (active) Color.WHITE else getColor(R.color.text_sub))
        }
    }

    // ---------- 守护额度卡 ----------

    private fun bindGuardian() {
        val greeting = when (val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 0..11 -> getString(R.string.main_greeting_morning)
            in 12..17 -> getString(R.string.main_greeting_afternoon)
            else -> getString(R.string.main_greeting_evening)
        }
        findViewById<TextView>(R.id.tvGreeting).text = greeting

        findViewById<View>(R.id.cardGuardian).setOnClickListener { explainGuardian() }
    }

    private fun refreshGuardian() {
        prefs.ensureTodayRolled()
        val used = prefs.usedMinutesToday
        val quota = prefs.dailyQuotaMinutes
        val remain = prefs.remainingMinutes()

        val tvValue = findViewById<TextView>(R.id.tvQuotaValue)
        val tvStage = findViewById<TextView>(R.id.tvGuardianStage)
        val bar = findViewById<ProgressBar>(R.id.progressGuardian)

        val stage = prefs.lockStage()
        when (stage) {
            0 -> {
                tvValue.text = getString(R.string.main_remaining, remain)
                tvStage.text = getString(R.string.main_guardian_free)
            }
            1 -> {
                tvValue.text = getString(R.string.main_remaining, remain)
                tvStage.text = getString(R.string.main_guardian_normal, prefs.intervalNormalMinutes)
            }
            else -> {
                // 超额且进入加密段：剩余额度显示为负，提示「已超额」
                tvValue.text = getString(R.string.main_guardian_over)
                tvStage.text = getString(R.string.main_guardian_tight, prefs.intervalTightMinutes)
            }
        }
        bar.progress = ((used.toFloat() / quota.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)
    }

    private fun explainGuardian() {
        val text = getString(
            R.string.main_guardian_explain,
            prefs.dailyQuotaMinutes,
            prefs.intervalNormalMinutes,
            prefs.tightAfterMinutes,
            prefs.intervalTightMinutes,
            prefs.passStreak,
            prefs.earnMinutes
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.main_guardian_title)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------- 模块卡片 ----------

    private fun buildModuleCards() {
        val container = findViewById<LinearLayout>(R.id.modulesContainer)
        container.removeAllViews()
        val grade = prefs.grade
        Subject.all.forEach { subject ->
            container.addView(buildModuleCard(subject, grade))
        }
    }

    private fun buildModuleCard(subject: Subject, grade: Int): View {
        val ctx = this
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(getColor(R.color.bg_card))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
            layoutParams = lp
            // 卡片左侧加一条模块色竖条
            val color = getColor(subject.colorRes)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(getColor(R.color.bg_card))
                setStroke((4 * resources.displayMetrics.density).toInt(), color)
                cornerRadius = (14 * resources.displayMetrics.density)
            }
            isClickable = true
            isFocusable = true
        }

        // 第一行：emoji + 名称 + 进度
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val emoji = TextView(ctx).apply {
            text = subject.emoji
            textSize = 26f
        }
        val name = TextView(ctx).apply {
            text = subject.label
            textSize = 18f
            setTextColor(getColor(R.color.text_main))
            textStyleIdentifier()
            val wp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
            layoutParams = wp
            setPadding((10 * resources.displayMetrics.density).toInt(), 0, 0, 0)
        }
        val done = prefs.getProgress(subject, grade)
        val progText = TextView(ctx).apply {
            text = getString(R.string.main_module_progress, done, QUESTIONS_PER_GRADE)
            textSize = 13f
            setTextColor(getColor(subject.colorRes))
        }
        row.addView(emoji)
        row.addView(name)
        row.addView(progText)
        card.addView(row)

        // 进度条
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = QUESTIONS_PER_GRADE
            progress = done
            progressTintList = android.content.res.ColorStateList.valueOf(getColor(subject.colorRes))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * resources.displayMetrics.density).toInt() }
            layoutParams = lp
        }
        card.addView(bar)

        // 难度档小圆点（三档都可用）
        val dots = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * resources.displayMetrics.density).toInt() }
            layoutParams = lp
        }
        val labels = listOf(R.string.diff_easy, R.string.diff_medium, R.string.diff_hard)
        labels.forEach { lbl ->
            val dot = TextView(ctx).apply {
                text = getString(lbl)
                textSize = 11f
                setTextColor(getColor(subject.colorRes))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = (16 * resources.displayMetrics.density).toInt() }
                layoutParams = lp
            }
            dots.addView(dot)
        }
        card.addView(dots)

        card.setOnClickListener {
            startActivity(Intent(ctx, PracticeActivity::class.java).apply {
                putExtra(PracticeActivity.EXTRA_SUBJECT, subject.key)
            })
        }
        return card
    }

    private fun TextView.textStyleIdentifier() {
        // 占位：仅为可读性，兼容不同 API 的 bold 设置
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
}
