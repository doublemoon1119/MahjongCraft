package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 規則解析本局自動操作時可讀取的完整權威情境。
 *
 * @property tableState 目前權威桌況。
 * @property player 目前取得決策權的玩家。
 * @property legalActions 此次決策可用的特殊動作。
 * @property enabledControlIds 玩家目前啟用且受此規則支援的 control ID。
 */
data class AutomaticControlContext(
    val tableState: TableState,
    val player: MahjongPlayer,
    val legalActions: List<GameAction>,
    val enabledControlIds: Set<String>,
)

/**
 * 規則要求立即採用的一個完整動作。
 *
 * @property action 要提交的動作；一般特殊動作必須存在於目前合法動作中，規則允許的普通捨牌除外。
 * @property selectedTileIds 擴充動作需要額外選牌時使用的牌 ID；不需選牌時為空。
 */
data class AutomaticControlAction(
    val action: GameAction,
    val selectedTileIds: List<Uuid> = emptyList(),
)

/**
 * 規則對一次自動操作情境的解析結果。
 *
 * @property immediateAction 可安全立即提交的動作；仍需玩家決定時為 null。
 * @property hiddenActions 已由控制明確拒絕、不應再提供給玩家的合法動作。
 */
data class AutomaticControlResult(
    val immediateAction: AutomaticControlAction? = null,
    val hiddenActions: Set<GameAction> = emptySet(),
) {
    init {
        require(immediateAction?.action !in hiddenActions) { "Immediate automatic action must not be hidden" }
    }
}

/** 依規則語意解析玩家啟用的本局自動操作控制。 */
fun interface AutomaticControlPolicy {
    /** 回傳此次決策應立即採用或隱藏的動作。 */
    fun evaluate(context: AutomaticControlContext): AutomaticControlResult
}

/** 沒有自動操作行為的安全預設 policy。 */
object NoOpAutomaticControlPolicy : AutomaticControlPolicy {
    /** 維持全部合法動作與正常玩家決策。 */
    override fun evaluate(context: AutomaticControlContext): AutomaticControlResult = AutomaticControlResult()
}
