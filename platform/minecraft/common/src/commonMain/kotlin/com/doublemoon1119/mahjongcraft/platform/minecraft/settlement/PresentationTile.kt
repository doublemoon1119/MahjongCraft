package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 宣告式模板中的一格牌：露出牌面，或只畫牌背。 */
sealed interface PresentationTile {
    /**
     * 露出牌面的牌。
     *
     * @property assetKey 牌面素材 key。
     */
    data class Face(
        val assetKey: String,
    ) : PresentationTile

    /**
     * 蓋著的牌；只帶要畫的牌背，不帶牌面。
     *
     * @property appearanceId 牌背使用的牌組外觀 ID。
     */
    data class Back(
        val appearanceId: String,
    ) : PresentationTile
}
