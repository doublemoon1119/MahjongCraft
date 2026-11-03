package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomAdmissionState
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.STANDARD_TILE_ASSET_KEYS
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證角色選擇、開局凍結、歷史保留與公開同步。 */
class AiCharacterStateStoreTest {
    /** 具有穩定玩家與規則資料的測試桌況。 */
    private val table = FakeTableStateFactory.create()

    /** 測試中的真人房主。 */
    private val host = table.players[0].id

    /** 測試中的 AI。 */
    private val bot = table.players[1].id

    /** 等待中的房間。 */
    private val room = Room(table.id, host, GameConfig(table.config), table.players.map { it.id }, listOf(bot), mapOf(bot to "example:strategy"))

    /** 可供房主選擇的另一角色。 */
    private val alternate = AiCharacterDefinition("example:alternate", "example.character.alternate", AiCharacterFace.Tile("east"), null, null)

    /** 原生能力不參與描述測試。 */
    private val registry = AiCharacterRegistryImpl().apply {
        register(BuiltInAiCharacters.TILE_MASCOT)
        register(alternate)
        freeze()
    }

    /**
     * 建立已載入空世界、並收到房間更新的角色 store。
     *
     * @return 可接受角色選擇的測試 store。
     */
    private fun store(): AiCharacterStateStore = AiCharacterStateStore(registry, {}).apply {
        restore(AuthoritativeStateSnapshot(), AiCharacterMetadata(emptyMap(), emptyMap()))
        update(AuthoritativeStateSnapshot(rooms = mapOf(room.id to room)))
    }

