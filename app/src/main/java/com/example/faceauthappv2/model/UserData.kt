package com.example.faceauthappv2.model

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face

/*
================================================================================
【ファイル概要：UserData.kt】
このファイルは、アプリ全体で受け渡しを行う「データ構造」を一元管理するモデルクラス群です。

【定義されている3つの主要データ構造】
1. UserData       : 登録されたユーザーの基本情報（ID・名前・画像パス）
2. DetectionData  : ML Kitがリアルタイム検出した顔データとカメラフレーム情報
3. AuthStep       : 多段階生体認証（Liveness Detection）の進行ステータス定義
================================================================================
*/

/**
 * 👤 【登録ユーザーモデル】
 * 端末内に保存・一覧表示するためのユーザー1人分のデータを保持します。
 *
 * 💡 `data class` とは：
 * Kotlin特有のクラス構造で、自動的に equals()、hashCode()、toString()、copy() などの
 * 便利なメソッドが内部生成されます。データの保持に特化したクラスです。
 *
 * @property id ユーザーを唯一識別するためのUUID（ユニーク文字列）
 * @property name ダイアログ等で入力されたユーザー表示名
 * @property imagePath 内部ストレージに保存された顔写真ファイルの絶対パス
 */
data class UserData(
    val id: String,
    val name: String,
    val imagePath: String
)

/**
 * 📷 【カメラ検出データモデル】
 * ML KitのFaceAnalyzerが解析した最新のフレーム情報を、描画レイヤー（FaceOverlay等）へ引き渡すためのデータバケツです。
 *
 * @property faces ML Kitが検出した顔（`Face`）オブジェクトのリスト。顔がない場合は空リスト。
 * @property imageWidth カメラから送られてきた生の解析画像フレームの幅（ピクセル）
 * @property imageHeight カメラから送られてきた生の解析画像フレームの高さ（ピクセル）
 * @property rotationDegrees 端末の向きに応じた画像の回転角度（例: 縦持ちの場合 90 または 270）
 *
 * 💡 座標変換における重要性：
 * カメラの解像度（例: 1280x720）とスマホ画面の解像度（例: 2400x1080）は異なるため、
 * ターゲット枠やランドマークを正しく拡大縮小して描画する計算（スケーリング）でこれらの値を使用します。
 */
data class DetectionData(
    val faces: List<Face> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val rotationDegrees: Int = 0
)

/**
 * 🔄 【生体認証ステップ（状態遷移）列挙型】
 * 写真での「なりすまし」を防ぐための多段階アクションチェック（Liveness Detection）の進行状態を表します。
 *
 * 💡 `enum class` とは：
 * 決まった値のリスト（選択肢）を安全に扱うための型です。
 * 文字列（"CHECK_SMILE" など）で直接管理するのと違い、タイポ（打ち間違い）によるバグを防ぐことができます。
 */
enum class AuthStep {
    /** 🔴 待機状態：カメラに顔が映るのを待っている、または対象を検索中の状態 */
    WAITING,

    /** 🔄 Step 1：写真なりすまし防止のため、左右どちらかに首を振る動きをチェックしている状態 */
    CHECK_TURN,

    /** 🔽 Step 2：首を振り終わった後、再びカメラ正面を向く動きをチェックしている状態 */
    CHECK_FRONT,

    /** 😊 Step 3：仕上げとして、指定値以上の笑顔を見せる動きをチェックしている状態 */
    CHECK_SMILE,

    /** ❇️ 認証成功：すべての生体判定アクションをクリアし、本人確認が完了した状態 */
    GRANTED,

    /** 🚨 セキュリティ警告：顔検知後に一定時間（5秒間）動かない（写真や画面固定）ため一時ロックされた状態 */
    TIMEOUT
}