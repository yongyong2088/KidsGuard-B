package com.kidsguard.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 配置存储。全部使用 SharedPreferences，不引入第三方库、不依赖网络。
 */
class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("kidsguard_config", Context.MODE_PRIVATE)

    // ---------- 外观 ----------

    /** 主题：ocean / forest / sunset / candy */
    var theme: String
        get() = prefs.getString(KEY_THEME, "ocean") ?: "ocean"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /** 孩子姓名：首页问候与资料卡使用，默认「小朋友」 */
    var childName: String
        get() = prefs.getString(KEY_CHILD_NAME, "小朋友") ?: "小朋友"
        set(value) = prefs.edit().putString(KEY_CHILD_NAME, value.ifBlank { "小朋友" }).apply()

    // ---------- 弹窗规则（三段式） ----------

    /**
     * 第一段：0 到 dailyQuotaMinutes 分钟，自由使用，完全不弹窗。
     * 这是给孩子的「确定性」——他知道这段时间内是安全的。
     */
    var dailyQuotaMinutes: Int
        get() = prefs.getInt(KEY_DAILY_QUOTA, 60)
        set(value) = prefs.edit().putInt(KEY_DAILY_QUOTA, value).apply()

    /** 第二段：超出额度后，每玩满这么多分钟弹一次窗 */
    var intervalNormalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL_NORMAL, 10)
        set(value) = prefs.edit().putInt(KEY_INTERVAL_NORMAL, value).apply()

    /** 第三段：当天总时长达到这个数之后，收紧到 intervalTightMinutes */
    var tightAfterMinutes: Int
        get() = prefs.getInt(KEY_TIGHT_AFTER, 90)
        set(value) = prefs.edit().putInt(KEY_TIGHT_AFTER, value).apply()

    /** 第三段的弹窗间隔 */
    var intervalTightMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL_TIGHT, 5)
        set(value) = prefs.edit().putInt(KEY_INTERVAL_TIGHT, value).apply()

    /** 一次弹窗答对后换取的娱乐时长（分钟） */
    var earnMinutes: Int
        get() = prefs.getInt(KEY_EARN_MINUTES, 10)
        set(value) = prefs.edit().putInt(KEY_EARN_MINUTES, value).apply()

    /** 解锁需要连续答对几题 */
    var passStreak: Int
        get() = prefs.getInt(KEY_PASS_STREAK, 3)
        set(value) = prefs.edit().putInt(KEY_PASS_STREAK, value).apply()

    /** 每题限时（秒） */
    var secondsPerQuestion: Int
        get() = prefs.getInt(KEY_SECONDS_PER_Q, 30)
        set(value) = prefs.edit().putInt(KEY_SECONDS_PER_Q, value).apply()

    /**
     * 当前处于第几阶段：
     * 0 = 额度内（不弹窗）、1 = 常规拦截（每 10 分钟）、2 = 加密拦截（每 5 分钟）
     */
    fun lockStage(): Int {
        ensureTodayRolled()
        return when {
            usedMinutesToday < dailyQuotaMinutes -> 0
            usedMinutesToday < tightAfterMinutes -> 1
            else -> 2
        }
    }

    /** 当前阶段下，弹窗之间应间隔多少分钟。返回 0 表示不弹窗 */
    fun currentIntervalMinutes(): Int = when (lockStage()) {
        0 -> 0
        1 -> intervalNormalMinutes
        else -> intervalTightMinutes
    }

    /** 上一次弹窗之后累计使用的分钟数，达到 currentIntervalMinutes 就再弹一次 */
    var minutesSinceLastLock: Int
        get() = prefs.getInt(KEY_SINCE_LOCK, 0)
        set(value) = prefs.edit().putInt(KEY_SINCE_LOCK, value.coerceAtLeast(0)).apply()

    /** 兼容旧字段：连续使用多少分钟后弹窗（不再用于判定，保留给家长端展示） */
    var thresholdMinutes: Int
        get() = prefs.getInt(KEY_THRESHOLD, 10)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value).apply()

    // ---------- 学习 ----------

    /** 孩子年级，决定出题范围 */
    var grade: Int
        get() = prefs.getInt(KEY_GRADE, 1)
        set(value) = prefs.edit().putInt(KEY_GRADE, value).apply()

    /** 弹窗出题科目：math / chinese / english / mixed */
    var lockSubject: String
        get() = prefs.getString(KEY_LOCK_SUBJECT, "mixed") ?: "mixed"
        set(value) = prefs.edit().putString(KEY_LOCK_SUBJECT, value).apply()

    /**
     * 某模块某年级已完成到第几题（0..600）。
     * 题目由题号确定性生成，所以只需要记住进度数字，不需要记住做过哪些题。
     */
    fun getProgress(subject: Subject, grade: Int): Int {
        ensureTodayRolled()
        return prefs.getInt(keyProgress(subject, grade), 0)
    }

    fun setProgress(subject: Subject, grade: Int, done: Int) {
        prefs.edit()
            .putInt(keyProgress(subject, grade), done.coerceIn(0, QUESTIONS_PER_GRADE))
            .apply()
    }

    fun addProgress(subject: Subject, grade: Int) {
        setProgress(subject, grade, getProgress(subject, grade) + 1)
    }

    private fun keyProgress(subject: Subject, grade: Int) = "prog_${subject.key}_$grade"

    /** 今日学习分钟数（跨天清零） */
    var learnedMinutesToday: Int
        get() = prefs.getInt(KEY_LEARNED_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_LEARNED_MINUTES, value).apply()

    /** 连续学习天数 */
    var streakDays: Int
        get() = prefs.getInt(KEY_STREAK_DAYS, 0)
        set(value) = prefs.edit().putInt(KEY_STREAK_DAYS, value).apply()

    /** 今天累计答了多少题（主界面展示用） */
    var answeredToday: Int
        get() = prefs.getInt(KEY_ANSWERED_TODAY, 0)
        set(value) = prefs.edit().putInt(KEY_ANSWERED_TODAY, value).apply()

    /** 今日完成的运动打卡次数 */
    var sportDoneToday: Int
        get() = prefs.getInt(KEY_SPORT_TODAY, 0)
        set(value) = prefs.edit().putInt(KEY_SPORT_TODAY, value).apply()

    // ---------- 监控开关 ----------

    var monitorEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITOR_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITOR_ENABLED, value).apply()

    var presetsApplied: Boolean
        get() = prefs.getBoolean(KEY_PRESETS_APPLIED, false)
        set(value) = prefs.edit().putBoolean(KEY_PRESETS_APPLIED, value).apply()

    // ---------- 应用归类 ----------

    fun getAppRules(): MutableMap<String, AppRule> {
        val raw = prefs.getString(KEY_APP_RULES, null) ?: return mutableMapOf()
        return try {
            val arr = JSONArray(raw)
            val map = mutableMapOf<String, AppRule>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pkg = o.getString("pkg")
                map[pkg] = AppRule(
                    packageName = pkg,
                    category = AppCategory.fromKey(o.optString("cat", "unknown")),
                    thresholdOverride = o.optInt("thr", 0)
                )
            }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun saveAppRules(rules: Map<String, AppRule>) {
        val arr = JSONArray()
        rules.values.forEach { rule ->
            val o = JSONObject()
            o.put("pkg", rule.packageName)
            o.put("cat", rule.category.key)
            o.put("thr", rule.thresholdOverride)
            arr.put(o)
        }
        prefs.edit().putString(KEY_APP_RULES, arr.toString()).apply()
    }

    fun setCategory(packageName: String, category: AppCategory) {
        val rules = getAppRules()
        val old = rules[packageName]
        rules[packageName] = AppRule(packageName, category, old?.thresholdOverride ?: 0)
        saveAppRules(rules)
    }

    fun getCategory(packageName: String): AppCategory =
        getAppRules()[packageName]?.category ?: AppCategory.UNKNOWN

    // ---------- 今日用量 ----------

    private var usageDate: String
        get() = prefs.getString(KEY_USAGE_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USAGE_DATE, value).apply()

    var usedMinutesToday: Int
        get() = prefs.getInt(KEY_USED_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_USED_MINUTES, value).apply()

    /** 每次读取或写入用量前调用，日期变了就清零当日数据 */
    fun ensureTodayRolled() {
        val today = LocalDate.now().toString()
        if (usageDate != today) {
            usageDate = today
            usedMinutesToday = 0
            learnedMinutesToday = 0
            answeredToday = 0
            sportDoneToday = 0
            minutesSinceLastLock = 0
        }
    }

    fun addUsedSeconds(seconds: Int) {
        ensureTodayRolled()
        val addedMinutes = seconds / 60
        if (addedMinutes > 0) {
            usedMinutesToday += addedMinutes
            minutesSinceLastLock += addedMinutes
        }
    }

    fun remainingMinutes(): Int {
        ensureTodayRolled()
        return (dailyQuotaMinutes - usedMinutesToday).coerceAtLeast(0)
    }

    fun quotaExhausted(): Boolean {
        ensureTodayRolled()
        return usedMinutesToday >= dailyQuotaMinutes
    }

    /** 答对换时长：把已用时长往回减，等价于追加额度 */
    fun rewardEarnedMinutes() {
        ensureTodayRolled()
        usedMinutesToday = (usedMinutesToday - earnMinutes).coerceAtLeast(0)
        minutesSinceLastLock = 0
    }

    // ---------- 防改系统时间 ----------

    private var anchorWallClock: Long
        get() = prefs.getLong(KEY_ANCHOR_WALL, 0L)
        set(value) = prefs.edit().putLong(KEY_ANCHOR_WALL, value).apply()

    private var anchorElapsed: Long
        get() = prefs.getLong(KEY_ANCHOR_ELAPSED, 0L)
        set(value) = prefs.edit().putLong(KEY_ANCHOR_ELAPSED, value).apply()

    var timeTamperDetected: Boolean
        get() = prefs.getBoolean(KEY_TIME_TAMPER, false)
        set(value) = prefs.edit().putBoolean(KEY_TIME_TAMPER, value).apply()

    fun updateTimeAnchor() {
        val wall = System.currentTimeMillis()
        val elapsed = android.os.SystemClock.elapsedRealtime()
        val prevWall = anchorWallClock
        val prevElapsed = anchorElapsed
        if (prevWall > 0 && prevElapsed > 0) {
            val wallDelta = wall - prevWall
            val elapsedDelta = elapsed - prevElapsed
            // 单调时钟前进了，但系统时间反而倒退超过 5 分钟，判定为被修改
            if (elapsedDelta > 0 && wallDelta < -5 * 60 * 1000) {
                timeTamperDetected = true
            }
        }
        anchorWallClock = wall
        anchorElapsed = elapsed
    }

    // ---------- 奖励星星 ----------

    /** 星星：答对题目 / 完成运动获得，可兑换奖励 */
    var stars: Int
        get() = prefs.getInt(KEY_STARS, 0)
        set(value) = prefs.edit().putInt(KEY_STARS, value.coerceAtLeast(0)).apply()

    /** 免一次弹窗的次数 */
    var freePasses: Int
        get() = prefs.getInt(KEY_FREE_PASSES, 0)
        set(value) = prefs.edit().putInt(KEY_FREE_PASSES, value.coerceAtLeast(0)).apply()

    fun addStars(delta: Int) {
        if (delta > 0) stars += delta
    }

    /** 消费星星，余额不足返回 false */
    fun spendStars(cost: Int): Boolean {
        if (stars < cost) return false
        stars -= cost
        return true
    }

    /** 消耗一次免弹窗机会，没有则返回 false */
    fun consumeFreePass(): Boolean {
        if (freePasses <= 0) return false
        freePasses--
        return true
    }

    companion object {
        private const val DEFAULT_PIN = "1234"

        private const val KEY_PIN = "pin"
        private const val KEY_THEME = "theme"
        private const val KEY_CHILD_NAME = "child_name"
        private const val KEY_THRESHOLD = "threshold_minutes"
        private const val KEY_DAILY_QUOTA = "daily_quota_minutes"
        private const val KEY_INTERVAL_NORMAL = "interval_normal"
        private const val KEY_INTERVAL_TIGHT = "interval_tight"
        private const val KEY_TIGHT_AFTER = "tight_after"
        private const val KEY_EARN_MINUTES = "earn_minutes"
        private const val KEY_PASS_STREAK = "pass_streak"
        private const val KEY_SECONDS_PER_Q = "seconds_per_question"
        private const val KEY_SINCE_LOCK = "minutes_since_lock"
        private const val KEY_GRADE = "grade"
        private const val KEY_LOCK_SUBJECT = "lock_subject"
        private const val KEY_LEARNED_MINUTES = "learned_minutes"
        private const val KEY_STREAK_DAYS = "streak_days"
        private const val KEY_ANSWERED_TODAY = "answered_today"
        private const val KEY_SPORT_TODAY = "sport_today"
        private const val KEY_MONITOR_ENABLED = "monitor_enabled"
        private const val KEY_STARS = "stars"
        private const val KEY_FREE_PASSES = "free_passes"
        private const val KEY_PRESETS_APPLIED = "presets_applied"
        private const val KEY_APP_RULES = "app_rules"
        private const val KEY_USAGE_DATE = "usage_date"
        private const val KEY_USED_MINUTES = "used_minutes"
        private const val KEY_ANCHOR_WALL = "anchor_wall"
        private const val KEY_ANCHOR_ELAPSED = "anchor_elapsed"
        private const val KEY_TIME_TAMPER = "time_tamper"
    }

    var parentPin: String
        get() = prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        set(value) = prefs.edit().putString(KEY_PIN, value).apply()
}
