package com.kidsguard.app.monitor

import android.os.SystemClock

/**
 * 连续使用计时器。
 *
 * 关键点：计时全部基于 SystemClock.elapsedRealtime()（设备开机以来的单调时钟），
 * 修改系统时间对它完全无效，这是防绕过的基础。
 */
class TimeTracker {

    private var currentPackage: String? = null
    private var sessionStartElapsed: Long = 0L
    /** 累计未结算的秒数，满 60 秒才写入每日用量 */
    private var pendingSeconds: Int = 0

    /**
     * 前台应用变化时调用。
     * @return 当前这个应用本次连续的秒数
     */
    fun onForeground(packageName: String?): Long {
        val now = SystemClock.elapsedRealtime()
        if (packageName != currentPackage) {
            currentPackage = packageName
            sessionStartElapsed = now
            pendingSeconds = 0
            return 0L
        }
        if (packageName == null) return 0L
        return (now - sessionStartElapsed) / 1000L
    }

    /** 累计本轮检测间隔的秒数，满一分钟返回待入账的秒数 */
    fun accumulate(seconds: Int): Int {
        if (currentPackage == null) return 0
        pendingSeconds += seconds
        return if (pendingSeconds >= 60) {
            val toCommit = pendingSeconds
            pendingSeconds = 0
            toCommit
        } else 0
    }

    /** 触发弹窗后重置计时，让孩子重新开始这一轮 */
    fun reset() {
        sessionStartElapsed = SystemClock.elapsedRealtime()
        pendingSeconds = 0
    }

    /** 切到学习类应用时彻底停表 */
    fun stop() {
        currentPackage = null
        pendingSeconds = 0
    }

    fun currentPackageName(): String? = currentPackage
}
