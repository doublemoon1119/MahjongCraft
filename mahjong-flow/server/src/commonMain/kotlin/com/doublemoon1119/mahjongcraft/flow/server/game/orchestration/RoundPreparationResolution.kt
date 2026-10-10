package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 完成一個開局準備步驟後的規則結果。
 *
 * @property tableState 套用該準備步驟後的桌況。
 * @property nextStep 下一個準備步驟；準備流程已完成時為 null。
 */
data class RoundPreparationResolution(
    val tableState: TableState,
    val nextStep: PendingRoundPreparation?,
)
