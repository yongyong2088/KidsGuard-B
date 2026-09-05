package com.kidsguard.app.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kidsguard.app.R
import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.data.Subject
import com.kidsguard.app.data.ThemeManager
import com.kidsguard.app.databinding.ActivityParentBinding
import com.kidsguard.app.receiver.GuardDeviceAdminReceiver

/**
 * 家长模式。
 *
 * 所有规则配置都在这个界面，受密码保护。
 * 注意：这里的入口不能暴露给孩子，否则等于给了他一份绕过目标清单。
 */
class ParentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParentBinding
    private lateinit var prefs: PrefsManager
    private var unlocked = false

    private val adminComponent by lazy {
        ComponentName(this, GuardDeviceAdminReceiver::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityParentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        binding.btnUnlock.setOnClickListener { tryUnlock() }
        binding.etPin.setOnEditorActionListener { _, _, _ ->
            tryUnlock()
            true
        }
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnManageApps.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }
        binding.btnDeviceAdmin.setOnClickListener { activateDeviceAdmin() }
        binding.btnChangePin.setOnClickListener { showChangePinDialog() }
    }

    override fun onResume() {
        super.onResume()
        refreshAdminState()
    }

    private fun tryUnlock() {
        if (binding.etPin.text?.toString() == prefs.parentPin) {
            unlocked = true
            binding.layoutPin.visibility = View.GONE
            binding.layoutSettings.visibility = View.VISIBLE
            loadSettings()
        } else {
            Toast.makeText(this, R.string.parent_pin_wrong, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSettings() {
        binding.etQuota.setText(prefs.dailyQuotaMinutes.toString())
        binding.etIntervalNormal.setText(prefs.intervalNormalMinutes.toString())
        binding.etTightAfter.setText(prefs.tightAfterMinutes.toString())
        binding.etIntervalTight.setText(prefs.intervalTightMinutes.toString())
        binding.etStreak.setText(prefs.passStreak.toString())
        binding.etEarn.setText(prefs.earnMinutes.toString())
        binding.spinnerGrade.setSelection((prefs.grade - 1).coerceIn(0, 2))
        binding.spinnerSubject.setSelection(subjectIndex(prefs.lockSubject))
        binding.spinnerTheme.setSelection(themeIndex(prefs.theme))
    }

    private fun saveSettings() {
        binding.etQuota.text?.toString()?.toIntOrNull()?.let {
            prefs.dailyQuotaMinutes = it.coerceIn(0, 600)
        }
        binding.etIntervalNormal.text?.toString()?.toIntOrNull()?.let {
            prefs.intervalNormalMinutes = it.coerceIn(1, 60)
        }
        binding.etTightAfter.text?.toString()?.toIntOrNull()?.let {
            prefs.tightAfterMinutes = it.coerceIn(30, 600)
        }
        binding.etIntervalTight.text?.toString()?.toIntOrNull()?.let {
            prefs.intervalTightMinutes = it.coerceIn(1, 30)
        }
        binding.etStreak.text?.toString()?.toIntOrNull()?.let {
            prefs.passStreak = it.coerceIn(1, 20)
        }
        binding.etEarn.text?.toString()?.toIntOrNull()?.let {
            prefs.earnMinutes = it.coerceIn(1, 60)
        }
        prefs.grade = binding.spinnerGrade.selectedItemPosition + 1
        prefs.lockSubject = SUBJECT_VALUES[binding.spinnerSubject.selectedItemPosition]
        prefs.theme = THEME_VALUES[binding.spinnerTheme.selectedItemPosition]

        Toast.makeText(this, R.string.parent_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showChangePinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setHint(R.string.parent_pin_hint)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.parent_btn_change_pin)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.parent_btn_save) { _, _ ->
                val newPin = input.text?.toString().orEmpty()
                if (newPin.length >= 4) {
                    prefs.parentPin = newPin
                    Toast.makeText(this, R.string.parent_saved, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.parent_pin_too_short, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun activateDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        if (dpm?.isAdminActive(adminComponent) == true) {
            Toast.makeText(this, R.string.parent_device_admin_on, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "激活后孩子无法直接卸载本应用，也不会被一键清理掉"
            )
        }
        startActivity(intent)
    }

    private fun refreshAdminState() {
        if (!unlocked) return
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val active = dpm?.isAdminActive(adminComponent) ?: false
        binding.tvDeviceAdmin.visibility = if (active) View.VISIBLE else View.GONE
        if (active) {
            binding.tvDeviceAdmin.text = getString(R.string.parent_device_admin_on)
        }
    }

    private fun subjectIndex(value: String): Int {
        val idx = SUBJECT_VALUES.indexOf(value)
        return if (idx < 0) 0 else idx
    }

    private fun themeIndex(value: String): Int {
        val idx = THEME_VALUES.indexOf(value)
        return if (idx < 0) 0 else idx
    }

    companion object {
        private val SUBJECT_VALUES = arrayOf("math", "chinese", "english", "mixed")
        private val THEME_VALUES = arrayOf(
            ThemeManager.OCEAN, ThemeManager.FOREST, ThemeManager.SUNSET, ThemeManager.CANDY
        )
    }
}