    /** 排除後保留辨識資料，只有房主可讀；解除後清理，不公開模型能力。 */
    @Test
    fun `excluded appearance survives saving and is visible only to the host`() {
        val state = store()
        val identity = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, Uuid.random())
        val waiting = room.copy(admission = RoomAdmissionState(memberIdentities = mapOf(bot to identity)))
        val appearance = AiCharacterSnapshot(alternate.id, alternate, "Actor")
        state.prepareParticipant(AiCharacterParticipantCapture(waiting.admission.instanceId, bot, identity, appearance))
        state.update(AuthoritativeStateSnapshot(rooms = mapOf(waiting.id to waiting)))
        state.releaseParticipant(bot)
        val excluded = waiting.copy(playerIds = listOf(host), aiPlayerStrategyKeys = emptyMap(), admission = waiting.admission.exclude(bot))
        state.update(AuthoritativeStateSnapshot(rooms = mapOf(excluded.id to excluded)))
        val metadata = AiCharacterMetadataCodec().decode(AiCharacterMetadataCodec().encode(state.snapshot().characters!!))
        val restored = AiCharacterStateStore(registry, {})
        restored.restore(state.snapshot().authoritative, metadata)
        val projected = restored.snapshot().excludedAppearancesFor(excluded.id, host)
        assertEquals("Actor", projected[bot]?.displayName)
        assertNull(projected[bot]?.definition?.preview)
        assertNull(projected[bot]?.definition?.worldCapabilityId)
        assertTrue(restored.snapshot().excludedAppearancesFor(excluded.id, Uuid.random()).isEmpty())
        val cleared = excluded.copy(admission = excluded.admission.copy(excludedParticipants = emptyMap()))
        restored.update(AuthoritativeStateSnapshot(rooms = mapOf(cleared.id to cleared)))
        assertNull(restored.snapshot().characters!!.participant(bot))
    }

    /** 外部描述只隨成功成員提交保存，開局後不再讀登記預設。 */
    @Test
    fun `external participant capture requires matching room and identity and survives restart`() {
        val store = store()
        val identity = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, Uuid.random())
        val waiting = room.copy(admission = RoomAdmissionState(memberIdentities = mapOf(bot to identity)))
        val custom = AiCharacterDefinition(alternate.id, alternate.nameKey, AiCharacterFace.Tile("south"), null, null)
        val capture = AiCharacterParticipantCapture(waiting.admission.instanceId, bot, identity, AiCharacterSnapshot(custom.id, custom))
        store.prepareParticipant(capture)
        assertTrue(store.snapshot().characters!!.participants.isEmpty())
        store.update(AuthoritativeStateSnapshot(rooms = mapOf(waiting.id to waiting)))
        store.releaseParticipant(bot)
        assertEquals(custom, store.syncSnapshot(waiting.id).players[bot]?.definition)
        val game = Game(table, GameFlowConfig(), aiPlayerStrategyKeys = waiting.aiPlayerStrategyKeys, roomAdmission = waiting.admission)
        store.update(AuthoritativeStateSnapshot(games = mapOf(game.id to game)))
        val metadata = AiCharacterMetadataCodec().decode(AiCharacterMetadataCodec().encode(store.snapshot().characters!!))
        val restored = AiCharacterStateStore(registry, {})
        restored.restore(store.snapshot().authoritative, metadata)
        assertEquals(custom, restored.snapshot().characters!!.matches[game.matchId]?.get(bot)?.definition)
        assertEquals(identity, restored.snapshot().authoritative.games[game.id]?.roomAdmission?.memberIdentities?.get(bot))
    }

    /** 等待時可刷新造型與普通名稱，場次一旦建立就不隨後續刷新改變。 */
    @Test
    fun `waiting capture refreshes without roster changes and match stays frozen`() {
        val store = store()
        val identity = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, Uuid.random())
        val waiting = room.copy(admission = RoomAdmissionState(memberIdentities = mapOf(bot to identity)))
        val first = AiCharacterSnapshot(alternate.id, alternate, "First")
        val second = first.copy(displayName = "Second")
        val capture = AiCharacterParticipantCapture(waiting.admission.instanceId, bot, identity, first)
        store.prepareParticipant(capture)
        val authoritative = AuthoritativeStateSnapshot(rooms = mapOf(waiting.id to waiting))
        store.update(authoritative)
        store.releaseParticipant(bot)
        store.prepareParticipant(capture.copy(appearance = second))
        store.update(authoritative)
        store.releaseParticipant(bot)
        assertEquals(second, store.syncSnapshot(waiting.id).players[bot])
        val game = Game(table, GameFlowConfig(), aiPlayerStrategyKeys = waiting.aiPlayerStrategyKeys, roomAdmission = waiting.admission)
        store.update(AuthoritativeStateSnapshot(games = mapOf(game.id to game)))
        store.prepareParticipant(capture)
        store.update(store.snapshot().authoritative)
        store.releaseParticipant(bot)
        assertEquals(second, store.snapshot().characters!!.matchPlayers(game.matchId)[bot])
    }

    /** 解散重建不能套用舊房間預檢的描述。 */
    @Test
    fun `staged appearance cannot cross room lifetime or failed admission`() {
        val store = store()
        val identity = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, Uuid.random())
        val capture = AiCharacterParticipantCapture(room.admission.instanceId, bot, identity, AiCharacterSnapshot(alternate.id, alternate))
        store.prepareParticipant(capture)
        val other = room.copy(admission = RoomAdmissionState(memberIdentities = mapOf(bot to identity)))
        store.update(AuthoritativeStateSnapshot(rooms = mapOf(other.id to other)))
        store.releaseParticipant(bot)
        assertTrue(store.snapshot().characters!!.participants.isEmpty())
    }

    /** 開局快照不隨後來的角色、策略或回房間改變，歷史確認後才回收。 */
    @Test
    fun `match appearance is frozen retained for outbox and released after acknowledgement`() {
        val store = store()
        assertEquals(AiCharacterSelectionResult.ACCEPTED, store.select(store.snapshot().authoritative, room.id, bot, host, alternate.id))
        val game = Game(table, GameFlowConfig(), aiPlayerStrategyKeys = room.aiPlayerStrategyKeys)
        store.update(AuthoritativeStateSnapshot(games = mapOf(game.id to game)))
        assertEquals(alternate, store.snapshot().characters?.matches?.get(game.matchId)?.get(bot)?.definition)
        assertEquals(AiCharacterSelectionResult.ROOM_NOT_FOUND, store.select(store.snapshot().authoritative, room.id, bot, host, BuiltInAiCharacters.TILE_MASCOT_ID))
        val history = HistoryRecordingState(nextSequenceByMatchId = mapOf(game.matchId to 2L))
        store.update(AuthoritativeStateSnapshot(rooms = mapOf(room.id to room.copy(aiPlayerStrategyKeys = mapOf(bot to "example:new_strategy"))), historyRecordingState = history))
        assertEquals(AiCharacterSelectionResult.ACCEPTED, store.select(store.snapshot().authoritative, room.id, bot, host, BuiltInAiCharacters.TILE_MASCOT_ID))
        assertEquals(alternate, store.snapshot().characters?.matches?.get(game.matchId)?.get(bot)?.definition)
        val next = game.copy(matchId = Uuid.random())
        store.update(AuthoritativeStateSnapshot(games = mapOf(next.id to next), historyRecordingState = history))
        val newAppearance = store.snapshot().characters?.matches?.get(next.matchId)?.get(bot)?.definition
        assertEquals(BuiltInAiCharacters.TILE_MASCOT_ID, newAppearance?.id)
        assertTrue((newAppearance?.face as? AiCharacterFace.Tile)?.assetKey in STANDARD_TILE_ASSET_KEYS)
        assertEquals(alternate, store.snapshot().characters?.matches?.get(game.matchId)?.get(bot)?.definition)
        store.update(AuthoritativeStateSnapshot())
        assertTrue(store.snapshot().characters!!.matches.isEmpty())
        assertTrue(store.snapshot().characters!!.selections.isEmpty())
    }

    /** 權限、成員與登記檢查拒絕修改，正常成員不被當成 AI。 */
    @Test
    fun `unauthorized unknown and non AI choices leave state unchanged`() {
        val store = store()
        val before = store.snapshot()
        assertEquals(AiCharacterSelectionResult.NOT_HOST, store.select(before.authoritative, room.id, bot, Uuid.random(), alternate.id))
        assertEquals(AiCharacterSelectionResult.NOT_AI_MEMBER, store.select(before.authoritative, room.id, host, host, alternate.id))
        assertEquals(AiCharacterSelectionResult.UNKNOWN_CHARACTER, store.select(before.authoritative, room.id, bot, host, "example:missing"))
        assertEquals(before, store.snapshot())
    }

    /** 讀回的描述不被當前 provider 覆蓋，未知選擇也不被改成預設值。 */
    @Test
    fun `restoration preserves saved faces and missing provider choices`() {
        val store = store()
        val game = Game(table, GameFlowConfig(), aiPlayerStrategyKeys = room.aiPlayerStrategyKeys)
        val saved = AiCharacterMetadata(
            mapOf(room.id to mapOf(bot to alternate.id)),
            mapOf(game.matchId to mapOf(bot to AiCharacterSnapshot(alternate.id, alternate))),
        )
        val changedRegistry = AiCharacterRegistryImpl().apply {
            register(BuiltInAiCharacters.TILE_MASCOT)
            freeze()
        }
        val restored = AiCharacterStateStore(changedRegistry, {})
        restored.restore(AuthoritativeStateSnapshot(games = mapOf(game.id to game)), AiCharacterMetadataCodec().decode(AiCharacterMetadataCodec().encode(saved)))
        restored.update(restored.snapshot().authoritative)
        assertEquals(alternate, restored.snapshot().characters?.matches?.get(game.matchId)?.get(bot)?.definition)
        restored.update(AuthoritativeStateSnapshot(rooms = mapOf(room.id to room)))
        assertEquals(alternate.id, restored.snapshot().characters?.selections?.get(room.id)?.get(bot))
        assertNull(restored.syncSnapshot(room.id).players[bot]?.definition)
        assertNotEquals(store.snapshot().sessionId, restored.snapshot().sessionId)
    }

    /** 一般牌局更新不重建所有桌子的角色集合，避免多桌每次摸打都複製全量角色資料。 */
    @Test
    fun `ordinary game updates reuse immutable character metadata`() {
        val store = store()
        val game = Game(table, GameFlowConfig(), aiPlayerStrategyKeys = room.aiPlayerStrategyKeys)
        store.update(AuthoritativeStateSnapshot(games = mapOf(game.id to game)))
        val before = store.snapshot().characters
        store.update(AuthoritativeStateSnapshot(games = mapOf(game.id to game.copy(forcedAutoPlayPlayerIds = setOf(bot)))))
        assertSame(before, store.snapshot().characters)
    }

    /** 開局已選定的牌面在桌況更新及讀檔後都保持相同。 */
    @Test
    fun `chosen mascot face is saved once and remains stable across updates and reload`() {
        val store = store()
        val game = Game(table, GameFlowConfig(), aiPlayerStrategyKeys = room.aiPlayerStrategyKeys)
        store.update(AuthoritativeStateSnapshot(games = mapOf(game.id to game)))
        val captured = store.snapshot().characters!!.matches[game.matchId]!![bot]!!
        assertTrue((captured.definition?.face as? AiCharacterFace.Tile)?.assetKey in STANDARD_TILE_ASSET_KEYS)
        val updated = game.copy(tableState = table.copy(comboCount = table.comboCount + 1))
        val authoritative = AuthoritativeStateSnapshot(games = mapOf(updated.id to updated))
        store.update(authoritative)
        assertEquals(captured, store.syncSnapshot(game.id).players[bot])
        val restored = AiCharacterStateStore(registry, {})
        restored.restore(authoritative, AiCharacterMetadataCodec().decode(AiCharacterMetadataCodec().encode(store.snapshot().characters!!)))
        restored.update(authoritative)
        assertEquals(captured, restored.syncSnapshot(game.id).players[bot])
    }

    /** 不可用的角色區段保持不可用，牌局仍能更新，沒有偽造選擇。 */
    @Test
    fun `unavailable metadata blocks choices but not authoritative updates`() {
        val store = AiCharacterStateStore(registry, {})
        store.restore(AuthoritativeStateSnapshot(), null)
        val snapshot = AuthoritativeStateSnapshot(rooms = mapOf(room.id to room))
        store.update(snapshot)
        assertEquals(snapshot, store.snapshot().authoritative)
        assertEquals(AiCharacterSelectionResult.DATA_UNAVAILABLE, store.select(snapshot, room.id, bot, host, alternate.id))
        assertFalse(store.syncSnapshot(room.id).isAvailable)
        assertTrue(store.syncSnapshot(room.id).players.isEmpty())
    }

    /** 外部角色的外觀跟隨原實體，不讓房主把它改成另一個生成角色。 */
    @Test
    fun `external participant appearance cannot be selected in the room`() {
        val store = store()
        val identity = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, Uuid.random())
        val waiting = room.copy(admission = RoomAdmissionState(memberIdentities = mapOf(bot to identity)))
        val authoritative = AuthoritativeStateSnapshot(rooms = mapOf(waiting.id to waiting))
        store.update(authoritative)
        val before = store.snapshot()
        assertEquals(AiCharacterSelectionResult.SOURCE_MANAGED, store.select(authoritative, waiting.id, bot, host, alternate.id))
        assertEquals(before, store.snapshot())
    }

    /** 舊桌房間移除後釋放來源認領；新桌房間不繼承舊房間的身分或排除名單。 */
    @Test
    fun `removing a room releases participant claims before another room is created`() {
        val store = store()
        val identity = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, Uuid.random())
        val waiting = room.copy(admission = RoomAdmissionState(memberIdentities = mapOf(bot to identity)))
        store.update(AuthoritativeStateSnapshot(rooms = mapOf(waiting.id to waiting)))
        assertEquals(waiting.id, store.snapshot().claimedParticipants[identity])
        store.update(AuthoritativeStateSnapshot())
        assertFalse(identity in store.snapshot().claimedParticipants)
        val replacement = room.copy(id = Uuid.random(), admission = RoomAdmissionState())
        store.update(AuthoritativeStateSnapshot(rooms = mapOf(replacement.id to replacement)))
        assertTrue(store.snapshot().claimedParticipants.isEmpty())
        val joined = replacement.copy(admission = replacement.admission.copy(memberIdentities = mapOf(bot to identity)))
        store.update(AuthoritativeStateSnapshot(rooms = mapOf(joined.id to joined)))
        assertEquals(joined.id, store.snapshot().claimedParticipants[identity])
    }

    /** 名稱更新沿用排除者的來源與最後外觀，不恢復成員資格或覆寫既有場次。 */
    @Test
    fun `authorized excluded participant name updates preserve exclusion and appearance`() {
        val store = store()
        val identity = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, Uuid.random())
        val waiting = room.copy(admission = RoomAdmissionState(memberIdentities = mapOf(bot to identity)))
        val original = AiCharacterSnapshot(alternate.id, alternate, "Before")
        val capture = AiCharacterParticipantCapture(waiting.admission.instanceId, bot, identity, original)
        store.prepareParticipant(capture)
        store.update(AuthoritativeStateSnapshot(rooms = mapOf(waiting.id to waiting)))
        store.releaseParticipant(bot)
        val excluded = waiting.copy(
            playerIds = waiting.playerIds - bot,
            readyPlayerIds = waiting.readyPlayerIds - bot,
            aiPlayerStrategyKeys = waiting.aiPlayerStrategyKeys - bot,
            admission = waiting.admission.exclude(bot),
        )
        val state = AuthoritativeStateSnapshot(rooms = mapOf(excluded.id to excluded))
        store.update(state)
        val renamed = original.copy(displayName = "After")
        store.prepareParticipant(capture.copy(appearance = renamed))
        store.update(state)
        store.releaseParticipant(bot)
        assertEquals(renamed, store.snapshot().excludedAppearancesFor(excluded.id, host)[bot])
        assertTrue(store.snapshot().excludedAppearancesFor(excluded.id, Uuid.random()).isEmpty())
        assertEquals(mapOf(identity to bot), store.snapshot().authoritative.rooms.getValue(excluded.id).admission.excludedParticipants)
        assertFalse(bot in store.snapshot().authoritative.rooms.getValue(excluded.id).playerIds)
        store.prepareParticipant(capture.copy(roomInstanceId = Uuid.random(), appearance = original))
        store.update(state)
        store.releaseParticipant(bot)
        assertEquals(renamed, store.snapshot().excludedAppearancesFor(excluded.id, host)[bot])
    }
}
