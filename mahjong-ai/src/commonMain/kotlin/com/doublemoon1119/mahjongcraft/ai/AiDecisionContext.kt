package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/**
 * 供 [MahjongAiStrategy] 做決策所需的完整情境。
 *
 * @property snapshot 由呼叫端依 [selfId] 的可見範圍建立、並套用規則狀態投影的桌況快照。
 *           包含自己的手牌與規則允許公開的資訊，不包含他人隱藏牌面或私人規則狀態；
 *           呼叫端不可直接使用尚未套用規則狀態投影的低階快照。
 * @property selfId 這位 AI 玩家自己的 Uuid。不靠「[snapshot] 裡哪個玩家的手牌剛好可見」這種
 * 隱式推斷取得，避免依賴 [snapshot] 建構方式的巧合。
 * @property phase 目前所處的決策情境，見 [AiDecisionPhase]。
 * @property legalActions 目前情境下的合法動作清單，由呼叫端依規則算好後提供，AI 不重新實作規則判斷。捨牌本身不在
 *           清單裡：依 `LegalActionValidator` 的約定，捨牌是自己回合永遠可用的預設動作。
 * @property forcedDiscardTileId 規則要求本次一般捨牌必須打出的牌；`null` 表示規則未限制。
 */
data class AiDecisionContext(
    val snapshot: TableStateSnapshot,
    val selfId: Uuid,
    val phase: AiDecisionPhase,
    val legalActions: List<GameAction>,
    val forcedDiscardTileId: Uuid? = null,
)
