package com.freshnow.app.data

import kotlinx.serialization.json.Json

/**
 * 两者解析的都是外部服务可能新增字段的响应，认不出的字段忽略；同步协议另有 encodeDefaults
 * 的要求，实例分开，不合并。
 */
internal val appJson: Json = Json {
    ignoreUnknownKeys = true
}
