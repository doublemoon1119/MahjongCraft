package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
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

    /** 有版本欄位但沒有任何桌子位置時視為空索引。 */
    @Test
    fun `test missing entries load empty state`() {
        val nbt = NbtCompound().apply {
            putInt(MahjongTableLocationsPersistentState.NBT_KEY_VERSION, MahjongTableLocationsPersistentState.CURRENT_VERSION)
        }

        assertEquals(emptyList(), MahjongTableLocationsPersistentState.fromNbt(nbt).entries)
    }

    /** 寫入時附上目前格式版本。 */
    @Test
    fun `test written nbt carries the current format version`() {
        val nbt = MahjongTableLocationsPersistentState.create().writeNbt(NbtCompound())

        assertEquals(
            MahjongTableLocationsPersistentState.CURRENT_VERSION,
            nbt.getInt(MahjongTableLocationsPersistentState.NBT_KEY_VERSION),
        )
    }

    /** 沒有版本欄位的資料無法確定格式，整份略過。 */
    @Test
    fun `test entries without a version field load an empty index`() {
        val entry = TableLocationEntry(Uuid.random(), TableLocation("minecraft:overworld", 1, 2, 3), 5)
        val state = MahjongTableLocationsPersistentState.create()
        state.update(mapOf(entry.tableId to entry))
        val nbt = state.writeNbt(NbtCompound()).apply { remove(MahjongTableLocationsPersistentState.NBT_KEY_VERSION) }

        assertEquals(emptyList(), MahjongTableLocationsPersistentState.fromNbt(nbt).entries)
    }

    /** 無法解讀的單筆資料只略過該筆，其餘位置保留。 */
    @Test
    fun `test an unreadable entry is skipped without losing the rest`() {
        val valid = TableLocationEntry(Uuid.random(), TableLocation("minecraft:overworld", 4, 5, 6), 2)
        val state = MahjongTableLocationsPersistentState.create()
        state.update(mapOf(valid.tableId to valid))
        val nbt = state.writeNbt(NbtCompound())
        nbt.getList(MahjongTableLocationsPersistentState.NBT_KEY_ENTRIES, NbtElement.COMPOUND_TYPE.toInt())
            .add(NbtCompound().apply { putString(MahjongTableLocationsPersistentState.NBT_KEY_TABLE_ID, "not-a-uuid") })

        assertEquals(listOf(valid), MahjongTableLocationsPersistentState.fromNbt(nbt).entries)
    }

    /** 版本比程式新時整份略過，且不得拋出例外而擋下世界載入。 */
    @Test
    fun `test a newer format version loads an empty index`() {
        val entry = TableLocationEntry(Uuid.random(), TableLocation("minecraft:overworld", 7, 8, 9), 1)
        val state = MahjongTableLocationsPersistentState.create()
        state.update(mapOf(entry.tableId to entry))
        val nbt = state.writeNbt(NbtCompound()).apply {
            putInt(
                MahjongTableLocationsPersistentState.NBT_KEY_VERSION,
                MahjongTableLocationsPersistentState.CURRENT_VERSION + 1,
            )
        }

        assertEquals(emptyList(), MahjongTableLocationsPersistentState.fromNbt(nbt).entries)
    }

    /** 缺少座標或型別不符時略過該筆，不能把缺值默認成零座標。 */
    @Test
    fun `test missing or mistyped coordinates do not become zero`() {
        val valid = TableLocationEntry(Uuid.random(), TableLocation("minecraft:overworld", 4, 5, 6), 2)
        val nbt = indexedNbt(valid)
        val entries = nbt.getList(MahjongTableLocationsPersistentState.NBT_KEY_ENTRIES, NbtElement.COMPOUND_TYPE.toInt())
        entries.add(
            entries.getCompound(0).copy().apply {
                putString(MahjongTableLocationsPersistentState.NBT_KEY_TABLE_ID, Uuid.random().toString())
                remove("X")
            },
        )
        entries.add(
            entries.getCompound(0).copy().apply {
                putString(MahjongTableLocationsPersistentState.NBT_KEY_TABLE_ID, Uuid.random().toString())
                putString("Y", "5")
            },
        )

        assertEquals(listOf(valid), MahjongTableLocationsPersistentState.fromNbt(nbt).entries)
    }

    /** 重複桌號在讀取時被隔離，後續 registry.load 不得拋出例外。 */
    @Test
    fun `test duplicate table ids keep the first valid entry`() {
        val first = TableLocationEntry(Uuid.random(), TableLocation("minecraft:overworld", 4, 5, 6), 2)
        val nbt = indexedNbt(first)
        val entries = nbt.getList(MahjongTableLocationsPersistentState.NBT_KEY_ENTRIES, NbtElement.COMPOUND_TYPE.toInt())
        entries.add(entries.getCompound(0).copy().apply { putInt("X", 99) })
        val restored = MahjongTableLocationsPersistentState.fromNbt(nbt).entries

        assertEquals(listOf(first), restored)
        TableLocationRegistry().load(restored)
    }

    /** 錯誤型別的版本欄位不能被誤讀成任何版本。 */
    @Test
    fun `test invalid version type loads an empty index`() {
        val entry = TableLocationEntry(Uuid.random(), TableLocation("minecraft:overworld", 4, 5, 6), 2)
        val nbt = indexedNbt(entry).apply { putString(MahjongTableLocationsPersistentState.NBT_KEY_VERSION, "new") }

        assertEquals(emptyList(), MahjongTableLocationsPersistentState.fromNbt(nbt).entries)
    }

    private fun indexedNbt(entry: TableLocationEntry): NbtCompound = MahjongTableLocationsPersistentState.create().apply {
        update(mapOf(entry.tableId to entry))
    }.writeNbt(NbtCompound())
}
