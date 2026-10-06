package com.freshnow.app.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 调用 OpenAI 兼容的 /chat/completions 接口，把一帧 JPEG 交给带识图能力的模型并解析出日期字段
 */
class AiVisionClient {

    suspend fun analyze(settings: AiSettings, jpeg: ByteArray): ScanResult = withContext(Dispatchers.IO) {
        parseResult(requestCompletion(settings, jpeg))
    }

    private fun requestCompletion(settings: AiSettings, jpeg: ByteArray): String {
        val connection = (URL(settings.baseUrl.trimEnd('/') + CHAT_COMPLETIONS_PATH).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        }

        return try {
            connection.outputStream.use { it.write(buildPayload(settings.modelName, jpeg).toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code ${body.take(ERROR_BODY_LIMIT)}")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPayload(model: String, jpeg: ByteArray): String = JSONObject()
        .put("model", model)
        .put(
            "messages",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        JSONArray()
                            .put(JSONObject().put("type", "text").put("text", PROMPT))
                            .put(
                                JSONObject()
                                    .put("type", "image_url")
                                    .put(
                                        "image_url",
                                        JSONObject().put(
                                            "url",
                                            "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
                                        )
                                    )
                            )
                    )
            )
        )
        .toString()

    private fun parseResult(responseBody: String): ScanResult {
        val content = JSONObject(responseBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content")
        Log.d(TAG, "模型返回内容：$content")

        val json = extractJsonObject(content)
            ?: throw IllegalStateException("模型未返回 JSON：${content.take(ERROR_BODY_LIMIT)}")

        return ScanResult(
            productionDate = json.optString("productionDate"),
            expiryDate = json.optString("expiryDate"),
            shelfLife = json.optString("shelfLife")
        )
    }

    /**
     * 模型常把 JSON 包在 ``` 代码块或多余文字里，这里取首尾花括号之间的内容
     */
    private fun extractJsonObject(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    private companion object {
        const val TAG = "AiVisionClient"
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val ERROR_BODY_LIMIT = 300
        val PROMPT = """
            你是食品标签识别助手。请从图片中识别生产日期、过期日期（或保质期截止日期）和保质期。
            只输出 JSON，不要输出解释，也不要使用代码块标记，格式为：
            {"productionDate":"","expiryDate":"","shelfLife":""}
            识别不到的字段填空字符串。
        """.trimIndent()
    }
}
