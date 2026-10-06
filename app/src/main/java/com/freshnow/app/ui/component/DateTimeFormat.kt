package com.freshnow.app.ui.component

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SAVED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 记录保存时间，列表与详情页共用同一种展示格式 */
fun formatSavedAt(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(SAVED_AT_FORMATTER)
