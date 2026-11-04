package com.smartreplyai.app

import android.app.Activity
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.*
import android.graphics.Typeface
import android.view.ViewGroup
import android.text.InputType
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import kotlin.concurrent.thread
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var postUrl: EditText
    private lateinit var comment: EditText
    private lateinit var endpoint: EditText
    private lateinit var draft: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 40, 36, 28)
            setBackgroundColor(0xFFF6F8FB.toInt())
        }
        fun label(s: String) = TextView(this).apply {
            text = s; textSize = 15f; setTextColor(0xFF1B2B40.toInt())
            setTypeface(null, Typeface.BOLD); setPadding(0, 18, 0, 8)
        }
        fun field(hintText: String, multi: Boolean = false): EditText =
            EditText(this).apply {
                hint = hintText
                textSize = 15f
                setPadding(18, 14, 18, 14)
                setBackgroundColor(0xFFFFFFFF.toInt())
                if (multi) {
                    minLines = 3
                    gravity = android.view.Gravity.TOP
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                }
            }
        fun button(textValue: String, action: () -> Unit) =
            Button(this).apply { text = textValue; setOnClickListener { action() } }

        root.addView(TextView(this).apply {
            text = "Smart Reply AI"
            textSize = 26f; setTextColor(0xFF12365A.toInt())
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "مساعد ذكي لإعداد ردود تعليقات فيسبوك"
            textSize = 14f; setTextColor(0xFF536579.toInt())
        })
        root.addView(label("رابط المنشور المختار"))
        postUrl = field("الصق رابط منشور Facebook")
        root.addView(postUrl, match())
        root.addView(label("التعليق"))
        comment = field("الصق نص التعليق هنا", true)
        root.addView(comment, match())
        root.addView(label("عنوان خادم الذكاء الاصطناعي"))
        endpoint = field("https://your-server.example/reply")
        root.addView(endpoint, match())
        root.addView(TextView(this).apply {
            text = "لأمان حسابك، لا تضع مفتاح OpenAI داخل التطبيق. اربط التطبيق بخادمك الآمن."
            textSize = 12f; setTextColor(0xFF8A4B08.toInt()); setPadding(0, 10, 0, 10)
        })
        root.addView(button("إنشاء رد ذكي") { generateReply() }, match())
        root.addView(label("الرد المقترح"))
        draft = TextView(this).apply {
            text = "سيظهر الرد هنا"
            textSize = 16f; setTextColor(0xFF172B40.toInt())
            setPadding(18, 18, 18, 18); setBackgroundColor(0xFFFFFFFF.toInt())
        }
        root.addView(draft, match())
        root.addView(button("نسخ الرد") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Facebook reply", draft.text))
            Toast.makeText(this, "تم نسخ الرد", Toast.LENGTH_SHORT).show()
        }, match())
        status = TextView(this).apply {
            text = "جاهز — النشر يتم يدويًا من تطبيق Facebook"
            textSize = 12f; setTextColor(0xFF536579.toInt()); setPadding(0, 14, 0, 0)
        }
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun match(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 8
        }

    private fun generateReply() {
        val url = endpoint.text.toString().trim()
        val commentText = comment.text.toString().trim()
        if (commentText.isEmpty()) {
            comment.error = "أدخل التعليق أولاً"; return
        }
        if (!url.startsWith("https://")) {
            endpoint.error = "أدخل عنوان HTTPS لخادمك"; return
        }
        status.text = "جارٍ طلب الرد..."
        thread {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val payload = JSONObject().put("comment", commentText)
                    .put("post_url", postUrl.text.toString().trim())
                    .put("language_style", "اختَر تلقائيًا بين العامية المصرية والعربية الرسمية")
                    .put("instruction", "اكتب ردًا مهذبًا ومختصرًا وطبيعيًا. لا تخترع أسعارًا أو وعودًا.")
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
                val code = conn.responseCode
                val response = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = response.bufferedReader().use { it.readText() }
                conn.disconnect()
                if (code !in 200..299) throw Exception("HTTP $code: $body")
                val result = JSONObject(body).optString("reply", "")
                if (result.isBlank()) throw Exception("لم يرجع الخادم حقل reply")
                runOnUiThread {
                    draft.text = result
                    status.text = "تم إنشاء الرد. راجعه ثم انسخه وانشره بنفسك."
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "تعذر إنشاء الرد: ${e.message}" }
            }
        }
    }
}
