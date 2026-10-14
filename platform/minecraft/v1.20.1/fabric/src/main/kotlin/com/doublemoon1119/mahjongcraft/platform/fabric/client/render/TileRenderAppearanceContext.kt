package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition

/**
 * 一次牌面繪製所使用的公開牌組外觀上下文。
 *
 * 這個值只包含規則選定的共用外觀，不包含隱藏牌的真實牌種或牌面資訊；可傳給 item 與面板 renderer，
 * 讓同一桌的可見牌、蓋牌及暫時展示保持一致。
 *
 * @property definition 本次繪製使用的共用牌組外觀。
 */
data class TileRenderAppearanceContext(
    val definition: MinecraftTileSetAppearanceDefinition,
) {
    /** 供模型快取與 renderer 查詢使用的穩定外觀 ID。 */
    val appearanceId: String get() = definition.id
}
