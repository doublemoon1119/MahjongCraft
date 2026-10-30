package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 由規則提供公開事件標記的純解析器。
 *
 * 在權威交易的投影期間同步呼叫，必須可重入且盡快返回；不能寫入狀態、存取 repository、執行 I/O 或發布通知。
 * 相同情境須有相同結果。回傳資料會被複製，未提供的附加資料保留為空或未知。
 */
interface GameEventPublicMarkerResolver {
    /**
     * 解析一次和牌的公開狀態，不把它當作計分役種。
     *
     * @param context 明確指定贏家與交易前後桌況的情境。
     * @return 屬於該贏家的公開標記；沒有時為空清單。
     */
    fun resolveWinMarkers(context: WinPublicMarkerContext): List<RulePublicEventMarker> = emptyList()

    /**
     * 解析整場終局的公開標記，不重新計分或產生成就。
     *
     * @param context 終局原因、參與者與來源桌況。
     * @return 整場或個別玩家的公開標記；沒有時為空清單。
     */
    fun resolveMatchEndMarkers(context: MatchEndPublicMarkerContext): List<RulePublicEventMarker> = emptyList()

    /**
     * 指明這個規則的公開場風；不使用場風的規則回傳 null。
     *
     * @param state 完成或終局前的桌況。
     * @return 完整的場風 ID；無場風概念或未提供時為 null。
     */
    fun resolvePrevalentWindId(state: TableState): String? = null
}
