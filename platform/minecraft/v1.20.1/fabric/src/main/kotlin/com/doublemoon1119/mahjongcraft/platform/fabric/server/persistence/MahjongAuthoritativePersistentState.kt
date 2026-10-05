package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.world.PersistentState

/**
 * 將完整權威狀態保存為單一 UTF-8 JSON 位元組陣列的 Minecraft 1.20.1 [PersistentState] adapter。
 *
 * NBT 字串最多只能保存 65,535 bytes，超過時原版會改寫成空字串；位元組陣列以 int 記錄長度，不受此限制。
 * schema migration 與領域 DTO mapping 全部交由 [codec]；此類別只處理 NBT 容器與 Minecraft dirty flag。
 */
class MahjongAuthoritativePersistentState private constructor(
    private val codec: AuthoritativeStatePersistenceCodec,
    initialSnapshot: AuthoritativeStateSnapshot,
) : PersistentState() {
    /** 權威狀態持久化的 logger。 */
    private val logger = mahjongCraftLogger(MahjongAuthoritativePersistentState::class)

    /** `writeNbt` 同步讀取的最新不可變權威狀態。 */
    @Volatile
    var snapshot: AuthoritativeStateSnapshot = initialSnapshot
        private set

    /** 更新待保存 snapshot，並通知 Minecraft 此 [PersistentState] 需要寫入磁碟。 */
    fun update(snapshot: AuthoritativeStateSnapshot) {
        this.snapshot = snapshot
        markDirty()
    }

    /** 將目前 snapshot 編碼至 [nbt]；編碼結果過大時記錄警告，仍完整寫入。 */
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        val current = snapshot
        val encoded = codec.encode(current.rooms.values, current.games.values, current.historyRecordingState).encodeToByteArray()
        if (encoded.size > LARGE_STATE_WARNING_BYTES) {
            logger.warn(
                "Authoritative state is unusually large and slows world saving: bytes={}, rooms={}, games={}, pendingHistoryEvents={}",
                encoded.size,
                current.rooms.size,
                current.games.size,
                current.historyRecordingState.pendingEvents.size,
            )
        }
        nbt.putByteArray(NBT_KEY_STATE, encoded)
        return nbt
    }

    /** 建立與讀取 [MahjongAuthoritativePersistentState] 的固定 metadata。 */
    companion object {
        /** `PersistentStateManager` 使用的世界存檔 key。 */
        const val STORAGE_KEY: String = "${MinecraftModMetadata.MOD_ID}_authoritative_state"

        /** 編碼後超過此大小時記錄警告的門檻。 */
        internal const val LARGE_STATE_WARNING_BYTES: Int = 1024 * 1024

        /** NBT 中保存 codec JSON UTF-8 位元組的欄位名稱。 */
        internal const val NBT_KEY_STATE: String = "state"

        /** 建立沒有既有存檔的空狀態。 */
        fun create(codec: AuthoritativeStatePersistenceCodec): MahjongAuthoritativePersistentState = MahjongAuthoritativePersistentState(
            codec,
            AuthoritativeStateSnapshot(),
        )

        /** 從 [nbt] 載入既有狀態；沒有 payload 時視為空狀態。 */
        fun fromNbt(
            nbt: NbtCompound,
            codec: AuthoritativeStatePersistenceCodec,
        ): MahjongAuthoritativePersistentState {
            if (!nbt.contains(NBT_KEY_STATE, NbtElement.BYTE_ARRAY_TYPE.toInt())) return create(codec)

            val decoded = codec.decode(nbt.getByteArray(NBT_KEY_STATE).decodeToString())
            return MahjongAuthoritativePersistentState(
                codec,
                AuthoritativeStateSnapshot(decoded.rooms, decoded.games, decoded.historyRecordingState),
            )
        }
    }
}
