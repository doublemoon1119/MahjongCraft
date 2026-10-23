package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import kotlin.uuid.Uuid

/**
 * 將 [PendingReaction] 轉換成 persistence DTO。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @return 包含原模型資料的持久化 DTO。
 */
fun PendingReaction.toPersistenceDto(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
): PendingReactionPersistenceDto = PendingReactionPersistenceDto(
    id = id.toString(),
    selectedTileIds = selectedTileIds.mapKeys { it.key.toString() }.mapValues { (_, ids) -> ids.map(Uuid::toString) },
    discarderId = discarderId.toString(),
    tileId = tileId.toString(),
    eligiblePlayerIds = eligiblePlayerIds.map(Uuid::toString).toSet(),
    ronPlayerIds = ronPlayerIds.map(Uuid::toString).toSet(),
    responses = responses.mapKeys { it.key.toString() }.mapValues {
        it.value.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry)
    },
)

/**
 * 將 [PendingReactionPersistenceDto] 還原成 [PendingReaction]。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @return 還原的領域模型。
 */
fun PendingReactionPersistenceDto.toDomain(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
): PendingReaction = PendingReaction(
    id = Uuid.parse(id),
    selectedTileIds = selectedTileIds.mapKeys { Uuid.parse(it.key) }.mapValues { (_, ids) -> ids.map(Uuid::parse) },
    discarderId = Uuid.parse(discarderId),
    tileId = Uuid.parse(tileId),
    eligiblePlayerIds = eligiblePlayerIds.map(Uuid::parse).toSet(),
    ronPlayerIds = ronPlayerIds.map(Uuid::parse).toSet(),
    responses = responses.mapKeys { Uuid.parse(it.key) }.mapValues {
        it.value.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry)
    },
)

/**
 * 將 [PendingRobbingReaction] 轉換成 persistence DTO。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @return 包含原模型資料的持久化 DTO。
 */
fun PendingRobbingReaction.toPersistenceDto(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
): PendingRobbingReactionPersistenceDto = PendingRobbingReactionPersistenceDto(
    declarerId = declarerId.toString(),
    declaredAction = declaredAction.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry),
    robbedTile = robbedTile.toPersistenceDto(),
    eligiblePlayerIds = eligiblePlayerIds.map(Uuid::toString).toSet(),
    responses = responses.mapKeys { it.key.toString() }.mapValues {
        it.value.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry)
    },
    pendingFollowUpDraws = pendingFollowUpDraws.map { it.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry) },
)

/**
 * 將 [PendingRobbingReactionPersistenceDto] 還原成 [PendingRobbingReaction]。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @return 還原的領域模型。
 */
fun PendingRobbingReactionPersistenceDto.toDomain(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
): PendingRobbingReaction = PendingRobbingReaction(
    declarerId = Uuid.parse(declarerId),
    declaredAction = declaredAction.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry),
    robbedTile = robbedTile.toDomain(),
    eligiblePlayerIds = eligiblePlayerIds.map(Uuid::parse).toSet(),
    responses = responses.mapKeys { Uuid.parse(it.key) }.mapValues {
        it.value.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry)
    },
    pendingFollowUpDraws = pendingFollowUpDraws.map { it.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry) },
)
