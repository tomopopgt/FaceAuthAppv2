package com.example.faceauthappv2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.faceauthappv2.ui.screens.FaceAuthScreen
import com.example.faceauthappv2.ui.screens.HomeScreen
import com.example.faceauthappv2.ui.screens.UserListScreen
import com.example.faceauthappv2.utils.SoundManager

// 画面の種類
enum class ScreenState {
    HOME,       // ホーム/ログイン選択画面
    FACE_AUTH,  // 顔認証・登録カメラ画面
    USER_LIST   // 登録ユーザー一覧画面
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf(ScreenState.HOME) }

                    when (currentScreen) {
                        ScreenState.HOME -> {
                            HomeScreen(
                                onNavigateToAuth = { currentScreen = ScreenState.FACE_AUTH },
                                onNavigateToUserList = { currentScreen = ScreenState.USER_LIST }
                            )
                        }
                        ScreenState.FACE_AUTH -> {
                            FaceAuthScreen(
                                onBack = { currentScreen = ScreenState.HOME }
                            )
                        }
                        ScreenState.USER_LIST -> {
                            UserListScreen(
                                onBack = { currentScreen = ScreenState.HOME }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}