package com.example.faceauthappv2.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.faceauthappv2.model.AuthStep
import com.example.faceauthappv2.model.DetectionData
import com.google.mlkit.vision.face.FaceLandmark

/**
 * 🎯 【顔認識ターゲットオーバレイ（サイバー枠＆レーザー）】
 * ML Kitが検知した顔の座標データをもとに、画面上に直接「四隅のブラケット枠」「走査レーザー」「特徴点（目や鼻）」
 * を描画（Canvas描画）するコンポーネントです。
 *
 * @param detectionData カメラから取得した顔の座標や画像のサイズデータ
 * @param currentStep 現在の認証状態（状態によって枠の色を変えるため）
 * @param hasUsers 登録済みユーザーがいるかどうか
 */
@Composable
fun FaceOverlay(detectionData: DetectionData, currentStep: AuthStep, hasUsers: Boolean) {
    // 〰️ レーザー走査線用のアニメーション設定（0f から 1f を永遠に往復する）
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser"
    )

    // 🎨 Canvas：自由に線や図形を描画できる領域
    Canvas(modifier = Modifier.fillMaxSize()) {
        // 顔が検知されていない、または画像サイズが不明な場合は何も描画しない
        if (detectionData.faces.isEmpty() || detectionData.imageWidth == 0 || detectionData.imageHeight == 0) return@Canvas

        // 📐 座標変換（スケーリング）の計算
        // カメラの画像サイズと、スマホ画面のサイズが異なるため、比率を計算して座標を合わせる
        val isPortrait = detectionData.rotationDegrees == 90 || detectionData.rotationDegrees == 270
        val imgWidth = if (isPortrait) detectionData.imageHeight else detectionData.imageWidth
        val imgHeight = if (isPortrait) detectionData.imageWidth else detectionData.imageHeight
        val scaleX = size.width / imgWidth.toFloat()
        val scaleY = size.height / imgHeight.toFloat()

        // 検出されたすべての顔（通常は1つ）に対して描画処理を行う
        detectionData.faces.forEach { face ->
            // インカメラは左右反転（ミラー）しているため、X座標は size.width から引いて反転させる
            val left = size.width - (face.boundingBox.right * scaleX)
            val top = face.boundingBox.top * scaleY
            val right = size.width - (face.boundingBox.left * scaleX)
            val bottom = face.boundingBox.bottom * scaleY

            val width = right - left
            val height = bottom - top
            val corner = width * 0.25f // 四隅の枠線の長さ（顔の幅の25%）
            val stroke = 6.dp.toPx()   // 枠線の太さ

            // 🎨 状態に応じた枠のカラーリング
            val color = if (!hasUsers) Color(0xFF00CCFF) else when (currentStep) {
                AuthStep.GRANTED -> Color(0xFF00FF66)    // 成功：緑
                AuthStep.CHECK_SMILE -> Color(0xFFFFCC00)// 笑顔待機：黄
                AuthStep.CHECK_FRONT -> Color(0xFFB388FF)// 正面待機：紫
                AuthStep.TIMEOUT -> Color.Red            // エラー：赤
                else -> Color(0xFF00CCFF)                // 通常：青
            }

            // 🔲 四隅のブラケット（枠）を描画
            // 左上
            drawLine(color, Offset(left, top), Offset(left + corner, top), stroke, StrokeCap.Round)
            drawLine(color, Offset(left, top), Offset(left, top + corner), stroke, StrokeCap.Round)
            // 右上
            drawLine(color, Offset(right, top), Offset(right - corner, top), stroke, StrokeCap.Round)
            drawLine(color, Offset(right, top), Offset(right, top + corner), stroke, StrokeCap.Round)
            // 左下
            drawLine(color, Offset(left, bottom), Offset(left + corner, bottom), stroke, StrokeCap.Round)
            drawLine(color, Offset(left, bottom), Offset(left, bottom - corner), stroke, StrokeCap.Round)
            // 右下
            drawLine(color, Offset(right, bottom), Offset(right - corner, bottom), stroke, StrokeCap.Round)
            drawLine(color, Offset(right, bottom), Offset(right, bottom - corner), stroke, StrokeCap.Round)

            // ⚡ エラー時以外は、顔を上下にスキャンするレーザー線を描画
            if (currentStep != AuthStep.TIMEOUT) {
                val laserY = top + (height * scanProgress)
                drawLine(color.copy(alpha = 0.8f), Offset(left + 10f, laserY), Offset(right - 10f, laserY), 4.dp.toPx(), StrokeCap.Round)
            }

            // 📍 顔の主要な特徴点（目、鼻、口）を取得してドットを描画
            val landmarks = listOfNotNull(
                face.getLandmark(FaceLandmark.LEFT_EYE),
                face.getLandmark(FaceLandmark.RIGHT_EYE),
                face.getLandmark(FaceLandmark.NOSE_BASE),
                face.getLandmark(FaceLandmark.MOUTH_LEFT),
                face.getLandmark(FaceLandmark.MOUTH_RIGHT),
                face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
            )

            landmarks.forEach { lm ->
                val lx = size.width - (lm.position.x * scaleX) // X座標を反転
                val ly = lm.position.y * scaleY
                // 中心に濃い点、周りに薄い波紋を描画してサイバー感を演出
                drawCircle(color, 4.dp.toPx(), Offset(lx, ly))
                drawCircle(color.copy(alpha = 0.4f), 9.dp.toPx(), Offset(lx, ly))
            }
        }
    }
}