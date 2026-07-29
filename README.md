# 🛡️ Cyber Face Auth (サイバー顔認証＆多段階生体検知システム)

Android (Kotlin / Jetpack Compose) と Google ML Kit を活用した、近未来風UIの多段階生体検知（Liveness Detection）＆ユーザー管理アプリケーションです。

単なる顔検出にとどまらず、写真等によるなりすまし（Spoofing）を防ぐインタラクティブな生体判定ロジック、リアルタイムシステムログ、グラフィカルHUD、サウンド＆バイブレーション演出、複数ユーザーのデータ永続化を備えています。

---

## 🌟 主な機能

* 🛡️ **多段階生体検知（Action-Based Liveness Detection）**
    * **Step 1:** 首の横振り検知 (`headEulerAngleY`)
    * **Step 2:** 正面復帰検知
    * **Step 3:** 笑顔度判定 (`smilingProbability`)
    * 3段階のアクションを求めることで、平面写真やディスプレイを用いた「なりすまし」を強力に防止します。
* 🚨 **なりすまし防止タイムアウト機能**
    * 顔検出後、一定時間（5秒間）動かない（写真や静止画）場合にセキュリティアラートを発動し、一時ロックします。
* 💻 **リアルタイム・サイバーUI / HUD**
    * **ターミナルログ:** 処理状況やセンサー検知ログをハッカー風ターミナルにリアルタイム出力。
    * **生体HUD:** 笑顔度、首角度、左右の目の開き具合（`EyeOpenProbability`）をグラフィカルなプログレスバーで可視化。
    * **ターゲットオーバレイ:** カッコいい四隅ブラケット、走査レーザー線、顔の特徴点（Landmarks）を描画。
* 🔊 **サウンド ＆ バイブレーション演出**
    * `ToneGenerator` を用いたプログラム生成による近未来風アルペジオメロディ（ステップ通過音・登録音・認証成功音・エラー音）。
* 💾 **複数ユーザーのローカルデータ永続化**
    * ユーザー名と撮影した顔サムネイル画像を内部ストレージに保存・管理。
    * アプリ再起動後も登録データを維持し、一覧画面からの確認・削除が可能。

---

## 🛠️ 使用技術 (Tech Stack)

| カテゴリ | 使用技術 |
| :--- | :--- |
| **言語** | Kotlin |
| **UI Framework** | Jetpack Compose (Material3) |
| **カメラ** | CameraX (ImageAnalysis / Preview) |
| **画像解析 (AI)** | Google ML Kit Face Detection |
| **アーキテクチャ** | Clean Architecture 基準のディレクトリ分離 (`ui`, `data`, `model`, `utils`) |
| **データ保存** | SharedPreferences / Internal File Storage |

---

## 📁 プロジェクト構造

```text
com.example.faceauthappv2/
├── model/
│   └── UserData.kt             # ユーザー情報・検出データ・認証ステップのモデル定義
├── data/
│   └── LocalDataManager.kt     # 複数ユーザーデータの永続化・画像のローカル保存管理
├── utils/
│   ├── SoundManager.kt         # プログラム生成サウンド管理（シングルトン構造）
│   └── VibratorUtil.kt         # 安全なバイブレーション制御
├── ui/
│   ├── screens/
│   │   ├── HomeScreen.kt       # メインメニュー/ナビゲーション画面
│   │   ├── UserListScreen.kt   # 登録ユーザー一覧・削除画面
│   │   └── FaceAuthScreen.kt   # 生体検知サイバーカメラ画面
│   └── components/
│       ├── FaceOverlay.kt      # レーザー＆ターゲット枠キャンバス描画
│       ├── BiometricHUD.kt     # 生体パラメータ（目・笑顔・角度）メーター
│       └── LogTerminal.kt      # リアルタイムログ端末コンポーネント
└── MainActivity.kt             # アプリケーションエントリポイント＆ルーティング

🚀 動作環境・動作手順
動作要件
Android Studio: Ladybug (2024.2.1) 以降推奨

対応OS: Android 8.0 (API Level 26) 以上

推奨環境: インカメラ搭載の物理Android端末（CameraX & ML Kit 動作確認用）