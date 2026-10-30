package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 牌局管理旗標與桌子 UUID NBT 讀寫的邊界測試。 */
class GameManagedEntityNbtTest {
    /** 管理旗標與有效桌子 UUID 應能完整往返。 */
    @Test
    fun `managed state survives round trip`() {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val nbt = NbtCompound()

        GameManagedEntityNbt.write(nbt, managedByGame = true, tableId = id)

        assertEquals(GameManagedEntityState(true, id), GameManagedEntityNbt.read(nbt))
    }

    /** 缺少管理旗標與桌子 UUID 時應代表自由放置模式。 */
    @Test
    fun `missing state is unmanaged`() {
        val state = GameManagedEntityNbt.read(NbtCompound())

        assertFalse(state.managedByGame)
        assertNull(state.tableId)
    }

    /** 管理旗標為 true 但 UUID 無效時，仍不得降級為自由放置模式。 */
    @Test
    fun `true flag remains managed when table id is invalid`() {
        val nbt = NbtCompound().apply {
            putBoolean("ManagedByGame", true)
            putString("ManagedTableId", "invalid")
        }

        val state = GameManagedEntityNbt.read(nbt)

        assertTrue(state.managedByGame)
        assertNull(state.tableId)
    }

    /** 管理旗標型別錯誤時不應誤判為牌局管理。 */
    @Test
    fun `wrong managed flag type is false`() {
        val nbt = NbtCompound().apply { putString("ManagedByGame", "true") }

        assertFalse(GameManagedEntityNbt.read(nbt).managedByGame)
    }

    /** 寫入 null 桌子 UUID 應清除舊值，但保留管理旗標。 */
    @Test
    fun `null table id clears stale value`() {
        val nbt = NbtCompound()
        GameManagedEntityNbt.write(nbt, managedByGame = true, tableId = null)

        val state = GameManagedEntityNbt.read(nbt)

        assertTrue(state.managedByGame)
        assertNull(state.tableId)
    }

    /** 寫入管理狀態不應改動其他欄位。 */
    @Test
    fun `unrelated fields are preserved`() {
        val nbt = NbtCompound().apply { putString("OtherKey", "value") }

        GameManagedEntityNbt.write(nbt, managedByGame = false, tableId = null)

        assertEquals("value", nbt.getString("OtherKey"))
    }

    /** 保留 NBT 原生數值布林的讀取行為，不把已管理物件因數值型別不同而解鎖。 */
    @Test
    fun `numeric true flag keeps the existing managed behavior`() {
        val nbt = NbtCompound().apply { putInt("ManagedByGame", 1) }
        assertTrue(GameManagedEntityNbt.read(nbt).managedByGame)
        assertNull(GameManagedEntityNbt.read(nbt).tableId)
    }
}
