package com.kidsguard.app.data

import android.app.Activity
import android.content.Context
import androidx.core.content.res.use
import com.kidsguard.app.R

/**
 * 主题管理。
 *
 * Android 的主题只能在 Activity 创建时设置，所以每个界面在 super.onCreate() 之前
 * 调用 [apply]，切换主题后调用 [Activity.recreate()] 才会生效。
 *
 * 答题锁界面不使用主题（在 Manifest 里写死了 Theme.KidsGuard.Lock），
 * 避免孩子通过换主题去影响拦截界面。
 */
object ThemeManager {

    const val OCEAN = "ocean"
    const val FOREST = "forest"
    const val SUNSET = "sunset"
    const val CANDY = "candy"

    val all: List<Pair<String, String>> = listOf(
        OCEAN to "海洋蓝",
        FOREST to "森林绿",
        SUNSET to "暖阳橙",
        CANDY to "糖果马卡龙"
    )

    /** 主题 key → style 资源 */
    fun themeRes(key: String): Int = when (key) {
        FOREST -> R.style.Theme_KidsGuard_Forest
        SUNSET -> R.style.Theme_KidsGuard_Sunset
        CANDY -> R.style.Theme_KidsGuard_Candy
        else -> R.style.Theme_KidsGuard_Ocean
    }

    fun label(key: String): String = all.firstOrNull { it.first == key }?.second ?: "海洋蓝"

    /** 必须在 super.onCreate() 之前调用 */
    fun apply(activity: Activity) {
        activity.setTheme(themeRes(PrefsManager(activity).theme))
    }

    /** 切换主题并立即重建当前界面 */
    fun switchTo(activity: Activity, key: String) {
        PrefsManager(activity).theme = key
        activity.recreate()
    }

    // ---------- 在代码里读主题色 ----------

    fun color(context: Context, attrRes: Int): Int {
        val typedArray = context.obtainStyledAttributes(intArrayOf(attrRes))
        return typedArray.use { it.getColor(0, 0) }
    }

    fun brand(context: Context): Int = color(context, R.attr.kgBrand)
    fun brandLight(context: Context): Int = color(context, R.attr.kgBrandLight)
    fun pageBg(context: Context): Int = color(context, R.attr.kgPageBg)
    fun cardBg(context: Context): Int = color(context, R.attr.kgCardBg)
}
