package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.migration.UnsupportedPersistenceSchemaVersionException
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtIo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 Minecraft NBT adapter 以位元組陣列保存 codec JSON，並維持每個世界各自的 snapshot。 */
class MahjongAuthoritativePersistentStateTest {
    /** 使用所有內建 mapper 的待測 codec。 */
    private val codec = AuthoritativeStatePersistenceCodec(bundledPersistenceRegistries())

    /** 驗證沒有既有 NBT payload 時建立空且乾淨的狀態。 */
    @Test
    fun `missing payload creates empty clean state`() {
        val state = MahjongAuthoritativePersistentState.fromNbt(NbtCompound(), codec)

        assertEquals(AuthoritativeStateSnapshot(), state.snapshot)
        assertFalse(state.isDirty)
    }

    /** 驗證更新 snapshot 會 mark dirty，並完整通過 NBT round-trip。 */
    @Test
    fun `updated snapshot round-trips through NBT`() {
        val room = createRoom()
        val expected = AuthoritativeStateSnapshot(rooms = mapOf(room.id to room))
        val state = MahjongAuthoritativePersistentState.create(codec)

        state.update(expected)
        val restored = MahjongAuthoritativePersistentState.fromNbt(state.writeNbt(NbtCompound()), codec)

        assertTrue(state.isDirty)
        assertEquals(expected, restored.snapshot)
    }

    /** 驗證兩個 NBT 容器各自還原自己的世界狀態，不共享 adapter 記憶體。 */
    @Test
    fun `separate NBT containers retain separate world states`() {
        val room = createRoom()
        val first = MahjongAuthoritativePersistentState.create(codec).apply {
            update(AuthoritativeStateSnapshot(rooms = mapOf(room.id to room)))
        }
        val second = MahjongAuthoritativePersistentState.create(codec)

        val restoredFirst = MahjongAuthoritativePersistentState.fromNbt(first.writeNbt(NbtCompound()), codec)
        val restoredSecond = MahjongAuthoritativePersistentState.fromNbt(second.writeNbt(NbtCompound()), codec)

        assertEquals(setOf(room.id), restoredFirst.snapshot.rooms.keys)
        assertTrue(restoredSecond.snapshot.rooms.isEmpty())
    }

    /** 驗證超過 NBT 字串上限的狀態經原版壓縮寫入與讀回後完全一致。 */
    @Test
    fun `state larger than NBT string limit round-trips through compressed NBT`() {
        val rooms = List(LARGE_ROOM_COUNT) { createRoom() }.associateBy { it.id }
        val expected = AuthoritativeStateSnapshot(rooms = rooms)
        val state = MahjongAuthoritativePersistentState.create(codec).apply { update(expected) }
        assertTrue(codec.encode(rooms.values, emptyList()).encodeToByteArray().size > NBT_STRING_LIMIT_BYTES)

        val output = ByteArrayOutputStream()
        NbtIo.writeCompressed(state.writeNbt(NbtCompound()), output)
        val nbt = NbtIo.readCompressed(ByteArrayInputStream(output.toByteArray()))
        val restored = MahjongAuthoritativePersistentState.fromNbt(nbt, codec)

        assertEquals(expected, restored.snapshot)
    }

    /** 驗證狀態以位元組陣列欄位寫入。 */
    @Test
    fun `state is written as a byte array`() {
        val nbt = MahjongAuthoritativePersistentState.create(codec).writeNbt(NbtCompound())

        assertEquals(setOf(MahjongAuthoritativePersistentState.NBT_KEY_STATE), nbt.keys)
        assertEquals(NbtElement.BYTE_ARRAY_TYPE, nbt.getType(MahjongAuthoritativePersistentState.NBT_KEY_STATE))
    }

    /** 驗證損壞 JSON 不會被當成空存檔而靜默接受。 */
    @Test
    fun `malformed payload fails loading`() {
        val nbt = NbtCompound().apply { putByteArray(MahjongAuthoritativePersistentState.NBT_KEY_STATE, "{not-json".encodeToByteArray()) }

        assertFailsWith<SerializationException> {
            MahjongAuthoritativePersistentState.fromNbt(nbt, codec)
        }
    }

    /** 驗證 NBT 內較新的未知 schema 不會被 adapter 當成空狀態。 */
    @Test
    fun `newer schema fails through NBT adapter`() {
        val encoded = codec.encode(emptyList(), emptyList())
        val envelope = Json.decodeFromString(PersistenceEnvelopeDto.serializer(), encoded)
            .copy(schemaVersion = Int.MAX_VALUE)
        val nbt = NbtCompound().apply {
            putByteArray(
                MahjongAuthoritativePersistentState.NBT_KEY_STATE,
                Json.encodeToString(PersistenceEnvelopeDto.serializer(), envelope).encodeToByteArray(),
            )
        }

        assertFailsWith<UnsupportedPersistenceSchemaVersionException> {
            MahjongAuthoritativePersistentState.fromNbt(nbt, codec)
        }
    }

    /** 驗證恢復端缺少規則 mapper 時，NBT adapter 會保留 codec 的明確失敗。 */
    @Test
    fun `missing persistence mapper fails through NBT adapter`() {
        val room = createRoom()
        val encoded = codec.encode(listOf(room), emptyList())
        val registriesWithoutRules = bundledPersistenceRegistries().copy(
            ruleConfigs = PersistenceDtoRegistry<MahjongRuleConfig>(),
        )
        val codecWithoutRules = AuthoritativeStatePersistenceCodec(registriesWithoutRules)
        val nbt = NbtCompound().apply { putByteArray(MahjongAuthoritativePersistentState.NBT_KEY_STATE, encoded.encodeToByteArray()) }

        assertFailsWith<IllegalStateException> {
            MahjongAuthoritativePersistentState.fromNbt(nbt, codecWithoutRules)
        }
    }

    /** 建立測試用等待階段 Room。 */
    private fun createRoom(): Room {
        val hostId = Uuid.random()
        return Room(
            id = Uuid.random(),
            hostId = hostId,
            gameConfig = GameConfig(RiichiRuleConfig()),
            playerIds = listOf(hostId),
        )
    }

    /** 測試共用的固定值。 */
    private companion object {
        /** 原版 NBT 字串可保存的最大 UTF-8 位元組數。 */
        const val NBT_STRING_LIMIT_BYTES: Int = 65_535

        /** 讓編碼結果超過 NBT 字串上限的房間數。 */
        const val LARGE_ROOM_COUNT: Int = 500
    }
}
