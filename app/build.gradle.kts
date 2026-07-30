/*
================================================================================
【ファイル概要：app/build.gradle.kts】
このファイルは、アプリ（appモジュール）のビルド設定・依存ライブラリを一元管理するファイルです。

【主な役割】
1. アプリの固有ID（applicationId）やバージョン情報、対応OSバージョン（minSdk / targetSdk）の指定
2. Jetpack Compose のビルド機能の有効化
3. 外部ライブラリ（CameraX: カメラ機能、ML Kit: 顔検出AI等）の導入とバージョン管理
================================================================================
*/

// 🔌 【使用するプラグインの設定】
plugins {
    // Android アプリケーションとしてビルドするための標準プラグイン
    alias(libs.plugins.android.application)
    // Jetpack Compose コンパイラを有効化する Kotlin プラグイン
    alias(libs.plugins.kotlin.compose)
}

// 📱 【Android OS向けビルド基本設定】
android {
    // 📦 リソースクラス（R.java等）が生成されるパッケージ名
    namespace = "com.example.faceauthappv2"

    // 🛠️ コンパイル（ビルド）時に使用する Android SDK バージョン
    compileSdk = 37

    // ⚙️ 【全ビルドタイプ共通のデフォルト設定】
    defaultConfig {
        // 🆔 Google Play Store や OS 上でアプリを一意に識別するID
        applicationId = "com.example.faceauthappv2"

        // 📱 アプリが動作する最小OSバージョン（API 26 = Android 8.0 Oreo 以上が必要）
        minSdk = 26

        // 🎯 アプリの動作確認・最適化を行っているターゲットOSバージョン
        targetSdk = 36

        // 🔢 アプリの内部バージョンコード（Play Store 更新時にカウントアップする数値）
        versionCode = 1

        // 🏷️ ユーザーに表示されるアプリのバージョン名
        versionName = "1.0"

        // 🧪 計装テスト（UIテストなど）を実行するための標準テストランナー
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 🏗️ 【ビルドバリエーション（リリース / デバッグ）設定】
    buildTypes {
        release {
            // リリース用ビルドでのコード最適化・難読化設定
            optimization {
                enable = false
            }
        }
    }

    // ☕ 【Java コンパイラ互換性設定】
    compileOptions {
        // Java 11 互換のバイトコードを出力
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // 🎨 【ビルド機能フラグ設定】
    buildFeatures {
        // Jetpack Compose によるUIビルドを有効化
        compose = true
    }
}

// 📦 【依存ライブラリ（外部ライブラリ）の追加定義】
dependencies {
    // 🧱 Jetpack Compose バージョン一括管理（BOM: Bill of Materials）
    implementation(platform(libs.androidx.compose.bom))

    // 🎨 Jetpack Compose 基本UI・Material 3 コンポーネント群
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // 🛠️ AndroidX コア拡張機能およびライフサイクル（ViewModel等）管理
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // 🧪 ユニットテスト（単体テスト）用ライブラリ
    testImplementation(libs.junit)

    // 📱 実機・エミュレータ用UIテストライブラリ
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // 🐛 デバッグ専用ツール（Preview表示・レイアウトインスペクター等）
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 📷 【CameraX ライブラリ群：カメラ映像の取得・ライフサイクル連動】
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")       // カメラ制御のコア機能
    implementation("androidx.camera:camera-camera2:$cameraxVersion")    // Camera2 API互換エンジン
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")  // Compose/Activityの画面生存期間と連携
    implementation("androidx.camera:camera-view:$cameraxVersion")       // PreviewView等のUIコンポーネント

    // 🤖 【ML Kit Face Detection：Google製オンデバイス顔検出AI】
    // リアルタイムで顔の位置、目・口の開閉、輪郭などを端末内だけで高速検出
    implementation("com.google.mlkit:face-detection:16.1.6")
}