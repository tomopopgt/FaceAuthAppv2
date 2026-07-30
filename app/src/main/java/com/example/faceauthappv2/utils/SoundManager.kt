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

/*
================================================================================
【ファイル概要：SoundManager.kt / VibratorUtil.kt】
このファイルは、アプリ内のサウンド演出と振動（バイブレーション）フィードバックを担当します。

【主な役割と技術的工夫】
1. 音声ファイル（MP3等）を使わないプログラム生成音：
   ToneGenerator（電話のDTMFトーン等）を組み合わせて効果音を自作し、アプリの容量を軽量化。
2. UIスレッドの保護：
   音の連続再生（メロディ生成）で `Thread.sleep` を使う際、単一バックグラウンドスレッド（Executors）上で実行し、
   画面のフレーム落ち（カクつき）を完全に防止。
3. バージョンセーフなバイブレーション制御：
   Android 8.0（API 26）および Android 12（API 31）の非推奨APIに対応し、全OSバージョンで安全に振動処理を実行。
================================================================================
*/

/**
 * 🔊 【効果音タイプ（列挙型）】
 * アプリ内で発生するサウンドイベントの種類を定義します。
 */
enum class SoundType {
    /** 🔴 顔検出開始音（単発ビープ） */
    BEEP,

    /** 🔄 生体検知ステップ突破音（短い通過音） */
    STEP_PASS,

    /** ⚡ スキャン中演出音（ピピ音） */
    SCANNING,

    /** ❇️ 本人認証完了音（近未来風の3連和音メロディ） */
    SUCCESS,

    /** 👤 新規ユーザー登録音（和音メロディ） */
    REGISTERED,

    /** 🚨 セキュリティアラート音（エラー音） */
    ERROR
}

/**
 * 🎵 【サウンド管理シングルトンクラス】
 * アプリ全体で共有される効果音再生エンジンです。
 */
object SoundManager {
    // 🔔 Android標準のトーン生成オブジェクト
    private var toneGen: ToneGenerator? = null

    /**
     * 🧵 【バックグラウンドスレッドプール】
     * 効果音再生（特に複数の音を連打・遅延させる処理）をメインUIスレッドから分離して実行するために使用します。
     */
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * 🎶 【効果音再生関数】
     * 指定された `SoundType` に応じたプログラムトーンをバックグラウンドで再生します。
     *
     * @param type 再生したい効果音の種類
     */
    fun play(type: SoundType) {
        try {
            // ToneGeneratorの初期化（MEDIAストリーム、音量100%）
            if (toneGen == null) toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

            // UIをブロックしないようバックグラウンドスレッドで再生処理を実行
            executor.execute {
                when (type) {
                    // 単発ビープ音 (100ミリ秒)
                    SoundType.BEEP -> toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)

                    // ステップ通過音 (80ミリ秒)
                    SoundType.STEP_PASS -> toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 80)

                    // スキャン音 (120ミリ秒)
                    SoundType.SCANNING -> toneGen?.startTone(ToneGenerator.TONE_CDMA_PIP, 120)

                    // ❇️ 認証成功音：音高の異なるトーンを時間差（100ms）で重ねてサイバー風アルペジオを表現
                    SoundType.SUCCESS -> {
                        toneGen?.startTone(ToneGenerator.TONE_DTMF_6, 80); Thread.sleep(100)
                        toneGen?.startTone(ToneGenerator.TONE_DTMF_9, 80); Thread.sleep(100)
                        toneGen?.startTone(ToneGenerator.TONE_SUP_CONFIRM, 400)
                    }

                    // 👤 ユーザー登録完了音：2段階のトーンで登録完了を表現
                    SoundType.REGISTERED -> {
                        toneGen?.startTone(ToneGenerator.TONE_DTMF_D, 100); Thread.sleep(120)
                        toneGen?.startTone(ToneGenerator.TONE_PROP_PROMPT, 400)
                    }

                    // 🚨 エラーアラート音 (400ミリ秒の警告トーン)
                    SoundType.ERROR -> toneGen?.startTone(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 400)
                }
            }
        } catch (e: Exception) {
            Log.e("Sound", "サウンド再生エラー", e)
        }
    }

    /**
     * 🧹 【リソース解放関数】
     * アプリ終了時などに ToneGenerator が占有している音声メモリをOSに返却します。
     */
    fun release() {
        toneGen?.release()
        toneGen = null
    }
}

/**
 * 📳 【安全なバイブレーション実行関数】
 * Android OS のバージョン差異を吸収し、どの端末でもクラッシュせずにバイブレーションを鳴らします。
 *
 * @param context アプリのコンテキスト（バイブレーションサービス取得用）
 * @param durationMs 振動させる時間（ミリ秒）
 */
fun safeVibrate(context: Context, durationMs: Long) {
    try {
        // 📱 Android 12 (API 31/S) 以降とそれ以前で、バイブレーションサービスの取得方法を分岐
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 以降：VibratorManager を経由してデフォルトの Vibrator を取得
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            // Android 11 以前：従来通りの VIBRATOR_SERVICE を取得
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        // ⚡ Android 8.0 (API 26/O) 以降とそれ以前で振動APIを分岐
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0 以降：VibrationEffect による強さと時間の制御
            v?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            // Android 7.1 以前：ミリ秒直接指定の旧方式
            @Suppress("DEPRECATION")
            v?.vibrate(durationMs)
        }
    } catch (e: Exception) {
        // 端末にバイブレーターが搭載されていない場合や権限エラー時のクラッシュを防止
    }
}