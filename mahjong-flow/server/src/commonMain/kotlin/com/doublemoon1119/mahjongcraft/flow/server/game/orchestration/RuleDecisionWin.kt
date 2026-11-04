package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionRoundOutcomeDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionWinOutcome
import kotlin.uuid.Uuid

/**
 * 規則選擇步驟直接成立的和牌。
 *
 * [outcome] 與分數變動一起寫入權威狀態，供結束本局的流程使用；其餘欄位供共用胡牌演出與結算呈現使用。
 *
 * @property outcome 本次和牌的權威結果。
 * @property scoreDeltas 各玩家本次結算的分數增減；必須與同一步驟寫入桌況的分數變動一致。
 * @property extensionDetail 規則提供的結果詳情，例如成立的牌型與點數；沒有時為 null。
 */
data class RuleDecisionWin(
    val outcome: RuleDecisionWinOutcome,
    val scoreDeltas: Map<Uuid, Int>,
    val extensionDetail: ExtensionRoundOutcomeDetail?,
)
