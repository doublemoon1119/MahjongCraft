package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import kotlin.uuid.Uuid

/**
 * 一次胡牌的完整呈現請求。
 *
 * 「提供哪些內容」與「是否暫停其他玩家」是兩件獨立的事：
 * - 內容：[celebration] 與 [settlement]；中途胡牌時結算的資訊範圍見 [ContinuingWinSettlementDetail]。
 * - 是否暫停：[roundContinues] 為 `false` 時一律暫停；為 `true` 時由平台決定，見下方說明。
 *
 * @property winnerPlayerIds 這次一起成立的所有贏家；[roundContinues] 時平台須依此收尾這些玩家的手牌呈現。
 * @property celebration 胡牌演出請求；一律呈現、不可省略，這是其他玩家得知「這位玩家胡了」的依據。
 * @property settlement 結算請求。
 * @property roundContinues 本局是否在這次胡牌之後仍然繼續（已完成玩家退出、其他人續打）。
 *
 * `false`（本局就此結束）時，整段呈現期間其他玩家都在等待。
 *
 * `true` 時只有平台認為需要所有人觀看的段落才暫停遊戲，例如依展示理由播放、且要求所有人看完的展示；暫停期間停止
 * 玩家輸入、AI、強制自動操作與決策計時器。其餘段落（包括整個結算）不得阻擋仍在本局中的玩家。不論是否暫停，換局都會
 * 等整段呈現結束；整段結束後，平台須把贏家的手牌呈現收尾（見 `GamePresentationBusyGate.isPresentingContinuingWin`）。
 */
data class WinPresentationRequest(
    val winnerPlayerIds: Set<Uuid>,
    val celebration: WinCelebrationRequest,
    val settlement: WinSettlementPresentationRequest,
    val roundContinues: Boolean,
) {
    init {
        require(winnerPlayerIds.isNotEmpty()) { "A win presentation must have at least one winner" }
    }
}
