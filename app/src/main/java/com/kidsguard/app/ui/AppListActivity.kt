package com.kidsguard.app.ui

import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kidsguard.app.R
import com.kidsguard.app.data.AppCategory
import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.databinding.ActivityAppListBinding
import com.kidsguard.app.databinding.ItemAppBinding
import com.kidsguard.app.monitor.AppClassifier

/**
 * 应用归类管理。
 *
 * 三个入口的设计意图：
 * 1. 最近使用 —— 家长往往说不清孩子玩的那些游戏叫什么，在「最近用过」里一眼就能认出来，最实用
 * 2. 全部应用 —— 兜底
 * 3. 预置名单 —— 已安装在设备上的常见游戏/短视频，首次启动时自动标记
 */
class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var prefs: PrefsManager
    private lateinit var classifier: AppClassifier
    private lateinit var adapter: AppAdapter

    private var showRecent = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        classifier = AppClassifier(prefs)

        adapter = AppAdapter { pkg, category ->
            prefs.setCategory(pkg, category)
            reload()
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnRecent.setOnClickListener {
            showRecent = true
            updateFilterButtons()
            reload()
        }
        binding.btnAll.setOnClickListener {
            showRecent = false
            updateFilterButtons()
            reload()
        }

        updateFilterButtons()
        reload()
    }

    private fun updateFilterButtons() {
        binding.btnRecent.setTextColor(resources.getColor(if (showRecent) R.color.brand else R.color.text_hint, theme))
        binding.btnAll.setTextColor(resources.getColor(if (showRecent) R.color.text_hint else R.color.brand, theme))
    }

    private fun reload() {
        val list = if (showRecent) getRecentApps() else classifier.getLauncherApps(this)
        adapter.submit(list)
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 近 7 天实际使用过的应用，按使用时长倒序 */
    private fun getRecentApps(): List<AppClassifier.AppInfo> {
        if (!com.kidsguard.app.monitor.UsageMonitorService.hasUsagePermission(this)) {
            return emptyList()
        }
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val weekAgo = now - 7L * 24 * 60 * 60 * 1000
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, weekAgo, now)
            val pm = packageManager
            stats.filter { it.totalTimeInForeground > 60_000L }
                .sortedByDescending { it.totalTimeInForeground }
                .mapNotNull { s ->
                    try {
                        val info = pm.getApplicationInfo(s.packageName, 0)
                        AppClassifier.AppInfo(
                            pkg = s.packageName,
                            label = pm.getApplicationLabel(info).toString(),
                            category = classifier.classify(s.packageName)
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                .distinctBy { it.pkg }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private class AppAdapter(
    private val onCategoryChanged: (String, AppCategory) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private val items = mutableListOf<AppClassifier.AppInfo>()

    fun submit(list: List<AppClassifier.AppInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.binding
        val ctx = holder.itemView.context

        b.tvName.text = item.label
        // 包名后面附上预置名单里的中文备注，方便家长对照
        b.tvPkg.text = item.pkg

        tint(ctx, b.btnLearning, item.category == AppCategory.LEARNING)
        tint(ctx, b.btnRestricted, item.category == AppCategory.RESTRICTED)
        tint(ctx, b.btnUnknown, item.category == AppCategory.UNKNOWN)

        b.btnLearning.setOnClickListener { onCategoryChanged(item.pkg, AppCategory.LEARNING) }
        b.btnRestricted.setOnClickListener { onCategoryChanged(item.pkg, AppCategory.RESTRICTED) }
        b.btnUnknown.setOnClickListener { onCategoryChanged(item.pkg, AppCategory.UNKNOWN) }
    }

    private fun tint(ctx: Context, button: android.widget.Button, active: Boolean) {
        if (active) {
            val color = when (button.id) {
                R.id.btnLearning -> ctx.getColor(R.color.learning)
                R.id.btnRestricted -> ctx.getColor(R.color.restricted)
                else -> ctx.getColor(R.color.unknown)
            }
            button.setBackgroundColor(color)
            button.setTextColor(Color.WHITE)
        } else {
            button.setBackgroundColor(Color.parseColor("#F1EFE8"))
            button.setTextColor(ctx.getColor(R.color.text_sub))
        }
    }

    class VH(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)
}
