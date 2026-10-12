package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import kotlinx.serialization.Serializable

/**
 * [MahjongPlayer] 的完整 persistence DTO。
 *
 * @property id 玩家 UUID 字串。
 * @property initialSeatIndex 玩家在對局開始時的座位索引。
 * @property hand 玩家的手牌。
 * @property discardPile 玩家牌河的 registry DTO。
 * @property playerRuleState 玩家的規則狀態；沒有時為 null。
 * @property score 玩家目前分數。
 * @property seatWind 玩家目前的座位風。
 * @property passedTilesInRound 本局已被跳過的牌。
 * @property actionHistory 玩家已接受動作的歷史。
 */
@Serializable
data class MahjongPlayerPersistenceDto(
    val id: String,
    val initialSeatIndex: Int,
    val hand: HandPersistenceDto,
    val discardPile: TypedPersistenceDto,
    val playerRuleState: TypedPersistenceDto?,
    val score: Int,
    val seatWind: WindPersistenceDto,
    val passedTilesInRound: List<TilePersistenceDto>,
    val actionHistory: List<GameActionPersistenceDto>,
)
