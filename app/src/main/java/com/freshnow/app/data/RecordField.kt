package com.freshnow.app.data

/**
 * 一条记录可编辑的一项。
 *
 * 放在数据层而不是表单旁边：扫描页要按它记住「哪几项是用户自己写的」（见 [mergeObservation]），
 * 而那件事与表单长什么样无关。
 */
enum class RecordField { PRODUCT_NAME, LOCATION, PRODUCTION_DATE, EXPIRY_DATE, SHELF_LIFE }
