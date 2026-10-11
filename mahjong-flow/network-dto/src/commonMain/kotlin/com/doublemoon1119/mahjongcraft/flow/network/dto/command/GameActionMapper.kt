package com.doublemoon1119.mahjongcraft.flow.network.dto.command
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun GameAction.toDto(registries: NetworkDtoRegistries): GameActionDto = when (this) {
    GameAction.GameStarted -> GameActionDto.GameStarted
    GameAction.RoundStarted -> GameActionDto.RoundStarted
    GameAction.MatchEnded -> GameActionDto.MatchEnded
    is GameAction.DiceRolled -> GameActionDto.DiceRolled(dice.values)
    GameAction.Draw -> GameActionDto.Draw
    is GameAction.Discard -> GameActionDto.Discard(tileId.toString())
    is GameAction.Chi -> GameActionDto.Chi(tileId.toString(), withTiles.map { it.toString() })
    is GameAction.Pon -> GameActionDto.Pon(tileId.toString(), withTiles.map { it.toString() })
    is GameAction.Kan -> GameActionDto.Kan(type.toDto(), tileId.toString(), withTiles.map { it.toString() })
    is GameAction.Ron -> GameActionDto.Ron(tileId.toString())
    GameAction.Tsumo -> GameActionDto.Tsumo
    is GameAction.Extension -> GameActionDto.Extension(value.toDto(registries))
    GameAction.Pass -> GameActionDto.Pass
    is GameAction.ExhaustiveDraw -> GameActionDto.ExhaustiveDraw(reason.toDto(registries))
}

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun GameActionDto.toDomain(registries: NetworkDtoRegistries): GameAction = when (this) {
    GameActionDto.GameStarted -> GameAction.GameStarted
    GameActionDto.RoundStarted -> GameAction.RoundStarted
    GameActionDto.MatchEnded -> GameAction.MatchEnded
    is GameActionDto.DiceRolled -> GameAction.DiceRolled(DiceRollResult.of(dice))
    GameActionDto.Draw -> GameAction.Draw
    is GameActionDto.Discard -> GameAction.Discard(Uuid.parse(tileId))
    is GameActionDto.Chi -> GameAction.Chi(Uuid.parse(tileId), withTiles.map { Uuid.parse(it) })
    is GameActionDto.Pon -> GameAction.Pon(Uuid.parse(tileId), withTiles.map { Uuid.parse(it) })
    is GameActionDto.Kan -> GameAction.Kan(kanType.toDomain(), Uuid.parse(tileId), withTiles.map { Uuid.parse(it) })
    is GameActionDto.Ron -> GameAction.Ron(Uuid.parse(tileId))
    GameActionDto.Tsumo -> GameAction.Tsumo
    is GameActionDto.Extension -> GameAction.Extension(value.toDomain(registries))
    GameActionDto.Pass -> GameAction.Pass
    is GameActionDto.ExhaustiveDraw -> GameAction.ExhaustiveDraw(reason.toDomain(registries))
}

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun GameAction.KanType.toDto(): KanTypeDto = when (this) {
    GameAction.KanType.OPEN_KAN -> KanTypeDto.OPEN_KAN
    GameAction.KanType.CLOSED_KAN -> KanTypeDto.CLOSED_KAN
    GameAction.KanType.ADDED_KAN -> KanTypeDto.ADDED_KAN
}

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun KanTypeDto.toDomain(): GameAction.KanType = when (this) {
    KanTypeDto.OPEN_KAN -> GameAction.KanType.OPEN_KAN
    KanTypeDto.CLOSED_KAN -> GameAction.KanType.CLOSED_KAN
    KanTypeDto.ADDED_KAN -> GameAction.KanType.ADDED_KAN
}
