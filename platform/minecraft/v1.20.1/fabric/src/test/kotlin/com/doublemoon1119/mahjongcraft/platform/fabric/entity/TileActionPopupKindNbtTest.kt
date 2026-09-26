package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertEquals

/** 短暫牌面提示 NBT 以名稱保存。 */
class TileActionPopupKindNbtTest {
    private val key = "ActionPopupKind"

    @Test
    fun `all kinds survive a name round trip`() {
        TileActionPopupKind.entries.forEach { kind ->
            val nbt = NbtCompound()
            TileActionPopupKindNbt.write(nbt, key, kind)

            assertEquals(kind.name, nbt.getString(key))
            assertEquals(kind, TileActionPopupKindNbt.read(nbt, key))
        }
    }

    @Test
    fun `unknown or malformed values hide the popup`() {
        assertEquals(TileActionPopupKind.NONE, TileActionPopupKindNbt.read(NbtCompound(), key))
        assertEquals(TileActionPopupKind.NONE, TileActionPopupKindNbt.read(NbtCompound().apply { putString(key, "UNKNOWN") }, key))
        assertEquals(TileActionPopupKind.NONE, TileActionPopupKindNbt.read(NbtCompound().apply { putInt(key, 99) }, key))
    }
}
