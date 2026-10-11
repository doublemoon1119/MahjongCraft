package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 和牌詳情欄位的同步快照。
 *
 * @property id 欄位識別碼。
 * @property type 欄位資料種類。
 * @property values 欄位依序使用的文字值。
 */
data class WinSettlementDetailSnapshot(val id: String, val type: String, val values: List<String>)
