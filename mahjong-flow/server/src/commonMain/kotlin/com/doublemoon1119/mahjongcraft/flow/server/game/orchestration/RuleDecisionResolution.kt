package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 完成一個規則選擇步驟後的規則結果。
 *
 * @property tableState 套用該選擇步驟後的桌況。重新分配既有牌時保留牌張識別碼；牌牆成員或位置改變時，
 * 同時更新其 [TableState.physicalWallLayout]。各牌區以這份結果同步，不另外重算選擇步驟。
 * @property nextStep 下一個選擇步驟；選擇流程已完成時為 null。
 * @property playerAreaPresentationSteps 此次結果需要依序呈現的玩家牌區變化；沒有時為空清單。
 * @property win 此次步驟直接成立的和牌；成立時必須同時結束選擇流程，且 [tableState] 中每位贏家最後記錄的動作
 * 都是和牌動作。其他結果為 null。
 */
data class RuleDecisionResolution(
    val tableState: TableState,
    val nextStep: RuleDecisionStep?,
    val playerAreaPresentationSteps: List<RuleDecisionPlayerAreaPresentationStep> = emptyList(),
    val win: RuleDecisionWin? = null,
)
