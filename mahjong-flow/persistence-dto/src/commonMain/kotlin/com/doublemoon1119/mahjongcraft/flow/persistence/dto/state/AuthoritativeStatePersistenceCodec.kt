package com.doublemoon1119.mahjongcraft.flow.persistence.dto.state

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.migration.PersistenceMigrationRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.migration.buildBuiltInPersistenceMigrationRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * 解碼並驗證後的伺服器權威狀態。
 *
 * @property rooms 以桌子 UUID 索引的等待階段狀態。
 * @property games 以桌子 UUID 索引的進行中狀態。
 */
data class DecodedAuthoritativeState(
    val rooms: Map<Uuid, Room>,
    val games: Map<Uuid, Game>,
)

/**
 * 在 Minecraft API 之外負責權威狀態 JSON、schema migration 與 DTO mapping 的 codec。
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
        )
    }
}
