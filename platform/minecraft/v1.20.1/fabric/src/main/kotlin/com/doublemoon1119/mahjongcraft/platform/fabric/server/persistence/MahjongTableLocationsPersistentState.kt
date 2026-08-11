package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.world.PersistentState
import kotlin.uuid.Uuid

/** 將 Minecraft 麻將桌位置索引保存為獨立 NBT list 的 [PersistentState]。 */
class MahjongTableLocationsPersistentState private constructor(
    initialEntries: Collection<TableLocationEntry>,
) : PersistentState() {
    /** 目前等待 Minecraft 世界儲存流程寫入的不可變位置集合。 */
    var entries: List<TableLocationEntry> = initialEntries.toList()
        private set

    /** 更新待保存位置並標記此 state 為 dirty。 */
    fun update(entriesByTableId: Map<Uuid, TableLocationEntry>) {
        entries = entriesByTableId.values.toList()
        markDirty()
    }

    /** 將完整位置索引寫入 Minecraft NBT。 */
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        val entriesNbt = NbtList()
        entries.forEach { entry ->
            entriesNbt.add(
                NbtCompound().apply {
                    putString(NBT_KEY_TABLE_ID, entry.tableId.toString())
                    putString(NBT_KEY_DIMENSION, entry.location.dimensionId)
                    putInt(NBT_KEY_X, entry.location.x)
                    putInt(NBT_KEY_Y, entry.location.y)
                    putInt(NBT_KEY_Z, entry.location.z)
                    putLong(NBT_KEY_REVISION, entry.revision)
                },
            )
        }
        nbt.put(NBT_KEY_ENTRIES, entriesNbt)
        return nbt
    }

    /** 建立與讀取位置 [PersistentState] 的固定 metadata。 */
    companion object {
        /** `PersistentStateManager` 使用的世界存檔 key。 */
        const val STORAGE_KEY: String = "${MinecraftModMetadata.MOD_ID}_table_locations"

        /** 建立空的位置 state。 */
        fun create(): MahjongTableLocationsPersistentState = MahjongTableLocationsPersistentState(emptyList())

        /** 從 Minecraft NBT 還原完整位置 state。 */
        fun fromNbt(nbt: NbtCompound): MahjongTableLocationsPersistentState {
            if (!nbt.contains(NBT_KEY_ENTRIES, NbtElement.LIST_TYPE.toInt())) return create()
            val entriesNbt = nbt.getList(NBT_KEY_ENTRIES, NbtElement.COMPOUND_TYPE.toInt())
            val entries = buildList {
                repeat(entriesNbt.size) { index ->
                    val entryNbt = entriesNbt.getCompound(index)
                    add(
                        TableLocationEntry(
                            tableId = Uuid.parse(entryNbt.getString(NBT_KEY_TABLE_ID)),
                            location = TableLocation(
                                dimensionId = entryNbt.getString(NBT_KEY_DIMENSION),
                                x = entryNbt.getInt(NBT_KEY_X),
                                y = entryNbt.getInt(NBT_KEY_Y),
                                z = entryNbt.getInt(NBT_KEY_Z),
                            ),
                            revision = entryNbt.getLong(NBT_KEY_REVISION),
                        ),
                    )
                }
            }
            return MahjongTableLocationsPersistentState(entries)
        }

        /** NBT list 欄位名稱。 */
        private const val NBT_KEY_ENTRIES: String = "Entries"

        /** 桌子 UUID 欄位名稱。 */
        private const val NBT_KEY_TABLE_ID: String = "TableId"

        /** Dimension identifier 欄位名稱。 */
        private const val NBT_KEY_DIMENSION: String = "Dimension"

        /** 方塊 X 座標欄位名稱。 */
        private const val NBT_KEY_X: String = "X"

        /** 方塊 Y 座標欄位名稱。 */
        private const val NBT_KEY_Y: String = "Y"

        /** 方塊 Z 座標欄位名稱。 */
        private const val NBT_KEY_Z: String = "Z"

        /** 位置 revision 欄位名稱。 */
        private const val NBT_KEY_REVISION: String = "Revision"
    }
}
