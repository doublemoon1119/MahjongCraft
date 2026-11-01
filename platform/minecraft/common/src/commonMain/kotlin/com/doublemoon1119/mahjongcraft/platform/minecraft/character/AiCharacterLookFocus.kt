package com.doublemoon1119.mahjongcraft.platform.minecraft.character

/** 世界角色的簡單注視情境，只能由公開位置解析實際目標。 */
enum class AiCharacterLookFocus {
    /** 自己手牌區的固定中央，不是某張私人牌。 */
    HAND_AREA,

    /** 最近一次仍有效的公開捨牌。 */
    DISCARD,

    /** 目前行動者的公開位置。 */
    PARTICIPANT,
}
