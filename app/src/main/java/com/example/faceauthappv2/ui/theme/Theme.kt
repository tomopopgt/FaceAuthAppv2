package com.example.faceauthappv2.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/*
================================================================================
【ファイル概要：Theme.kt】
このファイルは、アプリ全体に適用される「デザインテーマ（Material 3）」を統括するファイルです。

【主な役割と機能】
1. アプリ全体の色合い（カラーパレット：ダークモード / ライトモード）の一括制御
2. Android 12（API 31）以降の新機能「ダイナミックカラー（Material You）」への自動対応
3. カラーパレットおよびフォントスタイル（Typography）を全画面のUIコンポーネントへ一括適用
================================================================================
*/

/**
 * 🌙 【ダークテーマ用カラーパレットの設定】
 * スマホ本体がダークモードの場合、または強制的にダーク表示する場合に使用される色の組み合わせ。
 */
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,       // メインのアクセントカラー
    secondary = PurpleGrey80, // サブのアクセントカラー
    tertiary = Pink80         // 補正・強調用カラー
)

/**
 * ☀️ 【ライトテーマ用カラーパレットの設定】
 * スマホ本体がライトモードの場合に使用される色の組み合わせ。
 */
private val LightColorScheme = lightColorScheme(
    primary = Purple40,       // メインのアクセントカラー
    secondary = PurpleGrey40, // サブのアクセントカラー
    tertiary = Pink40         // 補正・強調用カラー

    /* 💡 【デフォルト色のカスタマイズ設定（必要に応じてオーバーライド可能）】
    background = Color(0xFFFFFBFE), // 画面全体の背景色
    surface = Color(0xFFFFFBFE),    // カードやダイアログの表面色
    onPrimary = Color.White,        // primaryカラーの上に配置される文字・アイコン色
    onSecondary = Color.White,      // secondaryカラーの上に配置される文字・アイコン色
    onTertiary = Color.White,       // tertiaryカラーの上に配置される文字・アイコン色
    onBackground = Color(0xFF1C1B1F),// 背景色の上に配置される文字色
    onSurface = Color(0xFF1C1B1F),   // 表面色の上に配置される文字色
    */
)

/**
 * 🎨 【アプリ全体を包み込むテーマプロバイダー（ラッパーコンポーネント）】
 * MainActivity 等で画面全体を `FaceAuthAppv2Theme { ... }` で囲むことで、
 * 内側のすべてのコンポーネントへテーマ設定が伝播します。
 *
 * @param darkTheme システムのダークモード設定に自動追従するかどうか（デフォルト：追従）
 * @param dynamicColor Android 12（API 31）以降で壁紙の色に自動適応させるか（デフォルト：有効）
 * @param content テーマを適用させたい画面の全UI要素
 */
@Composable
fun FaceAuthAppv2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(), // OS設定がダークモードか自動判定
    dynamicColor: Boolean = true,               // ダイナミックカラー機能の有効化フラグ
    content: @Composable () -> Unit             // 描画される画面コンテンツ
) {
    // 🎨 OSバージョンや端末設定に応じて適用するカラーパレットを決定する分岐処理
    val colorScheme = when {
        // 1. Android 12 (API level 31 / Build.VERSION_CODES.S) 以上かつ dynamicColor が有効な場合
        // ユーザーが設定している壁紙の色に基づいてOSが自動抽出する「Material You」カラーを採用
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // 2. OS設定または指定フラグがダークモードの場合
        darkTheme -> DarkColorScheme

        // 3. それ以外（ライトモード）の場合
        else -> LightColorScheme
    }

    // 🚀 MaterialTheme を通じて、決定したカラーパレットとフォントを子コンポーネント全体へ反映
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Type.kt で定義されているフォントスタイルを適用
        content = content
    )
}