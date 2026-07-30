package com.example.faceauthappv2.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/*
================================================================================
【ファイル概要：VibratorUtil.kt】
このファイルは、アプリ内のバイブレーション（端末の振動機能）を制御するユーティリティファイルです。

【主な役割と技術的ポイント】
1. Android OS のバージョン差異（API 26 / 8.0、API 31 / 12.0）を自動吸収
2. どの端末・OSバージョンでもクラッシュせずに安全に振動を実行するトップレベル関数を提供
================================================================================
*/

/**
 * 📳 【安全なバイブレーション実行関数】
 * Android OS のバージョン差異を自動判定し、どの端末でもエラーを出さずに指定ミリ秒だけ端末を振動させます。
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