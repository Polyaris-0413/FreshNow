package com.freshnow.app.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

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
        val response = httpRequest(
            url = settings.baseUrl.trimEnd('/') + CHAT_COMPLETIONS_PATH,
            method = "POST",
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS,
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${settings.apiKey}"
            ),
            body = buildPayload(settings, jpeg).toByteArray(Charsets.UTF_8)
        )
        if (response.code !in 200..299) {
            throw IllegalStateException("HTTP ${response.code} ${response.body.take(ERROR_BODY_LIMIT)}")
        }
        return response.body
    }

    /** 提为 internal 是为了能在仪器化测试里直接断言请求体内容，不必真发一次请求 */
    internal fun buildPayload(settings: AiSettings, jpeg: ByteArray): String {
        val payload = buildJsonObject {
            put("model", settings.modelName)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", PROMPT)
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put(
                                    "url",
                                    "data:image/jpeg;base64," +
                                        Base64.encodeToString(jpeg, Base64.NO_WRAP)
                                )
                            })
                        })
                    })
                })
            })
        }
        val withExtra = applyExtraRequestParams(payload, settings.extraRequestJson)
        return appJson.encodeToString(JsonObject.serializer(), withExtra)
    }

    /**
     * 把用户自定义的附加参数并进请求体。
     *
     * 思考开关各家字段名与取值都不一样（OpenAI 系是 reasoning_effort，DeepSeek / 智谱 / 火山是 thinking 对象，
     * 通义是 enable_thinking），服务商自己也不提供"支持哪些参数"的元数据，所以这里不替用户猜，
     * 只把他写的原样带上；写坏了（不是合法 JSON）等同于没填。
     */
    private fun applyExtraRequestParams(payload: JsonObject, extraJson: String): JsonObject {
        val extra = runCatching { appJson.parseToJsonElement(extraJson).jsonObject }.getOrNull()
            ?: return payload
        return JsonObject(
            payload.toMutableMap().apply {
                extra.forEach { (key, value) ->
                    if (key !in RESERVED_KEYS) put(key, value)
                }
            }
        )
    }

    private fun parseAnalysis(responseBody: String): AiAnalysis {
        val message = appJson.parseToJsonElement(responseBody)
            .jsonObject
            .getValue("choices")
            .jsonArray
            .first()
            .jsonObject
            .getValue("message")
            .jsonObject
        val content = message.text("content")
        Log.d(TAG, "模型返回内容：$content")

        val json = extractJsonObject(content)
            ?: throw IllegalStateException("模型未返回 JSON：${content.take(ERROR_BODY_LIMIT)}")

        return AiAnalysis(
            // 模型每帧独立作答，同一项的写法规整一遍，免得换了一帧就变成另一种写法
            result = ScanResult(
                productName = ScanValueFormat.text(json.text("productName")),
                productionDate = ScanValueFormat.date(json.text("productionDate")),
                expiryDate = ScanValueFormat.date(json.text("expiryDate")),
                shelfLife = ScanValueFormat.shelfLife(json.text("shelfLife"))
            ),
            reasoning = REASONING_KEYS
                .firstNotNullOfOrNull { key -> message.text(key).trim().takeIf { it.isNotEmpty() } }
                .orEmpty()
        )
    }

    /** 取字段文本：与 org.json 的 optString 同一套规矩——缺失与 null 为空串，对象/数组取其 JSON 文本 */
    private fun JsonObject.text(key: String): String = when (val value = this[key]) {
        null, is JsonNull -> ""
        is JsonPrimitive -> value.content
        else -> value.toString()
    }

    /**
     * 模型常把 JSON 包在 ``` 代码块或多余文字里，这里取首尾花括号之间的内容
     */
    private fun extractJsonObject(text: String): JsonObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            appJson.parseToJsonElement(text.substring(start, end + 1)).jsonObject
        }.getOrNull()
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
        // 只写规则，不举取值例子：举了例子，就分不清模型是服从了规则的通用性、还是照着那个例子套，
        // 而且例子本身会把这些写法教给模型。允许的单位是封闭枚举，属于规则的一部分，不算例子。
        val PROMPT = """
            你是食品标签识别助手。请从这张照片中读取以下四项，只输出 JSON，不要输出解释，也不要使用代码标记：
            {"productName":"","productionDate":"","expiryDate":"","shelfLife":""}

            规则：
            - productName：这是什么食品，用最简的通用品名，只写品类本身
            - productionDate：标签上印刷的生产日期，只写 yyyy-MM-dd
            - expiryDate：标签上印刷的过期日期或保质期截止日期，只写 yyyy-MM-dd
            - shelfLife：标签上印刷的保质期，只写「整数 + 单位」这一个量，单位只允许 年、个月、天、周；
              标签上写的是中文数字也要换算成阿拉伯数字
            - 每一项都只写标签上那一项的内容本身，不要带上那一项的名称
            - 内容里若有符合上面写法的部分，只写那一部分，其余说明不要带上；若没有，原样照抄内容，
              不要自己换算或补全
            - 只填写这张照片里确实看得清的字段；看不清或没拍到的一律留空。
            - 留空表示「这张照片里没看到这一项」，不会清除此前已识别到的内容，所以不要为了补全而推测或编造。
            - 不要做任何日期推算或计算。
        """.trimIndent()
    }
}
