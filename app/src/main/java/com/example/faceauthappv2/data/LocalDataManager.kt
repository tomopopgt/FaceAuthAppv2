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

object LocalDataManager {
    private const val PREF_NAME = "FaceAuthMultiPrefs"
    private const val KEY_USERS = "user_list_json"

    // ユーザー一覧の取得
    fun getUsers(context: Context): List<UserData> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_USERS, null) ?: return emptyList()
        val userList = mutableListOf<UserData>()

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                userList.add(
                    UserData(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        imagePath = obj.getString("imagePath")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return userList
    }

    // 新規ユーザー追加
    fun addUser(context: Context, name: String, bitmap: Bitmap?): UserData {
        val userId = UUID.randomUUID().toString()
        var imagePath = ""

        // 画像の保存
        bitmap?.let {
            val file = File(context.filesDir, "face_$userId.png")
            FileOutputStream(file).use { out -> it.compress(Bitmap.CompressFormat.PNG, 100, out) }
            imagePath = file.absolutePath
        }

        val currentUsers = getUsers(context).toMutableList()
        val newUser = UserData(id = userId, name = name, imagePath = imagePath)
        currentUsers.add(newUser)

        saveUserList(context, currentUsers)
        return newUser
    }

    // ユーザー削除
    fun deleteUser(context: Context, userId: String) {
        val currentUsers = getUsers(context).toMutableList()
        val userToDelete = currentUsers.find { it.id == userId }

        userToDelete?.let {
            if (it.imagePath.isNotEmpty()) {
                File(it.imagePath).delete()
            }
            currentUsers.remove(it)
            saveUserList(context, currentUsers)
        }
    }

    private fun saveUserList(context: Context, users: List<UserData>) {
        val jsonArray = JSONArray()
        users.forEach { user ->
            val obj = JSONObject().apply {
                put("id", user.id)
                put("name", user.name)
                put("imagePath", user.imagePath)
            }
            jsonArray.put(obj)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USERS, jsonArray.toString())
            .apply()
    }

    // パスからBitmap読み込み
    fun loadBitmap(path: String): Bitmap? {
        val file = File(path)
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }
}