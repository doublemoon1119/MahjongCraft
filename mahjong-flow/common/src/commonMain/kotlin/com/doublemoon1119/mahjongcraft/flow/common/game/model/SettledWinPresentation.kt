package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 一次胡牌剛結算完成、但還沒決定該立即呈現或延後呈現的完整呈現內容。
 *
 * 由 `DeclareTsumoUseCase`／`RespondToDiscardUseCase`／`RespondToRobbingUseCase` 建構後交給
 * `WinPresentationHandoff` 暫存（而不是直接發布），再由 `ResolveWinRoundContinuationUseCase` 依本次
 * 的 [WinRoundDirective] 取走。
 *
 * 之所以需要一個暫存交接點，而不是讓 use case 直接回傳給呼叫端：`GameActionRouter` 對所有指令（含
 * 第三方擴充指令）統一回傳 `Outcome<Unit, GameError>`，要讓演出內容穿過它就得改動每一種指令的回傳
 * 型別，連與胡牌無關的摸牌／捨牌都會被迫認識胡牌演出型別。走交接點則讓建構邏輯留在原本就持有所需
 * registry 的 use case 內。
 *
 * 交接點刻意**不持久化**：它只在單次指令派發內存活（use case 寫入、同一次派發的收斂階段就取走），
 * 重啟後也沒有任何路徑會去消費殘留值，持久化換不到任何恢復能力。跨重啟的呈現狀態由平台自行保存。
 *
 * @property winnerPlayerIds 這次一起成立的所有贏家。
 * @property celebration 胡牌演出請求。
 * @property settlement 結算請求。
 */
data class SettledWinPresentation(
    val winnerPlayerIds: Set<Uuid>,
    val celebration: WinCelebrationRequest,
    val settlement: WinSettlementPresentationRequest,
) {
    init {
        require(winnerPlayerIds.isNotEmpty()) { "A settled win presentation must have at least one winner" }
    }
}
