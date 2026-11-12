package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.TileReplacementRound
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStep
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 完成一個規則選擇步驟後的規則結果。
 *
 * @property tableState 套用該選擇步驟後的桌況。重新分配既有牌時保留牌張識別碼；牌牆成員或位置改變時，
 * 同時更新其 [TableState.physicalWallLayout]。各牌區以這份結果同步，不另外重算選擇步驟。
 * @property nextStep 下一個選擇步驟；選擇流程已完成時為 null。
 * @property steps 得出 [tableState] 之前依序經過的中間步驟，例如逐輪補牌時每一次移出與補牌之後的桌況；平台依序呈現這些
 * 步驟，最後呈現 [tableState]。沒有中間步驟時為空清單。
 * @property win 此次步驟直接成立的和牌；成立時必須同時結束選擇流程，[tableState] 中每位贏家最後記錄的動作都是和牌
 * 動作，且 [TableState.turnPhase] 是以這些贏家胡牌結束本局。其他結果為 null。以流局直接結束本局時，[tableState] 的
 * 階段是以對應的流局結束本局。
 * @property replacementRounds 這個步驟中逐輪公開換牌的結果，例如補花時每一輪各玩家移出的牌，依輪次排列；共用流程據此寫入牌譜。
 * 沒有換牌時為空清單。
 */
data class RuleDecisionResolution(
    val tableState: TableState,
    val nextStep: RuleDecisionStep?,
    val steps: List<TableStep> = emptyList(),
    val win: RuleDecisionWin? = null,
    val replacementRounds: List<TileReplacementRound> = emptyList(),
)
