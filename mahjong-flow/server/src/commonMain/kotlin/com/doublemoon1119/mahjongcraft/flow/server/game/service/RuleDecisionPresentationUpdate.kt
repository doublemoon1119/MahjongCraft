package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStep
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 一次選擇步驟提交前後的呈現；只在權威交易成功後交付。
 *
 * @property previous 規則選擇結果套用前的桌況。
 * @property current 此次交易提交的最終桌況。
 * @property steps 此次交易中依序經過的中間步驟，不含 [current]。
 */
internal data class RuleDecisionPresentationUpdate(
    val previous: TableState,
    val current: TableState,
    val steps: List<TableStep> = emptyList(),
) {
    /**
     * 依序呈現中間步驟，最後呈現 [current]（最後一步已是 [current] 時不重複）；桌況沒有改變時不呈現。
     *
     * @param publisher 平台呈現出口。
     */
    fun publish(publisher: GamePresentationPublisher) {
        if (previous == current && steps.isEmpty()) return
        val final = if (steps.lastOrNull()?.tableState == current) emptyList() else listOf(TableStep(current, emptyList()))
        publisher.publishTableSteps(current.id, steps + final)
    }
}
