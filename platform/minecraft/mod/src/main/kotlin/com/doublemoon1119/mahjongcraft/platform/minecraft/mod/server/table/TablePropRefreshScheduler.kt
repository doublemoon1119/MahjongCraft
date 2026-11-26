package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.ServerThreadGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.prop.TablePropSearchArea
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel

/**
 * 在桌子及其規則物件搜尋範圍的 chunk 都可用後，重新請求桌面物件呈現。
 *
 * 不強制載入 chunk。實體尚未載入時下一個 tick 再試，已移除索引或世界的要求則捨棄。
 *
 * @property queue 桌子載入後產生的刷新請求。
 * @property locations 桌子的最後已知位置索引。
 * @property publisher 依最新桌況建立桌面物件呈現資料的 publisher。
 */
class TablePropRefreshScheduler(
    private val queue: TablePropRefreshQueue,
    private val locations: TableLocationRegistry,
    private val publisher: ServerThreadGamePresentationPublisher,
) {
    /** 目前接收刷新的伺服器 session。 */
    private var activeServer: MinecraftServer? = null

    /**
     * 註冊伺服器 tick 與 session 生命週期。
     *
     * @param events 登記事件用的伺服器事件來源。
     */
    fun registerEvents(events: ServerEvents) {
        events.onServerTickEnd { server ->
            if (activeServer === server) drain(server)
        }
    }

    /**
     * 開始新的伺服器 session。
     *
     * @param server 要接收刷新的伺服器。
     */
    fun startSession(server: MinecraftServer) {
        activeServer = server
    }

    /** 停止新的刷新請求並讓舊 session 的 tick 不再消費佇列。 */
    fun stopSession() {
        activeServer = null
    }

    /**
     * 在目前 tick 消費已可安全呈現的桌子。
     *
     * @param server 目前伺服器。
     */
    private fun drain(server: MinecraftServer) {
        queue.drain(server).forEach { tableId ->
            val entry = locations.get(tableId) ?: return@forEach
            val world = resolveWorld(server, entry.location.dimensionId) ?: return@forEach
            if (!TablePropSearchArea.entitiesLoaded(world, BlockPos(entry.location.x, entry.location.y, entry.location.z))) {
                queue.enqueue(server, tableId)
                return@forEach
            }
            publisher.refreshTableProps(tableId)
        }
    }

    /** 取得桌子所在的已載入世界。 */
    private fun resolveWorld(server: MinecraftServer, dimensionId: String): ServerLevel? {
        val id = ResourceLocation.tryParse(dimensionId) ?: return null
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id))
    }
}
