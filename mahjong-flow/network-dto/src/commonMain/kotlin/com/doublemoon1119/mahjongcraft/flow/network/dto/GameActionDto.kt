package com.doublemoon1119.mahjongcraft.flow.network.dto

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * [GameAction] 的線路 DTO。[ExhaustiveDraw.reason] 是開放型別
 * （見 [ExhaustiveDrawReasonDto]），透過 registry 轉換，不是窮舉 `when`。
 */
@Serializable
sealed interface GameActionDto {
    @Serializable data object GameStarted : GameActionDto

    @Serializable data object RoundStarted : GameActionDto

    @Serializable data object Draw : GameActionDto

    @Serializable data class Discard(val tileId: String) : GameActionDto

    @Serializable data class Chi(val tileId: String, val withTiles: List<String>) : GameActionDto

    @Serializable data class Pon(val tileId: String) : GameActionDto

    @Serializable data class Kan(val kanType: KanTypeDto, val tileId: String, val withTiles: List<String>) : GameActionDto

    @Serializable data class Ron(val tileId: String) : GameActionDto

    @Serializable data object Tsumo : GameActionDto

    @Serializable data object Riichi : GameActionDto

    @Serializable data object Pass : GameActionDto

    @Serializable data class ExhaustiveDraw(val reason: ExhaustiveDrawReasonDto) : GameActionDto
}

@Serializable
enum class KanTypeDto { OPEN_KAN, CLOSED_KAN, ADDED_KAN }

fun GameAction.toDto(): GameActionDto = when (this) {
    GameAction.GameStarted -> GameActionDto.GameStarted
    GameAction.RoundStarted -> GameActionDto.RoundStarted
    GameAction.Draw -> GameActionDto.Draw
    is GameAction.Discard -> GameActionDto.Discard(tileId.toString())
    is GameAction.Chi -> GameActionDto.Chi(tileId.toString(), withTiles.map { it.toString() })
    is GameAction.Pon -> GameActionDto.Pon(tileId.toString())
    is GameAction.Kan -> GameActionDto.Kan(type.toDto(), tileId.toString(), withTiles.map { it.toString() })
    is GameAction.Ron -> GameActionDto.Ron(tileId.toString())
    GameAction.Tsumo -> GameActionDto.Tsumo
    GameAction.Riichi -> GameActionDto.Riichi
    GameAction.Pass -> GameActionDto.Pass
    is GameAction.ExhaustiveDraw -> GameActionDto.ExhaustiveDraw(reason.toDto())
}

fun GameActionDto.toDomain(): GameAction = when (this) {
    GameActionDto.GameStarted -> GameAction.GameStarted
    GameActionDto.RoundStarted -> GameAction.RoundStarted
    GameActionDto.Draw -> GameAction.Draw
    is GameActionDto.Discard -> GameAction.Discard(Uuid.parse(tileId))
    is GameActionDto.Chi -> GameAction.Chi(Uuid.parse(tileId), withTiles.map { Uuid.parse(it) })
    is GameActionDto.Pon -> GameAction.Pon(Uuid.parse(tileId))
    is GameActionDto.Kan -> GameAction.Kan(kanType.toDomain(), Uuid.parse(tileId), withTiles.map { Uuid.parse(it) })
    is GameActionDto.Ron -> GameAction.Ron(Uuid.parse(tileId))
    GameActionDto.Tsumo -> GameAction.Tsumo
    GameActionDto.Riichi -> GameAction.Riichi
    GameActionDto.Pass -> GameAction.Pass
    is GameActionDto.ExhaustiveDraw -> GameAction.ExhaustiveDraw(reason.toDomain())
}

fun GameAction.KanType.toDto(): KanTypeDto = when (this) {
    GameAction.KanType.OPEN_KAN -> KanTypeDto.OPEN_KAN
    GameAction.KanType.CLOSED_KAN -> KanTypeDto.CLOSED_KAN
    GameAction.KanType.ADDED_KAN -> KanTypeDto.ADDED_KAN
}

fun KanTypeDto.toDomain(): GameAction.KanType = when (this) {
    KanTypeDto.OPEN_KAN -> GameAction.KanType.OPEN_KAN
    KanTypeDto.CLOSED_KAN -> GameAction.KanType.CLOSED_KAN
    KanTypeDto.ADDED_KAN -> GameAction.KanType.ADDED_KAN
}
