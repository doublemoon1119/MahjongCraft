package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.flow.server.state.CommittedFactsListener
import com.doublemoon1119.mahjongcraft.logic.component.initializeRuleComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSelectionResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledComponentValidator
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import kotlinx.coroutines.test.runTest
import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證原生 NBT 保存與真正權威提交共用相同的角色快照。 */
class AiCharacterPersistenceIntegrationTest {
    /** 使用真實內建轉換器與組件驗證的保存 codec。 */
    private val codec = AuthoritativeStatePersistenceCodec(bundledPersistenceRegistries(), componentValidator = bundledComponentValidator())

    /** 開局後立刻保存，提交接收者與還原後看到相同角色，不依賴延後公開事件。 */
    @Test
    fun `opening snapshot is captured before committed notification and survives immediate save`() = runTest {
        val players = List(4) { Uuid.random() }
        val room = Room(Uuid.random(), players.first(), GameConfig(RiichiRuleConfig()), players, players.drop(1), players.drop(1).associateWith { "example:strategy" })
        val core = bundledCoreExtensionRegistries()
        val table = GameInitializer.initialize(room.id, players, core.moduleRegistry.getModule(room.gameConfig.ruleConfig)).initializeRuleComponents(
            ruleModuleId = core.moduleRegistry.getModule(room.gameConfig.ruleConfig).id,
            registry = core.componentRegistry,
        ).tableState
        val game = Game(table, GameFlowConfig(), aiPlayerStrategyKeys = room.aiPlayerStrategyKeys)
        val saved = MahjongAuthoritativePersistentState.create(codec, testCharacterRegistry())
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        store.load(saved.snapshot)
        store.setDirtyListener(saved::update)
        store.update { AuthoritativeStateUpdate(it.copy(rooms = mapOf(room.id to room)), Unit) }
        var observed = false
        store.setCommittedFactsListener(object : CommittedFactsListener {
            /** 在持鎖通知中只讀已擷取的封套，不再次進入 store。 */
            override fun onCommitted(sequence: Long, facts: CommittedGameFacts) {
                val opening = saved.characterSnapshot().characters!!.matches[game.matchId]
                assertEquals(room.aiPlayerStrategyKeys.keys, opening?.keys)
                assertEquals(BuiltInAiCharacters.TILE_MASCOT, opening?.get(players[1])?.definition)
                observed = true
            }

            /** 此測試不使用延後公開事件。 */
            override fun onReleased(sequence: Long) = Unit
        })
        store.update { before ->
            AuthoritativeStateUpdate(
                state = before.copy(rooms = emptyMap(), games = mapOf(game.id to game)),
                result = Unit,
                historyDraftsByVenueId = mapOf(game.id to listOf(HistoryEventDraft(null, HistoryFact.MatchStarted(table, game.flowConfig, game.aiPlayerStrategyKeys)))),
            )
        }
        assertTrue(observed)
        val restored = MahjongAuthoritativePersistentState.fromNbt(saved.writeNbt(NbtCompound()), codec, testCharacterRegistry())
        assertNull(restored.characterDataFailure)
        assertEquals(saved.characterSnapshot().characters, restored.characterSnapshot().characters)
        assertEquals(game, restored.snapshot.games[game.id])
        assertTrue(restored.snapshot.historyRecordingState.pendingEvents.isNotEmpty())
    }

    /** 格式錯誤的角色 JSON 不影響牌局，之後保存也不覆寫原始區段。 */
    @Test
    fun `unreadable character bytes are retained while valid match data can change`() {
        val created = MahjongAuthoritativePersistentState.create(codec, testCharacterRegistry())
        val nbt = created.writeNbt(NbtCompound())
        val damaged = "{invalid-character-data".encodeToByteArray()
        nbt.putByteArray(MahjongAuthoritativePersistentState.NBT_KEY_CHARACTERS, damaged)
        val restored = MahjongAuthoritativePersistentState.fromNbt(nbt, codec, testCharacterRegistry())
        assertNotNull(restored.characterDataFailure)
        assertNull(restored.characterSnapshot().characters)
        val room = Room(Uuid.random(), Uuid.random(), GameConfig(RiichiRuleConfig()))
        val updated = AuthoritativeStateSnapshot(rooms = mapOf(room.id to room))
        restored.update(updated)
        assertEquals(AiCharacterSelectionResult.DATA_UNAVAILABLE, restored.selectCharacter(updated, room.id, Uuid.random(), room.hostId, BuiltInAiCharacters.TILE_MASCOT_ID))
        val written = restored.writeNbt(NbtCompound())
        assertContentEquals(damaged, written.getByteArray(MahjongAuthoritativePersistentState.NBT_KEY_CHARACTERS))
        assertEquals(updated, MahjongAuthoritativePersistentState.fromNbt(written, codec, testCharacterRegistry()).snapshot)
    }

    /** 非法 UTF-8 不得被替代字元默默修正後覆寫。 */
    @Test
    fun `invalid UTF8 in a valid JSON string preserves the original section`() {
        val nbt = MahjongAuthoritativePersistentState.create(codec, testCharacterRegistry()).writeNbt(NbtCompound())
        val encoded = """{"selections":[],"matches":[{"matchId":"${Uuid.random()}","players":[{"playerId":"${Uuid.random()}","characterId":"example:actor","definition":{"id":"example:actor","nameKey":"x","face":null,"worldCapabilityId":null,"preview":null}}]}]}"""
        val bytes = encoded.encodeToByteArray()
        val index = encoded.indexOf("\"nameKey\":\"x\"") + "\"nameKey\":\"".length
        bytes[index] = 0xff.toByte()
        nbt.putByteArray(MahjongAuthoritativePersistentState.NBT_KEY_CHARACTERS, bytes)
        val restored = MahjongAuthoritativePersistentState.fromNbt(nbt, codec, testCharacterRegistry())
        assertNotNull(restored.characterDataFailure)
        assertContentEquals(bytes, restored.writeNbt(NbtCompound()).getByteArray(MahjongAuthoritativePersistentState.NBT_KEY_CHARACTERS))
    }

    /** 錯誤 NBT 型別也原樣保留；缺少區段不以空選擇偷偷補上。 */
    @Test
    fun `wrong NBT type and absent section are not silently replaced`() {
        val original = MahjongAuthoritativePersistentState.create(codec, testCharacterRegistry()).writeNbt(NbtCompound())
        original.putString(MahjongAuthoritativePersistentState.NBT_KEY_CHARACTERS, "unknown representation")
        val restored = MahjongAuthoritativePersistentState.fromNbt(original, codec, testCharacterRegistry())
        assertNotNull(restored.characterDataFailure)
        assertEquals(original.get(MahjongAuthoritativePersistentState.NBT_KEY_CHARACTERS), restored.writeNbt(NbtCompound()).get(MahjongAuthoritativePersistentState.NBT_KEY_CHARACTERS))
        original.remove(MahjongAuthoritativePersistentState.NBT_KEY_CHARACTERS)
        val absent = MahjongAuthoritativePersistentState.fromNbt(original, codec, testCharacterRegistry())
        assertNotNull(absent.characterDataFailure)
        assertNull(absent.writeNbt(NbtCompound()).get(MahjongAuthoritativePersistentState.NBT_KEY_CHARACTERS))
    }
}
