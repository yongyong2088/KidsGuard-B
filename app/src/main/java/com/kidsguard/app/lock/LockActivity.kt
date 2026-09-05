package com.kidsguard.app.lock

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.kidsguard.app.R
import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.data.QuestionRepository
import com.kidsguard.app.databinding.ActivityLockBinding
import com.kidsguard.app.quiz.QuizEngine

/**
 * 全屏答题锁。
 *
 * 这是整个产品最关键的界面：不可关闭、不可返回、必须答对才能离开。
 * 面板样式可以随意调整，但下面三件事不要动，否则拦截会失效：
 *   1. onCreate 里的沉浸模式与锁屏显示设置
 *   2. 返回键拦截（blockBack）
 *   3. 只有通过答题才调用 finish()
 *
 * 三段式规则由 PrefsManager 统一判定：
 *   - 阶段 0：额度内，不会进到这里
 *   - 阶段 1：超出额度，每 10 分钟弹一次，答对 3 题换 10 分钟
 *   - 阶段 2：当天总时长超 90 分钟，收紧到每 5 分钟弹一次
 */
class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private lateinit var prefs: PrefsManager
    private lateinit var repo: QuestionRepository
    private lateinit var engine: QuizEngine

    private var countdown: CountDownTimer? = null
    private var blockedPackage: String = ""
    private var locked = false   // 是否已通过，防止重复 finish

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        repo = QuestionRepository(this)
        engine = QuizEngine(prefs, repo)
        // 当前处于第几阶段：1 或 2。阶段 0 不会触发弹窗
        engine.stage = prefs.lockStage().coerceAtLeast(1)

        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()

        setupImmersive()
        blockBack()
        tryLockTask()

        binding.tvReason.text = getString(
            R.string.lock_reason,
            appLabel(blockedPackage),
            prefs.minutesSinceLastLock
        )

        binding.btnSubmit.setOnClickListener { onSubmit() }
        binding.etAnswer.setOnEditorActionListener { _, _, _ ->
            onSubmit()
            true
        }
        binding.btnCallParent.setOnClickListener { onAskParent() }

        renderHeader()
        showNextQuestion()
    }

    override fun onDestroy() {
        countdown?.cancel()
        super.onDestroy()
    }

    // ---------- 拦截相关，改动前请先看完注释 ----------

    private fun setupImmersive() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun blockBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 故意留空：返回键被吞掉，这是「无法关闭」的第一道保障
            }
        })
    }

    /**
     * 尝试进入锁定任务模式。
     * 只有设备所有者（Device Owner）才能真正屏蔽 Home 键与多任务键；
     * 普通应用调用会被系统忽略，这里做容错处理，不影响其他功能。
     */
    private fun tryLockTask() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                    startLockTask()
                }
            }
        } catch (e: Exception) {
            // 非设备所有者环境下会抛异常，忽略即可
        }
    }

    // ---------- 答题流程 ----------

    private fun showNextQuestion() {
        val q = engine.nextQuestion()
        binding.tvQuestion.text = q.text
        binding.tvFeedback.text = ""
        binding.etAnswer.text?.clear()
        binding.etAnswer.isEnabled = true
        binding.btnSubmit.isEnabled = true

        if (engine.hasOptions()) {
            // 四选一：隐藏输入框，动态生成选项按钮
            binding.etAnswer.visibility = View.GONE
            buildOptionButtons(engine.currentOptions())
        } else {
            // 填空题：隐藏选项，显示输入框
            binding.optionsContainer.visibility = View.GONE
            binding.optionsContainer.removeAllViews()
            binding.etAnswer.visibility = View.VISIBLE
            binding.etAnswer.requestFocus()
        }

        startCountdown()
        renderHeader()
    }

    private fun buildOptionButtons(options: List<String>) {
        binding.optionsContainer.visibility = View.VISIBLE
        binding.optionsContainer.removeAllViews()
        val ctx = this
        for (opt in options) {
            val btn = Button(ctx).apply {
                text = opt
                textSize = 17f
                setBackgroundResource(R.drawable.btn_cartoon_option)
                setTextColor(resources.getColor(R.color.text_main, theme))
                setPadding(24, 18, 24, 18)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
                layoutParams = lp
                setOnClickListener { onSubmit(opt) }
            }
            binding.optionsContainer.addView(btn)
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        val total = prefs.secondsPerQuestion * 1000L
        countdown = object : CountDownTimer(total, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvCountdown.text = (millisUntilFinished / 1000).toString()
            }

            override fun onFinish() {
                binding.tvCountdown.text = "0"
                if (locked) return
                showFeedback(false, engine.currentAnswer())
                binding.etAnswer.isEnabled = false
                binding.btnSubmit.isEnabled = false
                postDelayedNext(1500)
            }
        }.start()
    }

    private fun onSubmit(picked: String? = null) {
        val input = picked ?: binding.etAnswer.text?.toString().orEmpty()
        if (picked == null && input.isBlank()) {
            Snackbar.make(binding.root, R.string.lock_empty_answer, Snackbar.LENGTH_SHORT).show()
            return
        }
        countdown?.cancel()
        val correct = engine.submit(input)
        binding.etAnswer.isEnabled = false
        binding.btnSubmit.isEnabled = false
        showFeedback(correct, engine.currentAnswer())

        if (correct) {
            prefs.addStars(1)
        }
        if (correct && engine.isPassed()) {
            onPassed()
            return
        }
        postDelayedNext(if (correct) 700 else 1500)
    }

    /** 达到解锁条件：把换来的时长写回（等价于追加娱乐额度），然后关闭 */
    private fun onPassed() {
        locked = true
        prefs.rewardEarnedMinutes()
        prefs.addStars(5)
        binding.tvFeedback.text = getString(R.string.lock_done)
        binding.tvFeedback.setTextColor(resources.getColor(R.color.learning, theme))
        binding.root.postDelayed({ finish() }, 800)
    }

    private fun showFeedback(correct: Boolean, answer: String) {
        binding.tvFeedback.text = if (correct) {
            getString(R.string.lock_correct)
        } else {
            getString(R.string.lock_wrong, answer)
        }
        binding.tvFeedback.setTextColor(
            resources.getColor(
                if (correct) R.color.learning else R.color.danger,
                theme
            )
        )
    }

    private fun postDelayedNext(delay: Long) {
        binding.root.postDelayed({ if (!locked) showNextQuestion() }, delay)
    }

    private fun renderHeader() {
        val need = prefs.passStreak
        binding.tvProgress.text = getString(R.string.lock_progress, engine.state.streak, need)
        binding.tvHint.text = getString(R.string.lock_hint, need)
    }

    // ---------- 找家长 ----------

    /**
     * 家长在场时可以输入密码直接放行。
     * 这不是后门——密码只有家长知道，用于「孩子在跟家长视频、家长同意再用一会儿」等真实场景。
     */
    private fun onAskParent() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.parent_pin_hint)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.parent_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.parent_btn_unlock) { _, _ ->
                if (input.text.toString() == prefs.parentPin) {
                    locked = true
                    finish()
                } else {
                    Snackbar.make(binding.root, R.string.parent_pin_wrong, Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun appLabel(pkg: String): String {
        if (pkg.isBlank()) return ""
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"

        fun launch(context: Context, packageName: String) {
            val intent = Intent(context, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_PACKAGE, packageName)
            }
            context.startActivity(intent)
        }
    }
}
