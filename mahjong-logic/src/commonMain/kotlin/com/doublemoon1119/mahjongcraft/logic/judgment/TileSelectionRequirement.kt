package com.doublemoon1119.mahjongcraft.logic.judgment

import kotlin.uuid.Uuid

/**
 * 描述某個合法動作在成立前，還需要玩家從立牌中額外選出哪些牌，形狀比照
 * `RoundPreparationPromptDto.TileSelection`（network-dto），但用途不同：後者的候選集合是靜態列舉，
 * 這裡的候選集合則由規則模組依動作語意動態算出（例如「打了這張牌之後是否仍然聽牌」）。
 *
 * @property eligibleTileIds 允許被選中的立牌識別碼集合。
 * @property minCount 至少需要選幾張。
 * @property maxCount 最多可以選幾張。
 */
data class TileSelectionRequirement(
    val eligibleTileIds: Set<Uuid>,
    val minCount: Int = 1,
    val maxCount: Int = 1,
) {
    init {
        require(minCount >= 1) { "Minimum tile selection count must be at least one" }
        require(maxCount >= minCount) { "Maximum tile selection count must not be less than the minimum" }
        require(maxCount <= eligibleTileIds.size) { "Maximum tile selection count exceeds eligible tiles" }
    }
}
