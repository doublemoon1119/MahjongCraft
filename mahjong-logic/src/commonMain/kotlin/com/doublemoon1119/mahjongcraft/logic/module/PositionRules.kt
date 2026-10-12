package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.uuid.Uuid

/**
 * 從一個局面視角查詢規則事實。
 *
 * 所有方法只讀取 [PositionView]，不接觸權威桌況，因此不可能使用評估者看不到的資訊。這裡只回答規則怎麼規定，
 * 不包含任何估計；估計對手的打點、聽牌可能性與捨牌危險度屬於 AI。真正的合法性、和牌與計分仍由規則模組的正式流程決定。
 */
interface PositionRules {
    /**
     * 評估者以 [winningTile] 完成 [hand] 時可得的點數。
     *
     * @param hand 不含和牌張的手牌。
     * @param view 評估者目前可見的局面。
     * @param winningTile 完成和牌的牌種。
     * @param isTsumo 是否為自摸。
     * @param declarations 假設和牌前已經宣告的擴充動作；評估者本人已宣告的動作不需要重複傳入。
     * @return 依目前可見局面與宣告判定的和牌價值。
     */
    fun winValue(
        view: PositionView,
        hand: Hand,
        winningTile: Tile,
        isTsumo: Boolean,
        declarations: Set<GameAction.Extension> = emptySet(),
    ): WinValue

    /**
     * 評估者宣告 [action] 的效果。
     *
     * @param view 評估者目前可見的局面。
     * @param action 評估者假設已宣告的擴充動作。
     * @return 此動作對局面的宣告效果。
     */
    fun declarationEffect(view: PositionView, action: GameAction.Extension): DeclarationEffect

    /**
     * 評估者的 [hand] 之後聽牌時可以選擇宣告的擴充動作，用於估計尚未聽牌的手牌聽牌後的打點。
     *
     * 只依手牌型態與評估者目前的狀態判斷，不保證聽牌當下一定合法；預設沒有任何動作。
     *
     * @param hand 尚未聽牌的手牌。
     * @param view 評估者目前可見的局面。
     * @return 手牌聽牌後可選擇的擴充宣告。
     */
    fun prospectiveDeclarations(view: PositionView, hand: Hand): Set<GameAction.Extension> = emptySet()

    /**
     * 從公開資訊可以確定 [opponentId] 依規則不能榮和的牌；預設沒有。
     *
     * @param view 評估者目前可見的局面。
     * @param opponentId 要查詢的對手 ID。
     * @return 可確定對手不能榮和的牌種。
     */
    fun ronExclusions(view: PositionView, opponentId: Uuid): RonExclusions = RonExclusions.NONE

    /**
     * 持有 [tile] 時依規則額外加計的打點單位數，例如日麻的寶牌；規則沒有依牌張加計打點時恆為 0。
     *
     * @param view 評估者目前可見的局面。
     * @param tile 要查詢的牌種。
     * @return 此牌依規則額外增加的打點單位數。
     */
    fun bonusTileCount(view: PositionView, tile: Tile): Int = 0

    /**
     * [opponentId] 宣告聽牌時打出的牌，例如日麻的立直宣告牌，牌面以規則的牌面正規化表示。
     *
     * 對手沒有宣告，或規則沒有這種宣告時為 null；預設為 null。
     *
     * @param view 評估者目前可見的局面。
     * @param opponentId 要查詢的對手 ID。
     * @return 對手宣告時打出的牌；沒有宣告資訊時為 null。
     */
    fun declarationTile(view: PositionView, opponentId: Uuid): Tile? = null

    /**
     * 評估者把手牌中的 [tileId] 移出手牌、公開擺在桌上之後的視角，例如日麻的拔北；不包含之後補進的牌。
     *
     * 只是假想局面，不改變任何狀態。預設只移動那張牌（見 [PositionView.withTileSetAside]）；玩家狀態另外記錄這些牌的
     * 規則需覆寫，一併更新玩家狀態。
     *
     * @param view 評估者目前可見的局面。
     * @param tileId 要移出手牌的實體牌 ID。
     * @return 移出牌後的新局面。
     */
    fun afterTileSetAside(view: PositionView, tileId: Uuid): PositionView = view.withTileSetAside(tileId)
}
