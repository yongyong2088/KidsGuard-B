package com.kidsguard.app.quiz

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/**
 * 答题音效封装。
 *
 * 设计：
 * - 用 SoundPool（不是 MediaPlayer）：延迟 < 50ms，适合即时反馈
 * - 单例懒加载，第一次 play 时才初始化
 * - 三个音效：答对 / 答对连续 / 答错
 * - 资源释放跟随 Activity 生命周期（onDestroy 调 shutdown）
 */
object SoundFx {

    private const val TAG = "SoundFx"

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<String, Int>()
    @Volatile private var loaded = false

    /** 第一次 play 时自动初始化；可主动调一次以提前加载 */
    fun init(context: Context) {
        if (pool != null) return
        synchronized(this) {
            if (pool != null) return
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            pool = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(attrs)
                .build()
            val p = pool ?: return
            // 同步加载三个音效
            ids["correct"] = p.load(context, com.kidsguard.app.R.raw.correct, 1)
            ids["correct_bonus"] = p.load(context, com.kidsguard.app.R.raw.correct_bonus, 1)
            ids["wrong"] = p.load(context, com.kidsguard.app.R.raw.wrong, 1)
            p.setOnLoadCompleteListener { _, _, status ->
                loaded = status == 0
                Log.i(TAG, "SoundPool loaded=$loaded status=$status")
            }
        }
    }

    /** 播放指定音效。资源未加载完时静默跳过（不阻塞 UI） */
    fun play(name: String, volume: Float = 0.7f) {
        val p = pool ?: return
        val id = ids[name] ?: return
        if (!loaded) return
        p.play(id, volume, volume, 1, 0, 1.0f)
    }

    /** 释放 SoundPool 资源。Activity.onDestroy 时调用 */
    fun shutdown() {
        synchronized(this) {
            pool?.release()
            pool = null
            ids.clear()
            loaded = false
        }
    }

    /** 是否已初始化（用于单元测试） */
    fun isInitialized(): Boolean = pool != null
}