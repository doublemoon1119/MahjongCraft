package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
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
        assertEquals(BuiltInAiCharacters.TILE_MASCOT, store.snapshot().characters?.matches?.get(next.matchId)?.get(bot)?.definition)
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
}
