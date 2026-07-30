package com.example.faceauthappv2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * 📊 【生体パラメータHUDメーター】
 * カメラで検知した顔の各パラメータ（笑顔度、首の角度、目の開き具合）を、
 * サイバー風のプログレスバーとして画面右上に表示するUIコンポーネントです。
 *
 * @param smile 笑顔の確率 (0.0 〜 1.0)
 * @param headY 首の左右の傾き角度 (Euler Angle Y)
 * @param leftEye 左目が開いている確率 (0.0 〜 1.0)
 * @param rightEye 右目が開いている確率 (0.0 〜 1.0)
 */
@Composable
fun BiometricHUD(smile: Float, headY: Float, leftEye: Float, rightEye: Float) {
    // 画面の右上（TopEnd）に配置するためのコンテナ
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 90.dp, end = 16.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        // メーター全体を囲む半透明の黒いパネル
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(10.dp)
                .width(110.dp)
        ) {
            // パネルのタイトル
            Text("BIOMETRICS", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(6.dp))

            // 各パラメータのプログレスバーを描画
            HudProgressBar("SMILE", smile, Color(0xFFFFCC00)) // 笑顔：黄色
            // 首の角度は ±20度 をMAX (1.0) としてゲージ化（絶対値で計算）
            HudProgressBar("HEAD_Y", minOf(abs(headY) / 20f, 1f), Color(0xFF00CCFF)) // 角度：青
            HudProgressBar("L_EYE", leftEye, Color(0xFF00FFCC))  // 左目：青緑
            HudProgressBar("R_EYE", rightEye, Color(0xFF00FFCC)) // 右目：青緑
        }
    }
}

/**
 * 📈 【HUD用プログレスバー（個別部品）】
 * ラベル名、現在値のパーセンテージ、バーを描画する共通関数です。
 */
@Composable
fun HudProgressBar(label: String, progress: Float, color: Color) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        // ラベルとパーセンテージ数値を左右に配置
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = color.copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Text("${(progress * 100).toInt()}%", color = color, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
        // 線状のプログレスバー
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = color,
            trackColor = Color.DarkGray
        )
    }
}