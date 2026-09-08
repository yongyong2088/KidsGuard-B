package com.kidsguard.app.quiz

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 英语 TTS 朗读封装。
 *
 * 为什么单独做一个：
 * - TextToSpeech 初始化是异步的（要等 onInit 回调才知道 Locale 可不可用），
 *   不能每道题 new 一个，会一直闪。
 * - Activity onDestroy 必须 shutdown()，否则 TTS 引擎不会被回收、会在后台继续朗读。
 * - 用单例懒加载：第一次用时初始化 + onInit 回调里 setLanguage（兜底 UK → US）。
 *
 * 用法：
 * ```
 *   EnglishSpeech.init(applicationContext)
 *   EnglishSpeech.speak("apple")
 *   override fun onDestroy() { EnglishSpeech.shutdown(); super.onDestroy() }
 * ```
 */
object EnglishSpeech {

    private const val TAG = "EnglishSpeech"

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready: Boolean = false
    @Volatile private var appCtx: Context? = null

    /** 初始化一次（application context 即可，无需 Activity） */
    fun init(context: Context) {
        if (tts != null) return
        synchronized(this) {
            if (tts != null) return
            val ctx = context.applicationContext
            appCtx = ctx
            tts = TextToSpeech(ctx) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "TTS init failed: status=$status")
                    ready = false
                    return@TextToSpeech
                }
                val engine = tts ?: return@TextToSpeech
                // 优先美式，没装就退回英式；都没有也不报错（朗读时直接 skip）
                val lang = when {
                    engine.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE -> Locale.US
                    engine.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE -> Locale.UK
                    else -> Locale.ENGLISH
                }
                val setRes = engine.setLanguage(lang)
                ready = setRes >= TextToSpeech.LANG_AVAILABLE
                Log.i(TAG, "TTS ready=$ready language=$lang setRes=$setRes")
            }
        }
    }

    /**
     * 朗读一段英文。安静失败：不可用时不做任何事，不弹 Toast 不阻塞 UI。
     */
    fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val engine = tts ?: run {
            // 还没初始化过，懒初始化
            appCtx?.let { init(it) }
            return
        }
        if (!ready) {
            Log.d(TAG, "TTS not ready yet, drop: $trimmed")
            return
        }
        // FLUSH 是把当前正在读的打断，立即读新的；QUEUE 是排队连读
        engine.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, "kg_${System.currentTimeMillis()}")
    }

    /** 停掉正在读的 */
    fun stop() {
        tts?.stop()
    }

    /** Activity.onDestroy 调一下，否则引擎泄漏 */
    fun shutdown() {
        synchronized(this) {
            tts?.stop()
            tts?.shutdown()
            tts = null
            ready = false
        }
    }
}