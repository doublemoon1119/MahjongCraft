package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RelativeDirectionDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import kotlinx.serialization.Serializable

/**
 * [PaoLiability] 的網路 DTO。
 *
 * @property yaku 由包牌責任承擔的役種。
 * @property direction 責任承擔者相對於持有者的方向。
 */
@Serializable
data class PaoLiabilityDto(
    val yaku: PaoYakuDto,
    val direction: RelativeDirectionDto,
)
