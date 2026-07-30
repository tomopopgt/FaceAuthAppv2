package com.example.faceauthappv2.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
================================================================================
【ファイル概要：HomeScreen.kt】
このファイルは、アプリ起動時に最初に表示される「メインメニュー（ポータル）画面」です。

【主な役割と設計の特徴】
1. サイバー調のメインロゴヘッダー表示
2. 「顔認証・新規登録カメラ画面（FaceAuthScreen）」への起動ボタン
3. 「登録ユーザー管理画面（UserListScreen）」への画面遷移ボタン
4. イベント駆動設計：ボタン押下時の遷移処理は直接書かず、ラムダ関数（コールバック）で親へ通知
================================================================================
*/

/**
 * 🏠 【メインメニュー画面（コンポーネント）】
 *
 * @param onNavigateToAuth 「顔認証システムを起動」ボタンが押された時に実行するコールバック関数
 * @param onNavigateToUserList 「登録ユーザー管理」ボタンが押された時に実行するコールバック関数
 *
 * 💡 コールバック引数のメリット：
 * 画面側（HomeScreen）が「画面遷移の仕組み（NavHostなど）」を直接知る必要が無いため、
 * プレビュー機能（@Preview）での確認やユニットテストが非常にやりやすくなります。
 */
@Composable
fun HomeScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToUserList: () -> Unit
) {
    // 🌌 画面全体の最外郭コンテナ（ダークサイバー風背景）
    Box(
        modifier = Modifier
            .fillMaxSize() // 画面いっぱいに広げる
            .background(Color(0xFF0D1117)), // ダークグレー/ネイビー背景
        contentAlignment = Alignment.Center // コンテンツを画面中央に配置
    ) {
        // 縦方向にパーツを並べるカラムコンテナ
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, // 横方向の中央揃え
            verticalArrangement = Arrangement.Center,           // 縦方向の中央揃え
            modifier = Modifier.padding(24.dp)                   // 画面端からの余白
        ) {
            // -----------------------------------------------------------------
            // 1. サイバーロゴヘッダー
            // -----------------------------------------------------------------
            Box(
                modifier = Modifier
                    // ネオングリーン（#00FFCC）の角丸2dp枠線
                    .border(2.dp, Color(0xFF00FFCC), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    // 半透明の黒背景で枠線を際立たせる
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Text(
                    text = "CYBER FACE AUTH",
                    color = Color(0xFF00FFCC),             // ネオングリーン文字
                    fontSize = 24.sp,                      // フォントサイズ
                    fontWeight = FontWeight.Bold,          // 太字
                    fontFamily = FontFamily.Monospace       // 等幅サイバーフォント
                )
            }

            // ↕️ ロゴとボタン群の間の余白
            Spacer(modifier = Modifier.height(48.dp))

            // -----------------------------------------------------------------
            // 2. 顔認証ログイン・新規登録画面への遷移ボタン（メインボタン）
            // -----------------------------------------------------------------
            Button(
                onClick = onNavigateToAuth, // 押されたらカメラ画面へ遷移するコールバックを実行
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00FFCC) // ボタン背景色：ネオングリーン
                ),
                shape = RoundedCornerShape(12.dp), // 角丸12dp
                modifier = Modifier
                    .fillMaxWidth() // 横幅いっぱいに広げる
                    .height(56.dp)  // タップしやすい大きめの高さ（56dp）
            ) {
                Text(
                    text = "🔑 顔認証システムを起動",
                    color = Color.Black, // 黒文字で視認性を確保
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ↕️ ボタン同士の間の余白
            Spacer(modifier = Modifier.height(16.dp))

            // -----------------------------------------------------------------
            // 3. 登録ユーザー一覧画面への切り替えボタン（サブボタン）
            // -----------------------------------------------------------------
            OutlinedButton(
                onClick = onNavigateToUserList, // 押されたらユーザー一覧画面へ遷移するコールバックを実行
                // 枠線のみのボタン（BorderStroke）
                border = BorderStroke(1.dp, Color(0xFF00CCFF)), // シアン（水色）の薄い枠線
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "👥 登録ユーザー管理",
                    color = Color(0xFF00CCFF), // 水色文字
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}