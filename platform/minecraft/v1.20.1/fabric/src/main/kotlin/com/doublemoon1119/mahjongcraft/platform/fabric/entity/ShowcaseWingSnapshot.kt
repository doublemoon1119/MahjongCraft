package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 單一贏家展示翼的同步資料。
 *
 * @property seatIndex 贏家座位索引。
 * @property cueKey 展示效果識別碼。
 * @property cards 展示翼包含的牌。
 */
data class ShowcaseWingSnapshot(val seatIndex: Int, val cueKey: String, val cards: List<ShowcaseCardSnapshot>)
