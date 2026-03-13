package com.doublemoon1119.mahjongcraft.domain.rules.taiwan

import com.doublemoon1119.mahjongcraft.domain.base.GameAction
import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.domain.table.TableState

/**
 * 台灣麻將規則的合法動作判定器。
 *
 * 負責根據台灣麻將的規則（包含過水、補花、槓牌等）分析玩家的合法動作。
 */
class TaiwanLegalActionValidator : LegalActionValidator {
    /**
     * 判斷在當前遊戲狀態下，指定玩家可以執行的合法動作列表。
     *
     * @param tableState 當前的遊戲桌況。
     * @param player 欲判斷合法動作的玩家。
     * @param source 動作的來源方位。
     * @param incomingTile 可選參數，表示剛摸到或他家打出的牌。
     * @return 該玩家可以執行的合法動作列表。
     */
    override fun getLegalActions(
        tableState: TableState,
        player: MahjongPlayer,
        source: RelativeDirection,
        incomingTile: IdentifiedTile?
    ): List<GameAction> {
        // TODO: 實作台灣麻將的合法動作判定邏輯
        return emptyList()
    }
}
