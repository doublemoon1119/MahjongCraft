package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.IdentifiedTilePersistenceDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import kotlinx.serialization.Serializable

/**
 * [RiichiPlayerState] 的完整 persistence DTO。
 *
 * @property riichiTile 立直宣告時的牌張；沒有時為 null。
 * @property doubleRiichiTile 雙立直宣告時的牌張；沒有時為 null。
 * @property isIppatsu 是否保留一發狀態。
 * @property paoLiability 包牌責任資料；沒有時為 null。
 * @property isPermanentlyFuriten 是否處於永久振聽狀態。
 * @property nukiDoraTiles 已拔出的北牌列表。
 */
@Serializable
data class RiichiPlayerStatePersistenceDto(
    val riichiTile: IdentifiedTilePersistenceDto?,
    val doubleRiichiTile: IdentifiedTilePersistenceDto?,
    val isIppatsu: Boolean,
    val paoLiability: PaoLiabilityPersistenceDto?,
    val isPermanentlyFuriten: Boolean,
    val nukiDoraTiles: List<IdentifiedTilePersistenceDto>,
)
