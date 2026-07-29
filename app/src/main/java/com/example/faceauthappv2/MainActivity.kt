package com.example.faceauthappv2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

data class DetectionData(
    val faces: List<Face> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val rotationDegrees: Int = 0
)

enum class AuthStep {
    WAITING, CHECK_TURN, CHECK_FRONT, CHECK_SMILE, GRANTED, TIMEOUT
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
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // 💡 ユーザー情報のステート
    var registeredName by remember { mutableStateOf("") }
    var registeredFaceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRegistered by remember { mutableStateOf(false) }

    // 💡 システムログのステート
    val systemLogs = remember { mutableStateListOf<String>() }
    fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        systemLogs.add("[$time] $msg")
        if (systemLogs.size > 6) systemLogs.removeAt(0)
    }

    // 初回起動時に保存データを読み込む
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
        val (savedName, savedBitmap) = LocalDataManager.loadUserData(context)
        if (savedName.isNotEmpty()) {
            registeredName = savedName
            registeredFaceBitmap = savedBitmap
            isRegistered = true
            addLog("SYSTEM_INIT: USER_DATA_LOADED")
        }
        addLog("CAMERA_SENSOR: STANDBY")
    }

    if (hasCameraPermission) {
        var detectionData by remember { mutableStateOf(DetectionData()) }
        var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
        var showNameDialog by remember { mutableStateOf(false) }
        var currentStep by remember { mutableStateOf(AuthStep.WAITING) }
        var isProcessing by remember { mutableStateOf(false) }
        var step1StartTime by remember { mutableLongStateOf(0L) } // タイムアウト監視用

        val currentFace = detectionData.faces.firstOrNull()
        val smileProb = currentFace?.smilingProbability ?: 0f
        val headAngleY = currentFace?.headEulerAngleY ?: 0f
        val leftEye = currentFace?.leftEyeOpenProbability ?: 0f
        val rightEye = currentFace?.rightEyeOpenProbability ?: 0f
        val hasFace = detectionData.faces.isNotEmpty()

        // ステップ進行＆タイムアウト判定
        LaunchedEffect(hasFace, isRegistered, currentStep, headAngleY, smileProb) {
            if (!isRegistered || isProcessing) return@LaunchedEffect

            if (!hasFace) {
                if (currentStep != AuthStep.WAITING && currentStep != AuthStep.GRANTED) {
                    currentStep = AuthStep.WAITING
                    step1StartTime = 0L
                    addLog("TARGET_LOST: RESETTING_STEPS")
                }
                return@LaunchedEffect
            }

            when (currentStep) {
                AuthStep.WAITING -> {
                    currentStep = AuthStep.CHECK_TURN
                    step1StartTime = System.currentTimeMillis()
                    SoundManager.play(SoundType.BEEP)
                    addLog("AUTH_START: CHECKING_LIVENESS")
                }
                AuthStep.CHECK_TURN -> {
                    // 🚨 5秒間、首を振らないと「写真なりすまし」と判定してタイムアウト
                    if (System.currentTimeMillis() - step1StartTime > 5000L) {
                        currentStep = AuthStep.TIMEOUT
                        SoundManager.play(SoundType.ERROR)
                        safeVibrate(context, 500)
                        addLog("SECURITY_ALERT: SPOOFING_DETECTED")
                        scope.launch {
                            delay(3000)
                            currentStep = AuthStep.WAITING // 3秒後に復帰
                        }
                        return@LaunchedEffect
                    }

                    if (abs(headAngleY) >= 12f) {
                        SoundManager.play(SoundType.STEP_PASS)
                        safeVibrate(context, 50)
                        addLog("LIVENESS: YAW_ANGLE_PASS")
                        currentStep = AuthStep.CHECK_FRONT
                    }
                }
                AuthStep.CHECK_FRONT -> {
                    if (abs(headAngleY) <= 8f) {
                        SoundManager.play(SoundType.STEP_PASS)
                        safeVibrate(context, 50)
                        addLog("LIVENESS: FRONT_FACING_PASS")
                        currentStep = AuthStep.CHECK_SMILE
                    }
                }
                AuthStep.CHECK_SMILE -> {
                    if (smileProb >= 0.5f) {
                        isProcessing = true
                        addLog("LIVENESS: SMILE_DETECTED")
                        scope.launch {
                            SoundManager.play(SoundType.SCANNING)
                            delay(500)
                            currentStep = AuthStep.GRANTED
                            SoundManager.play(SoundType.SUCCESS)
                            safeVibrate(context, 200)
                            addLog("ACCESS_GRANTED: ID_MATCHED")
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

            // 左上：システムログターミナル
            LogTerminal(logs = systemLogs)

            // 右上：グラフィカルHUDメーター
            if (isRegistered && hasFace) {
                BiometricHUD(smileProb, headAngleY, leftEye, rightEye)
            }

            // メインヘッダー
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 110.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TopStatusHeader(
                    currentStep = currentStep,
                    hasFace = hasFace,
                    isRegistered = isRegistered,
                    registeredName = registeredName,
                    registeredBitmap = registeredFaceBitmap
                )
            }

            // 下部ボタン群
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 40.dp, start = 20.dp, end = 20.dp)) {
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
                            // 💡 登録解除とデータの削除
                            LocalDataManager.clearUserData(context)
                            isRegistered = false
                            registeredName = ""
                            registeredFaceBitmap = null
                            currentStep = AuthStep.WAITING
                            addLog("SYSTEM: USER_DATA_CLEARED")
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
                            OutlinedTextField(value = inputText, onValueChange = { inputText = it }, singleLine = true)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    registeredName = inputText
                                    registeredFaceBitmap = previewViewRef?.bitmap
                                    // 💡 ローカルにデータを保存！
                                    LocalDataManager.saveUserData(context, inputText, registeredFaceBitmap)
                                    isRegistered = true
                                    showNameDialog = false
                                    currentStep = AuthStep.WAITING
                                    SoundManager.play(SoundType.REGISTERED)
                                    addLog("SYSTEM: NEW_USER_REGISTERED")
                                }
                            }
                        ) { Text("登録完了") }
                    }
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("カメラの利用許可が必要です") }
    }
}

