package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.PlayerRuleStateDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerStateSnapshot
import kotlinx.serialization.Serializable

/**
 * [RiichiPlayerStateSnapshot] 的 observer 網路 DTO。
 *
 * 仍然未公開的布林欄位使用 `null`，不以 `false` 偽裝成已確認的公開狀態；一發資格因可由公開流程推導而保留。
 *
 * @property riichiTile 公開的立直牌；沒有時為 `null`。
 * @property doubleRiichiTile 公開的雙立直牌；沒有時為 `null`。
 * @property isIppatsu 可由公開立直與鳴牌流程推導的一發資格。
 * @property paoLiability 公開的包牌責任；沒有時為 `null`。
 * @property isPermanentlyFuriten 永久振聽；未向目前讀取者公開時為 `null`。
 * @property nukiDoraTiles 已公開移出的北牌。
 */
@Serializable
data class RiichiPlayerStateSnapshotDto(
    val riichiTile: IdentifiedTileDto?,
    val doubleRiichiTile: IdentifiedTileDto?,
    val isIppatsu: Boolean?,
    val paoLiability: PaoLiabilityDto?,
    val isPermanentlyFuriten: Boolean?,
    val nukiDoraTiles: List<IdentifiedTileDto>,
) : PlayerRuleStateDto
