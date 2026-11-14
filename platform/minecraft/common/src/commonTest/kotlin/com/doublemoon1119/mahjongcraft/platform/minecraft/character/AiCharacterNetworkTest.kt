package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomAdmissionState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterLimits
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterPreview
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證角色傳輸與歷史保存的獨立格式、必要欄位與跨場次隔離。 */
class AiCharacterNetworkTest {
    /** 即時實體參照只屬於當前同步，不混入角色保存描述。 */
    @Test
    fun `live references round trip without entering historical appearance`() {
        val player = Uuid.random()
        val entity = Uuid.random()
        val role = AiCharacterSnapshot(BuiltInAiCharacters.TILE_MASCOT_ID, BuiltInAiCharacters.TILE_MASCOT)
        val references = mutableMapOf(player to entity)
        val source = AiCharacterSyncSnapshot(Uuid.random(), 1, Uuid.random(), true, emptyList(), mapOf(player to role), worldEntityIds = references)
        references.clear()
        assertEquals(mapOf(player to entity), source.worldEntityIds)
        val payload = source.toPayload()
        assertEquals(source.worldEntityIds, Json.decodeFromString<AiCharacterSyncPayload>(Json.encodeToString(payload)).toSnapshot().worldEntityIds)
        assertFalse(AiCharacterSnapshotCodec().encode(role).contains(entity.toString()))
        assertFailsWith<IllegalArgumentException> { payload.copy(worldEntityIds = mapOf(Uuid.random().toString() to entity.toString())).toSnapshot() }
        assertFailsWith<IllegalArgumentException> { payload.copy(worldEntityIds = mapOf(player.toString() to "invalid")).toSnapshot() }
        assertFailsWith<IllegalArgumentException> { payload.copy(players = emptyList()).toSnapshot() }
    }

    /** 同一個本人不能出現在多個成員欄位；補充同步也必須依序更新與清除。 */
    @Test
    fun `live references reject duplicate actors and clear with current state`() {
        val player = Uuid.random()
        val second = Uuid.random()
        val entity = Uuid.random()
        val session = Uuid.random()
        val table = Uuid.random()
        val role = AiCharacterSnapshot(BuiltInAiCharacters.TILE_MASCOT_ID, BuiltInAiCharacters.TILE_MASCOT)
        val source = AiCharacterSyncSnapshot(session, 2, table, true, emptyList(), mapOf(player to role), worldEntityIds = mapOf(player to entity))
        assertFailsWith<IllegalArgumentException> {
            AiCharacterSyncSnapshot(session, 2, table, true, emptyList(), mapOf(player to role, second to role), worldEntityIds = mapOf(player to entity, second to entity))
        }
        val store = AiCharacterClientStateStore()
        assertTrue(store.receive(source))
        assertEquals(entity, store.find(table)?.worldEntityIds?.get(player))
        assertFalse(store.receive(AiCharacterSyncSnapshot(session, 1, table, true, emptyList(), mapOf(player to role))))
        assertTrue(store.receive(AiCharacterSyncSnapshot(session, 3, table, true, emptyList(), mapOf(player to role))))
        assertTrue(store.find(table)?.worldEntityIds.orEmpty().isEmpty())
        store.remove(table)
        assertNull(store.find(table))
    }

