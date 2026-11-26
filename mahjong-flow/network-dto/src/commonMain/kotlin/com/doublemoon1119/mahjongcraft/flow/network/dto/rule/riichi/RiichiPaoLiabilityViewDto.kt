package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 已公開的包牌責任，不包含其他日麻玩家狀態。
 *
 * @property liability 役種與責任方位。
 */
@Serializable
@SerialName("mahjongcraft:riichi/player_view/pao_liability")
data class RiichiPaoLiabilityViewDto(val liability: RiichiPaoLiabilityDto) : ComponentViewDto
