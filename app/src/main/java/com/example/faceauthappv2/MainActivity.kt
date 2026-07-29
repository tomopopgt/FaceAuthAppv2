package com.example.faceauthappv2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class DetectionData(
    val faces: List<Face> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val rotationDegrees: Int = 0
)

// 💡 認証状態を表すステート
enum class AuthStatus {
    WAITING_FOR_FACE, // 顔待ち
    UNREGISTERED,     // 顔検出中（未登録）
    SCANNING,         // 照合スキャン中
    GRANTED           // 認証成功
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraScreen()
                }
            }
        }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        var detectionData by remember { mutableStateOf(DetectionData()) }
        var isRegistered by remember { mutableStateOf(false) } // 顔が登録されているか
        var authStatus by remember { mutableStateOf(AuthStatus.WAITING_FOR_FACE) }

        // 顔の検出状況に応じてステートを更新
        LaunchedEffect(detectionData.faces, isRegistered) {
            if (authStatus != AuthStatus.SCANNING && authStatus != AuthStatus.GRANTED) {
                authStatus = if (detectionData.faces.isNotEmpty()) {
                    if (isRegistered) AuthStatus.UNREGISTERED else AuthStatus.UNREGISTERED
                } else {
                    AuthStatus.WAITING_FOR_FACE
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 1. カメラプレビュー
            CameraPreview(onFacesDetected = { data -> detectionData = data })

            // 2. サイバースキャンオーバレイ
            FaceOverlay(
                detectionData = detectionData,
                authStatus = authStatus
            )

            // 3. 上部ステータス表示
            TopStatusHeader(authStatus = authStatus, isRegistered = isRegistered)

            // 4. 画面下のコントロールボタン
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp, start = 20.dp, end = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val faceDetected = detectionData.faces.isNotEmpty()

                if (!isRegistered) {
                    Button(
                        onClick = {
                            if (faceDetected) {
                                scope.launch {
                                    authStatus = AuthStatus.SCANNING
                                    delay(1500) // 1.5秒のスキャン演出
                                    isRegistered = true
                                    authStatus = AuthStatus.GRANTED
                                }
                            }
                        },
                        enabled = faceDetected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FFCC),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = if (faceDetected) "👤 この顔をユーザー登録する" else "顔をカメラに合わせてください",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (faceDetected) {
                                    scope.launch {
                                        authStatus = AuthStatus.SCANNING
                                        delay(1200)
                                        authStatus = AuthStatus.GRANTED
                                    }
                                }
                            },
                            enabled = faceDetected && authStatus != AuthStatus.SCANNING,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Text("🔑 顔認証を実行", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                isRegistered = false
                                authStatus = AuthStatus.WAITING_FOR_FACE
                            },
                            modifier = Modifier.height(56.dp),
                            border = BorderStroke(1.dp, Color.Red)
                        ) {
                            Text("リセット", color = Color.Red)
                        }
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("カメラの利用許可が必要です")
        }
    }
}

@Composable
fun TopStatusHeader(authStatus: AuthStatus, isRegistered: Boolean) {
    val (statusText, statusColor) = when (authStatus) {
        AuthStatus.WAITING_FOR_FACE -> "🔴 TARGET SEARCHING..." to Color(0xFFFF3366)
        AuthStatus.UNREGISTERED -> if (isRegistered) "🟡 FACE DETECTED" to Color(0xFFFFCC00) else "🔵 NEW FACE DETECTED" to Color(0xFF00CCFF)
        AuthStatus.SCANNING -> "⚡ SCANNING & MATCHING..." to Color(0xFF00FFCC)
        AuthStatus.GRANTED -> "❇️ ACCESS GRANTED [ OK ]" to Color(0xFF00FF66)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 16.dp, end = 16.dp)
            .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(12.dp))
            .border(1.dp, statusColor, shape = RoundedCornerShape(12.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FaceOverlay(
    detectionData: DetectionData,
    authStatus: AuthStatus
) {
    // 💡 レーザースキャン線用の無限ループアニメーション (0.0f -> 1.0f)
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

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (detectionData.faces.isEmpty() || detectionData.imageWidth == 0 || detectionData.imageHeight == 0) return@Canvas

        val isPortrait = detectionData.rotationDegrees == 90 || detectionData.rotationDegrees == 270
        val imgWidth = if (isPortrait) detectionData.imageHeight else detectionData.imageWidth
        val imgHeight = if (isPortrait) detectionData.imageWidth else detectionData.imageHeight

        val scaleX = size.width / imgWidth.toFloat()
        val scaleY = size.height / imgHeight.toFloat()

        detectionData.faces.forEach { face ->
            val boundingBox = face.boundingBox

            val left = size.width - (boundingBox.right * scaleX)
            val top = boundingBox.top * scaleY
            val right = size.width - (boundingBox.left * scaleX)
            val bottom = boundingBox.bottom * scaleY

            val width = right - left
            val height = bottom - top
            val cornerLength = width * 0.25f
            val strokeWidth = 6.dp.toPx()

            // 状態に応じたテーマカラー切り替え
            val themeColor = when (authStatus) {
                AuthStatus.GRANTED -> Color(0xFF00FF66)   // 認証成功：鮮烈なグリーン
                AuthStatus.SCANNING -> Color(0xFF00E5FF)  // スキャン中：ネオンブルー
                else -> Color(0xFF00FFCC)                 // 通常：シアン
            }

            // 四隅ブラケット描画
            drawLine(themeColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, top), Offset(right - cornerLength, top), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, top), Offset(right, top + cornerLength), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth, StrokeCap.Round)

            // 💡 スキャン中または認識中は、枠内をレーザー線が上下に移動！
            if (authStatus == AuthStatus.SCANNING || authStatus == AuthStatus.UNREGISTERED) {
                val laserY = top + (height * scanProgress)
                drawLine(
                    color = themeColor.copy(alpha = 0.8f),
                    start = Offset(left + 10f, laserY),
                    end = Offset(right - 10f, laserY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun CameraPreview(onFacesDetected: (DetectionData) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val cameraExecutor = Executors.newSingleThreadExecutor()
                imageAnalysis.setAnalyzer(cameraExecutor, FaceAnalyzer(onFacesDetected))

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                } catch (e: Exception) {
                    Log.e("CameraPreview", "カメラエラー", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

class FaceAnalyzer(private val onFacesDetected: (DetectionData) -> Unit) : ImageAnalysis.Analyzer {
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()

    private val detector = FaceDetection.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    onFacesDetected(
                        DetectionData(
                            faces = faces,
                            imageWidth = mediaImage.width,
                            imageHeight = mediaImage.height,
                            rotationDegrees = rotationDegrees
                        )
                    )
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }
}