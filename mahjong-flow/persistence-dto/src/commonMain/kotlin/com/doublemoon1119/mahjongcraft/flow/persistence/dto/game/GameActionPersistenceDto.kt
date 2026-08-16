package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** [GameAction] 的完整 persistence DTO。 */
@Serializable
sealed interface GameActionPersistenceDto {
    /** [GameAction.GameStarted] 的 persistence DTO。 */
    @Serializable
    data object GameStarted : GameActionPersistenceDto

    /** [GameAction.RoundStarted] 的 persistence DTO。 */
    @Serializable
    data object RoundStarted : GameActionPersistenceDto

    /** [GameAction.MatchEnded] 的 persistence DTO。 */
    @Serializable
    data object MatchEnded : GameActionPersistenceDto

    /** [GameAction.Draw] 的 persistence DTO。 */
    @Serializable
    data object Draw : GameActionPersistenceDto

    /** [GameAction.Discard] 的 persistence DTO。 */
    @Serializable
    data class Discard(val tileId: String) : GameActionPersistenceDto

    /** [GameAction.Chi] 的 persistence DTO。 */
    @Serializable
    data class Chi(val tileId: String, val withTileIds: List<String>) : GameActionPersistenceDto

    /** [GameAction.Pon] 的 persistence DTO。 */
    @Serializable
    data class Pon(val tileId: String) : GameActionPersistenceDto

    /** [GameAction.Kan] 的 persistence DTO。 */
    @Serializable
    data class Kan(
        val kanType: KanTypePersistenceDto,
        val tileId: String,
        val withTileIds: List<String>,
    ) : GameActionPersistenceDto

    /** [GameAction.Ron] 的 persistence DTO。 */
    @Serializable
    data class Ron(val tileId: String) : GameActionPersistenceDto

    /** [GameAction.Tsumo] 的 persistence DTO。 */
    @Serializable
    data object Tsumo : GameActionPersistenceDto

    /** [GameAction.Riichi] 的 persistence DTO。 */
    @Serializable
    data object Riichi : GameActionPersistenceDto

    /** [GameAction.Pass] 的 persistence DTO。 */
    @Serializable
    data object Pass : GameActionPersistenceDto

    /** [GameAction.ExhaustiveDraw] 的 persistence DTO。 */
    @Serializable
    data class ExhaustiveDraw(val reason: TypedPersistenceDto) : GameActionPersistenceDto
}

/** [GameAction.KanType] 的 persistence DTO。 */
@Serializable
enum class KanTypePersistenceDto { OPEN_KAN, CLOSED_KAN, ADDED_KAN }

/** 將 [GameAction] 轉換成 persistence DTO。 */
fun GameAction.toPersistenceDto(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    json: Json = Json,
): GameActionPersistenceDto = when (this) {
    GameAction.GameStarted -> GameActionPersistenceDto.GameStarted
    GameAction.RoundStarted -> GameActionPersistenceDto.RoundStarted
    GameAction.MatchEnded -> GameActionPersistenceDto.MatchEnded
    GameAction.Draw -> GameActionPersistenceDto.Draw
    is GameAction.Discard -> GameActionPersistenceDto.Discard(tileId.toString())
    is GameAction.Chi -> GameActionPersistenceDto.Chi(tileId.toString(), withTiles.map(Uuid::toString))
    is GameAction.Pon -> GameActionPersistenceDto.Pon(tileId.toString())
    is GameAction.Kan -> toPersistenceDto()
    is GameAction.Ron -> GameActionPersistenceDto.Ron(tileId.toString())
    GameAction.Tsumo -> GameActionPersistenceDto.Tsumo
    GameAction.Riichi -> GameActionPersistenceDto.Riichi
    GameAction.Pass -> GameActionPersistenceDto.Pass
    is GameAction.ExhaustiveDraw -> GameActionPersistenceDto.ExhaustiveDraw(
        exhaustiveDrawReasonRegistry.encode(reason, json),
    )
}

/** 將 [GameActionPersistenceDto] 還原成 [GameAction]。 */
fun GameActionPersistenceDto.toDomain(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    json: Json = Json,
): GameAction = when (this) {
    GameActionPersistenceDto.GameStarted -> GameAction.GameStarted
    GameActionPersistenceDto.RoundStarted -> GameAction.RoundStarted
    GameActionPersistenceDto.MatchEnded -> GameAction.MatchEnded
    GameActionPersistenceDto.Draw -> GameAction.Draw
    is GameActionPersistenceDto.Discard -> GameAction.Discard(Uuid.parse(tileId))
    is GameActionPersistenceDto.Chi -> GameAction.Chi(Uuid.parse(tileId), withTileIds.map(Uuid::parse))
    is GameActionPersistenceDto.Pon -> GameAction.Pon(Uuid.parse(tileId))
    is GameActionPersistenceDto.Kan -> toDomain()
    is GameActionPersistenceDto.Ron -> GameAction.Ron(Uuid.parse(tileId))
    GameActionPersistenceDto.Tsumo -> GameAction.Tsumo
    GameActionPersistenceDto.Riichi -> GameAction.Riichi
    GameActionPersistenceDto.Pass -> GameAction.Pass
    is GameActionPersistenceDto.ExhaustiveDraw -> GameAction.ExhaustiveDraw(
        exhaustiveDrawReasonRegistry.decode(reason, json),
    )
}

/** 將 [GameAction.Kan] 轉換成 persistence DTO。 */
fun GameAction.Kan.toPersistenceDto(): GameActionPersistenceDto.Kan = GameActionPersistenceDto.Kan(
    kanType = type.toPersistenceDto(),
    tileId = tileId.toString(),
    withTileIds = withTiles.map(Uuid::toString),
)

/** 將 [GameActionPersistenceDto.Kan] 還原成 [GameAction.Kan]。 */
fun GameActionPersistenceDto.Kan.toDomain(): GameAction.Kan = GameAction.Kan(
    type = kanType.toDomain(),
    tileId = Uuid.parse(tileId),
    withTiles = withTileIds.map(Uuid::parse),
)

/** 將 [GameAction.KanType] 轉換成 persistence DTO。 */
private fun GameAction.KanType.toPersistenceDto(): KanTypePersistenceDto = when (this) {
    GameAction.KanType.OPEN_KAN -> KanTypePersistenceDto.OPEN_KAN
    GameAction.KanType.CLOSED_KAN -> KanTypePersistenceDto.CLOSED_KAN
    GameAction.KanType.ADDED_KAN -> KanTypePersistenceDto.ADDED_KAN
}

/** 將 [KanTypePersistenceDto] 還原成 [GameAction.KanType]。 */
private fun KanTypePersistenceDto.toDomain(): GameAction.KanType = when (this) {
    KanTypePersistenceDto.OPEN_KAN -> GameAction.KanType.OPEN_KAN
    KanTypePersistenceDto.CLOSED_KAN -> GameAction.KanType.CLOSED_KAN
    KanTypePersistenceDto.ADDED_KAN -> GameAction.KanType.ADDED_KAN
}
