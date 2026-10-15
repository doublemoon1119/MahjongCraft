package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinShowcaseOverride
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricMahjongLobbyInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.TableBreakPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.allowsTableBreak
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTileSelectionConfirmPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableCornerWidthTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.block.BlockState
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldAccess
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 套用玩家破壞政策，並在成功破壞後清理桌子狀態。
 *
 * 破壞與替換事件在伺服器主執行緒同步觸發：世界中的呈現當場清除，權威清理排入 [cleanupQueue] 後立即返回，
 * 不在主執行緒等待權威狀態的鎖。
 *
 * @property store 讀取已發布的權威房間與對局狀態。
 * @property locations 查詢桌子位置索引。
 * @property debugWinRoundContinuationState 清除桌子專屬的開發用續局設定。
 * @property debugWinShowcaseOverride 清除桌子專屬的開發用胡牌展示設定。
 * @property cleanupService 執行缺失桌子的權威資料清理。
 * @property cleanupQueue 依序執行權威清理，不讓主執行緒等待。
 * @property configState 讀取目前破壞政策。
 * @property diceRollPresenter 清理桌子的骰子呈現。
 * @property tileWallPresenter 清理桌子的牌牆。
 * @property playerAreaPresenter 清理桌子的手牌與副露。
 * @property tablePropPresenter 清理桌面規則物件。
 * @property tableCornerWidths 清除桌面角落寬度追蹤。
 * @property discardPresenter 清理桌子的牌河。
 * @property roundInfoPresenter 清理局況 overlay。
 * @property playerInfoPresenter 清理玩家資訊 overlay。
 * @property lobbyInfoPresenter 清理等待室提示。
 * @property tileSelectionConfirmPresenter 清理選牌確認面板。
 * @property presentationCleaner 清理臨時呈現與演出。
 */
