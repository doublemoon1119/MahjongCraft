package com.doublemoon1119.mahjongcraft.flow.persistence.format.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceSchema
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.persistenceJson
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toAiPlayerStrategyKeys
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toEnabledAutomaticControlIdsByPlayerId
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toForcedAutoPlayPlayerIds
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toInterruptedBaseMillisByPlayerId
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toRemainingReserveMillisByPlayerId
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toRuntimeStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.room.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.room.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.Uuid

/**
 * 將 [Room] 與 [Game] 集合轉換成同批保存的權威狀態 DTO。
 *
 * @param rooms 欲保存的房間集合；房間識別碼不得重複。
 * @param games 欲保存的遊戲集合；遊戲識別碼不得重複。
 * @param ruleConfigRegistry 規則設定的持久化轉換註冊表。
 * @param discardPileRegistry 牌河的持久化轉換註冊表。
 * @param playerRuleStateRegistry 玩家規則狀態的持久化轉換註冊表。
 * @param dynamicRuleStateRegistry 動態規則狀態的持久化轉換註冊表。
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @param json 供註冊的轉換器與歷史記錄映射使用的 JSON 編解碼器。
 * @param historyRecordingState 與房間及遊戲一併保存的歷史記錄狀態。
 * @return 包含房間、桌況、流程設定、執行狀態及歷史待寫資料的權威狀態 DTO。
 */
fun createAuthoritativeStatePersistenceDto(
    rooms: Collection<Room>,
    games: Collection<Game>,
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    playerRuleStateRegistry: PersistenceDtoRegistry<PlayerRuleState>,
    dynamicRuleStateRegistry: PersistenceDtoRegistry<DynamicRuleState>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    json: Json = persistenceJson,
    historyRecordingState: HistoryRecordingState = HistoryRecordingState(),
): AuthoritativeStatePersistenceDto {
    require(rooms.map(Room::id).distinct().size == rooms.size) { "Room IDs must be unique" }
    require(games.map(Game::id).distinct().size == games.size) { "Game IDs must be unique" }
    return AuthoritativeStatePersistenceDto(
        rooms = rooms.associate { room -> room.id.toString() to room.toPersistenceDto(ruleConfigRegistry, json) },
        games = games.associate { game ->
            game.id.toString() to game.tableState.toPersistenceDto(
                ruleConfigRegistry,
                discardPileRegistry,
                playerRuleStateRegistry,
                dynamicRuleStateRegistry,
                exhaustiveDrawReasonRegistry,
                extensionGameActionRegistry,
                json,
            )
        },
        gameFlowConfigs = games.associate { game -> game.id.toString() to game.flowConfig.toPersistenceDto() },
        gameRuntimeStates = games.associate { game -> game.id.toString() to game.toRuntimeStatePersistenceDto() },
        historyRecordingState = HistoryRecordingPersistenceMapper(
            PersistenceRegistries(ruleConfigRegistry, discardPileRegistry, playerRuleStateRegistry, dynamicRuleStateRegistry, exhaustiveDrawReasonRegistry, extensionGameActionRegistry),
            json,
        ).encode(historyRecordingState),
    )
}

/**
 * 還原權威存檔中的歷史記錄狀態。
 *
 * @param registries 歷史事件所需的持久化轉換註冊表集合。
 * @param json 解碼歷史事件使用的 JSON 編解碼器。
 * @return 還原的歷史記錄狀態；無法還原的事件依歷史映射規則標記缺口。
 */
fun AuthoritativeStatePersistenceDto.toHistoryRecordingState(registries: PersistenceRegistries, json: Json = persistenceJson): HistoryRecordingState = HistoryRecordingPersistenceMapper(registries, json).decode(historyRecordingState)

/**
 * 還原權威存檔中的房間集合。
 *
 * @param ruleConfigRegistry 規則設定的持久化轉換註冊表。
 * @param json 解碼規則設定使用的 JSON 編解碼器。
 * @return 以房間識別碼為鍵的房間集合。
 */
fun AuthoritativeStatePersistenceDto.toRooms(ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>, json: Json = persistenceJson): Map<Uuid, Room> = rooms.values.associate { room -> Uuid.parse(room.id) to room.toDomain(ruleConfigRegistry, json) }

