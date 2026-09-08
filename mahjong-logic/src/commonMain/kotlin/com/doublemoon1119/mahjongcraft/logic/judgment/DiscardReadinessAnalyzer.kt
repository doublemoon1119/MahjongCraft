package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 分析捨牌後聽牌狀態的介面，供呈現層顯示打某張牌之後的等待牌與風險，讓玩家判斷該不該打這張牌。
 *
 * 目前只有日麻有具體實作；規則模組透過
 * [com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule.createDiscardReadinessAnalyzer]
 * 提供，不支援此分析（例如尚未實作聽牌概念的規則）時回傳 null，不需要實作這個介面。
 */
interface DiscardReadinessAnalyzer {
    /**
     * 逐張立牌假想捨牌後分析聽牌狀態，只回傳打出後仍聽牌的候選。
     *
     * @param tableState 目前的權威桌況。
     * @param player 欲分析的玩家。
     * @return 依立牌順序排列的分析結果列表。
     */
    fun analyze(tableState: TableState, player: MahjongPlayer): List<DiscardReadinessAnalysis>
}

/** 假想捨出 [discardTileId] 後的聽牌分析結果。 */
data class DiscardReadinessAnalysis(
    val discardTileId: Uuid,
    val waitingTiles: List<WaitingTileAvailability>,
    val statusIndicatorId: String?,
)

/**
 * 一張等待牌的可用性。
 *
 * [winAvailability] 是規則模組自訂的命名字串，比照 [DiscardReadinessAnalysis.statusIndicatorId] 的
 * 慣例：`"mahjongcraft:win_available"` 代表沒有任何和牌資格上的特殊限制，這是所有規則模組共通的中立
 * 預設；有更細分和牌可用性概念的規則模組（例如日麻的自摸限定、無役、未達最低翻符）另外提供各自的
 * 命名字串。
 */
data class WaitingTileAvailability(
    val tile: Tile,
    val remainingCount: Int,
    val winAvailability: String,
)
