package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/** 客戶端提交 prompt 選擇時使用的受控操作種類。 */
@Serializable
enum class PlayerDecisionSelectionKindDto {
    ACTION,
    PREPARATION_CONFIRM,
    PREPARATION_CHOICE,
    PREPARATION_TILES,

    /** 明確進入需要選取多張牌的實體牌選取模式。 */
    BEGIN_TILE_SELECTION,
}
