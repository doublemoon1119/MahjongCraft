package com.doublemoon1119.mahjongcraft.platform.fabric.persistence

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [MahjongTableLocationsPersistentState] 的 NBT round-trip 測試。 */
class MahjongTableLocationsPersistentStateTest {
    /** 完整 identifier、座標與 revision 應通過 NBT 往返。 */
    @Test
    fun `test entries survive nbt round trip`() {
        val first = TableLocationEntry(
            Uuid.random(),
            TableLocation("minecraft:overworld", 32, -60, -17),
            3,
        )
        val second = TableLocationEntry(
            Uuid.random(),
            TableLocation("example:custom_dimension", 32, -60, -17),
            7,
        )
        val state = MahjongTableLocationsPersistentState.create()
        state.update(mapOf(first.tableId to first, second.tableId to second))

        val restored = MahjongTableLocationsPersistentState.fromNbt(state.writeNbt(NbtCompound()))

        assertEquals(setOf(first, second), restored.entries.toSet())
    }

    /** 缺少位置 list 的舊存檔應視為空索引。 */
    @Test
    fun `test missing entries load empty state`() {
        assertEquals(emptyList(), MahjongTableLocationsPersistentState.fromNbt(NbtCompound()).entries)
    }
}