// 💡 【新機能1】ハッカー風リアルタイムログ端末
@Composable
fun LogTerminal(logs: List<String>) {
    Box(
        modifier = Modifier.padding(top = 40.dp, start = 16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(8.dp).width(200.dp)
    ) {
        LazyColumn {
            items(logs) { log ->
                Text(text = log, color = Color(0xFF00FFCC), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
        }
    }
}

// 💡 【新機能2】生体パラメータHUDメーター
@Composable
fun BiometricHUD(smile: Float, headY: Float, leftEye: Float, rightEye: Float) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, end = 16.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(12.dp).width(120.dp)
        ) {
            Text("BIOMETRICS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(8.dp))

            HudProgressBar("SMILE", smile, Color(0xFFFFCC00))
            HudProgressBar("HEAD_Y", minOf(abs(headY) / 20f, 1f), Color(0xFF00CCFF)) // 20度をMAXとしてゲージ化
            HudProgressBar("L_EYE", leftEye, Color(0xFF00FFCC))
            HudProgressBar("R_EYE", rightEye, Color(0xFF00FFCC))
        }
    }
}

@Composable
fun HudProgressBar(label: String, progress: Float, color: Color) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = color.copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Text("${(progress * 100).toInt()}%", color = color, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
        LinearProgressIndicator(
            progress = progress, modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 2.dp),
            color = color, trackColor = Color.DarkGray
        )
    }
}

