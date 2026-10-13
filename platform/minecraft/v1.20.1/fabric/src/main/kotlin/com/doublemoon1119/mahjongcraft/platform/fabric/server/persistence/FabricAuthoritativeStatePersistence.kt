package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateRestorer
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.minecraft.server.MinecraftServer
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * 將單次 Minecraft server session 與其 overworld [MahjongAuthoritativePersistentState] 接起來。
 *
 * @property codec 編碼與解碼權威狀態的持久化 codec。
 * @property store 目前的權威狀態儲存庫。
 * @property stateRestorer 將持久化快照還原到領域儲存庫。
 * @property stateCleaner 初始化失敗時清除本次 session 的衍生與權威記憶體，不寫回存檔。
 */
@Single
class FabricAuthoritativeStatePersistence(
    @Provided private val codec: AuthoritativeStatePersistenceCodec,
    private val store: AuthoritativeStateStore,
    private val stateRestorer: ServerSessionStateRestorer,
    private val stateCleaner: ServerSessionStateCleaner,
) {
    private val logger = mahjongCraftLogger(FabricAuthoritativeStatePersistence::class)

    /** 目前 server session 使用的 Minecraft persistent state。 */
    private var persistentState: MahjongAuthoritativePersistentState? = null

    /**
     * 載入 [server] 所屬世界的權威狀態，並開始轉送後續 dirty snapshot。
     *
     * @param server 要載入權威狀態的 Minecraft 伺服器。
     */
    suspend fun attach(server: MinecraftServer) {
        check(persistentState == null) { "Authoritative persistence is already attached to a server session" }
        try {
            val state = attachPersistenceState(
                load = {
                    loadPersistentStateStrict(
                        server = server,
                        key = MahjongAuthoritativePersistentState.STORAGE_KEY,
                        decode = { nbt -> MahjongAuthoritativePersistentState.fromNbt(nbt, codec) },
                        create = { MahjongAuthoritativePersistentState.create(codec) },
                    )
                },
                initialize = { state ->
                    store.load(state.snapshot)
                    val restoreResult = stateRestorer.restore(state.snapshot)
                    restoreResult.membershipConflicts.forEach { conflict ->
                        logger.warn(
                            "Skipped restoring membership for player {} because saved state references multiple tables: {}",
                            conflict.playerId,
                            conflict.venueIds,
                        )
                    }
                    store.setDirtyListener(state::update)
                    logger.debug(
                        "Attached authoritative persistence with {} room(s), {} game(s), and {} membership conflict(s)",
                        state.snapshot.rooms.size,
                        state.snapshot.games.size,
                        restoreResult.membershipConflicts.size,
                    )
                    // 完整還原與診斷成功後才將合法的還原調整交給世界保存容器。
                    if (store.isDirty()) state.update(store.snapshot())
                },
                cleanupFailedAttach = {
                    withContext(NonCancellable) {
                        store.setDirtyListener {}
                        stateCleaner.clear()
                    }
                },
            )
            persistentState = state
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            logger.error("Failed to attach authoritative persistence for the current server session", exception)
            throw exception
        }
    }

    /** 停止 dirty 轉送；Minecraft 仍持有 state，會在既有世界保存流程中寫入磁碟。 */
    suspend fun detach() {
        store.setDirtyListener {}
        persistentState = null
        logger.debug("Detached authoritative persistence from the current server session")
    }
}
