package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryActionTypeKeys
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** [GameAction] 的完整 persistence DTO。 */
@Serializable
sealed interface GameActionPersistenceDto {
    /** [GameAction.GameStarted] 的 persistence DTO。 */
    @Serializable
    @SerialName(HistoryActionTypeKeys.GAME_STARTED)
    data object GameStarted : GameActionPersistenceDto

    /** [GameAction.RoundStarted] 的 persistence DTO。 */
    @Serializable
    @SerialName(HistoryActionTypeKeys.ROUND_STARTED)
    data object RoundStarted : GameActionPersistenceDto

    /** [GameAction.MatchEnded] 的 persistence DTO。 */
    @Serializable
    @SerialName(HistoryActionTypeKeys.MATCH_ENDED)
    data object MatchEnded : GameActionPersistenceDto

    /**
     * [GameAction.DiceRolled] 的 persistence DTO。
     *
     * @property dice 擲骰結果的點數序列。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.DICE_ROLLED)
    data class DiceRolled(val dice: List<Int>) : GameActionPersistenceDto

    /** [GameAction.Draw] 的 persistence DTO。 */
    @Serializable
    @SerialName(HistoryActionTypeKeys.DRAW)
    data object Draw : GameActionPersistenceDto

    /**
     * [GameAction.Discard] 的 persistence DTO。
     *
     * @property tileId 被捨出牌張的 UUID 字串。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.DISCARD)
    data class Discard(val tileId: String) : GameActionPersistenceDto

    /**
     * [GameAction.Chi] 的 persistence DTO。
     *
     * @property tileId 被吃牌張的 UUID 字串。
     * @property withTileIds 與被吃牌張組成副露的其他牌張 UUID 字串。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.CHI)
    data class Chi(val tileId: String, val withTileIds: List<String>) : GameActionPersistenceDto

    /**
     * [GameAction.Pon] 的 persistence DTO。
     *
     * @property tileId 被碰牌張的 UUID 字串。
     * @property withTileIds 與被碰牌張組成副露的其他牌張 UUID 字串。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.PON)
    data class Pon(val tileId: String, val withTileIds: List<String>) : GameActionPersistenceDto

    /**
     * [GameAction.Kan] 的 persistence DTO。
     *
     * @property kanType 槓的種類。
     * @property tileId 槓所使用牌張的 UUID 字串。
     * @property withTileIds 與主要牌張組成槓的其他牌張 UUID 字串。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.KAN)
    data class Kan(
        val kanType: KanTypePersistenceDto,
        val tileId: String,
        val withTileIds: List<String>,
    ) : GameActionPersistenceDto

    /**
     * [GameAction.Ron] 的 persistence DTO。
     *
     * @property tileId 被榮和牌張的 UUID 字串。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.RON)
    data class Ron(val tileId: String) : GameActionPersistenceDto

    /** [GameAction.Tsumo] 的 persistence DTO。 */
    @Serializable
    @SerialName(HistoryActionTypeKeys.TSUMO)
    data object Tsumo : GameActionPersistenceDto

    /**
     * [GameAction.Extension] 的 persistence DTO。
     *
     * @property value 擴充動作的型別鍵與 payload。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.EXTENSION)
    data class Extension(val value: TypedPersistenceDto) : GameActionPersistenceDto

    /** [GameAction.Pass] 的 persistence DTO。 */
    @Serializable
    @SerialName(HistoryActionTypeKeys.PASS)
    data object Pass : GameActionPersistenceDto

