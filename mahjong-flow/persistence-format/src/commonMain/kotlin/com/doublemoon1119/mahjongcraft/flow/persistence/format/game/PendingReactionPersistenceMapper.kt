package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * 將 [PendingReaction] 轉換成 persistence DTO。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @param json 供註冊的轉換器編解碼擴充資料使用的 JSON 編解碼器。
 * @return 包含原模型資料的持久化 DTO。
 */
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

/**
 * 將 [PendingReactionPersistenceDto] 還原成 [PendingReaction]。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @param json 供註冊的轉換器編解碼擴充資料使用的 JSON 編解碼器。
 * @return 還原的領域模型。
 */
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

/**
 * 將 [PendingRobbingReaction] 轉換成 persistence DTO。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @param json 供註冊的轉換器編解碼擴充資料使用的 JSON 編解碼器。
 * @return 包含原模型資料的持久化 DTO。
 */
fun PendingRobbingReaction.toPersistenceDto(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    json: Json = Json,
): PendingRobbingReactionPersistenceDto = PendingRobbingReactionPersistenceDto(
    declarerId = declarerId.toString(),
    declaredAction = declaredAction.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, json),
    robbedTile = robbedTile.toPersistenceDto(),
    eligiblePlayerIds = eligiblePlayerIds.map(Uuid::toString).toSet(),
    responses = responses.mapKeys { it.key.toString() }.mapValues {
        it.value.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, json)
    },
)

/**
 * 將 [PendingRobbingReactionPersistenceDto] 還原成 [PendingRobbingReaction]。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @param json 供註冊的轉換器編解碼擴充資料使用的 JSON 編解碼器。
 * @return 還原的領域模型。
 */
fun PendingRobbingReactionPersistenceDto.toDomain(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    json: Json = Json,
): PendingRobbingReaction = PendingRobbingReaction(
    declarerId = Uuid.parse(declarerId),
    declaredAction = declaredAction.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, json),
    robbedTile = robbedTile.toDomain(),
    eligiblePlayerIds = eligiblePlayerIds.map(Uuid::parse).toSet(),
    responses = responses.mapKeys { Uuid.parse(it.key) }.mapValues {
        it.value.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, json)
    },
)
