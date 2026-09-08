package com.kidsguard.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kidsguard.app.R
import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.data.ThemeManager

/**
 * 我的资料（设置页）。
 *
 * 登记孩子姓名、年级、喜欢的界面颜色。
 * 保存后直接重启首页，让新的主题色立即生效。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    private var selectedGrade: Int = 1
    private var selectedTheme: String = ThemeManager.OCEAN

    private val gradeChips = listOf(
        R.id.btnGrade1 to 1,
        R.id.btnGrade2 to 2,
        R.id.btnGrade3 to 3
    )

    private val themeSwatches = listOf(
        R.id.swatchOcean to ThemeManager.OCEAN,
        R.id.swatchForest to ThemeManager.FOREST,
        R.id.swatchSunset to ThemeManager.SUNSET,
        R.id.swatchCandy to ThemeManager.CANDY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = PrefsManager(this)
        selectedGrade = prefs.grade
        selectedTheme = prefs.theme

        // 预填已登记的名字
        findViewById<EditText>(R.id.etName).setText(prefs.childName)

        gradeChips.forEach { (id, grade) ->
            findViewById<Button>(id).setOnClickListener {
                selectedGrade = grade
                highlightGrade()
            }
        }

        themeSwatches.forEach { (id, key) ->
            findViewById<FrameLayout>(id).setOnClickListener {
                selectedTheme = key
                highlightTheme()
            }
        }

        highlightGrade()
        highlightTheme()

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnWrong).setOnClickListener {
            startActivity(Intent(this, WrongQuestionsActivity::class.java))
        }
        // 错题数会随答题变化，每次进入设置页都刷新
        findViewById<Button>(R.id.btnWrong).text =
            getString(R.string.settings_wrong_btn, prefs.wrongCount)
    }

    override fun onResume() {
        super.onResume()
        // 从错题本页返回后，错题数可能变了，刷新按钮文案
        findViewById<Button>(R.id.btnWrong).text =
            getString(R.string.settings_wrong_btn, prefs.wrongCount)
    }

    private fun highlightGrade() {
        val brand = ThemeManager.brand(this)
        gradeChips.forEach { (id, grade) ->
            val btn = findViewById<Button>(id)
            val active = grade == selectedGrade
            btn.setBackgroundColor(if (active) brand else Color.parseColor("#EDEDEA"))
            btn.setTextColor(if (active) Color.WHITE else getColor(R.color.text_sub))
        }
    }

    private fun highlightTheme() {
        val brand = ThemeManager.brand(this)
        val density = resources.displayMetrics.density
        themeSwatches.forEach { (id, key) ->
            val frame = findViewById<FrameLayout>(id)
            val active = key == selectedTheme
            frame.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(getColor(R.color.bg_card))
                if (active) setStroke((3 * density).toInt(), brand)
                cornerRadius = 14 * density
            }
        }
    }

    private fun save() {
        val name = findViewById<EditText>(R.id.etName).text?.toString().orEmpty().trim()
            .ifBlank { "小朋友" }

        prefs.childName = name
        prefs.grade = selectedGrade
        prefs.theme = selectedTheme

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()

        // 重启首页，使新主题色立即生效
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
