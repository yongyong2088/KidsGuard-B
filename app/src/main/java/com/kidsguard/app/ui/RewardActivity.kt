package com.kidsguard.app.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.kidsguard.app.R
import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.data.ThemeManager

/**
 * 奖励小屋。
 *
 * 展示孩子累计的星星，并提供三种兑换：
 *   - 娱乐时间（+10 分钟，直接追加当天额度）
 *   - 免一次弹窗（记录 freePass，下次被拦时自动放行）
 *   - 实物小奖励（需家长输入 PIN 确认，避免孩子自己乱换）
 *
 * 兑换成本在代码里用常量，方便后续调整。
 */
class RewardActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    companion object {
        private const val COST_TIME = 10
        private const val COST_PASS = 15
        private const val COST_GIFT = 30
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reward)

        prefs = PrefsManager(this)

        findViewById<TextView>(R.id.tvCostTime).text = getString(R.string.reward_cost, COST_TIME)
        findViewById<TextView>(R.id.tvCostPass).text = getString(R.string.reward_cost, COST_PASS)
        findViewById<TextView>(R.id.tvCostGift).text = getString(R.string.reward_cost, COST_GIFT)

        findViewById<android.widget.Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.Button>(R.id.btnExchangeTime).setOnClickListener { exchangeTime() }
        findViewById<android.widget.Button>(R.id.btnExchangePass).setOnClickListener { exchangePass() }
        findViewById<android.widget.Button>(R.id.btnExchangeGift).setOnClickListener { exchangeGift() }

        refreshStars()
    }

    override fun onResume() {
        super.onResume()
        refreshStars()
    }

    private fun refreshStars() {
        findViewById<TextView>(R.id.tvStars).text = getString(R.string.reward_cost, prefs.stars)
    }

    private fun exchangeTime() {
        if (!prefs.spendStars(COST_TIME)) {
            notEnough()
            return
        }
        // 等价于追加当天娱乐额度
        prefs.rewardEarnedMinutes()
        refreshStars()
        Snackbar.make(findViewById(R.id.tvStars), R.string.reward_exchanged_time, Snackbar.LENGTH_SHORT).show()
    }

    private fun exchangePass() {
        if (!prefs.spendStars(COST_PASS)) {
            notEnough()
            return
        }
        prefs.freePasses = prefs.freePasses + 1
        refreshStars()
        Snackbar.make(findViewById(R.id.tvStars), R.string.reward_exchanged_pass, Snackbar.LENGTH_SHORT).show()
    }

    private fun exchangeGift() {
        if (prefs.stars < COST_GIFT) {
            notEnough()
            return
        }
        // 实物奖励需要家长确认
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.reward_gift_pin_hint)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.reward_gift_pin_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (input.text.toString() == prefs.parentPin) {
                    prefs.spendStars(COST_GIFT)
                    refreshStars()
                    Snackbar.make(findViewById(R.id.tvStars), R.string.reward_gift_ok, Snackbar.LENGTH_LONG).show()
                } else {
                    Snackbar.make(findViewById(R.id.tvStars), R.string.parent_pin_wrong, Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun notEnough() {
        Snackbar.make(findViewById(R.id.tvStars), R.string.reward_not_enough, Snackbar.LENGTH_SHORT).show()
    }
}
