package com.freshnow.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条扫描记录。只落库模型读到的原文——过期日期不存，它是由生产日期与保质期
 * 推算出的派生值，展示时用 ExpiryCalculator 现算，推算逻辑改进后旧记录也能受益
 */
@Entity(tableName = "scan_records")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productionDate: String,
    val expiryDate: String,
    val shelfLife: String,
    val savedAt: Long
)
