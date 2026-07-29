package com.example.faceauthappv2.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import java.util.concurrent.Executors

enum class SoundType { BEEP, STEP_PASS, SCANNING, SUCCESS, REGISTERED, ERROR }

object SoundManager {
    private var toneGen: ToneGenerator? = null
    private val executor = Executors.newSingleThreadExecutor()

    fun play(type: SoundType) {
        try {
            if (toneGen == null) toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            executor.execute {
                when (type) {
                    SoundType.BEEP -> toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                    SoundType.STEP_PASS -> toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 80)
                    SoundType.SCANNING -> toneGen?.startTone(ToneGenerator.TONE_CDMA_PIP, 120)
                    SoundType.SUCCESS -> {
                        toneGen?.startTone(ToneGenerator.TONE_DTMF_6, 80); Thread.sleep(100)
                        toneGen?.startTone(ToneGenerator.TONE_DTMF_9, 80); Thread.sleep(100)
                        toneGen?.startTone(ToneGenerator.TONE_SUP_CONFIRM, 400)
                    }
                    SoundType.REGISTERED -> {
                        toneGen?.startTone(ToneGenerator.TONE_DTMF_D, 100); Thread.sleep(120)
                        toneGen?.startTone(ToneGenerator.TONE_PROP_PROMPT, 400)
                    }
                    SoundType.ERROR -> toneGen?.startTone(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 400)
                }
            }
        } catch (e: Exception) { Log.e("Sound", "エラー", e) }
    }
    fun release() { toneGen?.release(); toneGen = null }
}

fun safeVibrate(context: Context, durationMs: Long) {
    try {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v?.vibrate(durationMs)
    } catch (e: Exception) {}
}