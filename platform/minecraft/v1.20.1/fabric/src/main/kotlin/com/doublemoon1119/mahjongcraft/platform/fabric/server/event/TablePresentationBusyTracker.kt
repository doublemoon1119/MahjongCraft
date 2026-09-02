package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.AnimatedMahjongEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.DiceRollPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 記錄「哪些桌子目前正在播放呈現動畫（擲骰、建牆、發牌…）」的本地狀態，供
 * [FabricGamePresentationPublisher] 查詢、供輸入分派入口（`MahjongTableGameActionService`）與自動操作
 * 心跳（`FabricDecisionTimerScheduler`）查詢是否要暫時擋下這桌的操作——動畫播放期間刻意不讓玩家操作
 * 或 AI／強制自動操作搶在畫面之前推進，避免出現牌局狀態跟畫面不一致的情況。
 *
 * `isBusy` 先查詢 controller [MahjongTableBlockEntity] 的持久化整桌呈現時間軸，再查詢這桌
 * **目前管理中的 entity 是否還有任何一個 [AnimatedMahjongEntity.isAnimating]**
 * （掃描範圍與篩選方式比照各 presenter 既有的 `findManagedTiles`／`findManagedDice`），不再像過去那樣
 * 由呼叫端手動算一次「這批動畫總共要幾個 tick」再呼叫 `markBusyFor`——動畫佇列本身（見
 * [AnimatedMahjongEntity]）已經是「這桌是否還在忙」唯一需要的資訊來源，重複算一次總時長只是把同一份
 * 資訊記錄兩遍，且過去那份記錄是純記憶體、撐不過伺服器重啟；改成直接查詢佇列後，「忙碌」狀態自動
 * 繼承佇列本身的持久化正確性——伺服器重啟後如果佇列還沒播完，`isBusy` 依然正確回傳 `true`。
 *
 * [markPending]／[clearPending] 額外覆蓋一個窄範圍的情境：[FabricGamePresentationPublisher] 的
 * `publishXxx` 方法把實際呈現（生成/移動 entity）丟回伺服器主執行緒非同步執行（`scope.launch`），
 * 呼叫端（`AdvanceRoundUseCase`／`StartGameUseCase`）呼叫完 `publishXxx` 就會緊接著繼續走自己的自動
 * 連鎖（莊家自動摸牌、開始思考計時器）——如果這時候動畫用的 entity 還沒真正生成（非同步工作還沒
 * 排到），單純掃描 entity 會查無所獲、誤判成不忙碌，自動連鎖就會搶在畫面之前推進。[markPending] 由
 * `publishXxx` 在方法最前面同步呼叫，涵蓋「已經決定要呈現、但 entity 還沒生成」這段極短暫的窗口；
 * [clearPending] 在非同步工作真正執行後呼叫（不論成功或失敗），之後就完全交給 entity 掃描判斷。這段
 * pending 標記不需要撐過伺服器重啟——非同步排程的窗口本身就不可能跨越一次伺服器重啟。
 */
@Single
class TablePresentationBusyTracker(
    private val serverHolder: FabricServerHolder,
    private val tableLocationRegistry: TableLocationRegistry,
) {
    private val pendingTableIds: MutableSet<Uuid> = ConcurrentHashMap.newKeySet()

    /** 標記 [tableId] 有一段呈現工作已經排定、但 entity 可能還沒生成，見類別 KDoc。 */
    fun markPending(tableId: Uuid) {
        pendingTableIds += tableId
    }

    /** 見 [markPending] KDoc；非同步呈現工作真正執行後呼叫，不論成功或失敗。 */
    fun clearPending(tableId: Uuid) {
        pendingTableIds -= tableId
    }

    /** [tableId] 目前是否仍在忙碌中。 */
    fun isBusy(tableId: Uuid): Boolean {
        if (tableId in pendingTableIds) return true
        val location = tableLocationRegistry.get(tableId)?.location ?: return false
        val world = resolveWorld(location) ?: return false
        val controllerPos = BlockPos(location.x, location.y, location.z)
        val table = world.getBlockEntity(controllerPos) as? MahjongTableBlockEntity
        if (table?.tableId == tableId && table.isPresenting(world.time)) return true
        val searchBox = Box(controllerPos).expand(TABLE_SEARCH_HORIZONTAL, TABLE_SEARCH_VERTICAL, TABLE_SEARCH_HORIZONTAL)
        // 用 blocksTableBusy 而不是直接看 isAnimating：中途胡牌會在已完成玩家的真實牌上排入理牌、
        // 倒牌、隱形、恢復顯示與蓋牌動畫，那些動畫持有「不阻塞全桌」的 lease，不該擋住其他仍在本局
        // 中的玩家（見 MahjongTileEntity.nonBlockingPresentationUntilGameTime）。沒有 lease 的動畫
        // （發牌、摸牌、捨牌等）一律讓整桌忙碌。
        val tileAnimating = world.getEntitiesByClass(MahjongTileEntity::class.java, searchBox) { tile ->
            tile.managedTableId == tableId && tile.blocksTableBusy(world.time)
        }.isNotEmpty()
        if (tileAnimating) return true
        val diceAnimating = world.getEntitiesByClass(MahjongDiceEntity::class.java, searchBox) { dice ->
            dice.managedTableId == tableId && dice.isAnimating
        }.isNotEmpty()
        if (diceAnimating) return true
        return world.getEntitiesByClass(DiceRollPresentationEntity::class.java, searchBox) { stage ->
            stage.managedTableId == tableId && world.time < stage.endGameTime
        }.isNotEmpty()
    }

    /**
     * [tableId] 是否仍在播放中途胡牌演出。
     *
     * 刻意只看 [MahjongTableBlockEntity.continuingWinPresentationBusyUntilGameTime] 這條獨立時間軸，
     * 完全不看 [isBusy] 會看的實體動畫與整桌時間軸——中途胡牌演出的用意就是「不擋其他人繼續打」，
     * 只用來讓本局延後換局。
     */
    fun isPresentingContinuingWin(tableId: Uuid): Boolean {
        val location = tableLocationRegistry.get(tableId)?.location ?: return false
        val world = resolveWorld(location) ?: return false
        val table = world.getBlockEntity(BlockPos(location.x, location.y, location.z)) as? MahjongTableBlockEntity
        return table?.tableId == tableId && table.isPresentingContinuingWin(world.time)
    }

    /** 由版本無關 dimension ID 取得目前 server session 的世界。 */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }

    private companion object {
        /** controller 周圍查詢管理中 entity 的水平半徑，跟各 presenter 既有查詢半徑一致。 */
        const val TABLE_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢管理中 entity 的垂直半徑，跟各 presenter 既有查詢半徑一致。 */
        const val TABLE_SEARCH_VERTICAL: Double = 2.0
    }
}
