package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RelativeDirectionPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPaoLiability
import kotlinx.serialization.Serializable

/**
 * [RiichiPaoLiability] 的完整 persistence DTO。
 *
 * @property yaku 包牌責任所對應的役種。
 * @property direction 相對方向。
 */
@Serializable
data class RiichiPaoLiabilityPersistenceDto(
    val yaku: RiichiPaoYakuPersistenceDto,
    val direction: RelativeDirectionPersistenceDto,
)
