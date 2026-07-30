package com.example.faceauthappv2.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.faceauthappv2.data.LocalDataManager
import com.example.faceauthappv2.model.UserData

/*
================================================================================
【ファイル概要：UserListScreen.kt】
このファイルは、アプリ内に保存されたユーザーデータ（名前・アイコン・ID）を一覧表示し、
不要なユーザーを個別削除するための「データ管理画面」です。

【主な役割と技術的ポイント】
1. LocalDataManager から保存済みユーザー一覧を取得してリスト表示
2. Jetpack Compose の `LazyColumn` によるメモリ効率の良いスクロールリスト処理
3. `remember(user.imagePath)` による画像ビットマップ復元のキャッシュ化（動作軽量化）
4. ユーザー削除時のリストリアルタイム再描画（ステート更新）
================================================================================
*/

/**
 * 👥 【登録ユーザー一覧画面（メインコンポーネント）】
 *
 * @param onBack 「◀ 戻る」ボタンが押された時にメインメニュー画面へ戻るためのナビゲーションコールバック
 */
@Composable
fun UserListScreen(
    onBack: () -> Unit
) {
    // 📱 現在のAndroidコンテキスト（ストレージアクセス等に使用）を取得
    val context = LocalContext.current

    // 🔄 ユーザーリストの状態管理
    // LocalDataManager から読み込み、mutableStateOf で保持することで、削除時に自動で画面が再描画されます。
    var userList by remember { mutableStateOf(LocalDataManager.getUsers(context)) }

    // 🌌 画面全体のレイアウト（縦並びColumn）
    Column(
        modifier = Modifier
            .fillMaxSize()                   // 画面全体に広げる
            .background(Color(0xFF0D1117))   // ダークサイバー風背景色
            .padding(16.dp)                  // 全体の内側余白
    ) {
        // ↕️ 上部のステータスバーと被らないための余白
        Spacer(modifier = Modifier.height(24.dp))

        // -----------------------------------------------------------------
        // 1. ヘッダーバー（戻るボタン ＆ タイトル）
        // -----------------------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically, // 縦方向の中央揃え
            modifier = Modifier.fillMaxWidth()
        ) {
            // 戻るボタン
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("◀ 戻る", color = Color.White)
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 登録人数を表示するタイトル
            Text(
                text = "登録ユーザー一覧 (${userList.size})",
                color = Color(0xFF00FFCC), // ネオングリーン
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // -----------------------------------------------------------------
        // 2. ユーザーリスト（条件分岐表示）
        // -----------------------------------------------------------------
        if (userList.isEmpty()) {
            // 📭 登録ユーザーが 0 人の場合の表示
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("登録されたユーザーはいません", color = Color.Gray)
            }
        } else {
            // 📜 登録ユーザーが存在する場合のスクロールリスト
            // 💡 `LazyColumn` とは：
            // 画面に映っているアイテムだけをメモリ上に生成する非常に効率的なスクロールリスト。
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp) // リスト項目同士の間隔（12dp）
            ) {
                // userList の要素数分だけ UserRowItem コンポーネントを生成
                items(userList) { user ->
                    UserRowItem(
                        user = user,
                        onDelete = {
                            // 🗑️ 削除処理の実行
                            LocalDataManager.deleteUser(context, user.id) // 端末ストレージから削除
                            userList = LocalDataManager.getUsers(context) // 画面のリスト状態を再読み込みして更新
                        }
                    )
                }
            }
        }
    }
}

/**
 * 👤 【ユーザー1人分の表示行コンポーネント】
 *
 * @param user 表示対象のユーザーデータ（UserData）
 * @param onDelete ごみ箱アイコンが押された際の削除実行コールバック
 */
@Composable
fun UserRowItem(
    user: UserData,
    onDelete: () -> Unit
) {
    /**
     * 🖼️ 【パフォーマンス最適化ポイント】
     * `remember(user.imagePath)` を使うことで、imagePath が変化しない限り
     * ファイルからの Bitmap 解読（読み込み）処理をスキップし、メモリスキャンを高速化します。
     */
    val bitmap = remember(user.imagePath) { LocalDataManager.loadBitmap(user.imagePath) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // 半透明の黒背景
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            // シアン（水色）の薄い外枠
            .border(1.dp, Color(0xFF00CCFF), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // 🖼️ 顔写真サムネイルの描画
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(), // Bitmap を Compose 用の ImageBitmap に変換
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)                                                // 50x50dp の正方形
                    .clip(CircleShape)                                           // 丸型に切り抜き
                    .border(1.dp, Color(0xFF00FFCC), CircleShape),              // ネオングリーンの丸枠
                contentScale = ContentScale.Crop                                // 枠に合わせて中央クロップ
            )
        } ?: Box(
            // 画像が無い場合のプレースホルダー（灰色の丸）
            modifier = Modifier
                .size(50.dp)
                .background(Color.Gray, CircleShape)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 📝 ユーザー名 ＆ ID 表示エリア（`.weight(1f)` で横幅いっぱいに伸縮）
        Column(modifier = Modifier.weight(1f)) {
            // ユーザー名
            Text(
                text = user.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            // ID（長いUUIDの先頭8文字だけを表示してスッキリさせる）
            Text(
                text = "ID: ${user.id.take(8)}...",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        // 🗑️ 削除アイコンボタン
        IconButton(onClick = onDelete) {
            Text("🗑️", fontSize = 18.sp)
        }
    }
}