package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.server.MinecraftServer
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

/** 將單次 Minecraft server session 與桌子位置 [MahjongTableLocationsPersistentState] 接起來。 */
@Single
class FabricTableLocationPersistence(
    private val registry: TableLocationRegistry,
) {
    /** 用於回報目前無法解析的 dimension identifier。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 目前 server session 使用的位置 persistent state。 */
    private var persistentState: MahjongTableLocationsPersistentState? = null

    /** 載入 [server] 的位置索引並開始轉送 dirty snapshot。 */
    fun attach(server: MinecraftServer) {
        check(persistentState == null) { "Table location persistence is already attached to a server session" }
        val state = server.overworld.persistentStateManager.getOrCreate(
            MahjongTableLocationsPersistentState::fromNbt,
            MahjongTableLocationsPersistentState::create,
            MahjongTableLocationsPersistentState.STORAGE_KEY,
        )
        registry.load(state.entries)
        registry.setDirtyListener(state::update)
        persistentState = state
        logger.debug("Attached table location persistence with {} saved location(s)", state.entries.size)

        val availableDimensions = server.worlds.map { it.registryKey.value.toString() }.toSet()
        registry.snapshot().values
            .map { it.location.dimensionId }
            .filterNot(availableDimensions::contains)
            .distinct()
            .forEach { dimensionId ->
                logger.warn("Saved Mahjong table locations reference unavailable dimension {}", dimensionId)
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
