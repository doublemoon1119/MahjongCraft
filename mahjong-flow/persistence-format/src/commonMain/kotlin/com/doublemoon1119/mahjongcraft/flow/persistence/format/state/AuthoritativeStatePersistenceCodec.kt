package com.doublemoon1119.mahjongcraft.flow.persistence.format.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.persistenceJson
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.SkippedHistoryEventReporter
import com.doublemoon1119.mahjongcraft.flow.persistence.format.migration.PersistenceMigrationRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.migration.buildBuiltInPersistenceMigrationRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.component.AuthoritativeComponentValidator

/**
 * 權威狀態的 JSON 編解碼、版本遷移與 DTO 對應，不依賴平台 API。
 *
 * - 各版本、各載入器的轉接層只需保存 [encode] 回傳的字串。
 * - 讀取時把 [decode] 的結果載入共用狀態儲存。
 *
 * @property registries 可擴充領域型別的持久化註冊表集合。
 * @property componentValidator 與正式寫入共用的完整組件驗證，不補建缺少的值。
 * @property skippedHistoryEventReporter 接收保存或讀取時因無法轉換而略過的待寫歷史事件。
 * @property migrationRegistry schema 版本遷移註冊表。
 */
class AuthoritativeStatePersistenceCodec(
    private val registries: PersistenceRegistries,
    private val componentValidator: AuthoritativeComponentValidator,
    private val skippedHistoryEventReporter: SkippedHistoryEventReporter,
    private val migrationRegistry: PersistenceMigrationRegistry = buildBuiltInPersistenceMigrationRegistry(),
) {
    /**
     * 將完整 Room／Game 狀態編碼成帶目前 schema version 的 JSON 字串。
     *
     * @param rooms 要保存的 Room 狀態。
     * @param games 要保存的 Game 狀態。
     * @param historyRecordingState 要保存的歷史記錄狀態，預設為空狀態。
     * @return 編碼後的權威狀態 JSON 字串。
     */
    fun encode(
        rooms: Collection<Room>,
        games: Collection<Game>,
        historyRecordingState: HistoryRecordingState = HistoryRecordingState(),
    ): String {
        games.forEach { componentValidator.validate(it.tableState) }
        val state = createAuthoritativeStatePersistenceDto(
            rooms = rooms,
            games = games,
            registries = registries,
            historyRecordingState = historyRecordingState,
            skippedHistoryEventReporter = skippedHistoryEventReporter,
        )
        return persistenceJson.encodeToString(PersistenceEnvelopeDto.serializer(), state.toEnvelope())
    }

    /**
     * 將 JSON 字串 migration 至目前 schema，驗證後還原完整 Room／Game 狀態。
     *
     * @param encoded 權威狀態 JSON 字串。
     * @return 驗證並還原的 Room、Game 與歷史記錄狀態。
     */
    fun decode(encoded: String): DecodedAuthoritativeState {
        val envelope = persistenceJson.decodeFromString(PersistenceEnvelopeDto.serializer(), encoded)
        val currentEnvelope = migrationRegistry.migrate(envelope)
        val state = currentEnvelope.decodeCurrentAuthoritativeState()
        return DecodedAuthoritativeState(
            rooms = state.toRooms(registries.ruleConfigs),
            games = state.toGames(
                ruleConfigRegistry = registries.ruleConfigs,
                discardPileRegistry = registries.discardPiles,
                exhaustiveDrawReasonRegistry = registries.exhaustiveDrawReasons,
                extensionGameActionRegistry = registries.extensionGameActions,
                componentRegistry = registries.components,
            ).also { games -> games.values.forEach { componentValidator.validate(it.tableState) } },
            historyRecordingState = state.toHistoryRecordingState(registries, skippedHistoryEventReporter),
        )
    }
}
