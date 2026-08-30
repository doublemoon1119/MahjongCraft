package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DebugWinShowcaseOverride
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricMahjongLobbyInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.TableBreakPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.allowsTableBreak
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
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
    private val debugWinRoundContinuationState: DebugWinRoundContinuationState,
    private val debugWinShowcaseOverride: DebugWinShowcaseOverride,
    private val cleanupService: OrphanedTableCleanupService,
    private val configState: MinecraftServerConfigState,
    private val diceRollPresenter: MahjongDiceRollPresenter,
    private val tileWallPresenter: MahjongTileWallPresenter,
    private val playerAreaPresenter: MahjongPlayerAreaPresenter,
    private val scoringStickPresenter: MahjongScoringStickPresenter,
    private val discardPresenter: MahjongDiscardPresenter,
    private val roundInfoPresenter: MahjongRoundInfoPresenter,
    private val playerInfoPresenter: MahjongPlayerInfoPresenter,
    private val lobbyInfoPresenter: FabricMahjongLobbyInfoPresenter,
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
        val removedWallTileCount = tileWallPresenter.clear(table.tableId, tableLocation)
        val removedPlayerAreaTileCount = playerAreaPresenter.clear(table.tableId, tableLocation)
        val removedStickCount = scoringStickPresenter.clear(table.tableId, tableLocation)
        val removedDiscardTileCount = discardPresenter.clear(table.tableId, tableLocation)
        val removedRoundInfoCount = roundInfoPresenter.clear(table.tableId, tableLocation)
        val removedPlayerInfoCount = playerInfoPresenter.clear(table.tableId, tableLocation)
        val removedLobbyInfoCount = lobbyInfoPresenter.clear(table.tableId)
        val entry = locations.put(table.tableId, tableLocation)
        val result = runBlocking { cleanupService.cleanupMissing(table.tableId, entry.revision) }
        logger.debug(
            "Handled replaced Mahjong table {} with cleanup result {}, removed {} managed dice, {} managed wall tiles, {} managed player area tiles, {} managed sticks, {} managed discard tiles, {} managed round info displays, {} managed player info displays and {} managed lobby info displays",
            table.tableId,
            result,
            removedDiceCount,
            removedWallTileCount,
            removedPlayerAreaTileCount,
            removedStickCount,
            removedDiscardTileCount,
            removedRoundInfoCount,
            removedPlayerInfoCount,
            removedLobbyInfoCount,
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
        // 桌子已被移除：一併清掉這桌的開發用 debug 設定，不讓條目累積（這兩個容器只在開發環境
        // 才會有內容，見 DebugWinShowcaseOverride／DebugWinRoundContinuationState KDoc）。
        debugWinRoundContinuationState.clear(tableId)
        debugWinShowcaseOverride.clear(tableId)
        val result = cleanupService.cleanupPlayerBroken(tableId, entry.revision)
        logger.debug("Handled player-broken Mahjong table {} with cleanup result {}", tableId, result)
        return result
    }
}
