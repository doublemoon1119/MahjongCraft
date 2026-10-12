package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.uuid.Uuid

/**
 * 判斷桌子是否仍有待執行的呈現工作、阻塞操作的時間軸或實體動畫。
 *
 * 待執行工作以 [beginPending] 的標記追蹤，避免實體尚未生成時誤判為不忙碌。
 * 動畫開始後，依桌子與實體保存的狀態判斷；獲准不阻塞整桌的中途和牌動畫不計入。
 * 查詢世界與實體的 [isBusy]、[isPresentingContinuingWin] 必須在伺服器主執行緒呼叫。
 *
 * @property serverHolder 目前伺服器與世界的查詢來源。
 * @property tableLocationRegistry 將桌子識別碼對應到最後已知位置。
 */
@Single
class TablePresentationBusyTracker(
    private val serverHolder: FabricServerHolder,
    private val tableLocationRegistry: TableLocationRegistry,
) {
    /** 依牌桌保存尚未完成的唯一 lease ID。 */
    private val pendingLeasesByTable: ConcurrentHashMap<Uuid, MutableSet<Long>> = ConcurrentHashMap()

    /** 跨 server session 單調遞增，避免舊 lease 遲到完成時命中新 session 的 ID。 */
    private val nextLeaseId = AtomicLong()

    /**
     * 建立一張屬於單一非同步呈現工作的 lease。
     *
     * @param tableId 需要暫時標記忙碌的牌桌識別碼。
     * @param operation 供診斷使用的工作名稱。
     * @return 完成時只會移除自己 token 的 lease。
     */
    fun beginPending(tableId: Uuid, operation: String): PendingLease {
        val leaseId = nextLeaseId.incrementAndGet()
        pendingLeasesByTable.computeIfAbsent(tableId) { ConcurrentHashMap.newKeySet() }.add(leaseId)
        return PendingLease(tableId, leaseId, operation)
    }

    /** 清除目前所有牌桌的 pending token；既有 lease 之後完成時不會影響新 lease。 */
    fun clearAll() {
        pendingLeasesByTable.clear()
    }

    /**
     * 一張只可完成一次的非同步呈現工作 lease。
     *
     * @property tableId lease 所屬桌子。
     * @property leaseId 此 tracker 配發的唯一 token。
     * @property operation 建立此 lease 時提供的診斷工作名稱。
     */
    inner class PendingLease internal constructor(
        private val tableId: Uuid,
        private val leaseId: Long,
        val operation: String,
    ) {
        /** 防止同一 lease 重複完成時再次存取 tracker。 */
        private val completed = AtomicBoolean(false)

        /** 完成此 lease；重複呼叫不會影響其他工作的 token。 */
        fun complete() {
            if (!completed.compareAndSet(false, true)) return
            pendingLeasesByTable.computeIfPresent(tableId) { _, tokens ->
                tokens.remove(leaseId)
                if (tokens.isEmpty()) null else tokens
            }
        }
    }

    /**
     * 在伺服器主執行緒查詢整桌是否仍被呈現工作或動畫占用。
     *
     * @param tableId 要查詢的桌子。
     * @return 有待執行工作、有效整桌時間軸或阻塞實體動畫時為 true；找不到世界且沒有待執行工作時為 false。
     */
    fun isBusy(tableId: Uuid): Boolean {
        if (!pendingLeasesByTable[tableId].isNullOrEmpty()) return true
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
     *
     * @param tableId 要查詢的桌子。
     * @return 對應桌子的中途和牌時間軸尚未結束時為 true；找不到桌子或世界時為 false。
     */
    fun isPresentingContinuingWin(tableId: Uuid): Boolean {
        val location = tableLocationRegistry.get(tableId)?.location ?: return false
        val world = resolveWorld(location) ?: return false
        val table = world.getBlockEntity(BlockPos(location.x, location.y, location.z)) as? MahjongTableBlockEntity
        return table?.tableId == tableId && table.isPresentingContinuingWin(world.time)
    }

    /**
     * 依桌子位置查詢目前伺服器的世界。
     *
     * @param location 提供維度識別碼的桌子位置。
     * @return 對應世界；維度識別碼無效、沒有伺服器或世界不存在時為 null。
     */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }

    /** 查詢桌子周圍受管理實體的範圍。 */
    private companion object {
        /** controller 周圍查詢管理中 entity 的水平半徑，跟各 presenter 既有查詢半徑一致。 */
        const val TABLE_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢管理中 entity 的垂直半徑，跟各 presenter 既有查詢半徑一致。 */
        const val TABLE_SEARCH_VERTICAL: Double = 2.0
    }
}
