package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 決定桌角物件寬度改變時牌區怎麼配合：擺牌區時用哪個寬度、規則狀態更新後哪些座位要一起平移、物件先擺還是等平移結束再擺。
 *
 * 桌角寬度可能在一局中改變，例如規則在局中把點數放進供託。這裡只依權威桌況算出的寬度與 [tracker] 記下的、桌上目前擺著的寬度
 * 做決定，不接觸世界物件；平台照決定擺放與排定延遲。寬度一律由呼叫端以規則登記的桌上物件描述算出後傳入。
 *
 * @property tracker 每張桌子目前桌上物件佔用的角落寬度。
 */
class TableCornerLayoutPlanner(
    private val tracker: TableCornerWidthTracker,
) {
    /**
     * 開局發牌時記下這一局桌上物件的寬度。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableState 新一局的權威桌況。
     * @param widthsBySeat 依權威桌況算出的各座位角落寬度。
     */
    fun recordRound(tableId: Uuid, tableState: TableState, widthsBySeat: Map<Int, Double>) {
        tracker.record(tableId, TableRound.of(tableState), widthsBySeat)
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
     * 只有這一局已經擺過、且寬度不同的座位需要平移；還沒擺過的座位之後擺放時就會使用新寬度。任何座位變寬時，物件要等平移
     * 結束才擺上，因為新物件的位置正是牌區原本佔用的地方；只有變窄時先收走物件再平移。
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
        return CornerPropsUpdatePlan(
            slidingSeatWidths = slidingSeatWidths,
            presentsPropsAfterSlide = slidingSeatWidths.any { (seatIndex, width) -> width > previousWidths.getValue(seatIndex) },
        )
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
    }
}

/**
 * 一次桌上物件同步時牌區的配合方式。
 *
 * @property slidingSeatWidths 需要依新寬度一起平移的座位與它們的新寬度。
 * @property presentsPropsAfterSlide 是否等平移結束才擺上物件；為 false 時先同步物件。
 */
data class CornerPropsUpdatePlan(
    val slidingSeatWidths: Map<Int, Double>,
    val presentsPropsAfterSlide: Boolean,
)
