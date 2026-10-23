package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 決定桌角物件寬度改變時牌區怎麼配合：擺牌區時用哪個寬度、規則狀態更新後哪些座位要一起平移、物件先擺還是等平移結束再擺。
 *
 * 桌角寬度可能在一局中改變，例如規則在局中把點數放進供託。這裡只依權威桌況算出的寬度、[tracker] 記下的牌區寬度與進行中的
 * 平移做決定，不接觸世界物件；平台照決定擺放，並以 [awaitSlideEnd] 等平移的牌全部停下。寬度一律由呼叫端以規則登記的桌上
 * 物件描述算出後傳入。只在伺服器主執行緒使用。
 *
 * @property tracker 每張桌子目前桌上物件佔用的角落寬度。
 */
class TableCornerLayoutPlanner(
    private val tracker: TableCornerWidthTracker,
) {
    /** 每張桌子進行中的平移；平移的牌全部停下、由最後一次更新回報結束後移除。 */
    private val slidesByTableId = mutableMapOf<Uuid, SlideInProgress>()

    /** 下一次平移的識別碼。 */
    private var nextSlideId = 0L

    /**
     * 一張桌子進行中的平移。
     *
     * @property id 最後一次接手等待的更新拿到的識別碼。
     * @property clearWidthsBySeat 平移開始以來各座位牌區不會碰到的角落寬度，也就是途中用過的寬度中最小的。
     */
    private data class SlideInProgress(val id: Long, val clearWidthsBySeat: Map<Int, Double>)

    /**
     * 開局發牌時記下這一局桌上物件的寬度。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableState 新一局的權威桌況。
     * @param widthsBySeat 依權威桌況算出的各座位角落寬度。
     */
    fun recordRound(tableId: Uuid, tableState: TableState, widthsBySeat: Map<Int, Double>) {
        tracker.record(tableId, TableRound.of(tableState), widthsBySeat)
        slidesByTableId.remove(tableId)
    }

    /**
     * 擺一個座位的牌區時要使用的角落寬度，並記下這個座位已依這個寬度擺放。
     *
     * 取這一局已擺上的寬度與權威桌況寬度較大者：桌上物件只在規則狀態更新時同步，物件即將變多時先依新寬度讓開；物件已不在
     * 權威桌況、但還沒從桌上收走時（例如和牌收走供託後，點棒到下一局才移除）維持原本的寬度。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableState 權威桌況。
     * @param seatIndex 要擺放的座位。
     * @param currentWidthsBySeat 依權威桌況算出的各座位角落寬度。
     * @return 這個座位使用的角落寬度。
     */
    fun widthForPlayerArea(
        tableId: Uuid,
        tableState: TableState,
        seatIndex: Int,
        currentWidthsBySeat: Map<Int, Double>,
    ): Double {
        val round = TableRound.of(tableState)
        val width = maxOf(tracker.find(tableId, round)?.get(seatIndex) ?: 0.0, currentWidthsBySeat[seatIndex] ?: 0.0)
        tracker.recordSeat(tableId, round, seatIndex, width)
        return width
    }

    /**
     * 規則狀態更新、桌上物件要同步成 [currentWidthsBySeat] 時，牌區要怎麼配合，並記下新的寬度。
     *
     * 只有這一局已經擺過、且寬度不同的座位需要平移；還沒擺過的座位之後擺放時就會使用新寬度。物件寬度不超過牌區途中用過的
     * 每個寬度時才立刻擺上，例如變窄時先收走物件再平移；否則新物件的位置正是牌區原本佔用、或正在移動經過的地方，要等所有
     * 平移結束才擺上。前一次平移還沒結束時，這次更新接手等待，前一次的回報不再擺物件。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableState 權威桌況。
     * @param currentWidthsBySeat 依權威桌況算出的各座位角落寬度。
     * @return 這次同步的做法。
     */
    fun planRuleStateUpdate(
        tableId: Uuid,
        tableState: TableState,
        currentWidthsBySeat: Map<Int, Double>,
    ): CornerPropsUpdatePlan {
        val round = TableRound.of(tableState)
        val previousWidths = tracker.find(tableId, round).orEmpty()
        tracker.record(tableId, round, currentWidthsBySeat)
        val slidingSeatWidths = currentWidthsBySeat.filter { (seatIndex, width) ->
            previousWidths[seatIndex]?.let { previous -> previous != width } == true
        }
        val ongoing = slidesByTableId[tableId]
        if (slidingSeatWidths.isEmpty() && ongoing == null) {
            return CornerPropsUpdatePlan(slidingSeatWidths = slidingSeatWidths, presentsPropsAfterSlide = false, slideId = null)
        }
        val clearWidthsBySeat = (previousWidths.keys + ongoing?.clearWidthsBySeat?.keys.orEmpty()).associateWith { seatIndex ->
            listOfNotNull(previousWidths[seatIndex], currentWidthsBySeat[seatIndex], ongoing?.clearWidthsBySeat?.get(seatIndex)).min()
        }
        val slide = SlideInProgress(id = nextSlideId++, clearWidthsBySeat = clearWidthsBySeat)
        slidesByTableId[tableId] = slide
        return CornerPropsUpdatePlan(
            slidingSeatWidths = slidingSeatWidths,
            presentsPropsAfterSlide = currentWidthsBySeat.any { (seatIndex, width) ->
                clearWidthsBySeat[seatIndex]?.let { clear -> width > clear } == true
            },
            slideId = slide.id,
        )
    }

    /**
     * 等 [CornerPropsUpdatePlan.slideId] 那次更新之後桌上的牌全部停下，並結束這次平移。
     *
     * 每個 tick 檢查一次、沒有等待上限，牌還在動時不結束平移。等待途中有新的更新接手、換局、清桌，或對局已不存在時立刻停止；
     * 等了 [SLOW_SLIDE_WARNING_TICKS] 個 tick 還沒停時呼叫一次 [onSlowSlide]，然後繼續等。協程取消照常往外傳。
     *
     * @param tableId 麻將桌 UUID。
     * @param slideId 那次更新拿到的平移識別碼。
     * @param areTilesMoving 桌上各牌區是否還有牌在動；對局已不存在時為 null。
     * @param waitOneTick 等待一個 server tick。
     * @param onSlowSlide 等了 [SLOW_SLIDE_WARNING_TICKS] 個 tick 牌還在動時呼叫一次。
     * @return 牌全部停下時這次更新仍負責這次平移為 true，平移就此結束；中途被接手、換局、清桌或對局不存在時為 false，
     *         呼叫端不要擺上那次更新的物件。
     */
    suspend fun awaitSlideEnd(
        tableId: Uuid,
        slideId: Long,
        areTilesMoving: suspend () -> Boolean?,
        waitOneTick: suspend () -> Unit,
        onSlowSlide: () -> Unit,
    ): Boolean {
        var waitedTicks = 0
        while (slidesByTableId[tableId]?.id == slideId) {
            when (areTilesMoving()) {
                null -> return false
                false -> return finishSlide(tableId, slideId)
                true -> Unit
            }
            if (waitedTicks == SLOW_SLIDE_WARNING_TICKS) onSlowSlide()
            waitOneTick()
            waitedTicks++
        }
        return false
    }

    /**
     * 這次更新仍負責平移時結束平移。
     *
     * @param tableId 麻將桌 UUID。
     * @param slideId 那次更新拿到的平移識別碼。
     * @return 這次更新仍是最後接手等待的更新時為 true。
     */
    private fun finishSlide(tableId: Uuid, slideId: Long): Boolean {
        if (slidesByTableId[tableId]?.id != slideId) return false
        slidesByTableId.remove(tableId)
        return true
    }

    /**
     * 桌上目前擺著的物件佔用的各座位角落寬度，供和牌演出與結算舞台避開。
     *
     * @param tableId 麻將桌 UUID。
     * @return 各座位角落寬度；尚未記錄時為 null。
     */
    fun widthsOnTable(tableId: Uuid): Map<Int, Double>? = tracker.find(tableId)

    /**
     * 桌上的牌區與物件全部清除時，移除這張桌子的紀錄。
     *
     * @param tableId 麻將桌 UUID。
     */
    fun clear(tableId: Uuid) {
        tracker.clear(tableId)
        slidesByTableId.remove(tableId)
    }

    /** 平移等待的參數。 */
    companion object {
        /** 平移等了這麼多個 tick 牌還在動時通知呼叫端一次，之後繼續等。 */
        const val SLOW_SLIDE_WARNING_TICKS: Int = 100
    }
}
