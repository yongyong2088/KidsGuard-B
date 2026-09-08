package com.kidsguard.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kidsguard.app.R
import com.kidsguard.app.data.Grade
import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.data.Subject
import com.kidsguard.app.data.ThemeManager

/**
 * 错题本查看页。
 *
 * 家长打开设置页 → 「错题本 (N 题)」进入这里 → 看到孩子最近做错的题。
 * - 显示每个错题：科目 /年级 /题号
 * - 可一键清空（二次确认）
 * - v16 之后会加「开始复习」入口，本期先只展示列表
 */
class WrongQuestionsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var container: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var headerCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wrong_questions)

        prefs = PrefsManager(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        container = findViewById(R.id.listContainer)
        emptyView = findViewById(R.id.tvEmpty)
        headerCount = findViewById(R.id.tvCount)

        findViewById<Button>(R.id.btnClear).setOnClickListener { confirmClear() }
        findViewById<Button>(R.id.btnPractice).setOnClickListener {
            Toast.makeText(this, "复习功能敬请期待（v16+）", Toast.LENGTH_SHORT).show()
        }

        renderList()
    }

    override fun onResume() {
        super.onResume()
        // 从练习页回到这里时（答对/答错更新了错题本），刷新列表
        renderList()
    }

    private fun renderList() {
        val list = prefs.wrongList()
        headerCount.text = getString(R.string.wrong_count, list.size)
        container.removeAllViews()

        if (list.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }
        emptyView.visibility = View.GONE
        container.visibility = View.VISIBLE

        val density = resources.displayMetrics.density
        val inflater = LayoutInflater.from(this)
        list.forEachIndexed { i, e ->
            val row = inflater.inflate(R.layout.item_wrong, container, false)
            val tvIdx = row.findViewById<TextView>(R.id.tvIdx)
            val tvTitle = row.findViewById<TextView>(R.id.tvTitle)
            val tvSub = row.findViewById<TextView>(R.id.tvSub)
            tvIdx.text = "${i + 1}."
            val subj = Subject.entries.firstOrNull { it.key == e.subject } ?: Subject.MATH
            val grade = Grade.of(e.grade)
            tvTitle.text = "${subj.label} · 第 ${e.index + 1} 题"
            tvSub.text = "年级：${grade.label} · 序号 ${e.index}"
            // 行间距
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (10 * density).toInt()
            row.layoutParams = lp
            container.addView(row)
        }
    }

    private fun confirmClear() {
        if (prefs.wrongCount == 0) {
            Toast.makeText(this, "错题本是空的", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.wrong_clear_title)
            .setMessage(R.string.wrong_clear_message)
            .setPositiveButton(R.string.wrong_clear_confirm) { _, _ ->
                prefs.clearWrong()
                Toast.makeText(this, R.string.wrong_cleared, Toast.LENGTH_SHORT).show()
                renderList()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }
}