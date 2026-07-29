package com.example.faceauthappv2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import kotlin.math.abs

// 💡 検出データとカメラのBitmapを保持
data class DetectionData(
    val faces: List<Face> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val rotationDegrees: Int = 0,
    val frameBitmap: Bitmap? = null
)

// 💡 多段階認証ステップ
enum class AuthStep {
    WAITING,        // 顔待ち
    CHECK_TURN,     // Step 1: 横を向く
    CHECK_SMILE,    // Step 2: 笑顔を作る
    GRANTED,        // 認証成功
    REJECTED        // 認証失敗
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
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
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

        // ユーザー情報
        var registeredName by remember { mutableStateOf("") }
        var registeredFaceBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var isRegistered by remember { mutableStateOf(false) }
        var showNameDialog by remember { mutableStateOf(false) }

        // 認証状態
        var currentStep by remember { mutableStateOf(AuthStep.WAITING) }
        var isProcessing by remember { mutableStateOf(false) }

        val currentFace = detectionData.faces.firstOrNull()
        val smileProb = currentFace?.smilingProbability ?: 0f
        val headAngleY = currentFace?.headEulerAngleY ?: 0f // 左右の首振り角度 (+: 左, -: 右)

        // 💡 ハンズフリー自動認証ロジック
        LaunchedEffect(detectionData.faces, isRegistered, currentStep) {
            if (!isRegistered || isProcessing) return@LaunchedEffect

            if (detectionData.faces.isEmpty()) {
                currentStep = AuthStep.WAITING
                return@LaunchedEffect
            }

            when (currentStep) {
                AuthStep.WAITING -> {
                    currentStep = AuthStep.CHECK_TURN
                    playCyberSound(context, SoundType.BEEP)
                }
                AuthStep.CHECK_TURN -> {
                    // 首を20度以上横に向けたらクリア！
                    if (abs(headAngleY) >= 20f) {
                        playCyberSound(context, SoundType.STEP_PASS)
                        vibrate(context, 50)
                        currentStep = AuthStep.CHECK_SMILE
                    }
                }
                AuthStep.CHECK_SMILE -> {
                    // 笑顔度90%以上で最終クリア！
                    if (smileProb >= 0.9f) {
                        isProcessing = true
                        playCyberSound(context, SoundType.SCANNING)
                        delay(800)
                        currentStep = AuthStep.GRANTED
                        playCyberSound(context, SoundType.SUCCESS)
                        vibrate(context, 200)
                        isProcessing = false
                    }
                }
                else -> {}
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(onFacesDetected = { data -> detectionData = data })

            FaceOverlay(detectionData = detectionData, currentStep = currentStep)

            // 上部ステータス ＆ 生体HUD
            TopStatusHeader(
                currentStep = currentStep,
                smileProb = smileProb,
                headAngleY = headAngleY,
                registeredName = registeredName,
                registeredBitmap = registeredFaceBitmap
            )

            // 下部操作エリア
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp, start = 20.dp, end = 20.dp)
            ) {
                val faceDetected = detectionData.faces.isNotEmpty()

                if (!isRegistered) {
                    Button(
                        onClick = { showNameDialog = true },
                        enabled = faceDetected,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(if (faceDetected) "👤 顔と名前を新規登録する" else "カメラに顔を映してください", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            isRegistered = false
                            registeredName = ""
                            registeredFaceBitmap = null
                            currentStep = AuthStep.WAITING
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("登録解除・再リセット", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 名前入力ダイアログ
            if (showNameDialog) {
                var inputText by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showNameDialog = false },
                    title = { Text("ユーザー登録") },
                    text = {
                        Column {
                            Text("名前を入力してください")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    registeredName = inputText
                                    registeredFaceBitmap = detectionData.frameBitmap
                                    isRegistered = true
                                    showNameDialog = false
                                    currentStep = AuthStep.WAITING
                                    playCyberSound(context, SoundType.SUCCESS)
                                }
                            }
                        ) {
                            Text("登録完了")
                        }
                    }
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("カメラの利用許可が必要です")
        }
    }
}

@Composable
fun TopStatusHeader(
    currentStep: AuthStep,
    smileProb: Float,
    headAngleY: Float,
    registeredName: String,
    registeredBitmap: Bitmap?
) {
    val (statusText, statusColor) = when (currentStep) {
        AuthStep.WAITING -> "🔴 対象を検索中..." to Color(0xFFFF3366)
        AuthStep.CHECK_TURN -> "🔄 Step 1: 顔を左右どちらかに向けてください" to Color(0xFF00CCFF)
        AuthStep.CHECK_SMILE -> "😊 Step 2: 90%以上の笑顔を見せてください" to Color(0xFFFFCC00)
        AuthStep.GRANTED -> "❇️ ACCESS GRANTED [ 本人確認完了 ]" to Color(0xFF00FF66)
        AuthStep.REJECTED -> "❌ 認証エラー" to Color(0xFFFF3300)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 16.dp, end = 16.dp)
            .background(Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(12.dp))
            .border(1.dp, statusColor, shape = RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 登録ユーザーHUD表示
        if (registeredName.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                registeredBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, Color(0xFF00FFCC), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("対象者: $registeredName", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Divider(color = Color.Gray.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(text = statusText, color = statusColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        if (currentStep != AuthStep.WAITING) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Text("首角度: ${headAngleY.toInt()}° (目標: 20°以上)", color = Color.White.copy(0.8f), fontSize = 11.sp)
                Text("笑顔度: ${(smileProb * 100).toInt()}% (目標: 90%以上)", color = Color.White.copy(0.8f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun FaceOverlay(detectionData: DetectionData, currentStep: AuthStep) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
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

            val themeColor = when (currentStep) {
                AuthStep.GRANTED -> Color(0xFF00FF66)
                AuthStep.CHECK_SMILE -> Color(0xFFFFCC00)
                AuthStep.CHECK_TURN -> Color(0xFF00CCFF)
                else -> Color(0xFF00FFCC)
            }

            // 四隅ブラケット
            drawLine(themeColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, top), Offset(right - cornerLength, top), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, top), Offset(right, top + cornerLength), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth, StrokeCap.Round)

            // レーザー線
            val laserY = top + (height * scanProgress)
            drawLine(themeColor.copy(alpha = 0.8f), Offset(left + 10f, laserY), Offset(right - 10f, laserY), 4.dp.toPx(), StrokeCap.Round)

            // 特徴点
            val landmarks = listOfNotNull(
                face.getLandmark(FaceLandmark.LEFT_EYE), face.getLandmark(FaceLandmark.RIGHT_EYE),
                face.getLandmark(FaceLandmark.NOSE_BASE), face.getLandmark(FaceLandmark.MOUTH_LEFT),
                face.getLandmark(FaceLandmark.MOUTH_RIGHT), face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
            )
            landmarks.forEach { landmark ->
                val lx = size.width - (landmark.position.x * scaleX)
                val ly = landmark.position.y * scaleY
                drawCircle(themeColor, 4.dp.toPx(), Offset(lx, ly))
                drawCircle(themeColor.copy(alpha = 0.4f), 9.dp.toPx(), Offset(lx, ly))
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
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val cameraExecutor = Executors.newSingleThreadExecutor()
                imageAnalysis.setAnalyzer(cameraExecutor, FaceAnalyzer(onFacesDetected))

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis)
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
            val bitmap = imageProxy.toBitmap().rotate(rotationDegrees.toFloat())

            detector.process(image)
                .addOnSuccessListener { faces ->
                    onFacesDetected(
                        DetectionData(
                            faces = faces,
                            imageWidth = mediaImage.width,
                            imageHeight = mediaImage.height,
                            rotationDegrees = rotationDegrees,
                            frameBitmap = bitmap
                        )
                    )
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees); postScale(-1f, 1f) } // 鏡像補正
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}

// 💡 サイバー効果音再生関数
enum class SoundType { BEEP, STEP_PASS, SCANNING, SUCCESS }

fun playCyberSound(context: Context, type: SoundType) {
    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        when (type) {
            SoundType.BEEP -> toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            SoundType.STEP_PASS -> toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            SoundType.SCANNING -> toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 200)
            SoundType.SUCCESS -> toneGen.startTone(ToneGenerator.TONE_PROP_PROMPT, 300)
        }
    } catch (e: Exception) {
        Log.e("Sound", "音声再生エラー", e)
    }
}

// 💡 バイブレーション関数
fun vibrate(context: Context, durationMs: Long) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
    }
}