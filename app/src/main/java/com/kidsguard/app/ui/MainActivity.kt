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
import com.kidsguard.app.quiz.Curriculum

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

        // 卡通渐变头图：用主题主色到深色的渐变，让首页第一眼就明亮活泼
        findViewById<View>(R.id.headerCard).background =
            android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(ThemeManager.brand(this), ThemeManager.brandDark(this))
            ).apply { cornerRadius = 22 * resources.displayMetrics.density }

        bindGuardian()
        buildModuleCards()

        val gear = findViewById<android.widget.ImageButton>(R.id.btnSettings)
        // 图标已自带卡通配色，不要再着色，否则颜色会被盖掉
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
        val gap = (12 * density).toInt()
        val mods = Subject.all

        // 两列排布：一屏就能看全六个模块，更像孩子会喜欢的学习 App
        for (i in mods.indices step 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = gap }
            }

            val left = moduleCard(mods[i], grade, density)
            left.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(left)

            val rightLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = gap }
            if (i + 1 < mods.size) {
                val right = moduleCard(mods[i + 1], grade, density)
                right.layoutParams = rightLp
                row.addView(right)
            } else {
                // 奇数个时补一个等宽占位，保证左卡片不会被拉宽
                row.addView(View(this).apply { layoutParams = rightLp })
            }
            container.addView(row)
        }
    }

    /** 单个模块卡片：卡通图标 + 名称 + 单元 + 进度条 */
    private fun moduleCard(subject: Subject, grade: Int, density: Float): LinearLayout {
        val done = prefs.getProgress(subject, grade)
        val pad = (12 * density).toInt()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, (14 * density).toInt(), pad, pad)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(getColor(R.color.bg_card))
                setStroke((2 * density).toInt(), getColor(subject.colorRes))
                cornerRadius = 18 * density
            }
            isClickable = true
            isFocusable = true
        }

        // 卡通图标徽章：浅色圆角方块 + 彩色描边，中间放彩色卡通图标
        val badgeSize = (58 * density).toInt()
        val badge = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(getColor(subject.bgRes))
                setStroke((2.5 * density).toInt(), getColor(subject.colorRes))
                cornerRadius = 17 * density
            }
        }
        val iconSize = (38 * density).toInt()
        badge.addView(ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply { gravity = Gravity.CENTER }
            setImageResource(subject.iconRes)
        })
        card.addView(badge)

        card.addView(TextView(this).apply {
            text = subject.label
            textSize = 15.5f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(getColor(R.color.text_main))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (9 * density).toInt(), 0, 0)
        })

        // 当前学到的人教版单元名；音乐/脑筋急转弯没有单元，这一行不显示
        val unitName = if (subject.kind == Subject.Kind.QUIZ) {
            Curriculum.unitOf(subject, grade, done)
        } else ""
        if (unitName.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = unitName
                textSize = 11f
                gravity = Gravity.CENTER_HORIZONTAL
                setTextColor(getColor(R.color.text_hint))
                setPadding(0, (3 * density).toInt(), 0, 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }

        card.addView(TextView(this).apply {
            text = "$done/${QUESTIONS_PER_GRADE}"
            textSize = 12f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(getColor(subject.colorRes))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (6 * density).toInt(), 0, 0)
        })

        card.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = QUESTIONS_PER_GRADE
            progress = done
            progressTintList = android.content.res.ColorStateList.valueOf(getColor(subject.colorRes))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
        })

        card.setOnClickListener {
            startActivity(Intent(this, PracticeActivity::class.java).apply {
                putExtra(PracticeActivity.EXTRA_SUBJECT, subject.key)
            })
        }
        return card
    }
}
