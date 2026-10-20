package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import kotlin.uuid.Uuid

/**
 * 將 [GameAction] 轉換成 persistence DTO。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @return 包含原模型資料的持久化 DTO。
 * @throws IllegalArgumentException 擴充動作的保存識別碼與動作 ID 不一致。
 */
fun GameAction.toPersistenceDto(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
): GameActionPersistenceDto = when (this) {
    is GameAction.Extension -> {
        val encoded = extensionGameActionRegistry.encode(value)
        require(encoded.typeKey == value.id) {
            "Extension action persistence type key '${encoded.typeKey}' must match action ID '${value.id}'"
        }
        GameActionPersistenceDto.Extension(encoded)
    }
    GameAction.GameStarted -> GameActionPersistenceDto.GameStarted
    GameAction.RoundStarted -> GameActionPersistenceDto.RoundStarted
    GameAction.MatchEnded -> GameActionPersistenceDto.MatchEnded
    is GameAction.DiceRolled -> GameActionPersistenceDto.DiceRolled(roll.rollIndex, roll.playerId.toString(), roll.dice.values)
    GameAction.Draw -> GameActionPersistenceDto.Draw
    is GameAction.Discard -> GameActionPersistenceDto.Discard(tileId.toString())
    is GameAction.Chi -> GameActionPersistenceDto.Chi(tileId.toString(), withTiles.map(Uuid::toString))
    is GameAction.Pon -> GameActionPersistenceDto.Pon(tileId.toString(), withTiles.map(Uuid::toString))
    is GameAction.Kan -> toPersistenceDto()
    is GameAction.Ron -> GameActionPersistenceDto.Ron(tileId.toString())
    GameAction.Tsumo -> GameActionPersistenceDto.Tsumo
    GameAction.Pass -> GameActionPersistenceDto.Pass
    is GameAction.ExhaustiveDraw -> GameActionPersistenceDto.ExhaustiveDraw(exhaustiveDrawReasonRegistry.encode(reason))
}

/**
 * 將 [GameActionPersistenceDto] 還原成 [GameAction]。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @return 還原的領域模型。
 * @throws IllegalArgumentException 還原後的擴充動作 ID 與保存識別碼不一致。
 */
fun GameActionPersistenceDto.toDomain(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
): GameAction = when (this) {
    is GameActionPersistenceDto.Extension -> {
        val decoded = extensionGameActionRegistry.decode(value)
        require(decoded.id == value.typeKey) {
            "Decoded extension action ID '${decoded.id}' must match persistence type key '${value.typeKey}'"
        }
        GameAction.Extension(decoded)
    }
    GameActionPersistenceDto.GameStarted -> GameAction.GameStarted
    GameActionPersistenceDto.RoundStarted -> GameAction.RoundStarted
    GameActionPersistenceDto.MatchEnded -> GameAction.MatchEnded
    is GameActionPersistenceDto.DiceRolled -> GameAction.DiceRolled(
        OpeningDiceRoll(rollIndex, Uuid.parse(playerId), DiceRollResult.of(dice)),
    )
    GameActionPersistenceDto.Draw -> GameAction.Draw
    is GameActionPersistenceDto.Discard -> GameAction.Discard(Uuid.parse(tileId))
    is GameActionPersistenceDto.Chi -> GameAction.Chi(Uuid.parse(tileId), withTileIds.map(Uuid::parse))
    is GameActionPersistenceDto.Pon -> GameAction.Pon(Uuid.parse(tileId), withTileIds.map(Uuid::parse))
    is GameActionPersistenceDto.Kan -> toDomain()
    is GameActionPersistenceDto.Ron -> GameAction.Ron(Uuid.parse(tileId))
    GameActionPersistenceDto.Tsumo -> GameAction.Tsumo
    GameActionPersistenceDto.Pass -> GameAction.Pass
    is GameActionPersistenceDto.ExhaustiveDraw -> GameAction.ExhaustiveDraw(exhaustiveDrawReasonRegistry.decode(reason))
}

/**
 * 將 [GameAction.Kan] 轉換成 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun GameAction.Kan.toPersistenceDto(): GameActionPersistenceDto.Kan = GameActionPersistenceDto.Kan(
    kanType = type.toPersistenceDto(),
    tileId = tileId.toString(),
    withTileIds = withTiles.map(Uuid::toString),
)

/**
 * 將 [GameActionPersistenceDto.Kan] 還原成 [GameAction.Kan]。
 *
 * @return 轉換後的結果。
 */
fun GameActionPersistenceDto.Kan.toDomain(): GameAction.Kan = GameAction.Kan(
    type = kanType.toDomain(),
    tileId = Uuid.parse(tileId),
    withTiles = withTileIds.map(Uuid::parse),
)

/** 將槓種類轉成存檔 DTO。 */
private fun GameAction.KanType.toPersistenceDto(): KanTypePersistenceDto = when (this) {
    GameAction.KanType.OPEN_KAN -> KanTypePersistenceDto.OPEN_KAN
    GameAction.KanType.CLOSED_KAN -> KanTypePersistenceDto.CLOSED_KAN
    GameAction.KanType.ADDED_KAN -> KanTypePersistenceDto.ADDED_KAN
}

/** 將已保存的槓種類還原成領域值。 */
private fun KanTypePersistenceDto.toDomain(): GameAction.KanType = when (this) {
    KanTypePersistenceDto.OPEN_KAN -> GameAction.KanType.OPEN_KAN
    KanTypePersistenceDto.CLOSED_KAN -> GameAction.KanType.CLOSED_KAN
    KanTypePersistenceDto.ADDED_KAN -> GameAction.KanType.ADDED_KAN
}
