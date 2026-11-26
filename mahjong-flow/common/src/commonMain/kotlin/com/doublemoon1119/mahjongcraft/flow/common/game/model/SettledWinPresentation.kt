package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 一次胡牌剛結算完成、但還沒決定該立即呈現或延後呈現的完整呈現內容。
 *
 * 1. `DeclareTsumoUseCase`／`RespondToDiscardUseCase`／`RespondToRobbingUseCase` 建構後交給
 *    `WinPresentationHandoff` 暫存，不直接發布。
 * 2. `ResolveWinRoundContinuationUseCase` 依本次的 [WinRoundDirective] 取走。
 *
 * 為什麼用暫存交接點，而不讓 use case 直接回傳：
 *
 * - `GameActionRouter` 對所有指令（含第三方擴充指令）統一回傳 `Outcome<Unit, GameError>`。
 *   直接回傳就得改動每一種指令的回傳型別，連摸牌、捨牌都得認識胡牌演出型別。
 * - 走交接點，建構邏輯可以留在原本就持有所需 registry 的 use case 內。
 *
 * 交接點刻意**不持久化**：
 *
 * - 它只在單次指令派發內存活：use case 寫入，同一次派發的收斂階段就取走。
 * - 重啟後沒有任何路徑會消費殘留值，持久化不會帶來恢復能力。
 * - 跨重啟的呈現狀態由平台自行保存。
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
