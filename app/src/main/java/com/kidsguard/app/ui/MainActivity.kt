package com.kidsguard.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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
 * 学习中心主界面（改版后）。
 *
 * 六个模块卡通卡片在这里动态生成，点卡片进入对应模块的练习。
 * 顶部问候会带上孩子的名字；右上角齿轮进入「我的资料」设置页。
 * 年级和主题色不再放在首页，统一在设置页里登记。
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

        bindGuardian()
        buildModuleCards()

        val gear = findViewById<android.widget.ImageButton>(R.id.btnSettings)
        // 齿轮矢量图是白色，按主题主色着色后才能在浅色底上看清
        gear.setColorFilter(ThemeManager.brand(this))
        gear.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

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
        refreshProfile()
        refreshGuardian()
        buildModuleCards()
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

    /** 问候 + 资料行（姓名 / 年级 / 主题色） */
    private fun refreshProfile() {
        val name = prefs.childName
        val greeting = when (val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 0..11 -> getString(R.string.main_greeting_morning, name)
            in 12..17 -> getString(R.string.main_greeting_afternoon, name)
            else -> getString(R.string.main_greeting_evening, name)
        }
        findViewById<TextView>(R.id.tvGreeting).text = greeting

        // 首页只展示年级和今日战果；主题色选择放在设置页，首页不再出现
        val gradeLabel = Grade.of(prefs.grade).label
        prefs.ensureTodayRolled()
        findViewById<TextView>(R.id.tvProfile).text =
            getString(R.string.main_profile_today, gradeLabel, prefs.answeredToday)
    }

    // ---------- 守护额度卡 ----------

    private fun bindGuardian() {
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
        val density = resources.displayMetrics.density
        Subject.all.forEach { subject ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * density).toInt()
                setPadding(pad, pad, pad, pad)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(getColor(R.color.bg_card))
                    setStroke((4 * density).toInt(), getColor(subject.colorRes))
                    cornerRadius = 18 * density
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (12 * density).toInt() }
                layoutParams = lp
                isClickable = true
                isFocusable = true
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            // 卡通图标徽章：彩色圆 + 白色矢量图标
            val badgeSize = (56 * density).toInt()
            val badge = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                    marginEnd = (14 * density).toInt()
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(getColor(subject.colorRes))
                }
            }
            val iconSize = (30 * density).toInt()
            val icon = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply { gravity = Gravity.CENTER }
                setImageResource(subject.iconRes)
            }
            badge.addView(icon)

            // 中间：名称 + 进度
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
            }
            val name = TextView(this).apply {
                text = subject.label
                textSize = 18f
                setTextColor(getColor(R.color.text_main))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val done = prefs.getProgress(subject, grade)

            col.addView(name)

            // 当前学到的人教版单元名，让家长一眼看出进度落在哪一课
            // 音乐和脑筋急转弯不按单元排，unitOf 返回空串，这一行就不显示
            val unitName = if (subject.kind == Subject.Kind.QUIZ) {
                Curriculum.unitOf(subject, grade, done)
            } else ""
            if (unitName.isNotBlank()) {
                col.addView(TextView(this).apply {
                    text = "正在学：$unitName"
                    textSize = 12.5f
                    setTextColor(getColor(R.color.text_hint))
                    setPadding(0, (3 * density).toInt(), 0, 0)
                    maxLines = 1
                })
            }

            col.addView(TextView(this).apply {
                text = getString(R.string.main_module_progress, done, QUESTIONS_PER_GRADE)
                textSize = 13f
                setTextColor(getColor(subject.colorRes))
                setPadding(0, (4 * density).toInt(), 0, 0)
            })

            // 右侧：「去练习」卡通药丸
            val go = TextView(this).apply {
                text = "去练习 ›"
                textSize = 14f
                setTextColor(getColor(subject.colorRes))
                val h = (8 * density).toInt()
                val w = (14 * density).toInt()
                setPadding(w, h, w, h)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(getColor(subject.bgRes))
                    cornerRadius = 16 * density
                }
            }

            row.addView(badge)
            row.addView(col)
            row.addView(go)
            card.addView(row)

            // 进度条
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = QUESTIONS_PER_GRADE
                progress = done
                progressTintList = android.content.res.ColorStateList.valueOf(getColor(subject.colorRes))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (12 * density).toInt() }
                layoutParams = lp
            }
            card.addView(bar)

            card.setOnClickListener {
                startActivity(Intent(this, PracticeActivity::class.java).apply {
                    putExtra(PracticeActivity.EXTRA_SUBJECT, subject.key)
                })
            }
            container.addView(card)
        }
    }
}
