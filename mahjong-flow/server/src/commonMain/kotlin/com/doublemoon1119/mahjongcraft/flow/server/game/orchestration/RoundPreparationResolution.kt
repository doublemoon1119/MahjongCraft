package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 完成一個開局準備步驟後的規則結果。
 *
 * @property tableState 套用該準備步驟後的桌況。重新分配既有牌時保留牌張識別碼；牌牆成員或位置改變時，
 * 同時更新其 [TableState.physicalWallLayout]。各牌區以這份結果同步，不另外重算準備步驟。
 * @property nextStep 下一個準備步驟；準備流程已完成時為 null。
 */
data class RoundPreparationResolution(
    val tableState: TableState,
    val nextStep: PendingRoundPreparation?,
)
