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
     * 把预置名单中**已安装**的应用补进配置。
     *
     * 两个规则：
     * 1. 只补**已安装**的 —— 避免家长在列表里看到一堆没装过的东西而困惑
     * 2. 只补**家长从没设置过**的 —— 只要 rules 里已经有这个包，就一律以家长的选择为准，绝不覆盖
     *
     * 用版本号 [PRESET_VERSION] 而不是一次性开关：名单每次扩充都会 upgrade 版本号，
     * 孩子后装的游戏也能在下次打开 App 时被自动补进来。
     */
    fun applyPresetsIfNeeded(context: Context) {
        val installed = getLauncherApps(context).map { it.pkg }.toSet()
        val rules = prefs.getAppRules()

        val newRestricted = PRESET_RESTRICTED.keys.filter { it in installed && !rules.containsKey(it) }
        val newLearning = PRESET_LEARNING.keys.filter { it in installed && !rules.containsKey(it) }

        newRestricted.forEach { rules[it] = AppRule(it, AppCategory.RESTRICTED) }
        newLearning.forEach { rules[it] = AppRule(it, AppCategory.LEARNING) }
        rules[SELF_PACKAGE] = AppRule(SELF_PACKAGE, AppCategory.LEARNING)

        if (newRestricted.isNotEmpty() || newLearning.isNotEmpty() ||
            prefs.presetVersion < PRESET_VERSION
        ) {
            if (newRestricted.isNotEmpty() || newLearning.isNotEmpty()) {
                prefs.saveAppRules(rules)
            }
            prefs.presetsApplied = true
            prefs.presetVersion = PRESET_VERSION
        }
    }

    /** 预置名单里的中文名，用于在列表中显示备注 */
    fun presetLabel(pkg: String): String? = PRESET_RESTRICTED[pkg] ?: PRESET_LEARNING[pkg]

    /**
     * 推荐名单：**不管有没有安装**都列出来。
     *
     * 为什么必须有这一页：原来的「最近使用 / 全部应用」两页都只显示已装的应用，
     * 于是王者荣耀、蛋仔派对这类主流游戏只要孩子还没装，家长就永远找不到、也没法提前拦。
     * 这里把预置名单全列出来，未安装的标上「（未安装）」，家长可以先归好类，
     * 日后孩子装上就自动按这个分类生效。
     */
    fun presetApps(context: Context): List<AppInfo> {
        val installed = getLauncherApps(context).map { it.pkg }.toSet()
        val rules = prefs.getAppRules()
        return (PRESET_RESTRICTED.toList() + PRESET_LEARNING.toList())
            .map { (pkg, label) ->
                val suggested =
                    if (PRESET_RESTRICTED.containsKey(pkg)) AppCategory.RESTRICTED else AppCategory.LEARNING
                AppInfo(
                    pkg = pkg,
                    label = label + if (pkg in installed) "" else "（未安装）",
                    category = rules[pkg]?.category ?: suggested
                )
            }
            // 游戏/短视频（受限）排最前，方便家长一眼找到要拦的
            .sortedWith(
                compareBy<AppInfo>(
                    { if (it.category == AppCategory.RESTRICTED) 0 else if (it.category == AppCategory.LEARNING) 1 else 2 },
                    { it.label }
                )
            )
    }

    /**
     * 一键把推荐名单里的娱乐类全部标记为受限、学习类标记为学习。
     * 由家长在「推荐名单」页手动点按钮触发，不是自动执行。
     */
    fun markAllPresets() {
        val rules = prefs.getAppRules()
        PRESET_RESTRICTED.keys.forEach { rules[it] = AppRule(it, AppCategory.RESTRICTED) }
        PRESET_LEARNING.keys.forEach { rules[it] = AppRule(it, AppCategory.LEARNING) }
        rules[SELF_PACKAGE] = AppRule(SELF_PACKAGE, AppCategory.LEARNING)
        prefs.saveAppRules(rules)
    }

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
         * 预置名单的版本号。
         * **每次在下面两个名单里加条目，都必须把这个数字 +1**，
         * 否则已经装过 App 的手机不会重新扫描，新加的包名不会生效。
         */
        const val PRESET_VERSION = 2

        /**
         * 预置的受限候选名单（娱乐类）。
         * 注意：包名可能随版本或渠道变化，这里只是让家长少勾选几次，
         * 最终以「最近使用」列表里家长手动勾选的结果为准。
         */
        private val PRESET_RESTRICTED = mapOf(
            // ---- 短视频 ----
            "com.ss.android.ugc.aweme" to "抖音",
            "com.ss.android.ugc.aweme.lite" to "抖音极速版",
            "com.smile.gifmaker" to "快手",
            "com.kuaishou.nebula" to "快手极速版",
            "com.ss.android.article.video" to "西瓜视频",
            // ---- 图文社区 / 资讯 ----
            "com.xingin.xhs" to "小红书",
            "com.ss.android.article.news" to "今日头条",
            "com.baidu.tieba" to "百度贴吧",
            // ---- 长视频 ----
            "tv.danmaku.bili" to "哔哩哔哩",
            "com.qiyi.video" to "爱奇艺",
            "com.youku.phone" to "优酷",
            "com.tencent.qqlive" to "腾讯视频",
            "com.sohu.sohuvideo" to "搜狐视频",
            "com.hunantv.imgotv" to "芒果TV",
            // ---- 游戏 ----
            "com.tencent.tmgp.sgame" to "王者荣耀",
            "com.tencent.tmgp.pubgmhd" to "和平精英",
            "com.tencent.tmgp.cf" to "穿越火线手游",
            "com.miHoYo.GenshinImpact" to "原神",
            "com.netease.party" to "蛋仔派对",
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
            "com.alibaba.android.rimet" to "钉钉",
            "com.tencent.wemeet.app" to "腾讯会议",
            "com.baidu.homework" to "作业帮"
        )
    }
}
