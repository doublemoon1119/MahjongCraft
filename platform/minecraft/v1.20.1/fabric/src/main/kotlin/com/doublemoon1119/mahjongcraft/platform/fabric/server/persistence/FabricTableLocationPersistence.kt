package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.server.MinecraftServer
import org.koin.core.annotation.Single

/**
 * 將單次 Minecraft server session 與桌子位置 [MahjongTableLocationsPersistentState] 接起來。
 *
 * @property registry 桌子位置的記憶體索引。
 */
@Single
class FabricTableLocationPersistence(
    private val registry: TableLocationRegistry,
) {
    /** 用於回報目前無法解析的 dimension identifier。 */
    private val logger = mahjongCraftLogger(FabricTableLocationPersistence::class)

    /** 目前 server session 使用的位置 persistent state。 */
    private var persistentState: MahjongTableLocationsPersistentState? = null

    /**
     * 載入 [server] 的位置索引並開始轉送 dirty snapshot。
     *
     * @param server 要載入桌子位置索引的 Minecraft 伺服器。
     */
    fun attach(server: MinecraftServer) {
        check(persistentState == null) { "Table location persistence is already attached to a server session" }
        try {
            val state = attachPersistenceState(
                load = {
                    loadPersistentStateStrict(
                        server = server,
                        key = MahjongTableLocationsPersistentState.STORAGE_KEY,
                        decode = MahjongTableLocationsPersistentState::fromNbt,
                        create = MahjongTableLocationsPersistentState::create,
                    )
                },
                initialize = { state ->
                    registry.load(state.entries)
                    registry.setDirtyListener(state::update)
                    logger.debug("Attached table location persistence with {} saved location(s)", state.entries.size)

                    val availableDimensions = server.worlds.map { it.registryKey.value.toString() }.toSet()
                    registry.snapshot().values
                        .map { it.location.dimensionId }
                        .filterNot(availableDimensions::contains)
                        .distinct()
                        .forEach { dimensionId ->
                            logger.warn("Saved Mahjong table locations reference unavailable dimension {}", dimensionId)
                        }
                },
                cleanupFailedAttach = {
                    registry.setDirtyListener {}
                    registry.clear()
                },
            )
            persistentState = state
        } catch (exception: Exception) {
            logger.error("Failed to attach table location persistence for the current server session", exception)
            throw exception
        }
    }

    /** 解除 dirty listener 並清除目前 session 的位置索引記憶體。 */
    fun detach() {
        val locationCount = registry.snapshot().size
        registry.setDirtyListener {}
        registry.clear()
        persistentState = null
        logger.debug("Detached table location persistence and cleared {} location(s)", locationCount)
    }
}
