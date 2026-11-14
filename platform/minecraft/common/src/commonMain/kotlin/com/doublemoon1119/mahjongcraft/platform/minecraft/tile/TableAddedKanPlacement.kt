package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 加槓補上的牌的擺法。 */
enum class TableAddedKanPlacement {
    /** 疊在原本橫放的被鳴牌靠桌心那一側，不佔用額外的橫向格位；需要被鳴的牌橫放。 */
    BESIDE_CLAIMED_TILE,

    /** 與原本三張排成一列，四張並排。 */
    IN_ROW,
}
