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
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class DetectionData(
    val faces: List<Face> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val rotationDegrees: Int = 0
)

enum class AuthStatus {
    WAITING_FOR_FACE,
    UNREGISTERED,
    SCANNING,
    GRANTED,
    REJECTED
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
        var isRegistered by remember { mutableStateOf(false) }
        var authStatus by remember { mutableStateOf(AuthStatus.WAITING_FOR_FACE) }

        val currentFace = detectionData.faces.firstOrNull()
        val smileProb = currentFace?.smilingProbability ?: 0f

        LaunchedEffect(detectionData.faces, isRegistered) {
            if (authStatus != AuthStatus.SCANNING && authStatus != AuthStatus.GRANTED && authStatus != AuthStatus.REJECTED) {
                authStatus = if (detectionData.faces.isNotEmpty()) {
                    AuthStatus.UNREGISTERED
                } else {
                    AuthStatus.WAITING_FOR_FACE
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(onFacesDetected = { data -> detectionData = data })

            FaceOverlay(
                detectionData = detectionData,
                authStatus = authStatus
            )

            TopStatusHeader(authStatus = authStatus, smileProb = smileProb)

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
                                    delay(1200)
                                    isRegistered = true
                                    authStatus = AuthStatus.UNREGISTERED
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
                            text = if (faceDetected) "👤 顔をシステムに初期登録" else "カメラに顔を映してください",
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
                                        delay(1000)

                                        // 💡 笑顔度90%以上（0.9f）の時のみ認証許可！
                                        if (smileProb >= 0.9f) {
                                            authStatus = AuthStatus.GRANTED
                                        } else {
                                            authStatus = AuthStatus.REJECTED
                                        }
                                    }
                                }
                            },
                            enabled = faceDetected && authStatus != AuthStatus.SCANNING,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Text("😊 笑顔で認証を実行", color = Color.Black, fontWeight = FontWeight.Bold)
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
fun TopStatusHeader(authStatus: AuthStatus, smileProb: Float) {
    val (statusText, statusColor) = when (authStatus) {
        AuthStatus.WAITING_FOR_FACE -> "🔴 対象を検索中..." to Color(0xFFFF3366)
        AuthStatus.UNREGISTERED -> "🔵 顔を検出しました" to Color(0xFF00CCFF)
        AuthStatus.SCANNING -> "⚡ 笑顔度と特徴点を解析中..." to Color(0xFF00FFCC)
        AuthStatus.GRANTED -> "❇️ 認証成功 [ 最高の笑顔を確認 ]" to Color(0xFF00FF66)
        AuthStatus.REJECTED -> "❌ 認証拒否 [ 笑顔が足りません！ ]" to Color(0xFFFF3300)
    }

    val smilePercent = (smileProb * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 16.dp, end = 16.dp)
            .background(Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(12.dp))
            .border(1.dp, statusColor, shape = RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        if (authStatus != AuthStatus.WAITING_FOR_FACE) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "生体検知データ | 笑顔度: $smilePercent% (必要: 90%以上)",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun FaceOverlay(
    detectionData: DetectionData,
    authStatus: AuthStatus
) {
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

            val themeColor = when (authStatus) {
                AuthStatus.GRANTED -> Color(0xFF00FF66)
                AuthStatus.REJECTED -> Color(0xFFFF3300)
                AuthStatus.SCANNING -> Color(0xFF00E5FF)
                else -> Color(0xFF00FFCC)
            }

            drawLine(themeColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, top), Offset(right - cornerLength, top), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, top), Offset(right, top + cornerLength), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth, StrokeCap.Round)

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

            val landmarks = listOfNotNull(
                face.getLandmark(FaceLandmark.LEFT_EYE),
                face.getLandmark(FaceLandmark.RIGHT_EYE),
                face.getLandmark(FaceLandmark.NOSE_BASE),
                face.getLandmark(FaceLandmark.MOUTH_LEFT),
                face.getLandmark(FaceLandmark.MOUTH_RIGHT),
                face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
            )

            landmarks.forEach { landmark ->
                val landmarkX = size.width - (landmark.position.x * scaleX)
                val landmarkY = landmark.position.y * scaleY

                drawCircle(color = themeColor, radius = 4.dp.toPx(), center = Offset(landmarkX, landmarkY))
                drawCircle(color = themeColor.copy(alpha = 0.4f), radius = 9.dp.toPx(), center = Offset(landmarkX, landmarkY))
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
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
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