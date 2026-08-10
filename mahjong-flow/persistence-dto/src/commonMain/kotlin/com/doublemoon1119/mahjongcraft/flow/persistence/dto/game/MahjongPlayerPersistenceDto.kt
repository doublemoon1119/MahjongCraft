package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** [Wind] 的 persistence DTO。 */
@Serializable
enum class WindPersistenceDto { EAST, SOUTH, WEST, NORTH }

/** [MahjongPlayer] 的完整 persistence DTO。 */
@Serializable
data class MahjongPlayerPersistenceDto(
    val id: String,
    val initialSeat: WindPersistenceDto,
    val hand: HandPersistenceDto,
    val discardPile: TypedPersistenceDto,
    val playerRuleState: TypedPersistenceDto?,
    val score: Int,
    val aiStrategyKey: String?,
    val currentWind: WindPersistenceDto,
    val passedTilesInRound: List<TilePersistenceDto>,
    val actionHistory: List<GameActionPersistenceDto>,
)

/** 將 [MahjongPlayer] 轉換成 persistence DTO。 */
fun MahjongPlayer.toPersistenceDto(
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    playerRuleStateRegistry: PersistenceDtoRegistry<PlayerRuleState>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason>,
    json: Json = Json,
): MahjongPlayerPersistenceDto = MahjongPlayerPersistenceDto(
    id = id.toString(),
    initialSeat = WindPersistenceDto.valueOf(initialSeat.name),
    hand = hand.toPersistenceDto(),
    discardPile = discardPileRegistry.encode(discardPile, json),
    playerRuleState = playerRuleState?.let { playerRuleStateRegistry.encode(it, json) },
    score = score,
    aiStrategyKey = aiStrategyKey,
    currentWind = WindPersistenceDto.valueOf(currentWind.name),
    passedTilesInRound = passedTilesInRound.map { it.toPersistenceDto() },
    actionHistory = actionHistory.map { it.toPersistenceDto(exhaustiveDrawReasonRegistry, json) },
)

/** 將 [MahjongPlayerPersistenceDto] 還原成 [MahjongPlayer]。 */
fun MahjongPlayerPersistenceDto.toDomain(
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    playerRuleStateRegistry: PersistenceDtoRegistry<PlayerRuleState>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason>,
    json: Json = Json,
): MahjongPlayer = MahjongPlayer(
    id = Uuid.parse(id),
    initialSeat = Wind.valueOf(initialSeat.name),
    hand = hand.toDomain(),
    discardPile = discardPileRegistry.decode(discardPile, json),
    playerRuleState = playerRuleState?.let { playerRuleStateRegistry.decode(it, json) },
    score = score,
    aiStrategyKey = aiStrategyKey,
    currentWind = Wind.valueOf(currentWind.name),
    passedTilesInRound = passedTilesInRound.mapTo(mutableSetOf()) { it.toDomain() },
    actionHistory = actionHistory.map { it.toDomain(exhaustiveDrawReasonRegistry, json) },
)
