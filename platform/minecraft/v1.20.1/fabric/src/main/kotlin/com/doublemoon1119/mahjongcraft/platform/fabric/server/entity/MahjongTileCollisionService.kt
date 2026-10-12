package com.doublemoon1119.mahjongcraft.platform.fabric.server.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.server.MinecraftServer
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * 將目前 server config 的物理碰撞政策套用至已載入及後續載入的麻將牌 entity。
 *
 * @property configState 目前有效的 server config 狀態。
 */
@Single
class MahjongTileCollisionService(
    @Provided private val configState: MinecraftServerConfigState,
) {
    /** 註冊 entity load callback，讓 chunk 載入與新放置牌張採用目前有效設定。 */
    fun registerEvents() {
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ ->
            if (entity is MahjongTileEntity) apply(entity, configState.current)
        }
    }

    /**
     * Reload 或 server 啟動載入設定後，一次更新所有維度中目前已載入的麻將牌。
     *
     * @param server 目前 Minecraft server。
     * @param config 要套用的有效設定。
     */
    fun applyToLoaded(server: MinecraftServer, config: MinecraftServerConfig) {
        server.worlds.forEach { world ->
            world.iterateEntities()
                .filterIsInstance<MahjongTileEntity>()
                .forEach { entity -> apply(entity, config) }
        }
    }

    /**
     * 將單張牌的碰撞追蹤資料更新為目前設定值。
     *
     * @param entity 要更新的麻將牌實體。
     * @param config 要套用的伺服器設定。
     */
    private fun apply(entity: MahjongTileEntity, config: MinecraftServerConfig) {
        entity.physicalCollisionEnabled = config.mahjongTilePhysicalCollisionEnabled
    }
}
