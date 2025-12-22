package com.xzh.hexdeep.model

data class DeviceItem(
    val id: String,
    val remark: String = "",
    val createdAt: Long = System.currentTimeMillis()
)