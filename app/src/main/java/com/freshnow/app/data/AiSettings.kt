package com.freshnow.app.data

data class AiSettings(
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val showReasoning: Boolean = false,
    /**
     * 用户自己写的附加请求参数，例如 {"thinking":{"type":"disabled"}}，原样并入请求体；
     * 空串表示不附加、完全跟随服务商默认
     */
    val extraRequestJson: String = ""
)
