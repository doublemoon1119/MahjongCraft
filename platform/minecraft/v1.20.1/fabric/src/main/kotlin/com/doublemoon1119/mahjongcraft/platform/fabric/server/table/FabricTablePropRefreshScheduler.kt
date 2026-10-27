package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.FabricGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropSearchArea
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * 在桌子及其規則物件搜尋範圍的 chunk 都可用後，重新請求桌面物件呈現。
 *
 * 不強制載入 chunk；實體尚未載入時下一個 tick 再試，已移除索引或世界的要求則捨棄。
 *
 * @property queue 桌子載入後產生的刷新請求。
 * @property locations 桌子的最後已知位置索引。
 * @property publisher 依最新桌況建立桌面物件呈現資料的 publisher。
 */
class FabricTablePropRefreshScheduler(
    private val queue: TablePropRefreshQueue,
    private val locations: TableLocationRegistry,
    private val publisher: FabricGamePresentationPublisher,
) {
    /** 目前接收刷新的伺服器 session。 */
    private var activeServer: MinecraftServer? = null

    /** 註冊伺服器 tick 與 session 生命週期。 */
    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
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
            if (!FabricTablePropSearchArea.entitiesLoaded(world, BlockPos(entry.location.x, entry.location.y, entry.location.z))) {
                queue.enqueue(server, tableId)
                return@forEach
            }
            publisher.publishRuleStateUpdated(tableId)
        }
    }

    /** 取得桌子所在的已載入世界。 */
    private fun resolveWorld(server: MinecraftServer, dimensionId: String): ServerWorld? {
        val id = Identifier.tryParse(dimensionId) ?: return null
        return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id))
    }
}