@Single
class FabricTableLifecycleService(
    private val store: AuthoritativeStateStore,
    private val locations: TableLocationRegistry,
    private val debugWinRoundContinuationState: DebugWinRoundContinuationState,
    private val debugWinShowcaseOverride: DebugWinShowcaseOverride,
    private val cleanupService: OrphanedTableCleanupService,
    private val cleanupQueue: TableCleanupQueue,
    private val configState: MinecraftServerConfigState,
    private val diceRollPresenter: MahjongDiceRollPresenter,
    private val tileWallPresenter: MahjongTileWallPresenter,
    private val playerAreaPresenter: MahjongPlayerAreaPresenter,
    private val tablePropPresenter: TablePropPresenter,
    private val tableCornerWidths: TableCornerWidthTracker,
    private val discardPresenter: MahjongDiscardPresenter,
    private val roundInfoPresenter: MahjongRoundInfoPresenter,
    private val playerInfoPresenter: MahjongPlayerInfoPresenter,
    private val lobbyInfoPresenter: FabricMahjongLobbyInfoPresenter,
    private val tileSelectionConfirmPresenter: MahjongTileSelectionConfirmPresenter,
    private val presentationCleaner: FabricTablePresentationCleaner,
) {
    /** 記錄麻將桌破壞政策判斷與清理入口。 */
    private val logger = mahjongCraftLogger(FabricTableLifecycleService::class)

    /** 註冊 Fabric 玩家破壞前後事件。 */
    fun registerEvents() {
        PlayerBlockBreakEvents.BEFORE.register { world, _, pos, state, _ ->
            canBreak(world, pos, state)
        }
        PlayerBlockBreakEvents.AFTER.register { _, _, _, _, blockEntity ->
            val table = blockEntity as? MahjongTableBlockEntity ?: return@register
            onPlayerBroken(table.tableId)
        }
    }

    /**
     * 玩家成功破壞後，當場記下目前的位置索引項目，再排入不受 orphan policy 保留選項影響的權威清理。
     *
     * 清理只針對破壞當下的那個位置項目；排隊期間同一 UUID 的桌子重新登記時，清理會視為過期而不動新來源。
     *
     * @param tableId 玩家破壞的桌子 UUID。
     */
    internal fun onPlayerBroken(tableId: Uuid) {
        val entry = locations.get(tableId)
        if (entry == null) {
            logger.debug(
                "Skipped player-broken cleanup for Mahjong table {} because block replacement already removed its location",
                tableId,
            )
            return
        }
        cleanupQueue.submit(description = "player-broken table $tableId") { cleanupPlayerBroken(entry) }
    }

    /**
     * 非玩家來源替換麻將桌方塊時，當場清除世界中的呈現，並排入依 orphan policy 進行的權威清理。
     *
     * @param world 被替換方塊所在世界。
     * @param table 被替換的桌子 controller block entity。
     */
    fun onBlockReplaced(world: ServerWorld, table: MahjongTableBlockEntity) {
        val tableId = table.tableId
        val tableLocation = world.toTableLocation(table.pos)
        val removedDiceCount = diceRollPresenter.clear(tableId, tableLocation)
        val removedWallTileCount = tileWallPresenter.clear(tableId, tableLocation)
        val removedPlayerAreaTileCount = playerAreaPresenter.clear(tableId, tableLocation)
        val removedTablePropCount = tablePropPresenter.clear(tableId, tableLocation)
        tableCornerWidths.clear(tableId)
        val removedDiscardTileCount = discardPresenter.clear(tableId, tableLocation)
        val removedRoundInfoCount = roundInfoPresenter.clear(tableId, tableLocation)
        val removedPlayerInfoCount = playerInfoPresenter.clear(tableId, tableLocation)
        val removedLobbyInfoCount = lobbyInfoPresenter.clear(tableId)
        val removedTileSelectionConfirmCount = tileSelectionConfirmPresenter.clear(tableId, tableLocation)
        val removedTransientPresentationCount = presentationCleaner.clear(world, tableId, table.pos)
        val entry = locations.put(tableId, tableLocation)
        cleanupQueue.submit(description = "replaced table $tableId") {
            val result = cleanupService.cleanupMissing(entry)
            logger.debug("Handled replaced Mahjong table {} with cleanup result {}", tableId, result)
        }
        logger.debug(
            "Cleared replaced Mahjong table {} presentation: removed {} managed dice, {} managed wall tiles, {} managed player area tiles, {} managed table props, {} managed discard tiles, {} managed round info displays, {} managed player info displays, {} managed lobby info displays, {} managed tile selection confirm panels and {} transient presentations",
            tableId,
            removedDiceCount,
            removedWallTileCount,
            removedPlayerAreaTileCount,
            removedTablePropCount,
            removedDiscardTileCount,
            removedRoundInfoCount,
            removedPlayerInfoCount,
            removedLobbyInfoCount,
            removedTileSelectionConfirmCount,
            removedTransientPresentationCount,
        )
    }

    /**
     * 由任意麻將桌 part 判斷玩家破壞是否可依目前政策執行。
     *
     * @param world 方塊所在世界。
     * @param pos 正在破壞的桌子部件座標。
     * @param state 該位置的方塊狀態。
     * @return 目前破壞政策允許時為 true。
     */
    internal fun canBreak(world: WorldAccess, pos: BlockPos, state: BlockState): Boolean {
        val block = state.block as? MahjongTableBlock ?: return true
        val table = block.resolveController(world, pos, state)
        if (table == null) {
            logger.warn("Allowed removal of incomplete Mahjong table part at {} because its controller is missing", pos)
            return true
        }
        return isBreakAllowed(table.tableId)
    }

    /**
     * 依已發布的 Room／Game 與最新 [TableBreakPolicy] 判斷玩家能否破壞桌子，不等待權威狀態的鎖。
     *
     * @param tableId 要判斷的桌子 UUID。
     * @return 依狀態與政策決定是否允許破壞。
     */
    internal fun isBreakAllowed(tableId: Uuid): Boolean {
        val state = store.state.value
        val hasRoom = state.rooms.containsKey(tableId)
        val hasGame = state.games.containsKey(tableId)
        val policy = configState.current.tableBreakPolicy
        val allowed = policy.allowsTableBreak(hasRoom, hasGame)
        logger.debug("Mahjong table {} break allowed={} by policy {}", tableId, allowed, policy)
        return allowed
    }

    /**
     * 對玩家破壞當下的位置索引項目執行清理；清理未過期時一併清掉這桌的開發用 debug 設定。
     *
     * @param entry 玩家破壞當下觀察到的位置索引項目。
     * @return 清理結果；位置索引已不再是同一個項目時為 [OrphanedTableCleanupResult.STALE_REQUEST]。
     */
    internal suspend fun cleanupPlayerBroken(entry: TableLocationEntry): OrphanedTableCleanupResult {
        val result = cleanupService.cleanupPlayerBroken(entry)
        if (result != OrphanedTableCleanupResult.STALE_REQUEST) {
            // 這兩個容器只在開發環境才會有內容，見 DebugWinShowcaseOverride／DebugWinRoundContinuationState KDoc。
            debugWinRoundContinuationState.clear(entry.tableId)
            debugWinShowcaseOverride.clear(entry.tableId)
        }
        logger.debug("Handled player-broken Mahjong table {} with cleanup result {}", entry.tableId, result)
        return result
    }
}
