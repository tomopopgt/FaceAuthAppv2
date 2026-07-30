package com.example.faceauthappv2.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.faceauthappv2.data.LocalDataManager
import com.example.faceauthappv2.model.AuthStep
import com.example.faceauthappv2.model.DetectionData
import com.example.faceauthappv2.model.UserData
import com.example.faceauthappv2.ui.components.* // ➕ 【追加】分離したUIコンポーネント群を読み込み
import com.example.faceauthappv2.utils.SoundManager
import com.example.faceauthappv2.utils.SoundType
import com.example.faceauthappv2.utils.safeVibrate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.abs

/*
================================================================================
【ファイル概要：FaceAuthScreen.kt】
このファイルは、アプリの核となる「顔認証＆生体検知カメラ画面」です。

【主な役割と処理の流れ】
1. CameraX によるインカメラ映像のリアルタイムキャプチャとプレビュー表示
2. ML Kit による顔検出（表情・首の傾き・目の開き具合の解析）
3. 多段階生体認証ロジック（首振り ➔ 正面復帰 ➔ 笑顔）のリアルタイムステート管理
4. 写真等による「なりすまし」防止の5秒タイムアウト判定
5. ユーザー登録ダイアログ表示とローカルストレージ（LocalDataManager）への保存
================================================================================
*/

/**
 * 🎥 【顔認証・登録カメラ画面（メインコンポーネント）】
 *
 * @param onBack 「◀ 戻る」ボタンが押された際にホーム画面へ戻るためのナビゲーションコールバック
 */
@Composable
fun FaceAuthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 🔒 カメラのパーミッション（権限）状態の管理
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 📱 パーミッション要求ダイアログのランチャー
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    // 💾 保存済みユーザー一覧とターミナル用ログの管理
    var userList by remember { mutableStateOf(LocalDataManager.getUsers(context)) }
    val systemLogs = remember { mutableStateListOf<String>() }

    // 📝 ターミナルへタイムスタンプ付きログを出力する補助関数
    fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        systemLogs.add("[$time] $msg")
        if (systemLogs.size > 6) systemLogs.removeAt(0) // ログが溢れないよう最新6件のみ保持
    }

    // 🚀 画面初回表示時の処理：パーミッション未許可ならリクエスト発行
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
        addLog("SYSTEM_INIT: MULTI_USER_MODE")
    }

    if (hasCameraPermission) {
        // 📊 状態変数の定義
        var detectionData by remember { mutableStateOf(DetectionData()) }
        var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
        var showNameDialog by remember { mutableStateOf(false) }
        var currentStep by remember { mutableStateOf(AuthStep.WAITING) }
        var isProcessing by remember { mutableStateOf(false) }
        var step1StartTime by remember { mutableLongStateOf(0L) }

        // 🔍 解析データのショートカット参照
        val currentFace = detectionData.faces.firstOrNull()
        val smileProb = currentFace?.smilingProbability ?: 0f
        val headAngleY = currentFace?.headEulerAngleY ?: 0f
        val leftEye = currentFace?.leftEyeOpenProbability ?: 0f
        val rightEye = currentFace?.rightEyeOpenProbability ?: 0f
        val hasFace = detectionData.faces.isNotEmpty()

        // 🧠 【多段階生体検知（Liveness Detection）リアクティブエンジン】
        // 顔の動きや表情の変化を監視し、リアルタイムにステップを進行させます。
        LaunchedEffect(hasFace, currentStep, headAngleY, smileProb) {
            // 処理中またはユーザー未登録状態の場合は判定スキップ
            if (isProcessing || userList.isEmpty()) return@LaunchedEffect

            // 顔が画面から外れたら待機状態へリセット
            if (!hasFace) {
                if (currentStep != AuthStep.WAITING && currentStep != AuthStep.GRANTED) {
                    currentStep = AuthStep.WAITING
                    step1StartTime = 0L
                }
                return@LaunchedEffect
            }

            // 🔄 ステップごとの条件判定（ステートマシン）
            when (currentStep) {
                AuthStep.WAITING -> {
                    // 顔を検出したら Step 1 (首振りチェック) へ遷移し、タイマー開始
                    currentStep = AuthStep.CHECK_TURN
                    step1StartTime = System.currentTimeMillis()
                    SoundManager.play(SoundType.BEEP)
                    addLog("AUTH_START: CHECKING_LIVENESS")
                }
                AuthStep.CHECK_TURN -> {
                    // 🚨 セキュリティチェック：5秒以上動きが無い（写真など）場合はタイムアウト発動
                    if (System.currentTimeMillis() - step1StartTime > 5000L) {
                        currentStep = AuthStep.TIMEOUT
                        SoundManager.play(SoundType.ERROR)
                        safeVibrate(context, 500)
                        addLog("ALERT: SPOOFING_DETECTED")
                        scope.launch { delay(3000); currentStep = AuthStep.WAITING }
                        return@LaunchedEffect
                    }

                    // 12度以上 首を左右どちらかに傾けたら Step 1 クリア
                    if (abs(headAngleY) >= 12f) {
                        SoundManager.play(SoundType.STEP_PASS)
                        safeVibrate(context, 50)
                        addLog("LIVENESS: YAW_PASS")
                        currentStep = AuthStep.CHECK_FRONT
                    }
                }
                AuthStep.CHECK_FRONT -> {
                    // 正面（傾き8度以内）に戻ったら Step 2 クリア
                    if (abs(headAngleY) <= 8f) {
                        SoundManager.play(SoundType.STEP_PASS)
                        safeVibrate(context, 50)
                        addLog("LIVENESS: FRONT_PASS")
                        currentStep = AuthStep.CHECK_SMILE
                    }
                }
                AuthStep.CHECK_SMILE -> {
                    // 笑顔度 50% 以上で最終クリア！
                    if (smileProb >= 0.5f) {
                        isProcessing = true
                        addLog("LIVENESS: SMILE_PASS")
                        scope.launch {
                            SoundManager.play(SoundType.SCANNING)
                            delay(500)
                            currentStep = AuthStep.GRANTED // 認証成功
                            SoundManager.play(SoundType.SUCCESS)
                            safeVibrate(context, 200)
                            addLog("ACCESS_GRANTED")
                            isProcessing = false
                        }
                    }
                }
                else -> {}
            }
        }

        // 🎨 画面全体のレイアウト組み立て
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. カメラプレビュー（最下層）
            CameraPreview(
                onFacesDetected = { data -> detectionData = data },
                onPreviewViewCreated = { view -> previewViewRef = view }
            )

            // 2. ターゲット枠・スキャン線（分離した FaceOverlay.kt を使用）
            FaceOverlay(detectionData = detectionData, currentStep = currentStep, hasUsers = userList.isNotEmpty())

            // 3. リアルタイムログ（分離した LogTerminal.kt を使用）
            LogTerminal(logs = systemLogs)

            // 4. 生体HUDメーター（分離した BiometricHUD.kt を使用）
            if (userList.isNotEmpty() && hasFace) {
                BiometricHUD(smileProb, headAngleY, leftEye, rightEye)
            }

            // 5. ステータスヘッダー
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TopStatusHeader(
                    currentStep = currentStep,
                    hasFace = hasFace,
                    userCount = userList.size,
                    registeredUser = userList.lastOrNull()
                )
            }

            // 6. アクションバー（戻るボタン）
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.7f))
                ) { Text("◀ 戻る", color = Color.White) }
            }

            // 7. 新規ユーザー追加ボタン
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 40.dp, start = 20.dp, end = 20.dp)) {
                Button(
                    onClick = { showNameDialog = true },
                    enabled = hasFace,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(if (hasFace) "👤 新しいユーザーを追加登録" else "カメラに顔を映してください", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 8. 名前入力ダイアログ
            if (showNameDialog) {
                var inputText by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showNameDialog = false },
                    title = { Text("ユーザー登録") },
                    text = {
                        Column {
                            Text("登録名を入力してください")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = inputText, onValueChange = { inputText = it }, singleLine = true)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val newUser = LocalDataManager.addUser(context, inputText, previewViewRef?.bitmap)
                                    userList = LocalDataManager.getUsers(context)
                                    showNameDialog = false
                                    currentStep = AuthStep.WAITING
                                    SoundManager.play(SoundType.REGISTERED)
                                    addLog("REGISTERED: ${newUser.name}")
                                }
                            }
                        ) { Text("登録完了") }
                    }
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("カメラ許可が必要です") }
    }
}

