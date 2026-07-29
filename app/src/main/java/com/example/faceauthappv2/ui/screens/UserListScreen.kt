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

@Composable
fun UserListScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var userList by remember { mutableStateOf(LocalDataManager.getUsers(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // ヘッダーバー
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("◀ 戻る", color = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "登録ユーザー一覧 (${userList.size})",
                color = Color(0xFF00FFCC),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (userList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("登録されたユーザーはいません", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(userList) { user ->
                    UserRowItem(
                        user = user,
                        onDelete = {
                            LocalDataManager.deleteUser(context, user.id)
                            userList = LocalDataManager.getUsers(context) // リスト再更新
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UserRowItem(
    user: UserData,
    onDelete: () -> Unit
) {
    val bitmap = remember(user.imagePath) { LocalDataManager.loadBitmap(user.imagePath) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF00CCFF), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFF00FFCC), CircleShape),
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            modifier = Modifier
                .size(50.dp)
                .background(Color.Gray, CircleShape)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(text = "ID: ${user.id.take(8)}...", color = Color.Gray, fontSize = 12.sp)
        }

        IconButton(onClick = onDelete) {
            Text("🗑️", fontSize = 18.sp)
        }
    }
}