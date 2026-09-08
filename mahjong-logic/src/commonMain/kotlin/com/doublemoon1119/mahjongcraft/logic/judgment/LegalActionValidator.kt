package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 定義合法動作判定的介面。
 *
 * 負責根據當前的遊戲狀態、玩家資訊以及可能的新牌，判斷該玩家可以執行的所有合法動作。
 * 不同的麻將規則會有不同的合法動作判定邏輯。
 */
interface LegalActionValidator {
    /**
     * 判斷在當前遊戲狀態下，指定玩家可以執行的合法動作列表。
     *
     * @param tableState 當前的遊戲桌況。
     * @param player 欲判斷合法動作的玩家。
     * @param sourceAction 觸發此次判斷的動作。例如：[GameAction.Discard] 表示有玩家丟牌觸發判斷。
     * @param sourceDirection 動作的來源方位。例如：[RelativeDirection.Left] 表示上家，[RelativeDirection.Self] 表示自己。
     * @param incomingTile 可選參數，表示剛摸到或他家打出或加槓之類的牌。
     *                     若為 null，則表示判斷玩家在自己回合內（未摸牌或已摸牌但未捨牌）的動作。
     *                     [sourceDirection] 為 [RelativeDirection.Self] 且此參數為 null 時，若
     *                     `player.hand.lastDrawn` 非 null（已摸牌，只是呼叫端還不知道要不要傳入摸到
     *                     的牌），實作必須自行判斷是否需要一併檢查跟摸到的牌相關的資格（例如自摸／
     *                     加槓／暗槓），一次呼叫就回傳完整清單——呼叫端只給一次 `incomingTile = null`，
     *                     不會、也不應該為了拿到完整清單而自行剝離 `lastDrawn` 重複呼叫兩次。
     * @return 該玩家可以執行的合法動作列表。
     */
    fun getLegalActions(
        tableState: TableState,
        player: MahjongPlayer,
        sourceAction: GameAction,
        sourceDirection: RelativeDirection,
        incomingTile: IdentifiedTile? = null,
    ): List<GameAction>
}
