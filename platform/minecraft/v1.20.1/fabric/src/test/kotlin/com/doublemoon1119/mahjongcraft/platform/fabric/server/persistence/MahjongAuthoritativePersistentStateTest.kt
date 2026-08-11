package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import kotlinx.serialization.SerializationException
import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 Minecraft NBT adapter 只保存 codec JSON，並維持每個世界各自的 snapshot。 */
class MahjongAuthoritativePersistentStateTest {
    /** 使用所有內建 mapper 的待測 codec。 */
    private val codec = AuthoritativeStatePersistenceCodec(buildBuiltInPersistenceRegistries())

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

    /** 驗證損壞 JSON 不會被當成空存檔而靜默接受。 */
    @Test
    fun `malformed payload fails loading`() {
        val nbt = NbtCompound().apply { putString("state", "{not-json") }

        assertFailsWith<SerializationException> {
            MahjongAuthoritativePersistentState.fromNbt(nbt, codec)
        }
    }

    /** 建立測試用等待階段 Room。 */
    private fun createRoom(): Room {
        val hostId = Uuid.random()
        return Room(
            id = Uuid.random(),
            hostId = hostId,
            gameConfig = GameConfig(RiichiRuleConfig()),
            playerIds = setOf(hostId),
        )
    }
}
