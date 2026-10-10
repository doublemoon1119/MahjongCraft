package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 一次胡牌（自摸／榮和，含搶槓；不含流局）即時結算完成後，供
 * `com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolver` 判斷本局
 * 是否真的結束的規則中立上下文。
 *
 * 一炮多響只在全部贏家的分數都已結算進 [settledTableState] 之後呼叫一次，[winnerPlayerIds] 會包含
 * 這次一起成立的所有贏家，不逐位呼叫。
 *
 * 刻意不攜帶 `MahjongRuleModule.declareTsumo`／`declareRon` 算出的原始
 * `WinResolutionResult`（番數、役種等詳情）——
 * [settledTableState] 的分數與 `actionHistory` 已經是套用結算後的最終值，「本局是否結束」這個判斷
 * 只需要看結算後的桌況即可，不需要重新理解規則特有的算役細節。
 *
 * @property previousTableState 這次胡牌指令送出前的桌況（尚未套用本次結算），用於還原放銃者／搶槓
 * 宣告者身分（此時 `pendingReaction`／`pendingRobbingReaction` 仍未清除）。
 * @property settledTableState 本次結算完成後的權威桌況：分數與贏家的 `actionHistory` 皆已是最終值，
 * 但 `finishedPlayerIds`／`currentPlayerIndex` 仍是結算前的值，尚未套用 resolver 這次的決策。
 * @property winnerPlayerIds 這次一起成立的所有贏家 Uuid。
 * @property ronDiscarderId 榮和（含搶槓）時的放銃者／暗槓或加槓宣告者 Uuid；自摸時為 null。
 * @property winningTileId 胡牌張的 Uuid。
 */
data class WinRoundContinuationContext(
    val previousTableState: TableState,
    val settledTableState: TableState,
    val winnerPlayerIds: Set<Uuid>,
    val ronDiscarderId: Uuid?,
    val winningTileId: Uuid,
)
