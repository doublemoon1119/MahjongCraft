package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 桌子 UUID NBT 讀寫的邊界測試。 */
class ManagedTableNbtTest {
    /** 有效 UUID 應能往返保存。 */
    @Test
    fun `valid table id survives round trip`() {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val nbt = NbtCompound()

        ManagedTableNbt.write(nbt, id)

        assertEquals(id, ManagedTableNbt.read(nbt))
    }

    /** 欄位缺少時應回傳 null。 */
    @Test
    fun `missing table id returns null`() {
        assertNull(ManagedTableNbt.read(NbtCompound()))
    }

    /** UUID 欄位型別錯誤時應回傳 null。 */
    @Test
    fun `wrong nbt type returns null`() {
        assertNull(ManagedTableNbt.read(NbtCompound().apply { putInt("ManagedTableId", 1) }))
    }

    /** UUID 文字格式錯誤時應回傳 null。 */
    @Test
    fun `invalid uuid returns null`() {
        assertNull(ManagedTableNbt.read(NbtCompound().apply { putString("ManagedTableId", "invalid") }))
    }

    /** 寫入 null 應清除既有桌子 UUID。 */
    @Test
    fun `null table id clears stale value`() {
        val nbt = NbtCompound().apply { putString("ManagedTableId", "00000000-0000-0000-0000-000000000001") }

        ManagedTableNbt.write(nbt, null)

        assertNull(ManagedTableNbt.read(nbt))
    }

    /** 寫入桌子 UUID 不應改動其他欄位。 */
    @Test
    fun `unrelated fields are preserved`() {
        val nbt = NbtCompound().apply { putString("OtherKey", "value") }

        ManagedTableNbt.write(nbt, Uuid.parse("00000000-0000-0000-0000-000000000001"))

        assertEquals("value", nbt.getString("OtherKey"))
    }
}
