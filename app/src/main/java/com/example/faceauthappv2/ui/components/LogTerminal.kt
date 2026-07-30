package com.example.faceauthappv2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 💻 【リアルタイムログ端末】
 * システムの処理状況（認証ステップの進行やエラーなど）を、
 * ハッカー映画のターミナルのように画面左上にリアルタイム表示するUIコンポーネントです。
 *
 * @param logs 表示する文字列のリスト（最新の数件）
 */
@Composable
fun LogTerminal(logs: List<String>) {
    // ログ全体を囲む半透明の黒いパネル（画面左上に配置）
    Box(
        modifier = Modifier
            .padding(top = 90.dp, start = 16.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(8.dp)
            .width(180.dp)
    ) {
        // スクロール可能なリスト表示（アイテムが増えると自動で縦に並ぶ）
        LazyColumn {
            items(logs) { log ->
                // サイバー風の等幅フォント（Monospace）で緑色のテキストを描画
                Text(
                    text = log,
                    color = Color(0xFF00FFCC),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
        }
    }
}