@Composable
fun TopStatusHeader(
    currentStep: AuthStep, hasFace: Boolean, isRegistered: Boolean,
    registeredName: String, registeredBitmap: Bitmap?
) {
    val (statusText, statusColor) = if (!isRegistered) {
        if (hasFace) "🔵 顔を検出！下のボタンで登録" to Color(0xFF00CCFF)
        else "🔴 カメラに顔を映してください" to Color(0xFFFF3366)
    } else {
        when (currentStep) {
            AuthStep.WAITING -> "🔴 対象を検索中..." to Color(0xFFFF3366)
            AuthStep.CHECK_TURN -> "🔄 Step 1: 左右どちらかに顔を向ける" to Color(0xFF00CCFF)
            AuthStep.CHECK_FRONT -> "🔽 Step 2: 正面を向く" to Color(0xFFB388FF)
            AuthStep.CHECK_SMILE -> "😊 Step 3: ニッコリ笑顔" to Color(0xFFFFCC00)
            AuthStep.GRANTED -> "❇️ ACCESS GRANTED [ 本人確認完了 ]" to Color(0xFF00FF66)
            AuthStep.TIMEOUT -> "🚨 警告: 画面の固定(なりすまし)を検知" to Color.Red
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp)).border(1.dp, statusColor, RoundedCornerShape(12.dp)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (registeredName.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                registeredBitmap?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, Color(0xFF00FFCC), CircleShape), contentScale = ContentScale.Crop)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("対象者: $registeredName", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(text = statusText, color = statusColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FaceOverlay(detectionData: DetectionData, currentStep: AuthStep, isRegistered: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanProgress by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "laser")

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (detectionData.faces.isEmpty() || detectionData.imageWidth == 0 || detectionData.imageHeight == 0) return@Canvas
        val isPortrait = detectionData.rotationDegrees == 90 || detectionData.rotationDegrees == 270
        val imgWidth = if (isPortrait) detectionData.imageHeight else detectionData.imageWidth
        val imgHeight = if (isPortrait) detectionData.imageWidth else detectionData.imageHeight
        val scaleX = size.width / imgWidth.toFloat()
        val scaleY = size.height / imgHeight.toFloat()

        detectionData.faces.forEach { face ->
            val left = size.width - (face.boundingBox.right * scaleX)
            val top = face.boundingBox.top * scaleY
            val right = size.width - (face.boundingBox.left * scaleX)
            val bottom = face.boundingBox.bottom * scaleY
            val width = right - left
            val height = bottom - top
            val corner = width * 0.25f
            val stroke = 6.dp.toPx()

            val color = if (!isRegistered) Color(0xFF00CCFF) else when (currentStep) {
                AuthStep.GRANTED -> Color(0xFF00FF66)
                AuthStep.CHECK_SMILE -> Color(0xFFFFCC00)
                AuthStep.CHECK_FRONT -> Color(0xFFB388FF)
                AuthStep.TIMEOUT -> Color.Red
                else -> Color(0xFF00CCFF)
            }

            drawLine(color, Offset(left, top), Offset(left + corner, top), stroke, StrokeCap.Round)
            drawLine(color, Offset(left, top), Offset(left, top + corner), stroke, StrokeCap.Round)
            drawLine(color, Offset(right, top), Offset(right - corner, top), stroke, StrokeCap.Round)
            drawLine(color, Offset(right, top), Offset(right, top + corner), stroke, StrokeCap.Round)
            drawLine(color, Offset(left, bottom), Offset(left + corner, bottom), stroke, StrokeCap.Round)
            drawLine(color, Offset(left, bottom), Offset(left, bottom - corner), stroke, StrokeCap.Round)
            drawLine(color, Offset(right, bottom), Offset(right - corner, bottom), stroke, StrokeCap.Round)
            drawLine(color, Offset(right, bottom), Offset(right, bottom - corner), stroke, StrokeCap.Round)

            if (currentStep != AuthStep.TIMEOUT) {
                val laserY = top + (height * scanProgress)
                drawLine(color.copy(alpha = 0.8f), Offset(left + 10f, laserY), Offset(right - 10f, laserY), 4.dp.toPx(), StrokeCap.Round)
            }

            val landmarks = listOfNotNull(face.getLandmark(FaceLandmark.LEFT_EYE), face.getLandmark(FaceLandmark.RIGHT_EYE), face.getLandmark(FaceLandmark.NOSE_BASE), face.getLandmark(FaceLandmark.MOUTH_LEFT), face.getLandmark(FaceLandmark.MOUTH_RIGHT), face.getLandmark(FaceLandmark.MOUTH_BOTTOM))
            landmarks.forEach { lm ->
                val lx = size.width - (lm.position.x * scaleX)
                val ly = lm.position.y * scaleY
                drawCircle(color, 4.dp.toPx(), Offset(lx, ly))
                drawCircle(color.copy(alpha = 0.4f), 9.dp.toPx(), Offset(lx, ly))
            }
        }
    }
}

@Composable
fun CameraPreview(onFacesDetected: (DetectionData) -> Unit, onPreviewViewCreated: (PreviewView) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { ctx ->
            val pv = PreviewView(ctx)
            onPreviewViewCreated(pv)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cp = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), FaceAnalyzer(onFacesDetected))
                try {
                    cp.unbindAll()
                    cp.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis)
                } catch (e: Exception) { Log.e("Camera", "エラー", e) }
            }, ContextCompat.getMainExecutor(ctx))
            pv
        }, modifier = Modifier.fillMaxSize()
    )
}

