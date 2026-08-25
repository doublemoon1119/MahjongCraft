package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.table.PendingKanReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** [PendingReaction] 的完整 persistence DTO。 */
@Serializable
data class PendingReactionPersistenceDto(
    val discarderId: String,
    val tileId: String,
    val eligiblePlayerIds: Set<String>,
    val responses: Map<String, GameActionPersistenceDto>,
)

/** [PendingKanReaction] 的完整 persistence DTO。 */
@Serializable
data class PendingKanReactionPersistenceDto(
    val declarerId: String,
    val kanAction: GameActionPersistenceDto.Kan,
    val robbedTile: IdentifiedTilePersistenceDto,
    val eligiblePlayerIds: Set<String>,
    val responses: Map<String, GameActionPersistenceDto>,
)

/** 將 [PendingReaction] 轉換成 persistence DTO。 */
fun PendingReaction.toPersistenceDto(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    json: Json = Json,
): PendingReactionPersistenceDto = PendingReactionPersistenceDto(
    discarderId = discarderId.toString(),
    tileId = tileId.toString(),
    eligiblePlayerIds = eligiblePlayerIds.map(Uuid::toString).toSet(),
    responses = responses.mapKeys { it.key.toString() }.mapValues {
        it.value.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, json)
    },
)

/** 將 [PendingReactionPersistenceDto] 還原成 [PendingReaction]。 */
fun PendingReactionPersistenceDto.toDomain(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    json: Json = Json,
): PendingReaction = PendingReaction(
    discarderId = Uuid.parse(discarderId),
    tileId = Uuid.parse(tileId),
    eligiblePlayerIds = eligiblePlayerIds.map(Uuid::parse).toSet(),
    responses = responses.mapKeys { Uuid.parse(it.key) }.mapValues {
        it.value.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, json)
    },
)

/** 將 [PendingKanReaction] 轉換成 persistence DTO。 */
fun PendingKanReaction.toPersistenceDto(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    json: Json = Json,
): PendingKanReactionPersistenceDto = PendingKanReactionPersistenceDto(
    declarerId = declarerId.toString(),
    kanAction = kanAction.toPersistenceDto(),
    robbedTile = robbedTile.toPersistenceDto(),
    eligiblePlayerIds = eligiblePlayerIds.map(Uuid::toString).toSet(),
    responses = responses.mapKeys { it.key.toString() }.mapValues {
        it.value.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, json)
    },
)

/** 將 [PendingKanReactionPersistenceDto] 還原成 [PendingKanReaction]。 */
fun PendingKanReactionPersistenceDto.toDomain(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    json: Json = Json,
): PendingKanReaction = PendingKanReaction(
    declarerId = Uuid.parse(declarerId),
    kanAction = kanAction.toDomain(),
    robbedTile = robbedTile.toDomain(),
    eligiblePlayerIds = eligiblePlayerIds.map(Uuid::parse).toSet(),
    responses = responses.mapKeys { Uuid.parse(it.key) }.mapValues {
        it.value.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, json)
    },
)
