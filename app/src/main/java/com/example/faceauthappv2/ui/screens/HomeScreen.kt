package com.example.faceauthappv2.ui.screens

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

@Composable
fun HomeScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToUserList: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // サイバーロゴヘッダー
            Box(
                modifier = Modifier
                    .border(2.dp, Color(0xFF00FFCC), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Text(
                    text = "CYBER FACE AUTH",
                    color = Color(0xFF00FFCC),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // 1. 顔認証ログイン開始ボタン
            Button(
                onClick = onNavigateToAuth,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("🔑 顔認証システムを起動", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 登録ユーザー一覧画面への切り替えボタン
            OutlinedButton(
                onClick = onNavigateToUserList,
                border = ButtonDefaults.outlinedToolboxBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00CCFF))),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("👥 登録ユーザー管理", color = Color(0xFF00CCFF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}