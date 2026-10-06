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
 * 一次识别的完整返回：日期字段，外加模型给出的思维链（模型没有思考过程时为空串）
 */
data class AiAnalysis(val result: ScanResult, val reasoning: String)

/**
 * 调用 OpenAI 兼容的 /chat/completions 接口，把一帧 JPEG 交给带识图能力的模型并解析出日期字段
 */
class AiVisionClient {

    suspend fun analyze(settings: AiSettings, jpeg: ByteArray): AiAnalysis = withContext(Dispatchers.IO) {
        parseAnalysis(requestCompletion(settings, jpeg))
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
            connection.outputStream.use { it.write(buildPayload(settings, jpeg).toByteArray(Charsets.UTF_8)) }
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

    /** 提为 internal 是为了能在仪器化测试里直接断言请求体内容，不必真发一次请求 */
    internal fun buildPayload(settings: AiSettings, jpeg: ByteArray): String {
        val payload = JSONObject()
            .put("model", settings.modelName)
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
        applyExtraRequestParams(payload, settings.extraRequestJson)
        return payload.toString()
    }

    /**
     * 把用户自定义的附加参数并进请求体。
     *
     * 思考开关各家字段名与取值都不一样（OpenAI 系是 reasoning_effort，DeepSeek / 智谱 / 火山是 thinking 对象，
     * 通义是 enable_thinking），服务商自己也不提供"支持哪些参数"的元数据，所以这里不替用户猜，
     * 只把他写的原样带上；写坏了（不是合法 JSON）等同于没填。
     */
    private fun applyExtraRequestParams(payload: JSONObject, extraJson: String) {
        val extra = runCatching { JSONObject(extraJson) }.getOrNull() ?: return
        extra.keys().forEach { key ->
            if (key !in RESERVED_KEYS) payload.put(key, extra.get(key))
        }
    }

    private fun parseAnalysis(responseBody: String): AiAnalysis {
        val message = JSONObject(responseBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
        val content = message.optString("content")
        Log.d(TAG, "模型返回内容：$content")

        val json = extractJsonObject(content)
            ?: throw IllegalStateException("模型未返回 JSON：${content.take(ERROR_BODY_LIMIT)}")

        return AiAnalysis(
            // 模型每帧独立作答，同一项的写法规整一遍，免得换了一帧就变成另一种写法
            result = ScanResult(
                productName = json.optString("productName"),
                productionDate = ScanValueFormat.date(json.optString("productionDate")),
                expiryDate = ScanValueFormat.date(json.optString("expiryDate")),
                shelfLife = ScanValueFormat.shelfLife(json.optString("shelfLife"))
            ),
            reasoning = REASONING_KEYS
                .firstNotNullOfOrNull { key -> message.optString(key).trim().takeIf { it.isNotEmpty() } }
                .orEmpty()
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

        // model 与 messages 由应用组装，用户自定义参数里出现这两个键时忽略，避免请求结构被改坏
        val RESERVED_KEYS = setOf("model", "messages")

        // 思维链的键名各家不统一：DeepSeek / 智谱 / 通义 / 火山用 reasoning_content，OpenRouter 系用 reasoning
        val REASONING_KEYS = listOf("reasoning_content", "reasoning")
        val PROMPT = """
            你是食品标签识别助手。请从这张照片中读取以下四项，只输出 JSON，不要输出解释，也不要使用代码标记：
            {"productName":"","productionDate":"","expiryDate":"","shelfLife":""}

            规则：
            - productName：这是什么食品，用最简的通用品名，只写品类本身
            - productionDate：标签上印刷的生产日期，写成 yyyy-MM-dd
            - expiryDate：标签上印刷的过期日期或保质期截止日期，写成 yyyy-MM-dd
            - shelfLife：标签上印刷的保质期，一律用阿拉伯数字写成「数字+单位」，标签上写的是中文数字也要换算成阿拉伯数字
            - 只填写这张照片里确实看得清的字段；看不清或没拍到的一律留空。
            - 留空表示「这张照片里没看到这一项」，不会清除此前已识别到的内容，所以不要为了补全而推测或编造。
            - 不要做任何日期推算或计算。
        """.trimIndent()
    }
}
