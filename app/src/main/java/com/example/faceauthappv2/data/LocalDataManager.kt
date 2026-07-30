package com.example.faceauthappv2.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.faceauthappv2.model.UserData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/*
================================================================================
【ファイル概要：LocalDataManager.kt】
このファイルは、アプリ内の「データ永続化（ローカルデータベース）」を担うファイルです。

【主な役割と仕組み】
1. ユーザー情報（ID、名前、画像パス）をJSON形式の文字列に変換し、SharedPreferencesに保存
2. カメラで撮影した顔写真（Bitmap）を端末の内部ストレージ（Context.filesDir）にPNG画像として保存
3. 保存されたデータの一覧取得、新規ユーザー追加、指定ユーザーのデータ＆画像ファイルの削除
================================================================================
*/

/**
 * 💡 `object` キーワード：
 * Kotlinにおける「シングルトン（Singleton）」パターン。
 * クラスのインスタンスを毎回 `val manager = LocalDataManager()` のように作る必要がなく、
 * アプリ全体で常に1つだけ存在するオブジェクトとして `LocalDataManager.getUsers(...)` のように直接呼び出せます。
 */
object LocalDataManager {

    // 🔒 SharedPreferencesのファイル名を定義（他アプリと被らない固有の名前）
    private const val PREF_NAME = "FaceAuthMultiPrefs"

    // 🔒 SharedPreferences内でユーザー一覧（JSON文字列）を保存するためのキー名
    private const val KEY_USERS = "user_list_json"

    /**
     * 📥 【ユーザー一覧取得関数】
     * 端末内に保存されている全ユーザー情報を取得し、UserDataのリストとして返します。
     *
     * @param context アプリのコンテキスト（ストレージアクセス権限を取得するために必要）
     * @return 読み込んだユーザーのリスト (`List<UserData>`)
     */
    fun getUsers(context: Context): List<UserData> {
        // SharedPreferencesインスタンスを取得（MODE_PRIVATE: このアプリのみアクセス許可）
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // 保存されているJSON文字列を取り出す。データが存在しない場合は null が返るため `?:` で空リストを返却
        val jsonString = prefs.getString(KEY_USERS, null) ?: return emptyList()

        val userList = mutableListOf<UserData>()

        try {
            // 取り出したJSON文字列をJSON配列（JSONArray）として解析
            val jsonArray = JSONArray(jsonString)

            // 配列の要素数（ユーザー数）分だけループ処理
            for (i in 0 until jsonArray.length()) {
                // i 番目のJSONObject（1人分のデータ）を取り出す
                val obj = jsonArray.getJSONObject(i)

                // JSONからキーを指定して値を取り出し、UserDataオブジェクトを組み立ててリストに追加
                userList.add(
                    UserData(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        imagePath = obj.getString("imagePath")
                    )
                )
            }
        } catch (e: Exception) {
            // パース（解析）エラー等が発生した場合はスタックトレースをログに出力してアプリのクラッシュを防ぐ
            e.printStackTrace()
        }

        return userList
    }

    /**
     * ➕ 【新規ユーザー追加関数】
     * 名前と顔写真を受け取り、画像ファイルを保存した上でユーザー一覧に追加登録します。
     *
     * @param context アプリのコンテキスト
     * @param name 入力されたユーザー名
     * @param bitmap 撮影された顔写真のBitmap（null可）
     * @return 新しく作成された UserData オブジェクト
     */
    fun addUser(context: Context, name: String, bitmap: Bitmap?): UserData {
        // 🆔 重複しない完全ユニークなIDをUUIDで自動生成（例: "550e8400-e29b-41d4-a716-446655440000"）
        val userId = UUID.randomUUID().toString()
        var imagePath = ""

        // 🖼️ 顔写真が存在する場合、内部ストレージへファイル保存
        bitmap?.let {
            // 保存先ファイルパスを生成（/data/user/0/com.example.faceauthappv2/files/face_UUID.png）
            val file = File(context.filesDir, "face_$userId.png")

            /**
             * 💡 `.use { ... }` 構文：
             * ファイル出力ストリームの処理が終わった後、自動的に `.close()` を呼んでくれる安心設計。
             * これによりメモリリークやファイルロックの発生を防ぎます。
             */
            FileOutputStream(file).use { out ->
                // BitmapをPNG形式、最高画質（100%）でファイルに出力（書き込み）
                it.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // 保存したファイルの絶対パスを保持
            imagePath = file.absolutePath
        }

        // 現在保存されているユーザーリストを編集可能なMutableListとして読み込み
        val currentUsers = getUsers(context).toMutableList()

        // 新しいユーザーオブジェクトを作成
        val newUser = UserData(id = userId, name = name, imagePath = imagePath)

        // リストに追加
        currentUsers.add(newUser)

        // 更新されたリストをSharedPreferencesへ上書き保存
        saveUserList(context, currentUsers)

        return newUser
    }

    /**
     * 🗑️ 【ユーザー削除関数】
     * 指定されたユーザーIDのデータおよび保存されていた画像ファイルを削除します。
     *
     * @param context アプリのコンテキスト
     * @param userId 削除対象のユーザーID
     */
    fun deleteUser(context: Context, userId: String) {
        val currentUsers = getUsers(context).toMutableList()

        // IDが一致するユーザーを検索
        val userToDelete = currentUsers.find { it.id == userId }

        userToDelete?.let {
            // 📁 該当ユーザーの顔写真ファイルが存在すれば、端末ストレージから物理削除
            if (it.imagePath.isNotEmpty()) {
                File(it.imagePath).delete()
            }

            // メモリ上のリストからユーザーを削除
            currentUsers.remove(it)

            // 変更後のリストを保存
            saveUserList(context, currentUsers)
        }
    }

    /**
     * 💾 【内部補助関数：ユーザーリストのJSON書き込み】
     * UserDataのリストをJSON文字列に変換し、SharedPreferencesに保存します。
     *
     * @param context アプリのコンテキスト
     * @param users 保存したい全ユーザーのリスト
     */
    private fun saveUserList(context: Context, users: List<UserData>) {
        val jsonArray = JSONArray()

        // リスト内の全ユーザーをJSONObjectへ変換して配列に追加
        users.forEach { user ->
            val obj = JSONObject().apply {
                put("id", user.id)
                put("name", user.name)
                put("imagePath", user.imagePath)
            }
            jsonArray.put(obj)
        }

        // SharedPreferencesへの保存処理実行
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USERS, jsonArray.toString()) // JSON配列を1本の文字列にして保存
            .apply() // 💡 `.commit()`（同期処理）ではなく `.apply()`（非同期処理）を使うことでUIを固めずにバックグラウンドで保存する
    }

    /**
     * 🖼️ 【ユーティリティ関数：ファイルパスからの画像読み込み】
     * 保存されている画像ファイルの絶対パスから、画面表示用のBitmapオブジェクトを生成します。
     *
     * @param path 画像ファイルの絶対パス
     * @return 復元されたBitmap（ファイルが存在しない場合はnull）
     */
    fun loadBitmap(path: String): Bitmap? {
        val file = File(path)
        // ファイルが存在すればBitmapFactoryで復元、無ければnullを返す
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }
}