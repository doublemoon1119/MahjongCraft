package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.world.PersistentState
import org.slf4j.LoggerFactory
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

    /** 將完整位置索引連同格式版本寫入 Minecraft NBT。 */
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        nbt.putInt(NBT_KEY_VERSION, CURRENT_VERSION)
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
        private val logger = LoggerFactory.getLogger(MahjongTableLocationsPersistentState::class.java)

        /** `PersistentStateManager` 使用的世界存檔 key。 */
        const val STORAGE_KEY: String = "${MinecraftModMetadata.MOD_ID}_table_locations"

        /** 建立空的位置 state。 */
        fun create(): MahjongTableLocationsPersistentState = MahjongTableLocationsPersistentState(emptyList())

        /**
         * 從 Minecraft NBT 還原完整位置 state。
         *
         * 這份索引可以自行修復：每張桌子的 BlockEntity 載入後都會重新登記目前位置（見
         * `FabricTableLocationValidationService`），始終沒有出現的桌子則由缺失麻將桌清理流程依伺服器
         * 設定處理。因此無法解讀的資料一律略過並記錄，不讓世界載入失敗——擋下整個世界的代價遠高於
         * 暫時少幾筆索引。
         *
         * 沒有版本欄位的存檔視為加入版本欄位之前的格式，依相同欄位讀取；版本比程式新時整份略過，
         * 因為無法確定既有欄位的語意是否仍然相同。
         */
        fun fromNbt(nbt: NbtCompound): MahjongTableLocationsPersistentState {
            if (nbt.contains(NBT_KEY_VERSION) && !nbt.contains(NBT_KEY_VERSION, NbtElement.INT_TYPE.toInt())) {
                logger.error("Ignoring Mahjong table location index with an invalid format version type")
                return create()
            }
            val version = if (nbt.contains(NBT_KEY_VERSION)) nbt.getInt(NBT_KEY_VERSION) else LEGACY_VERSION
            if (version !in LEGACY_VERSION..CURRENT_VERSION) {
                logger.error(
                    "Ignoring Mahjong table location index with unsupported format version {} (supported: {})",
                    version,
                    CURRENT_VERSION,
                )
                return create()
            }
            if (!nbt.contains(NBT_KEY_ENTRIES, NbtElement.LIST_TYPE.toInt())) {
                if (nbt.contains(NBT_KEY_ENTRIES)) logger.error("Ignoring Mahjong table location index with an invalid entries type")
                return create()
            }
            val entriesNbt = nbt.getList(NBT_KEY_ENTRIES, NbtElement.COMPOUND_TYPE.toInt())
            val entries = buildList {
                val seenTableIds = mutableSetOf<Uuid>()
                repeat(entriesNbt.size) { index ->
                    val entryNbt = entriesNbt.getCompound(index)
                    val entry = runCatching {
                        require(entryNbt.contains(NBT_KEY_TABLE_ID, NbtElement.STRING_TYPE.toInt())) { "Missing or invalid $NBT_KEY_TABLE_ID" }
                        require(entryNbt.contains(NBT_KEY_DIMENSION, NbtElement.STRING_TYPE.toInt())) { "Missing or invalid $NBT_KEY_DIMENSION" }
                        require(entryNbt.contains(NBT_KEY_X, NbtElement.INT_TYPE.toInt())) { "Missing or invalid $NBT_KEY_X" }
                        require(entryNbt.contains(NBT_KEY_Y, NbtElement.INT_TYPE.toInt())) { "Missing or invalid $NBT_KEY_Y" }
                        require(entryNbt.contains(NBT_KEY_Z, NbtElement.INT_TYPE.toInt())) { "Missing or invalid $NBT_KEY_Z" }
                        require(entryNbt.contains(NBT_KEY_REVISION, NbtElement.LONG_TYPE.toInt())) { "Missing or invalid $NBT_KEY_REVISION" }
                        TableLocationEntry(
                            tableId = Uuid.parse(entryNbt.getString(NBT_KEY_TABLE_ID)),
                            location = TableLocation(
                                dimensionId = entryNbt.getString(NBT_KEY_DIMENSION),
                                x = entryNbt.getInt(NBT_KEY_X),
                                y = entryNbt.getInt(NBT_KEY_Y),
                                z = entryNbt.getInt(NBT_KEY_Z),
                            ),
                            revision = entryNbt.getLong(NBT_KEY_REVISION),
                        )
                    }.getOrElse { error ->
                        logger.error("Skipped an unreadable Mahjong table location entry at index {}", index, error)
                        return@repeat
                    }
                    if (seenTableIds.add(entry.tableId)) {
                        add(entry)
                    } else {
                        logger.error("Skipped duplicate Mahjong table location entry for {} at index {}", entry.tableId, index)
                    }
                }
            }
            return MahjongTableLocationsPersistentState(entries)
        }

        /** 這個索引目前的格式版本。 */
        const val CURRENT_VERSION: Int = 1

        /** 尚未加入版本欄位的存檔採用的版本。 */
        private const val LEGACY_VERSION: Int = 0

        /** 格式版本欄位名稱。 */
        const val NBT_KEY_VERSION: String = "Version"

        /** NBT list 欄位名稱。 */
        const val NBT_KEY_ENTRIES: String = "Entries"

        /** 桌子 UUID 欄位名稱。 */
        const val NBT_KEY_TABLE_ID: String = "TableId"

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
