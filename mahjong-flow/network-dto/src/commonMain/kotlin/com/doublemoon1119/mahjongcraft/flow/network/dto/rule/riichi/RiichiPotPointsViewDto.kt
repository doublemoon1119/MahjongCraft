package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 公開的供託點數。
 *
 * @property points 場上尚未分配的實際點數。
 */
@Serializable
@SerialName("mahjongcraft:riichi/table_view/pot_points")
data class RiichiPotPointsViewDto(val points: Int) : ComponentViewDto
