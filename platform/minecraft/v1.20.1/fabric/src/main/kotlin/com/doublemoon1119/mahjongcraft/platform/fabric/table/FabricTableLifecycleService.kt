package com.doublemoon1119.mahjongcraft.platform.fabric.table

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.allowsTableBreak
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.runBlocking
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.server.world.ServerWorld
import org.koin.core.annotation.Single

/** 套用玩家破壞政策，並在成功破壞後同步清理桌子狀態。 */
@Single
class FabricTableLifecycleService(
    private val store: AuthoritativeStateStore,
    private val locations: TableLocationRegistry,
    private val cleanupService: OrphanedTableCleanupService,
    private val config: MinecraftServerConfig,
) {
    /** 註冊 Fabric 玩家破壞前後事件。 */
    fun registerEvents() {
        PlayerBlockBreakEvents.BEFORE.register { _, _, _, _, blockEntity ->
            val table = blockEntity as? MahjongTableBlockEntity ?: return@register true
            runBlocking { canBreak(table) }
        }
        PlayerBlockBreakEvents.AFTER.register { world, _, _, _, blockEntity ->
            val serverWorld = world as? ServerWorld ?: return@register
            val table = blockEntity as? MahjongTableBlockEntity ?: return@register
            onPlayerBroken(serverWorld, table)
        }
    }

    /** 非玩家來源替換麻將桌方塊時，依 orphan policy 清理相關資料。 */
    fun onBlockReplaced(world: ServerWorld, table: MahjongTableBlockEntity) {
        val entry = locations.put(table.tableId, world.toTableLocation(table.pos))
        runBlocking { cleanupService.cleanupMissing(table.tableId, entry.revision) }
    }

    /** 依目前 Room／Game 與 [TableBreakPolicy] 判斷玩家能否破壞桌子。 */
    private suspend fun canBreak(table: MahjongTableBlockEntity): Boolean {
        val state = store.snapshot()
        val hasRoom = state.rooms.containsKey(table.tableId)
        val hasGame = state.games.containsKey(table.tableId)
        return config.tableBreakPolicy.allowsTableBreak(hasRoom, hasGame)
    }

    /** 玩家成功破壞後，不受 orphan policy 保留選項影響地清理已允許移除的桌子。 */
    private fun onPlayerBroken(world: ServerWorld, table: MahjongTableBlockEntity) {
        val entry = locations.put(table.tableId, world.toTableLocation(table.pos))
        runBlocking { cleanupService.cleanupPlayerBroken(table.tableId, entry.revision) }
    }
}
