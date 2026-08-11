package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateRestorer
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.server.MinecraftServer
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

/** 將單次 Minecraft server session 與其 overworld [MahjongAuthoritativePersistentState] 接起來。 */
@Single
class FabricAuthoritativeStatePersistence(
    @Provided private val codec: AuthoritativeStatePersistenceCodec,
    private val store: AuthoritativeStateStore,
    private val stateRestorer: ServerSessionStateRestorer,
) {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 目前 server session 使用的 Minecraft persistent state。 */
    private var persistentState: MahjongAuthoritativePersistentState? = null

    /** 載入 [server] 所屬世界的權威狀態，並開始轉送後續 dirty snapshot。 */
    suspend fun attach(server: MinecraftServer) {
        check(persistentState == null) { "Authoritative persistence is already attached to a server session" }
        val state = server.overworld.persistentStateManager.getOrCreate(
            { nbt -> MahjongAuthoritativePersistentState.fromNbt(nbt, codec) },
            { MahjongAuthoritativePersistentState.create(codec) },
            MahjongAuthoritativePersistentState.STORAGE_KEY,
        )
        store.load(state.snapshot)
        val restoreResult = stateRestorer.restore(state.snapshot)
        restoreResult.membershipConflicts.forEach { conflict ->
            logger.warn(
                "Skipped restoring membership for player {} because saved state references multiple tables: {}",
                conflict.playerId,
                conflict.tableIds,
            )
        }
        store.setDirtyListener(state::update)
        persistentState = state
    }

    /** 停止 dirty 轉送；Minecraft 仍持有 state，會在既有世界保存流程中寫入磁碟。 */
    suspend fun detach() {
        store.setDirtyListener {}
        persistentState = null
    }
}
