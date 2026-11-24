package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [MahjongTableLocationsSavedData] 的 NBT round-trip 測試。 */
class MahjongTableLocationsSavedDataTest {
    /** 完整 identifier、座標與 revision 應通過 NBT 往返。 */
    @Test
    fun `test entries survive nbt round trip`() {
        val first = TableLocationEntry(
            tableId = Uuid.random(),
            location = TableLocation("minecraft:overworld", 32, -60, -17),
            revision = 3,
        )
        val second = TableLocationEntry(
            tableId = Uuid.random(),
            location = TableLocation("example:custom_dimension", 32, -60, -17),
            revision = 7,
        )
        val state = MahjongTableLocationsSavedData.create()
        state.update(mapOf(first.tableId to first, second.tableId to second))

        val restored = MahjongTableLocationsSavedData.fromNbt(state.save(CompoundTag()))

        assertEquals(setOf(first, second), restored.entries.toSet())
    }

    /** 有版本欄位但沒有任何桌子位置時視為空索引。 */
    @Test
    fun `test missing entries load empty state`() {
        val nbt = CompoundTag().apply {
            putInt(MahjongTableLocationsSavedData.NBT_KEY_VERSION, MahjongTableLocationsSavedData.CURRENT_VERSION)
        }

        assertEquals(emptyList(), MahjongTableLocationsSavedData.fromNbt(nbt).entries)
    }

    /** 寫入時附上目前格式版本。 */
    @Test
    fun `test written nbt carries the current format version`() {
        val nbt = MahjongTableLocationsSavedData.create().save(CompoundTag())

        assertEquals(
            expected = MahjongTableLocationsSavedData.CURRENT_VERSION,
            actual = nbt.getInt(MahjongTableLocationsSavedData.NBT_KEY_VERSION),
        )
    }

    /** 沒有版本欄位的資料無法確定格式，整份略過。 */
    @Test
    fun `test entries without a version field load an empty index`() {
        val entry = TableLocationEntry(Uuid.random(), TableLocation("minecraft:overworld", 1, 2, 3), 5)
        val state = MahjongTableLocationsSavedData.create()
        state.update(mapOf(entry.tableId to entry))
        val nbt = state.save(CompoundTag()).apply { remove(MahjongTableLocationsSavedData.NBT_KEY_VERSION) }

        assertEquals(emptyList(), MahjongTableLocationsSavedData.fromNbt(nbt).entries)
    }

    /** 無法解讀的單筆資料只略過該筆，其餘位置保留。 */
    @Test
    fun `test an unreadable entry is skipped without losing the rest`() {
        val valid = TableLocationEntry(Uuid.random(), TableLocation("minecraft:overworld", 4, 5, 6), 2)
        val state = MahjongTableLocationsSavedData.create()
        state.update(mapOf(valid.tableId to valid))
        val nbt = state.save(CompoundTag())
        nbt.getList(MahjongTableLocationsSavedData.NBT_KEY_ENTRIES, Tag.TAG_COMPOUND.toInt())
            .add(CompoundTag().apply { putString(MahjongTableLocationsSavedData.NBT_KEY_TABLE_ID, "not-a-uuid") })

        assertEquals(listOf(valid), MahjongTableLocationsSavedData.fromNbt(nbt).entries)
    }

    /** 版本比程式新時整份略過，且不得拋出例外而擋下世界載入。 */
    @Test
    fun `test a newer format version loads an empty index`() {
        val entry = TableLocationEntry(Uuid.random(), TableLocation("minecraft:overworld", 7, 8, 9), 1)
        val state = MahjongTableLocationsSavedData.create()
        state.update(mapOf(entry.tableId to entry))
        val nbt = state.save(CompoundTag()).apply {
            putInt(
                MahjongTableLocationsSavedData.NBT_KEY_VERSION,
                MahjongTableLocationsSavedData.CURRENT_VERSION + 1,
            )
        }

        assertEquals(emptyList(), MahjongTableLocationsSavedData.fromNbt(nbt).entries)
    }

    /** 缺少座標或型別不符時略過該筆，不能把缺值默認成零座標。 */
    @Test
    fun `test missing or mistyped coordinates do not become zero`() {
        val valid = TableLocationEntry(Uuid.random(), TableLocation("minecraft:overworld", 4, 5, 6), 2)
        val nbt = indexedNbt(valid)
        val entries = nbt.getList(MahjongTableLocationsSavedData.NBT_KEY_ENTRIES, Tag.TAG_COMPOUND.toInt())
        entries.add(
            entries.getCompound(0).copy().apply {
                putString(MahjongTableLocationsSavedData.NBT_KEY_TABLE_ID, Uuid.random().toString())
                remove("X")
            },
        )
        entries.add(
            entries.getCompound(0).copy().apply {
                putString(MahjongTableLocationsSavedData.NBT_KEY_TABLE_ID, Uuid.random().toString())
                putString("Y", "5")
            },
        )

        assertEquals(listOf(valid), MahjongTableLocationsSavedData.fromNbt(nbt).entries)
    }

    /** 重複桌號在讀取時被隔離，後續 registry.load 不得拋出例外。 */
    @Test
    fun `test duplicate table ids keep the first valid entry`() {
        val first = TableLocationEntry(Uuid.random(), TableLocation("minecraft:overworld", 4, 5, 6), 2)
        val nbt = indexedNbt(first)
        val entries = nbt.getList(MahjongTableLocationsSavedData.NBT_KEY_ENTRIES, Tag.TAG_COMPOUND.toInt())
        entries.add(entries.getCompound(0).copy().apply { putInt("X", 99) })
        val restored = MahjongTableLocationsSavedData.fromNbt(nbt).entries

        assertEquals(listOf(first), restored)
        TableLocationRegistry().load(restored)
    }

    /** 錯誤型別的版本欄位不能被誤讀成任何版本。 */
    @Test
    fun `test invalid version type loads an empty index`() {
        val entry = TableLocationEntry(Uuid.random(), TableLocation("minecraft:overworld", 4, 5, 6), 2)
        val nbt = indexedNbt(entry).apply { putString(MahjongTableLocationsSavedData.NBT_KEY_VERSION, "new") }

        assertEquals(emptyList(), MahjongTableLocationsSavedData.fromNbt(nbt).entries)
    }

    /**
     * 建立只含指定桌子位置的版本化 NBT 索引。
     *
     * @param entry 要寫入索引的桌子位置。
     * @return 含目前格式版本與該筆位置的 NBT compound。
     */
    private fun indexedNbt(entry: TableLocationEntry): CompoundTag = MahjongTableLocationsSavedData.create().apply {
        update(mapOf(entry.tableId to entry))
    }.save(CompoundTag())
}