    /**
     * [GameAction.ExhaustiveDraw] 的 persistence DTO。
     *
     * @property reason 流局原因的型別鍵與 payload。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.EXHAUSTIVE_DRAW)
    data class ExhaustiveDraw(val reason: TypedPersistenceDto) : GameActionPersistenceDto
}

/** [GameAction.KanType] 的 persistence DTO。 */
@Serializable
enum class KanTypePersistenceDto { OPEN_KAN, CLOSED_KAN, ADDED_KAN }

/**
 * 將 [GameAction] 轉換成 persistence DTO。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的 persistence DTO registry。
 * @param extensionGameActionRegistry 擴充遊戲動作的 persistence DTO registry。
 * @param json 用於編解碼 registry payload 的 JSON 編解碼器。
 * @return 對應的遊戲動作 persistence DTO。
 */
fun GameAction.toPersistenceDto(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    json: Json = Json,
): GameActionPersistenceDto = when (this) {
    is GameAction.Extension -> GameActionPersistenceDto.Extension(extensionGameActionRegistry.encode(value, json))
    GameAction.GameStarted -> GameActionPersistenceDto.GameStarted
    GameAction.RoundStarted -> GameActionPersistenceDto.RoundStarted
    GameAction.MatchEnded -> GameActionPersistenceDto.MatchEnded
    is GameAction.DiceRolled -> GameActionPersistenceDto.DiceRolled(dice.values)
    GameAction.Draw -> GameActionPersistenceDto.Draw
    is GameAction.Discard -> GameActionPersistenceDto.Discard(tileId.toString())
    is GameAction.Chi -> GameActionPersistenceDto.Chi(tileId.toString(), withTiles.map(Uuid::toString))
    is GameAction.Pon -> GameActionPersistenceDto.Pon(tileId.toString(), withTiles.map(Uuid::toString))
    is GameAction.Kan -> toPersistenceDto()
    is GameAction.Ron -> GameActionPersistenceDto.Ron(tileId.toString())
    GameAction.Tsumo -> GameActionPersistenceDto.Tsumo
    GameAction.Pass -> GameActionPersistenceDto.Pass
    is GameAction.ExhaustiveDraw -> GameActionPersistenceDto.ExhaustiveDraw(
        exhaustiveDrawReasonRegistry.encode(reason, json),
    )
}

/**
 * 將 [GameActionPersistenceDto] 還原成 [GameAction]。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的 persistence DTO registry。
 * @param extensionGameActionRegistry 擴充遊戲動作的 persistence DTO registry。
 * @param json 用於編解碼 registry payload 的 JSON 編解碼器。
 * @return 對應的領域遊戲動作。
 */
fun GameActionPersistenceDto.toDomain(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    json: Json = Json,
): GameAction = when (this) {
    is GameActionPersistenceDto.Extension -> GameAction.Extension(extensionGameActionRegistry.decode(value, json))
    GameActionPersistenceDto.GameStarted -> GameAction.GameStarted
    GameActionPersistenceDto.RoundStarted -> GameAction.RoundStarted
    GameActionPersistenceDto.MatchEnded -> GameAction.MatchEnded
    is GameActionPersistenceDto.DiceRolled -> GameAction.DiceRolled(DiceRollResult.of(dice))
    GameActionPersistenceDto.Draw -> GameAction.Draw
    is GameActionPersistenceDto.Discard -> GameAction.Discard(Uuid.parse(tileId))
    is GameActionPersistenceDto.Chi -> GameAction.Chi(Uuid.parse(tileId), withTileIds.map(Uuid::parse))
    is GameActionPersistenceDto.Pon -> GameAction.Pon(Uuid.parse(tileId), withTileIds.map(Uuid::parse))
    is GameActionPersistenceDto.Kan -> toDomain()
    is GameActionPersistenceDto.Ron -> GameAction.Ron(Uuid.parse(tileId))
    GameActionPersistenceDto.Tsumo -> GameAction.Tsumo
    GameActionPersistenceDto.Pass -> GameAction.Pass
    is GameActionPersistenceDto.ExhaustiveDraw -> GameAction.ExhaustiveDraw(
        exhaustiveDrawReasonRegistry.decode(reason, json),
    )
}

/**
 * 將 [GameAction.Kan] 轉換成 persistence DTO。
 *
 * @return 對應的槓動作 persistence DTO。
 */
fun GameAction.Kan.toPersistenceDto(): GameActionPersistenceDto.Kan = GameActionPersistenceDto.Kan(
    kanType = type.toPersistenceDto(),
    tileId = tileId.toString(),
    withTileIds = withTiles.map(Uuid::toString),
)

/**
 * 將 [GameActionPersistenceDto.Kan] 還原成 [GameAction.Kan]。
 *
 * @return 對應的領域槓動作。
 */
fun GameActionPersistenceDto.Kan.toDomain(): GameAction.Kan = GameAction.Kan(
    type = kanType.toDomain(),
    tileId = Uuid.parse(tileId),
    withTiles = withTileIds.map(Uuid::parse),
)

/**
 * 將 [GameAction.KanType] 轉換成 persistence DTO。
 *
 * @return 對應的槓種類 persistence DTO。
 */
private fun GameAction.KanType.toPersistenceDto(): KanTypePersistenceDto = when (this) {
    GameAction.KanType.OPEN_KAN -> KanTypePersistenceDto.OPEN_KAN
    GameAction.KanType.CLOSED_KAN -> KanTypePersistenceDto.CLOSED_KAN
    GameAction.KanType.ADDED_KAN -> KanTypePersistenceDto.ADDED_KAN
}

/**
 * 將 [KanTypePersistenceDto] 還原成 [GameAction.KanType]。
 *
 * @return 對應的領域槓種類。
 */
private fun KanTypePersistenceDto.toDomain(): GameAction.KanType = when (this) {
    KanTypePersistenceDto.OPEN_KAN -> GameAction.KanType.OPEN_KAN
    KanTypePersistenceDto.CLOSED_KAN -> GameAction.KanType.CLOSED_KAN
    KanTypePersistenceDto.ADDED_KAN -> GameAction.KanType.ADDED_KAN
}
