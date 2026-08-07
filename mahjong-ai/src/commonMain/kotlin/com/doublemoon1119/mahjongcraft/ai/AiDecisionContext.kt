package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/**
 * 供 [MahjongAiStrategy] 做決策所需的完整情境。
 *
 * @property snapshot 以 [selfId] 為觀察者產生的桌況快照（即
 *           `TableState.toSnapshot(observerId = selfId)`）——只有這位 AI 自己的手牌可見，
 *           跟真人玩家的客戶端拿到的視角完全相同，AI 不會「偷看」其他玩家的手牌。
 * @property selfId 這位 AI 玩家自己的 Uuid。不靠「[snapshot] 裡哪個玩家的手牌剛好可見」這種
 *           隱式推斷取得，避免依賴 [snapshot] 建構方式的巧合。
 * @property phase 目前所處的決策情境，見 [AiDecisionPhase]。
 * @property legalActions 目前情境下的合法動作清單，直接重用
 *           `com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase`
 *           算好的結果，AI 不重新實作規則判斷。捨牌本身不在清單裡（`LegalActionValidator` 既有
 *           慣例：捨牌是永遠可用的預設動作，見該慣例對應的既有 KDoc 說明）。
 */
data class AiDecisionContext(
    val snapshot: TableStateSnapshot,
    val selfId: Uuid,
    val phase: AiDecisionPhase,
    val legalActions: List<GameAction>,
)
