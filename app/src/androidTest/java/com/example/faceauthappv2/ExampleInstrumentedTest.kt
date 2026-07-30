package com.example.faceauthappv2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/*
================================================================================
【ファイル概要：ExampleInstrumentedTest.kt】
このファイルは、実機またはエミュレータ上で実行される「計装テスト（Instrumented Test）」です。

【主な役割と特徴】
1. アプリが実際のAndroid端末（OS環境）上で正常に動作するかを自動検証するテストコード
2. 端末のコンテキスト（Context）やリソース、データベース等にアクセスできる本番同等の環境で実行
3. プロジェクト作成時にAndroid Studioによって標準で自動生成されるテストサンプル
================================================================================
*/

/**
 * 🧪 【計装テスト実行クラス】
 *
 * 💡 `@RunWith(AndroidJUnit4::class)` アノテーション：
 * このテストクラスをJUnit 4フレームワークではなく、Android特有の実機環境に対応した
 * 「AndroidJUnit4」テストランナー上で動かすことを指定します。
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    /**
     * ✅ 【アプリコンテキスト検証テストメソッド】
     *
     * 💡 `@Test` アノテーション：
     * この関数がテストケースの1つであることを示します。
     * Android Studioの緑色の再生ボタン（▶）を押すと、この関数内の検証コードが自動実行されます。
     */
    @Test
    fun useAppContext() {
        // 📱 実行中の端末から、テスト対象アプリのコンテキスト（Context）を取得
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // 🔍 アプリのパッケージ名が想定通り "com.example.faceauthappv2" になっているかを検証（アサート）
        // 第一引数：期待される正しい値（Expected）
        // 第二引数：実際のアプリから取得した値（Actual）
        // 両者が一致していればテスト成功（PASS）、異なればテスト失敗（FAIL）となります。
        assertEquals("com.example.faceauthappv2", appContext.packageName)
    }
}