package com.smartreplyai.app

import android.app.Activity
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var postUrl: EditText
    private lateinit var commentsInput: EditText
    private lateinit var endpoint: EditText
    private lateinit var accessToken: EditText
    private lateinit var results: TextView
    private lateinit var status: TextView
    private lateinit var generateButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 40, 36, 32)
            setBackgroundColor(0xFFF6F8FB.toInt())
        }

        fun label(value: String) = TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(0xFF1B2B40.toInt())
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 18, 0, 8)
        }

        fun field(hintText: String, multi: Boolean = false, secret: Boolean = false): EditText =
            EditText(this).apply {
                hint = hintText
                textSize = 15f
                setPadding(18, 14, 18, 14)
                setBackgroundColor(0xFFFFFFFF.toInt())
                inputType = when {
                    secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    else -> InputType.TYPE_CLASS_TEXT
                }
                if (multi) {
                    minLines = 7
                    gravity = Gravity.TOP
                }
            }

        fun button(value: String, action: () -> Unit) =
            Button(this).apply { text = value; setOnClickListener { action() } }

        root.addView(TextView(this).apply {
            text = "Smart Reply AI"
            textSize = 27f
            setTextColor(0xFF12365A.toInt())
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "تحليل حتى 100 تعليق وتجهيز ردود مقترحة"
            textSize = 14f
            setTextColor(0xFF536579.toInt())
        })

        root.addView(label("رابط المنشور (اختياري)"))
        postUrl = field("الصق رابط منشور Facebook")
        root.addView(postUrl, match())

        root.addView(label("التعليقات — تعليق واحد في كل سطر"))
        commentsInput = field("مثال:\nالتعليق الأول هنا\nالتعليق الثاني هنا", multi = true)
        root.addView(commentsInput, match())

        root.addView(label("رابط خادم Smart Reply AI"))
        endpoint = field("https://your-server.example/api/replies")
        root.addView(endpoint, match())

        root.addView(label("رمز دخول الخادم"))
        accessToken = field("أدخل APP_ACCESS_TOKEN الذي أعددته على الخادم", secret = true)
        root.addView(accessToken, match())

        root.addView(TextView(this).apply {
            text = "مفتاح OpenAI يبقى على الخادم ولا يوضع داخل التطبيق. ألصق التعليقات التي تملك حق استخدامها فقط. التطبيق لا يسجل الدخول إلى Facebook ولا ينفذ Like أو نشرًا تلقائيًا."
            textSize = 12f
            setTextColor(0xFF8A4B08.toInt())
            setPadding(0, 12, 0, 12)
        })

        generateButton = button("حلّل التعليقات واكتب الردود") { generateBatch() }
        root.addView(generateButton, match())

        root.addView(label("النتائج"))
        results = TextView(this).apply {
            text = "ستظهر الردود المقترحة هنا"
            textSize = 15f
            setTextColor(0xFF172B40.toInt())
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        root.addView(results, match())

        root.addView(button("نسخ كل الردود") { copyResults() }, match())

        status = TextView(this).apply {
            text = "جاهز"
            textSize = 12f
            setTextColor(0xFF536579.toInt())
            setPadding(0, 14, 0, 0)
        }
        root.addView(status)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun match(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 8 }

    private fun generateBatch() {
        val endpointUrl = endpoint.text.toString().trim()
        val token = accessToken.text.toString().trim()
        val postUrlValue = postUrl.text.toString().trim()
        val comments = commentsInput.text.toString()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (comments.isEmpty()) {
            commentsInput.error = "أدخل تعليقًا واحدًا على الأقل"
            return
        }
        if (comments.size > 100) {
            commentsInput.error = "الحد الأقصى 100 تعليق في الدفعة الواحدة"
            return
        }
        if (!endpointUrl.startsWith("https://") || !endpointUrl.endsWith("/api/replies")) {
            endpoint.error = "استخدم رابط HTTPS ينتهي بـ /api/replies"
            return
        }
        if (token.isBlank()) {
            accessToken.error = "رمز دخول الخادم مطلوب"
            return
        }

        generateButton.isEnabled = false
        status.text = "جارٍ تحليل ${comments.size} تعليقًا..."
        results.text = "يتم تجهيز الردود..."

        thread {
            try {
                val input = JSONArray()
                comments.forEachIndexed { index, text ->
                    input.put(JSONObject().put("id", (index + 1).toString()).put("text", text))
                }
                val payload = JSONObject()
                    .put("post_url", postUrlValue)
                    .put("language_style", "auto")
                    .put("comments", input)

                val conn = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 90000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $token")
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()
                if (code !in 200..299) throw Exception("HTTP $code: $body")

                val items = JSONObject(body).optJSONArray("items")
                    ?: throw Exception("استجابة الخادم لا تحتوي على items")
                if (items.length() == 0) throw Exception("لم يرجع الخادم أي ردود")

                val formatted = StringBuilder()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val id = item.optString("id", (i + 1).toString())
                    val reply = item.optString("reply", "")
                    val like = item.optBoolean("like_recommended", false)
                    val reason = item.optString("reason", "")
                    formatted.append("تعليق #").append(id).append("\n")
                    formatted.append("الرد المقترح: ").append(reply).append("\n")
                    formatted.append("الإعجاب: ").append(if (like) "مقترح" else "غير مقترح")
                    if (reason.isNotBlank()) formatted.append(" — ").append(reason)
                    formatted.append("\n\n")
                }

                runOnUiThread {
                    results.text = formatted.toString().trim()
                    status.text = "تم تحليل ${items.length()} تعليقًا. راجع الردود قبل استخدامها."
                    generateButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "تعذر التحليل: ${e.message ?: "خطأ غير معروف"}"
                    results.text = "تحقق من رابط الخادم ورمز الدخول واتصال الإنترنت."
                    generateButton.isEnabled = true
                }
            }
        }
    }

    private fun copyResults() {
        val text = results.text.toString()
        if (text.isBlank() || text == "ستظهر الردود المقترحة هنا") {
            Toast.makeText(this, "لا توجد نتائج لنسخها", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Smart Reply AI results", text))
        Toast.makeText(this, "تم نسخ النتائج", Toast.LENGTH_SHORT).show()
    }
}
