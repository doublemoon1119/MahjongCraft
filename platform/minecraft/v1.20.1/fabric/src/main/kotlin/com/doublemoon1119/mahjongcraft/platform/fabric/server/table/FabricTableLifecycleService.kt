package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.TableBreakPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.allowsTableBreak
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.runBlocking
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.block.BlockState
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldAccess
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/** 套用玩家破壞政策，並在成功破壞後同步清理桌子狀態。 */
@Single
class FabricTableLifecycleService(
    private val store: AuthoritativeStateStore,
    private val locations: TableLocationRegistry,
    private val cleanupService: OrphanedTableCleanupService,
    private val configState: MinecraftServerConfigState,
    private val diceRollPresenter: MahjongDiceRollPresenter,
) {
    /** 記錄麻將桌破壞政策判斷與清理入口。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 註冊 Fabric 玩家破壞前後事件。 */
    fun registerEvents() {
        PlayerBlockBreakEvents.BEFORE.register { world, _, pos, state, _ ->
            canBreak(world, pos, state)
        }
        PlayerBlockBreakEvents.AFTER.register { _, _, _, _, blockEntity ->
            val table = blockEntity as? MahjongTableBlockEntity ?: return@register
            runBlocking { cleanupPlayerBroken(table.tableId) }
        }
    }

    /** 非玩家來源替換麻將桌方塊時，依 orphan policy 清理相關資料。 */
    fun onBlockReplaced(world: ServerWorld, table: MahjongTableBlockEntity) {
        val tableLocation = world.toTableLocation(table.pos)
        val removedDiceCount = diceRollPresenter.clear(table.tableId, tableLocation)
        val entry = locations.put(table.tableId, tableLocation)
        val result = runBlocking { cleanupService.cleanupMissing(table.tableId, entry.revision) }
        logger.debug(
            "Handled replaced Mahjong table {} with cleanup result {} and removed {} managed dice",
            table.tableId,
            result,
            removedDiceCount,
        )
    }

    /** 由任意麻將桌 part 判斷玩家破壞是否可依目前政策執行。 */
    internal fun canBreak(world: WorldAccess, pos: BlockPos, state: BlockState): Boolean {
        val block = state.block as? MahjongTableBlock ?: return true
        val table = block.resolveController(world, pos, state)
        if (table == null) {
            logger.warn("Allowed removal of incomplete Mahjong table part at {} because its controller is missing", pos)
            return true
        }
        return runBlocking { canBreak(table) }
    }

    /** 依目前 Room／Game 與最新 [TableBreakPolicy] 判斷玩家能否破壞桌子。 */
    private suspend fun canBreak(table: MahjongTableBlockEntity): Boolean {
        val state = store.snapshot()
        val hasRoom = state.rooms.containsKey(table.tableId)
        val hasGame = state.games.containsKey(table.tableId)
        val policy = configState.current.tableBreakPolicy
        val allowed = policy.allowsTableBreak(hasRoom, hasGame)
        logger.debug("Mahjong table {} break allowed={} by policy {}", table.tableId, allowed, policy)
        return allowed
    }

    /** 玩家成功破壞後，不受 orphan policy 保留選項影響地清理已允許移除的桌子。 */
    internal suspend fun cleanupPlayerBroken(tableId: Uuid): OrphanedTableCleanupResult? {
        val entry = locations.get(tableId)
        if (entry == null) {
            logger.debug(
                "Skipped player-broken cleanup for Mahjong table {} because block replacement already removed its location",
                tableId,
            )
            return null
        }
        val result = cleanupService.cleanupPlayerBroken(tableId, entry.revision)
        logger.debug("Handled player-broken Mahjong table {} with cleanup result {}", tableId, result)
        return result
    }
}
