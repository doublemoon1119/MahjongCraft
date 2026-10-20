package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
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
     * @param context 從同一份桌況建立的來源與手牌資料。
     * 牌型判定使用其中的 handBeforeIncoming 或 handWithIncoming，不自行拆出摸牌或重複加牌。
     *
     * @return 該玩家可以執行的合法動作列表。
     */
    fun getLegalActions(context: LegalActionContext): List<GameAction>

    /**
     * 判斷 [action] 在成立前，是否還需要玩家從立牌中額外選出牌張（例如宣告立直後，還要另外指定打哪
     * 張牌才能完成宣告）。大多數動作在 [getLegalActions] 回傳時已經帶齊所需牌張（`incomingTile`／
     * [GameAction.Chi]／[GameAction.Pon]／[GameAction.Kan] 的 `withTiles`），不需要這個 hook；預設
     * 回傳 null 代表 [action] 不需要額外選牌。
     *
     * @param tableState 目前的權威桌況。
     * @param player 欲執行動作的玩家。
     * @param action 欲檢查是否需要額外選牌的動作。
     * @return 描述候選牌與選牌數量限制的 [TileSelectionRequirement]；[action] 不需要額外選牌則為 null。
     */
    fun tileSelectionRequirement(
        tableState: TableState,
        player: MahjongPlayer,
        action: GameAction,
    ): TileSelectionRequirement? = null
}
