package com.example.faceauthappv2.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
================================================================================
【ファイル概要：Type.kt】
このファイルは、アプリ全体の「文字スタイル（Typography）」を一元管理するファイルです。

【主な役割と特徴】
1. Material Design 3 に準拠したテキストスタイル（bodyLarge, titleLarge など）を一括定義
2. フォント種類（FontFamily）、太さ（FontWeight）、サイズ（fontSize）、行間（lineHeight）などの標準化
3. `Theme.kt` 経由でアプリ全体に一貫した文字デザインルールを適用
================================================================================
*/

/**
 * 🔤 【Material 3 タイポグラフィ設定】
 * ここで定義されたスタイル群は、`Theme.kt` 内の `MaterialTheme(typography = Typography)` を通じて
 * アプリ内のすべての `Text(...)` コンポーネントへ自動的に適用されます。
 */
val Typography = Typography(
    /**
     * 📝 【bodyLarge：標準的な本文テキスト用スタイル】
     * 主な文章、説明文、一般的なリスト表示などで標準適用される文字スタイルです。
     */
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, // 使用するフォント（Default: Android端末の標準フォント）
        fontWeight = FontWeight.Normal,  // 文字の太さ（Normal: 標準の太さ）
        fontSize = 16.sp,               // 文字サイズ（16sp）
        lineHeight = 24.sp,             // 複数行になった場合の行の高さ・行間（24sp）
        letterSpacing = 0.5.sp          // 文字同士の横の間隔・カーニング（0.5sp）
    )

    /* 💡 【その他の標準テキストスタイルをオーバーライドする場合の記述例】

    // 📌 【titleLarge：大きなタイトル・見出し用スタイル】
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),

    // 🏷️ 【labelSmall：ボタン上の文字や小さな注釈ラベル用スタイル】
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)