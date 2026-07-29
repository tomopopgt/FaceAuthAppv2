package com.example.faceauthappv2.model

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face

// ユーザーデータ
data class UserData(
    val id: String,
    val name: String,
    val imagePath: String
)

// カメラ検出データ
data class DetectionData(
    val faces: List<Face> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val rotationDegrees: Int = 0
)

// 認証ステップ
enum class AuthStep {
    WAITING, CHECK_TURN, CHECK_FRONT, CHECK_SMILE, GRANTED, TIMEOUT
}