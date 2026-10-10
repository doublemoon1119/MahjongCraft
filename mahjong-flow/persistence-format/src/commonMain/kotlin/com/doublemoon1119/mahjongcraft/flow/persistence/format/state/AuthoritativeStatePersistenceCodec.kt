package com.doublemoon1119.mahjongcraft.flow.persistence.format.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.migration.PersistenceMigrationRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.migration.buildBuiltInPersistenceMigrationRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import kotlinx.serialization.json.Json

/**
 * 不依賴任何平台 API、負責權威狀態 JSON、schema migration 與 DTO mapping 的 codec。
 *
 * 版本／loader adapter 只需保存 [encode] 回傳的字串，並將 [decode] 結果載入共用狀態儲存。
 *
 * @property registries 所有可擴充領域型別的 persistence registry。
 * @property migrationRegistry schema migration registry。
 * @property json envelope 與 DTO 使用的 JSON 編解碼器。
 */
class AuthoritativeStatePersistenceCodec(
    private val registries: PersistenceRegistries,
    private val migrationRegistry: PersistenceMigrationRegistry = buildBuiltInPersistenceMigrationRegistry(),
    private val json: Json = Json,
) {
    /** 將完整 Room／Game 狀態編碼成帶目前 schema version 的 JSON 字串。 */
    fun encode(
        rooms: Collection<Room>,
        games: Collection<Game>,
        historyRecordingState: HistoryRecordingState = HistoryRecordingState(),
    ): String {
        val state = createAuthoritativeStatePersistenceDto(
            rooms = rooms,
            games = games,
            ruleConfigRegistry = registries.ruleConfigs,
            discardPileRegistry = registries.discardPiles,
            playerRuleStateRegistry = registries.playerRuleStates,
            dynamicRuleStateRegistry = registries.dynamicRuleStates,
            exhaustiveDrawReasonRegistry = registries.exhaustiveDrawReasons,
            extensionGameActionRegistry = registries.extensionGameActions,
            historyRecordingState = historyRecordingState,
            json = json,
        )
        return json.encodeToString(PersistenceEnvelopeDto.serializer(), state.toEnvelope(json))
    }

    /** 將 JSON 字串 migration 至目前 schema，驗證後還原完整 Room／Game 狀態。 */
    fun decode(encoded: String): DecodedAuthoritativeState {
        val envelope = json.decodeFromString(PersistenceEnvelopeDto.serializer(), encoded)
        val currentEnvelope = migrationRegistry.migrate(envelope)
        val state = currentEnvelope.decodeCurrentAuthoritativeState(json)
        return DecodedAuthoritativeState(
            rooms = state.toRooms(registries.ruleConfigs, json),
            games = state.toGames(
                ruleConfigRegistry = registries.ruleConfigs,
                discardPileRegistry = registries.discardPiles,
                playerRuleStateRegistry = registries.playerRuleStates,
                dynamicRuleStateRegistry = registries.dynamicRuleStates,
                exhaustiveDrawReasonRegistry = registries.exhaustiveDrawReasons,
                extensionGameActionRegistry = registries.extensionGameActions,
                json = json,
            ),
            historyRecordingState = state.toHistoryRecordingState(registries, json),
        )
    }
}
