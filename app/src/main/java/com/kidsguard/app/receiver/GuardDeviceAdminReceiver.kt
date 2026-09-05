package com.kidsguard.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * 设备管理器接收器。
 *
 * 激活后（家长在设置里手动激活），孩子无法直接卸载本应用，
 * 也不能在「设置 → 应用管理」里一键强制停止。
 * 这是方案里推荐的默认防护档位。
 */
class GuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
