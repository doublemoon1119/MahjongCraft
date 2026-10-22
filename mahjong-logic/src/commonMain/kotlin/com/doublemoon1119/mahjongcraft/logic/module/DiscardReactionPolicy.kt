package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 描述規則如何把一張他家捨牌轉成可提交的反應。
 *
 * 這個 policy 只做純計算，不修改桌況，也不發布事件。Flow 會在提供選項與提交命令時使用同一個
 * policy，並在權威交易內重新驗證結果。
 */
fun interface DiscardReactionPolicy {
    /**
     * 依目前桌況產生此玩家可提交的反應候選。
     *
     * @param context 反應計算所需的權威桌況與合法動作。
     * @return 可供 Flow 驗證與排序的候選清單。
     */
    fun resolve(context: DiscardReactionPolicyContext): List<DiscardReactionCandidate>

    /**
     * 依玩家提交的選牌重新計算副露效果。
     *
     * 預設只接受 [resolve] 已列出的完整候選；需要依選牌組合計算效果的規則必須覆寫此方法，不能由 Flow
     * 猜測或拼接副露效果。
     *
     * @param context 權威反應情境。
     * @param action 玩家選擇的動作。
     * @param selectedTileIds 玩家選出的實體牌 ID。
     * @return 規則確認後的候選；選牌不合法時為 `null`。
     */
    fun resolveSelection(
        context: DiscardReactionPolicyContext,
        action: GameAction,
        selectedTileIds: List<Uuid>,
    ): DiscardReactionCandidate? = resolve(context).firstOrNull {
        it.action == action && it.selectedTileIds == selectedTileIds
    }
}