/**
 * 🏷️ 【ヘッダー表示用コンポーネント】
 * 現在の生体認証ステップや、最新の登録ユーザー情報を上部に表示します。
 */
@Composable
fun TopStatusHeader(currentStep: AuthStep, hasFace: Boolean, userCount: Int, registeredUser: UserData?) {
    val (statusText, statusColor) = if (userCount == 0) {
        if (hasFace) "🔵 顔検出！下のボタンで登録してください" to Color(0xFF00CCFF)
        else "🔴 顔を映してユーザー登録してください" to Color(0xFFFF3366)
    } else {
        when (currentStep) {
            AuthStep.WAITING -> "🔴 対象を検索中..." to Color(0xFFFF3366)
            AuthStep.CHECK_TURN -> "🔄 Step 1: 左右どちらかに首を傾ける" to Color(0xFF00CCFF)
            AuthStep.CHECK_FRONT -> "🔽 Step 2: 正面を向く" to Color(0xFFB388FF)
            AuthStep.CHECK_SMILE -> "😊 Step 3: ニッコリ笑顔を見せる" to Color(0xFFFFCC00)
            AuthStep.GRANTED -> "❇️ ACCESS GRANTED [ 本人確認完了 ]" to Color(0xFF00FF66)
            AuthStep.TIMEOUT -> "🚨 警告: 画面の固定(なりすまし)を検知" to Color.Red
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .border(1.dp, statusColor, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (userCount > 0 && registeredUser != null) {
            val bitmap = remember(registeredUser.imagePath) { LocalDataManager.loadBitmap(registeredUser.imagePath) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(CircleShape).border(1.dp, Color(0xFF00FFCC), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("最新登録者: ${registeredUser.name} (計${userCount}名)", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(text = statusText, color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 📷 【CameraX プレビューコンポーネント】
 * Androidの標準CameraViewをJetpack Compose内で動かすためのBridge（橋渡し）コンポーネントです。
 */
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

/**
 * 🔬 【ML Kit 解析アナライザー】
 * カメラから届くフレーム画像（ImageProxy）を毎秒数10回 ML Kit へ流し込んで顔を解析する裏方クラスです。
 */
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
                    onFacesDetected(DetectionData(faces, mediaImage.width, mediaImage.height, rotationDegrees))
                }
                .addOnCompleteListener { imageProxy.close() } // 必ずcloseを呼んで次のフレームを受信できるようにする
        } else { imageProxy.close() }
    }
}