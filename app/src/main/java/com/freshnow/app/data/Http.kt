package com.freshnow.app.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * 一次 HTTP 往返的原始结果。
 *
 * [body] 在非 2xx 时也是对端实际返回的内容（取自 errorStream）：识图服务把错误原因写在响应体里，
 * 丢掉它就只剩一个状态码可看。
 */
internal data class HttpResult(val code: Int, val body: String)

/**
 * 发一次请求并读完响应体。
 *
 * 识图与检查更新的差别只有超时、头部和请求体，读响应的方式完全一样；连接的生命周期
 * （含 disconnect）收在这里，免得两处各写一遍 finally。
 */
internal fun httpRequest(
    url: String,
    method: String,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    headers: Map<String, String> = emptyMap(),
    body: ByteArray? = null,
): HttpResult {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        headers.forEach { (name, value) -> setRequestProperty(name, value) }
        if (body != null) doOutput = true
    }
    return try {
        if (body != null) connection.outputStream.use { it.write(body) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        HttpResult(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
    } finally {
        connection.disconnect()
    }
}
