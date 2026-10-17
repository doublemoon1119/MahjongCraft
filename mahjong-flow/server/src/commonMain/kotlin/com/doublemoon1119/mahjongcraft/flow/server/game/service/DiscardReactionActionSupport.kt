package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.logic.base.GameAction

/** 集中定義目前 Flow 能處理的捨牌反應動作。 */
internal object DiscardReactionActionSupport {
    /**
     * 判斷動作是否能在捨牌反應視窗中由 Flow 執行。
     *
     * 擴充動作目前只在自己回合或其他專用流程中處理，不能被當成捨牌反應送入。
     * 暗槓與加槓也屬於自己回合動作；搶槓反應另由搶槓流程驗證。
     *
     * @param action 要檢查的合法動作。
     * @return 動作能在捨牌反應視窗中執行時為 `true`。
     */
    fun supports(action: GameAction): Boolean = when (action) {
        GameAction.Pass,
        is GameAction.Ron,
        is GameAction.Chi,
        is GameAction.Pon,
        -> true

        is GameAction.Kan -> action.type == GameAction.KanType.OPEN_KAN
        else -> false
    }

    /**
     * 移除目前捨牌反應流程無法執行的規則動作。
     *
     * @param actions 規則判定器回傳的完整合法動作清單。
     * @return 目前捨牌反應流程能執行的動作清單。
     */
    fun filter(actions: List<GameAction>): List<GameAction> = actions.filter(::supports)
}
