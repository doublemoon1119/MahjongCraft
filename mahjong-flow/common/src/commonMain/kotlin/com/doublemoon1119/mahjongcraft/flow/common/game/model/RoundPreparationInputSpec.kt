package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/** 開局準備步驟要求玩家提交的受控輸入。 */
sealed interface RoundPreparationInputSpec {
    /** 只需確認的輸入。 */
    data object Confirmation : RoundPreparationInputSpec

    /**
     * 從完整 namespaced 選項中選擇一項的輸入。
     *
     * @property optionIds 可選擇的完整命名空間識別碼；不可為空或重複。
     */
    data class SingleChoice(val optionIds: List<String>) : RoundPreparationInputSpec {
        init {
            require(optionIds.isNotEmpty()) { "Round preparation choices must not be empty" }
            require(optionIds.distinct().size == optionIds.size) { "Round preparation choices must be unique" }
            require(optionIds.all(NamespacedId::isValid)) {
                "Round preparation choices must use namespaced IDs"
            }
        }
    }

    /**
     * 從指定牌集合選取限定數量的輸入。
     *
     * @property eligibleTileIds 可選取的牌張識別碼集合。
     * @property minCount 最少選取張數。
     * @property maxCount 最多選取張數；不可少於最少張數或超過可選牌張數。
     */
    data class TileSelection(
        val eligibleTileIds: Set<Uuid>,
        val minCount: Int,
        val maxCount: Int,
    ) : RoundPreparationInputSpec {
        init {
            require(minCount >= 0) { "Minimum tile selection count must not be negative" }
            require(maxCount >= minCount) { "Maximum tile selection count must not be less than minimum" }
            require(maxCount <= eligibleTileIds.size) { "Maximum tile selection count exceeds eligible tiles" }
        }
    }
}
