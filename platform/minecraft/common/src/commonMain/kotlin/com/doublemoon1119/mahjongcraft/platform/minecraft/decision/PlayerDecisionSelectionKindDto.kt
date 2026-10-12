package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/** 客戶端提交 prompt 選擇時使用的受控操作種類。 */
@Serializable
enum class PlayerDecisionSelectionKindDto {
    /** 提交一項可用動作。 */
    ACTION,

    /** 確認目前的開局準備步驟。 */
    PREPARATION_CONFIRM,

    /** 提交一個開局準備選項。 */
    PREPARATION_CHOICE,

    /** 提交開局準備所選的牌張。 */
    PREPARATION_TILES,

    /** 明確進入需要選取多張牌的實體牌選取模式。 */
    BEGIN_TILE_SELECTION,
}