    /** 最長合法字串連同 JSON 跳脫後，完整名單仍低於頻道的位元組上限。 */
    @Test
    fun `maximum live and excluded descriptions fit the wire byte budget`() {
        val id = "example:" + "a".repeat(AiCharacterLimits.MAX_ID_LENGTH - 8)
        val escaped = "\u0000"
        val parameters = (0 until AiCharacterLimits.MAX_PREVIEW_PARAMETERS).associate { index ->
            val prefix = "example:key$index"
            (prefix + "a".repeat(AiCharacterLimits.MAX_ID_LENGTH - prefix.length)) to escaped.repeat(AiCharacterLimits.MAX_PARAMETER_LENGTH)
        }
        val face = AiCharacterFace.TextureRegion(id, 0f, 0f, 1f, 1f)
        val nameKey = escaped.repeat(AiCharacterLimits.MAX_ID_LENGTH)
        val definition = AiCharacterDefinition(id, nameKey, face, id, AiCharacterPreview(id, parameters))
        val live = AiCharacterSnapshot(id, definition, escaped.repeat(128))
        val excluded = live.copy(definition = AiCharacterDefinition(id, nameKey, face, null, null))
        val snapshot = AiCharacterSyncSnapshot(
            sessionId = Uuid.random(),
            revision = 1,
            tableId = Uuid.random(),
            isAvailable = true,
            catalogue = emptyList(),
            players = List(AiCharacterSyncPayload.MAX_PLAYERS) { Uuid.random() }.associateWith { live },
            excludedPlayers = List(RoomAdmissionState.MAX_EXCLUDED_PARTICIPANTS) { Uuid.random() }.associateWith { excluded },
        )
        val payload = snapshot.toPayload()
        val encoded = Json.encodeToString(payload)
        assertTrue(encoded.encodeToByteArray().size <= AiCharacterSyncPayload.MAX_BYTES, "Maximum legal descriptions must fit the channel byte budget")
        assertEquals(snapshot.excludedPlayers, Json.decodeFromString<AiCharacterSyncPayload>(encoded).toSnapshot().excludedPlayers)
        assertFailsWith<IllegalArgumentException> { payload.copy(excludedPlayers = payload.excludedPlayers + payload.excludedPlayers.first().copy(playerId = Uuid.random().toString())).toSnapshot() }
    }

    /** 完整描述與普通名稱往返，不依賴目前登記內容。 */
    @Test
    fun `network and saved descriptions retain captured parameters and names`() {
        val character = AiCharacterDefinition("example:actor", "example.actor", null, "example:world", AiCharacterPreview("example:preview", mapOf("example:skin" to "blue")))
        val captured = AiCharacterSnapshot(character.id, character, "Captured name")
        val player = Uuid.random()
        val source = AiCharacterSyncSnapshot(Uuid.random(), 4, Uuid.random(), true, emptyList(), mapOf(player to captured), Uuid.random())
        val decoded = Json.decodeFromString<AiCharacterSyncPayload>(Json.encodeToString(source.toPayload())).toSnapshot()
        assertEquals(source.matchId, decoded.matchId)
        assertEquals(captured, decoded.players[player])
        assertEquals(captured, AiCharacterSnapshotCodec().decode(AiCharacterSnapshotCodec().encode(captured)))
    }

    /** 重複與超量成員不能變成 map 後悄悄覆寫資料。 */
    @Test
    fun `network rejects duplicate and excessive players`() {
        val role = AiCharacterSnapshot(BuiltInAiCharacters.TILE_MASCOT_ID, BuiltInAiCharacters.TILE_MASCOT)
        val payload = AiCharacterSyncSnapshot(Uuid.random(), 1, Uuid.random(), true, emptyList(), mapOf(Uuid.random() to role)).toPayload()
        assertFailsWith<IllegalArgumentException> { payload.copy(players = payload.players + payload.players).toSnapshot() }
        assertFailsWith<IllegalArgumentException> { payload.copy(players = List(5) { role.toNetworkDto(Uuid.random().toString()) }).toSnapshot() }
    }

    /** nullable 的保存與傳輸欄位仍必須明確提供。 */
    @Test
    fun `missing nullable wire and saved fields are rejected`() {
        assertFailsWith<SerializationException> { Json.decodeFromString<AiCharacterSyncPayload>("{}") }
        assertFailsWith<SerializationException> { AiCharacterSnapshotCodec().decode("{\"characterId\":\"example:actor\",\"definition\":null}") }
    }

    /** 歷史缺少外觀時不從目前桌子取代，斷線會清掉歷史配對資料。 */
    @Test
    fun `history appearances remain separate from live table descriptions`() {
        val store = AiCharacterClientStateStore()
        val session = Uuid.random()
        val table = Uuid.random()
        val match = Uuid.random()
        val player = Uuid.random()
        val old = AiCharacterSnapshot("example:old", null, "Old name")
        val current = AiCharacterSnapshot("example:new", null, "New name")
        store.beginSession(session)
        store.apply(AiCharacterSyncSnapshot(session, 1, table, true, emptyList(), mapOf(player to current)))
        assertNull(store.history(match, player))
        val mutable = mutableMapOf(player to old)
        store.rememberHistory(match, mutable)
        mutable.clear()
        assertEquals(old, store.history(match, player))
        assertEquals(current, store.find(table)?.players?.get(player))
        store.clear()
        assertNull(store.history(match, player))
        assertFalse(store.apply(AiCharacterSyncSnapshot(session, 2, table, true, emptyList(), mapOf(player to current))))
    }
}
