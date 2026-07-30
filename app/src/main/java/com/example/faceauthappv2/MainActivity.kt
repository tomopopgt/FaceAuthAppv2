package com.example.faceauthappv2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.faceauthappv2.ui.screens.FaceAuthScreen
import com.example.faceauthappv2.ui.screens.HomeScreen
import com.example.faceauthappv2.ui.screens.UserListScreen
import com.example.faceauthappv2.utils.SoundManager

/*
================================================================================
【ファイル概要：MainActivity.kt】
このファイルは、アプリ全体の起動・画面遷移・終了処理を管理する「メインエントリーポイント」です。

【主な役割と設計の特徴】
1. アプリ起動時に Android OS から最初に呼び出される Activity クラス
2. Jetpack Compose の `setContent` を使用した宣言的UIの描画開始
3. `ScreenState`（列挙型）と Compose の状態管理（`remember`）を組み合わせた軽量な画面遷移（ステートドリブンナビゲーション）
4. 画面（HomeScreen / FaceAuthScreen / UserListScreen）同士の結合度を下げたクリーンな設計
5. アプリ終了時（`onDestroy`）における音響リソース（SoundManager）のメモリ解放処理
================================================================================
*/

/**
 * 🗺️ 【画面ステート定義（列挙型）】
 * 現在画面上にどの画面を表示すべきか（画面切り替えの状態）を管理します。
 */
enum class ScreenState {
    /** 🏠 メインメニュー画面 */
    HOME,

    /** 📷 顔認証・生体検知・新規ユーザー登録カメラ画面 */
    FACE_AUTH,

    /** 👥 登録済みユーザー一覧・削除管理画面 */
    USER_LIST
}

/**
 * 🚀 【メインActivityクラス】
 * `ComponentActivity` を継承しており、Jetpack Compose による描画に対応したAndroid標準の画面基盤です。
 */
class MainActivity : ComponentActivity() {

    /**
     * 🏁 【ライフサイクル関数：アプリ起動時】
     * アプリが起動した際に最初に実行される関数です。
     *
     * @param savedInstanceState アプリがバックグラウンドで一時停止された際などに保持される画面状態データ
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🎨 Jetpack Compose のUI描画領域をセットアップ
        setContent {
            // アプリ全体のデザインテーマ（Material 3）を適用
            MaterialTheme {
                // 画面全体のベースとなる背景コンテナ（fillMaxSize で画面全体へ広げる）
                Surface(modifier = Modifier.fillMaxSize()) {

                    /**
                     * 🔄 【現在表示中の画面状態】
                     * 初期値は `ScreenState.HOME`（ホーム画面）。
                     * この `currentScreen` の値が変化すると、Compose が自動的に再描画（Recomposition）を行い、
                     * 画面が瞬時に切り替わります。
                     */
                    var currentScreen by remember { mutableStateOf(ScreenState.HOME) }

                    // 🔀 現在のステートに応じた画面の切り替え（条件分岐）
                    when (currentScreen) {
                        // 1. ホーム画面の表示
                        ScreenState.HOME -> {
                            HomeScreen(
                                // 「顔認証起動」ボタンが押されたらカメラ画面ステートへ変更
                                onNavigateToAuth = { currentScreen = ScreenState.FACE_AUTH },
                                // 「ユーザー管理」ボタンが押されたらリスト画面ステートへ変更
                                onNavigateToUserList = { currentScreen = ScreenState.USER_LIST }
                            )
                        }

                        // 2. 顔認証・カメラ画面の表示
                        ScreenState.FACE_AUTH -> {
                            FaceAuthScreen(
                                // 「◀ 戻る」ボタンが押されたらホーム画面ステートへ戻る
                                onBack = { currentScreen = ScreenState.HOME }
                            )
                        }

                        // 3. 登録ユーザー管理画面の表示
                        ScreenState.USER_LIST -> {
                            UserListScreen(
                                // 「◀ 戻る」ボタンが押されたらホーム画面ステートへ戻る
                                onBack = { currentScreen = ScreenState.HOME }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 🧹 【ライフサイクル関数：アプリ破棄・終了時】
     * タスクキルや「戻る」ボタン連打等でアプリが完全に終了する際に実行されます。
     */
    override fun onDestroy() {
        super.onDestroy()
        // 🔊 音声生成エンジン（ToneGenerator）が占有していたメモリをOSへ開放し、メモリリークを防止
        SoundManager.release()
    }
}