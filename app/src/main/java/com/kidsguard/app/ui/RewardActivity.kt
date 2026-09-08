package com.kidsguard.app.ui

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.kidsguard.app.R
import com.kidsguard.app.data.CustomReward
import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.data.ThemeManager
import java.util.UUID

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
        private const val COST_TIME = 50
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
        findViewById<android.widget.Button>(R.id.btnAddReward).setOnClickListener { addReward() }

        refreshStars()
        renderCustomRewards()
    }

    override fun onResume() {
        super.onResume()
        refreshStars()
        renderCustomRewards()
    }

    private fun refreshStars() {
        findViewById<TextView>(R.id.tvStars).text = getString(R.string.reward_cost, prefs.stars)
    }

    private fun renderCustomRewards() {
        val container = findViewById<LinearLayout>(R.id.customContainer)
        container.removeAllViews()
        val density = resources.displayMetrics.density
        prefs.customRewards.forEach { reward ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val pad = (12 * density).toInt()
                setPadding(pad, pad, pad, pad)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(getColor(R.color.bg_card))
                    cornerRadius = 14 * density
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * density).toInt() }
            }
            row.addView(TextView(this).apply {
                text = "${reward.emoji} ${reward.name}"
                textSize = 16f
                setTextColor(getColor(R.color.text_main))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
            })
            row.addView(TextView(this).apply {
                text = getString(R.string.reward_cost, reward.cost)
                textSize = 14f
                setTextColor(getColor(R.color.text_hint))
                setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            })
            row.addView(Button(this).apply {
                text = getString(R.string.reward_btn_delete)
                textSize = 12f
                setTextColor(getColor(R.color.text_hint))
                setOnClickListener { confirmDelete(reward) }
            })
            container.addView(row)
        }
    }

    private fun confirmDelete(reward: CustomReward) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.reward_btn_delete))
            .setMessage(getString(R.string.reward_delete_confirm, reward.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.removeCustomReward(reward.id)
                renderCustomRewards()
            }
            .show()
    }

    private fun addReward() {
        val nameInput = EditText(this).apply {
            hint = getString(R.string.reward_add_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val costInput = EditText(this).apply {
            hint = getString(R.string.reward_add_cost_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(nameInput)
            addView(costInput)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.reward_add_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput.text.toString().trim()
                val cost = costInput.text.toString().trim().toIntOrNull()
                if (name.isBlank() || cost == null || cost <= 0) {
                    Snackbar.make(findViewById(R.id.tvStars), R.string.reward_cost_invalid, Snackbar.LENGTH_SHORT).show()
                } else {
                    prefs.addCustomReward(CustomReward(id = UUID.randomUUID().toString(), name = name, cost = cost))
                    renderCustomRewards()
                }
            }
            .show()
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
