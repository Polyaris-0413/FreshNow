package com.freshnow.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiVisionClientTest {

    private val client = AiVisionClient()
    private val jpeg = byteArrayOf(0x01, 0x02, 0x03)

    private fun payloadOf(extraJson: String): String = client.buildPayload(
        AiSettings(
            baseUrl = "https://example.com",
            modelName = "demo-model",
            apiKey = "sk-test",
            extraRequestJson = extraJson
        ),
        jpeg
    )

    @Test
    fun extraRequestParams_areMergedIntoPayload() {
        val payload = payloadOf("""{"thinking":{"type":"disabled"}}""")

        assertTrue(payload.contains("\"thinking\""))
        assertTrue(payload.contains("disabled"))
    }

    /** model 与 messages 由应用组装，用户写了也不生效，否则请求结构会被改坏 */
    @Test
    fun reservedKeys_inExtraParams_areIgnored() {
        val payload = payloadOf("""{"model":"hacked","messages":[]}""")

        assertTrue(payload.contains("\"model\":\"demo-model\""))
        assertFalse(payload.contains("hacked"))
    }

    @Test
    fun invalidJson_isIgnoredWithoutBreakingPayload() {
        val payload = payloadOf("thinking: disabled")

        assertTrue(payload.contains("\"model\":\"demo-model\""))
        assertTrue(payload.contains("\"messages\""))
        assertFalse(payload.contains("thinking:"))
    }

    @Test
    fun blankExtraParams_leavePayloadUnchanged() {
        val payload = payloadOf("")

        assertFalse(payload.contains("thinking"))
        assertTrue(payload.contains("\"model\":\"demo-model\""))
    }

    /**
     * 提示词只写规则、不举取值例子：举了例子，就分不清模型是服从了规则的通用性、还是照着那个例子套，
     * 而例子本身又会把这些写法教给模型。这条测试盯着别把例子加回来。
     * （yyyy-MM-dd 是格式定义，不是取值例子，所以它必须留着。）
     */
    @Test
    fun prompt_givesRulesWithoutExamples() {
        val prompt = payloadOf("")

        assertTrue(prompt.contains("yyyy-MM-dd"))
        assertFalse("提示词里不该出现取值例子", prompt.contains("例如"))
    }
}
