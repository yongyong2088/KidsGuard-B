package com.kidsguard.app.monitor

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.kidsguard.app.KidsGuardApp
import com.kidsguard.app.R
import com.kidsguard.app.data.AppCategory
import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.lock.LockActivity
import com.kidsguard.app.ui.MainActivity

/**
 * 核心监测服务。
 *
 * 每 3 秒检查一次前台应用，按家长设定的分类决定是否计时，
 * 达到阈值就拉起全屏答题锁。所有判定都在本地完成，断网、飞行模式照常工作。
 */
class UsageMonitorService : Service() {

    private lateinit var prefs: PrefsManager
    private lateinit var classifier: AppClassifier
    private val tracker = TimeTracker()
    private val handler = Handler(Looper.getMainLooper())

    private val checkTask = object : Runnable {
        override fun run() {
            checkForeground()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        classifier = AppClassifier(prefs)
        prefs.updateTimeAnchor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(checkTask)
        handler.post(checkTask)
        // 被系统杀掉后尝试重建，保证监控不中断
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(checkTask)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 被从最近任务划掉后自我拉活
        try {
            val restart = Intent(applicationContext, UsageMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restart)
            } else {
                applicationContext.startService(restart)
            }
        } catch (e: Exception) {
            // Android 12 起从后台启动前台服务会被系统拒绝，忽略即可，不影响手动启动
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun checkForeground() {
        if (!prefs.monitorEnabled) {
            tracker.stop()
            return
        }

        val pkg = getForegroundPackage()
        val category = classifier.classify(pkg.orEmpty())

        when (category) {
            AppCategory.LEARNING -> {
                // 学习类：完全不计时，永不弹窗
                tracker.stop()
            }

            AppCategory.RESTRICTED -> {
                tracker.onForeground(pkg)
                val toCommit = tracker.accumulate(CHECK_SECONDS)
                if (toCommit > 0) prefs.addUsedSeconds(toCommit)

                // 三段式判定：
                //   阶段 0（额度内）：不弹窗，但仍正常计时
                //   阶段 1（超额后）：每 currentIntervalMinutes 分钟弹一次
                //   阶段 2（总时长超 tightAfter）：收紧到更短的间隔
                val stage = prefs.lockStage()
                if (stage >= 1 && prefs.minutesSinceLastLock >= prefs.currentIntervalMinutes()) {
                    prefs.minutesSinceLastLock = 0
                    tracker.reset()
                    // 奖励换得的「免一次弹窗」：消耗一次，本次不拦
                    if (prefs.consumeFreePass()) {
                        return
                    }
                    launchLock(pkg.orEmpty())
                }
            }

            AppCategory.UNKNOWN -> {
                // 未分类：只记录，不弹窗
                tracker.stop()
            }
        }
    }

    /** 读取当前前台应用的包名 */
    private fun getForegroundPackage(): String? {
        if (!hasUsagePermission(this)) return null
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 2 * 60 * 1000, now)
            val event = UsageEvents.Event()
            var lastPkg: String? = null
            var lastTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    if (event.timeStamp >= lastTime) {
                        lastTime = event.timeStamp
                        lastPkg = event.packageName
                    }
                }
            }
            lastPkg
        } catch (e: Exception) {
            null
        }
    }

    private fun launchLock(packageName: String) {
        LockActivity.launch(this, packageName)
    }

    private fun buildNotification(): Notification {
        val channelId = KidsGuardApp.NOTIFICATION_CHANNEL_ID
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(R.string.notify_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        prefs.ensureTodayRolled()
        val text = getString(R.string.notify_text, prefs.usedMinutesToday)
        return Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 3000L
        private const val CHECK_SECONDS = 3
        private const val NOTIFICATION_ID = 1001

        /** 是否已获得使用情况访问权限。这个权限无法在应用内申请，只能引导用户去设置页开启 */
        fun hasUsagePermission(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun start(context: Context) {
            val intent = Intent(context, UsageMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageMonitorService::class.java))
        }

        /** 服务是否还活着，用于主界面显示状态 */
        fun isRunning(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            return am.getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == UsageMonitorService::class.java.name }
        }
    }
}
