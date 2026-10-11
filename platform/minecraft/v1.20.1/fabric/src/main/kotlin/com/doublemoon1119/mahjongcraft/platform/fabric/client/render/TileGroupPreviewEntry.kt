package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/**
 * 一張牌在橫向牌組預覽中的宣告式輸入。
 *
 * @property assetKey 牌面資產。
 * @property claimed 是否為鳴取自他家的那張牌，決定要不要畫鳴牌指標。
 */
data class TileGroupPreviewEntry(
    val assetKey: String,
    val claimed: Boolean = false,
)
