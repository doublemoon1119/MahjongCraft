package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.GameAction
import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.domain.table.TableState

/**
 * 日本麻將規則的合法動作判定器。
 *
 * 負責根據立直麻將的規則（包含振聽、立直、槓牌限制等）分析玩家的合法動作。
 */
class RiichiLegalActionValidator : LegalActionValidator {
    /**
     * 判斷在當前遊戲狀態下，指定玩家可以執行的合法動作列表。
     *
     * @param tableState 當前的遊戲桌況。
     * @param player 欲判斷合法動作的玩家。
     * @param incomingTile 可選參數，表示剛摸到或他家打出的牌。
     * @return 該玩家可以執行的合法動作列表。
     */
    override fun getLegalActions(
        tableState: TableState,
        player: MahjongPlayer,
        incomingTile: IdentifiedTile?
    ): List<GameAction> {
        // TODO: 實作日本麻將的合法動作判定邏輯
        return emptyList()
    }
}