/**
 * 將保存的桌況、流程設定與執行狀態組合還原成完整 [Game]。
 *
 * @param ruleConfigRegistry 規則設定的持久化轉換註冊表。
 * @param discardPileRegistry 牌河的持久化轉換註冊表。
 * @param playerRuleStateRegistry 玩家規則狀態的持久化轉換註冊表。
 * @param dynamicRuleStateRegistry 動態規則狀態的持久化轉換註冊表。
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @param json 供註冊的轉換器解碼規則資料使用的 JSON 編解碼器。
 * @return 以遊戲識別碼為鍵、包含流程執行狀態的遊戲集合。
 */
fun AuthoritativeStatePersistenceDto.toGames(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    playerRuleStateRegistry: PersistenceDtoRegistry<PlayerRuleState>,
    dynamicRuleStateRegistry: PersistenceDtoRegistry<DynamicRuleState>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    json: Json = persistenceJson,
): Map<Uuid, Game> = games.values.associate { game ->
    val id = Uuid.parse(game.id)
    val tableState = game.toDomain(
        ruleConfigRegistry,
        discardPileRegistry,
        playerRuleStateRegistry,
        dynamicRuleStateRegistry,
        exhaustiveDrawReasonRegistry,
        extensionGameActionRegistry,
        json,
    )
    val runtimeState = gameRuntimeStates.getValue(id.toString())
    id to Game(
        tableState = tableState,
        flowConfig = gameFlowConfigs.getValue(id.toString()).toDomain(),
        remainingReserveMillisByPlayerId = runtimeState.toRemainingReserveMillisByPlayerId(),
        forcedAutoPlayPlayerIds = runtimeState.toForcedAutoPlayPlayerIds(),
        enabledAutomaticControlIdsByPlayerId = runtimeState.toEnabledAutomaticControlIdsByPlayerId(),
        automaticControlRevision = runtimeState.automaticControlRevision,
        isMatchOver = runtimeState.isMatchOver,
        pendingTransition = runtimeState.pendingTransition?.toDomain(),
        roundCompletion = runtimeState.roundCompletion?.toDomain(),
        matchEndReasonId = runtimeState.matchEndReasonId,
        pendingRoundPreparation = runtimeState.pendingRoundPreparation?.toDomain(),
        hostId = Uuid.parse(runtimeState.hostId), roomPlayerIds = runtimeState.roomPlayerIds.map(Uuid::parse),
        aiPlayerStrategyKeys = runtimeState.toAiPlayerStrategyKeys(),
        interruptedBaseMillisByPlayerId = runtimeState.toInterruptedBaseMillisByPlayerId(),
        matchId = Uuid.parse(runtimeState.matchId),
    )
}

/**
 * 以目前的 schema version 將權威狀態包裝成 [PersistenceEnvelopeDto]。
 *
 * @param json 將權威狀態編碼成 JSON 物件的編解碼器。
 * @return 帶有目前 schema version 的權威狀態封套。
 */
fun AuthoritativeStatePersistenceDto.toEnvelope(json: Json = persistenceJson): PersistenceEnvelopeDto = PersistenceEnvelopeDto(
    schemaVersion = PersistenceSchema.CURRENT_VERSION,
    state = json.encodeToJsonElement(AuthoritativeStatePersistenceDto.serializer(), this).jsonObject,
)

/**
 * 解碼使用目前 schema version 的權威狀態封套；其他版本由遷移邊界處理。
 *
 * @param json 解碼封套內權威狀態的 JSON 編解碼器。
 * @return 解碼後的權威狀態 DTO；版本不符或內容無效時拋出例外。
 */
fun PersistenceEnvelopeDto.decodeCurrentAuthoritativeState(json: Json = persistenceJson): AuthoritativeStatePersistenceDto {
    require(schemaVersion == PersistenceSchema.CURRENT_VERSION) {
        "Persistence envelope must use current schema version ${PersistenceSchema.CURRENT_VERSION}"
    }
    return json.decodeFromJsonElement(AuthoritativeStatePersistenceDto.serializer(), state)
}
