package com.example.faceauthappv2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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

data class DetectionData(
    val faces: List<Face> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val rotationDegrees: Int = 0
)

enum class AuthStep {
    WAITING,        // 待機中
    CHECK_TURN,     // Step 1: 横を向く
    CHECK_FRONT,    // Step 2: 正面に戻る
    CHECK_SMILE,    // Step 3: 笑顔を作る
    GRANTED         // 認証成功
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

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
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
        var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

        var registeredName by remember { mutableStateOf("") }
        var registeredFaceBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var isRegistered by remember { mutableStateOf(false) }
        var showNameDialog by remember { mutableStateOf(false) }

        var currentStep by remember { mutableStateOf(AuthStep.WAITING) }
        var isProcessing by remember { mutableStateOf(false) }

        val currentFace = detectionData.faces.firstOrNull()
        val smileProb = currentFace?.smilingProbability ?: 0f
        val headAngleY = currentFace?.headEulerAngleY ?: 0f
        val hasFace = detectionData.faces.isNotEmpty()

        // 💡 フレーム更新にキャンセルされない堅牢な自動進行ロジック
        LaunchedEffect(hasFace, isRegistered, currentStep, headAngleY, smileProb) {
            if (!isRegistered || isProcessing) return@LaunchedEffect

            if (!hasFace) {
                if (currentStep != AuthStep.WAITING && currentStep != AuthStep.GRANTED) {
                    currentStep = AuthStep.WAITING
                }
                return@LaunchedEffect
            }

            when (currentStep) {
                AuthStep.WAITING -> {
                    currentStep = AuthStep.CHECK_TURN
                    SoundManager.play(SoundType.BEEP)
                }
                AuthStep.CHECK_TURN -> {
                    if (abs(headAngleY) >= 12f) {
                        SoundManager.play(SoundType.STEP_PASS)
                        safeVibrate(context, 50)
                        currentStep = AuthStep.CHECK_FRONT
                    }
                }
                AuthStep.CHECK_FRONT -> {
                    if (abs(headAngleY) <= 8f) {
                        SoundManager.play(SoundType.STEP_PASS)
                        safeVibrate(context, 50)
                        currentStep = AuthStep.CHECK_SMILE
                    }
                }
                AuthStep.CHECK_SMILE -> {
                    if (smileProb >= 0.5f) { // 50%以上の笑顔で判定
                        isProcessing = true
                        // 💡 scope.launch で非同期処理にすることでキャンセルを防ぐ
                        scope.launch {
                            SoundManager.play(SoundType.SCANNING)
                            delay(500)
                            currentStep = AuthStep.GRANTED
                            SoundManager.play(SoundType.SUCCESS)
                            safeVibrate(context, 200)
                            isProcessing = false
                        }
                    }
                }
                else -> {}
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(
                onFacesDetected = { data -> detectionData = data },
                onPreviewViewCreated = { view -> previewViewRef = view }
            )

            FaceOverlay(detectionData = detectionData, currentStep = currentStep, isRegistered = isRegistered)

            TopStatusHeader(
                currentStep = currentStep,
                hasFace = hasFace,
                isRegistered = isRegistered,
                smileProb = smileProb,
                headAngleY = headAngleY,
                registeredName = registeredName,
                registeredBitmap = registeredFaceBitmap
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp, start = 20.dp, end = 20.dp)
            ) {
                if (!isRegistered) {
                    Button(
                        onClick = { showNameDialog = true },
                        enabled = hasFace,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(if (hasFace) "👤 顔と名前を新規登録する" else "カメラに顔を映してください", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                                    registeredFaceBitmap = previewViewRef?.bitmap
                                    isRegistered = true
                                    showNameDialog = false
                                    currentStep = AuthStep.WAITING
                                    SoundManager.play(SoundType.SUCCESS)
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
    hasFace: Boolean,
    isRegistered: Boolean,
    smileProb: Float,
    headAngleY: Float,
    registeredName: String,
    registeredBitmap: Bitmap?
) {
    val (statusText, statusColor) = if (!isRegistered) {
        if (hasFace) "🔵 顔を検出しました！下のボタンで登録してください" to Color(0xFF00CCFF)
        else "🔴 カメラに顔を映してください" to Color(0xFFFF3366)
    } else {
        when (currentStep) {
            AuthStep.WAITING -> "🔴 対象を検索中..." to Color(0xFFFF3366)
            AuthStep.CHECK_TURN -> "🔄 Step 1: 顔を左右どちらかに向けてください" to Color(0xFF00CCFF)
            AuthStep.CHECK_FRONT -> "🔽 Step 2: 正面を向いてください" to Color(0xFFB388FF)
            AuthStep.CHECK_SMILE -> "😊 Step 3: ニッコリ笑顔を見せてください" to Color(0xFFFFCC00)
            AuthStep.GRANTED -> "❇️ ACCESS GRANTED [ 本人確認完了 ]" to Color(0xFF00FF66)
        }
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
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(text = statusText, color = statusColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)

        if (isRegistered && currentStep != AuthStep.WAITING) {
            Spacer(modifier = Modifier.height(6.dp))
            when (currentStep) {
                AuthStep.CHECK_TURN -> Text("首角度: ${headAngleY.toInt()}° (目標: 12°以上)", color = Color.White.copy(0.8f), fontSize = 12.sp)
                AuthStep.CHECK_FRONT -> Text("首角度: ${headAngleY.toInt()}° (目標: 正面 8°以下)", color = Color.White.copy(0.8f), fontSize = 12.sp)
                AuthStep.CHECK_SMILE -> Text("笑顔度: ${(smileProb * 100).toInt()}% (目標: 50%以上)", color = Color.White.copy(0.8f), fontSize = 12.sp)
                else -> {}
            }
        }
    }
}

@Composable
fun FaceOverlay(detectionData: DetectionData, currentStep: AuthStep, isRegistered: Boolean) {
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

            val themeColor = if (!isRegistered) {
                Color(0xFF00CCFF)
            } else {
                when (currentStep) {
                    AuthStep.GRANTED -> Color(0xFF00FF66)
                    AuthStep.CHECK_SMILE -> Color(0xFFFFCC00)
                    AuthStep.CHECK_FRONT -> Color(0xFFB388FF)
                    AuthStep.CHECK_TURN -> Color(0xFF00CCFF)
                    else -> Color(0xFF00FFCC)
                }
            }

            drawLine(themeColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, top), Offset(right - cornerLength, top), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, top), Offset(right, top + cornerLength), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth, StrokeCap.Round)
            drawLine(themeColor, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth, StrokeCap.Round)

            val laserY = top + (height * scanProgress)
            drawLine(themeColor.copy(alpha = 0.8f), Offset(left + 10f, laserY), Offset(right - 10f, laserY), 4.dp.toPx(), StrokeCap.Round)

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
fun CameraPreview(
    onFacesDetected: (DetectionData) -> Unit,
    onPreviewViewCreated: (PreviewView) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            onPreviewViewCreated(previewView)

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

enum class SoundType { BEEP, STEP_PASS, SCANNING, SUCCESS }

object SoundManager {
    private var toneGen: ToneGenerator? = null

    fun play(type: SoundType) {
        try {
            if (toneGen == null) {
                toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            }
            val tone = when (type) {
                SoundType.BEEP -> ToneGenerator.TONE_PROP_BEEP
                SoundType.STEP_PASS -> ToneGenerator.TONE_PROP_ACK
                SoundType.SCANNING -> ToneGenerator.TONE_CDMA_PIP
                SoundType.SUCCESS -> ToneGenerator.TONE_PROP_PROMPT
            }
            toneGen?.startTone(tone, 100)
        } catch (e: Exception) {
            Log.e("SoundManager", "音再生エラー", e)
        }
    }

    fun release() {
        toneGen?.release()
        toneGen = null
    }
}

fun safeVibrate(context: Context, durationMs: Long) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(durationMs)
        }
    } catch (e: Exception) {
        Log.e("Vibrate", "バイブレーション非対応または例外", e)
    }
}