package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證角色同步不跨 session，也不讓舊版本覆寫新資料。 */
class AiCharacterClientStateStoreTest {
    /** 未確認 session、舊版本與斷線後的更新都被拒絕。 */
    @Test
    fun `only newer snapshots from the confirmed session are accepted`() {
        val store = AiCharacterClientStateStore()
        val session = Uuid.random()
        val table = Uuid.random()
        val first = AiCharacterSyncSnapshot(session, 2, table, true, emptyList(), emptyMap())
        assertFalse(store.apply(first))
        store.beginSession(session)
        assertTrue(store.apply(first))
        assertFalse(store.apply(first))
        assertFalse(store.apply(AiCharacterSyncSnapshot(session, 1, table, true, emptyList(), emptyMap())))
        assertFalse(store.apply(AiCharacterSyncSnapshot(Uuid.random(), 3, table, true, emptyList(), emptyMap())))
        assertEquals(first, store.find(table))
        store.clear()
        assertFalse(store.apply(first))
        assertNull(store.find(table))
        store.beginSession(Uuid.random())
        assertFalse(store.apply(first))
    }

    /** 同步資料拒絕重複目錄與超量成員，並複製呼叫端集合。 */
    @Test
    fun `payload limits and collection ownership are enforced`() {
        val session = Uuid.random()
        val table = Uuid.random()
        val definitions = mutableListOf(BuiltInAiCharacters.TILE_MASCOT)
        val players = mutableMapOf(Uuid.random() to AiCharacterSnapshot(BuiltInAiCharacters.TILE_MASCOT_ID, BuiltInAiCharacters.TILE_MASCOT))
        val payload = AiCharacterSyncSnapshot(session, 1, table, true, definitions, players)
        definitions.clear()
        players.clear()
        assertEquals(1, payload.catalogue.size)
        assertEquals(1, payload.players.size)
        assertFailsWith<IllegalArgumentException> { AiCharacterSyncSnapshot(session, 0, table, true, List(2) { BuiltInAiCharacters.TILE_MASCOT }, emptyMap()) }
        assertFailsWith<IllegalArgumentException> { AiCharacterSyncSnapshot(session, 0, table, true, emptyList(), List(AiCharacterLimits.MAX_SYNC_PLAYERS + 1) { Uuid.random() }.associateWith { AiCharacterSnapshot(BuiltInAiCharacters.TILE_MASCOT_ID, null) }) }
    }
}
