package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.PlayerRuleStateDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import kotlinx.serialization.Serializable

/**
 * [RiichiPlayerState] 的網路 DTO。
 *
 * @property riichiTile 宣告立直時打出的牌；未宣告時為 `null`。
 * @property doubleRiichiTile 宣告雙立直時打出的牌；未宣告時為 `null`。
 * @property isIppatsu 是否仍保有一發狀態。
 * @property paoLiability 包責任資訊；沒有時為 `null`。
 * @property nukiDoraTiles 已拔出的北牌實體。
 */
@Serializable
data class RiichiPlayerStateDto(
    val riichiTile: IdentifiedTileDto?,
    val doubleRiichiTile: IdentifiedTileDto?,
    val isIppatsu: Boolean,
    val paoLiability: PaoLiabilityDto?,
    val nukiDoraTiles: List<IdentifiedTileDto>,
) : PlayerRuleStateDto