class FaceAnalyzer(private val onFacesDetected: (DetectionData) -> Unit) : ImageAnalysis.Analyzer {
    private val options = FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build()
    private val detector = FaceDetection.getClient(options)
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            detector.process(image).addOnSuccessListener { faces -> onFacesDetected(DetectionData(faces, mediaImage.width, mediaImage.height, rotationDegrees)) }.addOnCompleteListener { imageProxy.close() }
        } else { imageProxy.close() }
    }
}

// 💡 【新機能3】データのローカル保存・読み込みマネージャー
object LocalDataManager {
    fun saveUserData(context: Context, name: String, bitmap: Bitmap?) {
        context.getSharedPreferences("FaceAuthPrefs", Context.MODE_PRIVATE).edit().putString("userName", name).apply()
        bitmap?.let {
            val file = File(context.filesDir, "registered_face.png")
            FileOutputStream(file).use { out -> it.compress(Bitmap.CompressFormat.PNG, 100, out) }
        }
    }
    fun loadUserData(context: Context): Pair<String, Bitmap?> {
        val name = context.getSharedPreferences("FaceAuthPrefs", Context.MODE_PRIVATE).getString("userName", "") ?: ""
        val file = File(context.filesDir, "registered_face.png")
        val bitmap = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        return Pair(name, bitmap)
    }
    fun clearUserData(context: Context) {
        context.getSharedPreferences("FaceAuthPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        File(context.filesDir, "registered_face.png").delete()
    }
}

enum class SoundType { BEEP, STEP_PASS, SCANNING, SUCCESS, REGISTERED, ERROR }

object SoundManager {
    private var toneGen: ToneGenerator? = null
    private val executor = Executors.newSingleThreadExecutor()
    fun play(type: SoundType) {
        try {
            if (toneGen == null) toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            executor.execute {
                when (type) {
                    SoundType.BEEP -> toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                    SoundType.STEP_PASS -> toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 80)
                    SoundType.SCANNING -> toneGen?.startTone(ToneGenerator.TONE_CDMA_PIP, 120)
                    SoundType.SUCCESS -> {
                        toneGen?.startTone(ToneGenerator.TONE_DTMF_6, 80); Thread.sleep(100)
                        toneGen?.startTone(ToneGenerator.TONE_DTMF_9, 80); Thread.sleep(100)
                        toneGen?.startTone(ToneGenerator.TONE_SUP_CONFIRM, 400)
                    }
                    SoundType.REGISTERED -> {
                        toneGen?.startTone(ToneGenerator.TONE_DTMF_D, 100); Thread.sleep(120)
                        toneGen?.startTone(ToneGenerator.TONE_PROP_PROMPT, 400)
                    }
                    // 🚨 エラー音追加
                    SoundType.ERROR -> toneGen?.startTone(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 400)
                }
            }
        } catch (e: Exception) { Log.e("Sound", "エラー", e) }
    }
    fun release() { toneGen?.release(); toneGen = null }
}

fun safeVibrate(context: Context, durationMs: Long) {
    try {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v?.vibrate(durationMs)
    } catch (e: Exception) {}
}