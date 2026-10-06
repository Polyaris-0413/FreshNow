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
}
