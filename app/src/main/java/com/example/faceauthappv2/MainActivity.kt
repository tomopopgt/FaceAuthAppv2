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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.util.concurrent.Executors

// 💡 検出結果を保持するデータ構造
data class DetectionData(
    val faces: List<Face> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val rotationDegrees: Int = 0
)

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

        Box(modifier = Modifier.fillMaxSize()) {
            // 1. カメラプレビュー
            CameraPreview(
                onFacesDetected = { data ->
                    detectionData = data
                }
            )

            // 2. 近未来スキャン枠（顔追従オーバレイ）
            FaceOverlay(detectionData = detectionData)

            // 3. 上部ステータスヘッダー
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val isDetected = detectionData.faces.isNotEmpty()
                Text(
                    text = if (isDetected) "SYSTEM: FACE DETECTED [ OK ]" else "SYSTEM: SCANNING...",
                    color = if (isDetected) Color(0xFF00FFCC) else Color(0xFFFF3366),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("カメラの利用許可が必要です")
        }
    }
}

@Composable
fun CameraPreview(
    onFacesDetected: (DetectionData) -> Unit
) {
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
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "カメラ設定エラー", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * 💡 顔の座標に合わせて「近未来的なターゲット枠」をキャンバス描画するコンポーネント
 */
@Composable
fun FaceOverlay(detectionData: DetectionData) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (detectionData.faces.isEmpty() || detectionData.imageWidth == 0 || detectionData.imageHeight == 0) return@Canvas

        // カメラセンサーの向き（縦持ちの場合、横幅と高さが逆転する）
        val isPortrait = detectionData.rotationDegrees == 90 || detectionData.rotationDegrees == 270
        val imgWidth = if (isPortrait) detectionData.imageHeight else detectionData.imageWidth
        val imgHeight = if (isPortrait) detectionData.imageWidth else detectionData.imageHeight

        // 画面サイズへの拡大・縮小スケール
        val scaleX = size.width / imgWidth.toFloat()
        val scaleY = size.height / imgHeight.toFloat()

        detectionData.faces.forEach { face ->
            val boundingBox = face.boundingBox

            // インカメラの鏡像（左右反転）とスケール調整
            val left = size.width - (boundingBox.right * scaleX)
            val top = boundingBox.top * scaleY
            val right = size.width - (boundingBox.left * scaleX)
            val bottom = boundingBox.bottom * scaleY

            val width = right - left
            val height = bottom - top
            val cornerLength = width * 0.25f // 四隅の線の長さ
            val strokeWidth = 6.dp.toPx()
            val themeColor = Color(0xFF00FFCC) // サイバー感のあるネオンシアン

            // --- 🤖 近未来ターゲットフレーム（四隅のカッコ `[ ]`）の描画 ---
            // 1. 左上コーナー
            drawLine(themeColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth, StrokeCap.Round)

            // 2. 右上コーナー
            drawLine(themeColor, Offset(right, top), Offset(right - cornerLength, top), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, top), Offset(right, top + cornerLength), strokeWidth, StrokeCap.Round)

            // 3. 左下コーナー
            drawLine(themeColor, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth, StrokeCap.Round)

            // 4. 右下コーナー
            drawLine(themeColor, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth, StrokeCap.Round)
        }
    }
}

class FaceAnalyzer(
    private val onFacesDetected: (DetectionData) -> Unit
) : ImageAnalysis.Analyzer {

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
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
