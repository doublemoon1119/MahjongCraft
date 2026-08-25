package com.doublemoon1119.mahjongcraft.flow.network.dto.command

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExhaustiveDrawReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * [GameAction] 的網路 DTO。[ExhaustiveDraw.reason] 是開放型別
 * （見 [ExhaustiveDrawReasonDto]），透過 registry 轉換，不是窮舉 `when`。
 */
@Serializable
sealed interface GameActionDto {
    @Serializable data object GameStarted : GameActionDto

    @Serializable data object RoundStarted : GameActionDto

    @Serializable data object MatchEnded : GameActionDto

    @Serializable data class DiceRolled(val dice: List<Int>) : GameActionDto

    @Serializable data object Draw : GameActionDto

    @Serializable data class Discard(val tileId: String) : GameActionDto

    @Serializable data class Chi(val tileId: String, val withTiles: List<String>) : GameActionDto

    @Serializable data class Pon(val tileId: String) : GameActionDto

    @Serializable data class Kan(val kanType: KanTypeDto, val tileId: String, val withTiles: List<String>) : GameActionDto

    @Serializable data class Ron(val tileId: String) : GameActionDto

    @Serializable data object Tsumo : GameActionDto

    @Serializable data class Extension(@Polymorphic val value: com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameActionDto) : GameActionDto

    @Serializable data object Pass : GameActionDto

    @Serializable data class ExhaustiveDraw(val reason: ExhaustiveDrawReasonDto) : GameActionDto
}

@Serializable
enum class KanTypeDto { OPEN_KAN, CLOSED_KAN, ADDED_KAN }

fun GameAction.toDto(registries: NetworkDtoRegistries): GameActionDto = when (this) {
    GameAction.GameStarted -> GameActionDto.GameStarted
    GameAction.RoundStarted -> GameActionDto.RoundStarted
    GameAction.MatchEnded -> GameActionDto.MatchEnded
    is GameAction.DiceRolled -> GameActionDto.DiceRolled(dice.values)
    GameAction.Draw -> GameActionDto.Draw
    is GameAction.Discard -> GameActionDto.Discard(tileId.toString())
    is GameAction.Chi -> GameActionDto.Chi(tileId.toString(), withTiles.map { it.toString() })
    is GameAction.Pon -> GameActionDto.Pon(tileId.toString())
    is GameAction.Kan -> GameActionDto.Kan(type.toDto(), tileId.toString(), withTiles.map { it.toString() })
    is GameAction.Ron -> GameActionDto.Ron(tileId.toString())
    GameAction.Tsumo -> GameActionDto.Tsumo
    is GameAction.Extension -> GameActionDto.Extension(value.toDto(registries))
    GameAction.Pass -> GameActionDto.Pass
    is GameAction.ExhaustiveDraw -> GameActionDto.ExhaustiveDraw(reason.toDto(registries))
}

fun GameActionDto.toDomain(registries: NetworkDtoRegistries): GameAction = when (this) {
    GameActionDto.GameStarted -> GameAction.GameStarted
    GameActionDto.RoundStarted -> GameAction.RoundStarted
    GameActionDto.MatchEnded -> GameAction.MatchEnded
    is GameActionDto.DiceRolled -> GameAction.DiceRolled(DiceRollResult.of(dice))
    GameActionDto.Draw -> GameAction.Draw
    is GameActionDto.Discard -> GameAction.Discard(Uuid.parse(tileId))
    is GameActionDto.Chi -> GameAction.Chi(Uuid.parse(tileId), withTiles.map { Uuid.parse(it) })
    is GameActionDto.Pon -> GameAction.Pon(Uuid.parse(tileId))
    is GameActionDto.Kan -> GameAction.Kan(kanType.toDomain(), Uuid.parse(tileId), withTiles.map { Uuid.parse(it) })
    is GameActionDto.Ron -> GameAction.Ron(Uuid.parse(tileId))
    GameActionDto.Tsumo -> GameAction.Tsumo
    is GameActionDto.Extension -> GameAction.Extension(value.toDomain(registries))
    GameActionDto.Pass -> GameAction.Pass
    is GameActionDto.ExhaustiveDraw -> GameAction.ExhaustiveDraw(reason.toDomain(registries))
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
