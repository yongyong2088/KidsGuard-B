package com.kidsguard.app.monitor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.kidsguard.app.data.AppCategory
import com.kidsguard.app.data.AppRule
import com.kidsguard.app.data.PrefsManager

/**
 * 应用分类器。
 *
 * 判定原则：**只信任家长手动指定的结果，不做任何自动识别。**
 * 这样从根本上排除了把学习软件误判成游戏的可能。
 */
class AppClassifier(private val prefs: PrefsManager) {

    /** 判定分类。本应用自身永远视为学习类，避免自己拦自己 */
    fun classify(packageName: String): AppCategory {
        if (packageName == SELF_PACKAGE) return AppCategory.LEARNING
        return prefs.getCategory(packageName)
    }

    /** 该应用实际生效的触发阈值：优先用单独配置的，否则跟随全局 */
    fun thresholdFor(packageName: String): Int {
        val override = prefs.getAppRules()[packageName]?.thresholdOverride ?: 0
        return if (override > 0) override else prefs.thresholdMinutes
    }

    /**
     * 首次启动时把预置名单中**已安装**的应用写入配置，之后一律以家长手动修改为准。
     * 只写入已安装的应用，避免家长在列表里看到一堆没装过的东西而困惑。
     */
    fun applyPresetsIfNeeded(context: Context) {
        if (prefs.presetsApplied) return
        val installed = getLauncherApps(context).map { it.pkg }.toSet()
        val rules = prefs.getAppRules()

        PRESET_RESTRICTED.keys.filter { it in installed }
            .forEach { rules[it] = AppRule(it, AppCategory.RESTRICTED) }
        PRESET_LEARNING.keys.filter { it in installed }
            .forEach { rules[it] = AppRule(it, AppCategory.LEARNING) }
        rules[SELF_PACKAGE] = AppRule(SELF_PACKAGE, AppCategory.LEARNING)

        prefs.saveAppRules(rules)
        prefs.presetsApplied = true
    }

    /** 预置名单里的中文名，用于在列表中显示备注 */
    fun presetLabel(pkg: String): String? = PRESET_RESTRICTED[pkg] ?: PRESET_LEARNING[pkg]

    data class AppInfo(val pkg: String, val label: String, val category: AppCategory)

    /**
     * 获取设备上有桌面图标的应用。
     * 依赖 Manifest 中的 queries 声明，否则 Android 11+ 上只能拿到空列表。
     */
    fun getLauncherApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (e: Exception) {
            emptyList()
        }
        return resolved.map { ri ->
            val pkg = ri.activityInfo.packageName
            AppInfo(
                pkg = pkg,
                label = ri.loadLabel(pm).toString(),
                category = classify(pkg)
            )
        }.distinctBy { it.pkg }.sortedBy { it.label }
    }

    companion object {
        const val SELF_PACKAGE = "com.kidsguard.app"

        /**
         * 预置的受限候选名单（娱乐类）。
         * 注意：包名可能随版本或渠道变化，这里只是让家长少勾选几次，
         * 最终以「最近使用」列表里家长手动勾选的结果为准。
         */
        private val PRESET_RESTRICTED = mapOf(
            "com.ss.android.ugc.aweme" to "抖音",
            "com.ss.android.ugc.aweme.lite" to "抖音极速版",
            "com.smile.gifmaker" to "快手",
            "com.kuaishou.nebula" to "快手极速版",
            "tv.danmaku.bili" to "哔哩哔哩",
            "com.xingin.xhs" to "小红书",
            "com.qiyi.video" to "爱奇艺",
            "com.youku.phone" to "优酷",
            "com.tencent.qqlive" to "腾讯视频",
            "com.tencent.tmgp.sgame" to "王者荣耀",
            "com.tencent.tmgp.pubgmhd" to "和平精英",
            "com.tencent.tmgp.cf" to "穿越火线手游",
            "com.miHoYo.GenshinImpact" to "原神",
            "com.minitech.miniworld" to "迷你世界",
            "com.mojang.minecraftpe" to "我的世界",
            "com.popcap.pvz2" to "植物大战僵尸2",
            "com.kiloo.subwaysurf" to "地铁跑酷",
            "com.imangi.templerun" to "神庙逃亡",
            "com.rovio.angrybirds" to "愤怒的小鸟"
        )

        /**
         * 预置的学习类名单。这些应用永不弹窗、不计时。
         * 网课类（钉钉、腾讯会议）优先级最高，一旦被弹窗打断后果严重。
         */
        private val PRESET_LEARNING = mapOf(
            "com.dingtalk.android" to "钉钉",
            "com.tencent.wemeet.app" to "腾讯会议",
            "com.baidu.homework" to "作业帮"
        )
    }
}
