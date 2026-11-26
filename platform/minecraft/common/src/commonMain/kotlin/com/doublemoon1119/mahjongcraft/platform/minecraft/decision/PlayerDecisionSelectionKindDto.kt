package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/** 客戶端提交 prompt 選擇時使用的受控操作種類。 */
@Serializable
enum class PlayerDecisionSelectionKindDto {
    /** 提交一項可用動作。 */
    ACTION,

    /** 確認目前的規則選擇步驟。 */
    DECISION_CONFIRM,

    /** 提交一個規則選擇選項。 */
    DECISION_CHOICE,

    /** 提交規則選擇所選的牌張。 */
    DECISION_TILES,

    /** 明確進入需要選取多張牌的實體牌選取模式。 */
    BEGIN_TILE_SELECTION,
}
