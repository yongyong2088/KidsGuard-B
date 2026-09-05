package com.kidsguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.monitor.UsageMonitorService

/**
 * 开机自启与应用更新后自启。
 * 防止孩子通过重启设备来绕过监控。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val prefs = PrefsManager(context)
                if (prefs.monitorEnabled) {
                    try {
                        UsageMonitorService.start(context)
                    } catch (e: Exception) {
                        // 部分厂商限制后台启动，忽略即可，不影响手动启动
                    }
                }
            }
        }
    }
}
