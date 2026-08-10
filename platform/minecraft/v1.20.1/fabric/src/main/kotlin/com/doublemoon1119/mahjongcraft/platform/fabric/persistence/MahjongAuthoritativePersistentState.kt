package com.doublemoon1119.mahjongcraft.platform.fabric.persistence

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.world.PersistentState

/**
 * 將完整權威狀態保存為單一 JSON 字串的 Minecraft 1.20.1 [PersistentState] adapter。
 *
 * schema migration 與領域 DTO mapping 全部交由 [codec]；此類別只處理 NBT 容器與 Minecraft dirty flag。
 */
class MahjongAuthoritativePersistentState private constructor(
    private val codec: AuthoritativeStatePersistenceCodec,
    initialSnapshot: AuthoritativeStateSnapshot,
) : PersistentState() {
    /** `writeNbt` 同步讀取的最新不可變權威狀態。 */
    @Volatile
    var snapshot: AuthoritativeStateSnapshot = initialSnapshot
        private set

    /** 更新待保存 snapshot，並通知 Minecraft 此 [PersistentState] 需要寫入磁碟。 */
    fun update(snapshot: AuthoritativeStateSnapshot) {
        this.snapshot = snapshot
        markDirty()
    }

    /** 將目前 snapshot 編碼至 [nbt]。 */
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        nbt.putString(
            NBT_KEY_STATE,
            codec.encode(snapshot.rooms.values, snapshot.games.values),
        )
        return nbt
    }

    /** 建立與讀取 [MahjongAuthoritativePersistentState] 的固定 metadata。 */
    companion object {
        /** `PersistentStateManager` 使用的世界存檔 key。 */
        const val STORAGE_KEY: String = "${MinecraftModMetadata.MOD_ID}_authoritative_state"

        /** NBT 中保存 codec JSON 的欄位名稱。 */
        private const val NBT_KEY_STATE: String = "state"

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
            if (!nbt.contains(NBT_KEY_STATE, NbtElement.STRING_TYPE.toInt())) return create(codec)

            val decoded = codec.decode(nbt.getString(NBT_KEY_STATE))
            return MahjongAuthoritativePersistentState(
                codec,
                AuthoritativeStateSnapshot(decoded.rooms, decoded.games),
            )
        }
    }
}
