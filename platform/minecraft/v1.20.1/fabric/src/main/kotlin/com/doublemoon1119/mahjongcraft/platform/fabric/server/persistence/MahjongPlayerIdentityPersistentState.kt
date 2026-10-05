package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.world.PersistentState
import java.util.UUID

/**
 * 以伺服器存檔根目錄為範圍保存玩家 UUID 與最後已知普通名稱的可重建索引。
 *
 * @param initialNames 從既有世界存檔載入的玩家名稱對應。
 */
class MahjongPlayerIdentityPersistentState private constructor(
    initialNames: Map<UUID, String>,
) : PersistentState() {
    /** 目前已知的玩家普通名稱，不含聊天樣式或互動資料。 */
    private val names = initialNames.toMutableMap()

    /**
     * 取得供非持久化呈現服務使用的不可變名稱快照。
     *
     * @return 玩家 UUID 與最後已知普通名稱的對應快照。
     */
    fun snapshot(): Map<UUID, String> = names.toMap()

    /**
     * 取得指定 UUID 的最後已知名稱。
     *
     * @param playerId 玩家 UUID。
     * @return 最後已知普通名稱；尚未記錄時為 null。
     */
    fun nameOf(playerId: UUID): String? = names[playerId]

    /**
     * 更新玩家名稱並標記世界存檔需要寫入。
     *
     * @param playerId 玩家 UUID。
     * @param name 玩家普通名稱。
     * @return 名稱有變更且已寫入索引時為 true。
     */
    fun remember(playerId: UUID, name: String): Boolean {
        val normalized = name.trim()
        if (normalized.isEmpty() || normalized.length > MAX_NAME_LENGTH) return false
        if (names[playerId] == normalized) return false
        names[playerId] = normalized
        markDirty()
        return true
    }

    /**
     * 將索引連同獨立格式版本寫入 NBT。
     *
     * @param nbt Minecraft 要寫入的 NBT compound。
     * @return 寫入完成的 [nbt]。
     */
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        nbt.putInt(NBT_KEY_VERSION, CURRENT_VERSION)
        val entries = NbtList()
        names.forEach { (playerId, name) ->
            entries.add(
                NbtCompound().apply {
                    putString(NBT_KEY_PLAYER_ID, playerId.toString())
                    putString(NBT_KEY_NAME, name)
                },
            )
        }
        nbt.put(NBT_KEY_ENTRIES, entries)
        return nbt
    }

    companion object {
        private val logger = mahjongCraftLogger(MahjongPlayerIdentityPersistentState::class)

        /** Minecraft PersistentStateManager 使用的伺服器級索引 key。 */
        const val STORAGE_KEY: String = "${MinecraftModMetadata.MOD_ID}_player_identity"

        /** 建立空白身分索引。 */
        fun create(): MahjongPlayerIdentityPersistentState = MahjongPlayerIdentityPersistentState(emptyMap())

        /**
         * 從可重建 NBT 索引載入玩家名稱；單筆錯誤不影響世界載入。
         *
         * @param nbt Minecraft 讀出的身分索引 NBT。
         * @return 載入完成的身分索引；格式錯誤時回傳空索引。
         */
        fun fromNbt(nbt: NbtCompound): MahjongPlayerIdentityPersistentState {
            if (!nbt.contains(NBT_KEY_VERSION, NbtElement.INT_TYPE.toInt())) {
                logger.error("Ignoring player identity index because the format version is missing")
                return create()
            }
            val version = nbt.getInt(NBT_KEY_VERSION)
            if (version != CURRENT_VERSION) {
                logger.error("Ignoring player identity index with unsupported format version {}", version)
                return create()
            }
            if (!nbt.contains(NBT_KEY_ENTRIES, NbtElement.LIST_TYPE.toInt())) {
                logger.error("Ignoring player identity index because the entries field is missing or invalid")
                return create()
            }
            val names = buildMap {
                val entries = nbt.getList(NBT_KEY_ENTRIES, NbtElement.COMPOUND_TYPE.toInt())
                repeat(entries.size) { index ->
                    runCatching {
                        val entry = entries.getCompound(index)
                        require(entry.contains(NBT_KEY_PLAYER_ID, NbtElement.STRING_TYPE.toInt()))
                        require(entry.contains(NBT_KEY_NAME, NbtElement.STRING_TYPE.toInt()))
                        val playerId = UUID.fromString(entry.getString(NBT_KEY_PLAYER_ID))
                        val name = entry.getString(NBT_KEY_NAME).trim()
                        require(name.isNotEmpty() && name.length <= MAX_NAME_LENGTH)
                        put(playerId, name)
                    }.onFailure { error ->
                        logger.error("Skipped an unreadable player identity entry at index {}", index, error)
                    }
                }
            }
            return MahjongPlayerIdentityPersistentState(names)
        }

        /** 目前的身分索引格式版本。 */
        const val CURRENT_VERSION: Int = 1

        /** Minecraft 普通玩家名稱允許的最大字元數。 */
        private const val MAX_NAME_LENGTH = 16

        /** NBT 格式版本欄位名稱。 */
        private const val NBT_KEY_VERSION = "Version"

        /** NBT 玩家項目清單欄位名稱。 */
        private const val NBT_KEY_ENTRIES = "Entries"

        /** NBT 玩家 UUID 欄位名稱。 */
        private const val NBT_KEY_PLAYER_ID = "PlayerId"

        /** NBT 普通名稱欄位名稱。 */
        private const val NBT_KEY_NAME = "Name"
    }
}
