package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueContextCalculator
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 日本麻將役種上下文計算機。
 *
 * 負責根據當前遊戲狀態計算 [RiichiHandValueContext]，包含：
 * - 寶牌指示牌與裏寶牌指示牌
 * - 海底撈月、河底撈魚判定
 * - 嶺上花判定
 * - 榮和的牌是否為立直宣言牌、是否為槓後捨牌（古役燕返、槓振）
 *
 * @param config 日本麻將（四人或三人）規則配置。
 */
class RiichiHandValueContextCalculator(
    private val config: RiichiFamilyRuleConfig,
) : HandValueContextCalculator<RiichiHandValueContext, RiichiHandValueContextCalculator.Input> {

    /**
     * 計算役種上下文所需的輸入參數。
     */
    data class Input(
        val tableState: TableState,
        val player: MahjongPlayer,
        val incomingTile: IdentifiedTile,
        val isTsumo: Boolean,
        val isRobbingKan: Boolean = false,
    )

    override fun calculate(input: Input): RiichiHandValueContext {
        val (tableState, player, incomingTile, isTsumo, isRobbingKan) = input
        val hand = player.hand
        val isMenzen = hand.exposedMelds.isEmpty() || hand.exposedMelds.all { it.type == MeldType.CLOSED_KAN }
        val riichiState = player.playerRuleState as? RiichiPlayerState
        val actionHistory = player.actionHistory
        val discarder = if (isTsumo || isRobbingKan) null else discarderOf(tableState = tableState, winner = player, incomingTile = incomingTile)

        // 計算海底撈月或河底撈魚
        var isLastDraw = false
        var isLastDiscard = false

        // tileWall 在開門時已經排除 reservedWallTiles，只保存仍可正常摸取的活牌；最後一張活牌摸走後
        // remainingCount 才會成為 0，不可再次扣除 deadTileCount，否則會提早 14 張誤判海底／河底。
        if (tableState.tileWall.remainingCount == 0) {
            if (isTsumo) {
                // 自摸時，視為海底撈月
                isLastDraw = true
            } else {
                // 非自摸時，視為河底撈魚
                isLastDiscard = true
            }
        }

        // 計算寶牌指示器
        val doraIndicators: List<Tile>
        val uraDoraIndicators: List<Tile>

        val riichiDynamicState = tableState.dynamicRuleState as? RiichiDynamicState
        if (riichiDynamicState != null) {
            val indicators = riichiDynamicState.getDoraIndicators(tableState)
            doraIndicators = indicators.first.map { it.tile }
            uraDoraIndicators = indicators.second.map { it.tile }
        } else {
            doraIndicators = emptyList()
            uraDoraIndicators = emptyList()
        }

        return RiichiHandValueContext(
            hand = hand,
            winningTile = incomingTile.tile,
            isTsumo = isTsumo,
            isMenzen = isMenzen,
            roundWind = tableState.prevalentWind,
            seatWind = player.seatWind,
            isDealer = tableState.isDealer(player.id),
            isRiichi = riichiState?.isRiichi == true,
            isDoubleRiichi = riichiState?.isDoubleRiichi == true,
            isIppatsu = riichiState?.isIppatsu == true,
            allowOpenTanyao = config.allowOpenTanyao,
            doraIndicators = doraIndicators,
            uraDoraIndicators = if (riichiState?.isRiichi == true) uraDoraIndicators else emptyList(),
            isLastDraw = isLastDraw,
            isLastDiscard = isLastDiscard,
            isRobbingKan = isRobbingKan,
            isRinshanKaihou = if (actionHistory.size >= 2) {
                // 嶺上花需要「槓牌 → 摸牌 → 自摸」的動作序列
                // 依循 M League 公式競技規則（見 RiichiRuleConfig 的規則基準），
                // 大明槓後槓上開花不採用包牌，直接視為一般自摸胡牌。
                // 三人麻將拔北後的補牌同樣是嶺上牌。
                val lastTwoActions = actionHistory.takeLast(2)
                val firstAction = lastTwoActions.first()
                val secondAction = lastTwoActions.last()
                (firstAction is GameAction.Kan || firstAction == PULL_NORTH_GAME_ACTION) && secondAction is GameAction.Draw && isTsumo
            } else {
                false
            },
            isFirstTurn = tableState.players.all { it.hand.exposedMelds.isEmpty() && !it.hasPulledNorth() } &&
                tableState.players.all { it.discardPile.entries.size <= 1 } &&
                player.discardPile.entries.isEmpty(),
            isRiichiDeclarationDiscard = discarder?.let { isRiichiDeclarationDiscard(it, incomingTile) } == true,
            isDiscardAfterKan = discarder?.let(::isDiscardAfterKan) == true,
            paoLiability = riichiState?.paoLiability,
            nukiDoraTiles = riichiState?.nukiDoraTiles.orEmpty().map { it.tile },
            usesThreePlayerTiles = config.usesThreePlayerTiles,
        )
    }

    /**
     * 找出打出榮和牌的玩家；自摸與搶槓沒有放銃的捨牌。
     *
     * @param tableState 目前桌況。
     * @param winner 和牌玩家。
     * @param incomingTile 榮和的牌。
     * @return 最後一個動作是打出這張牌的其他玩家；找不到時為 null。
     */
    private fun discarderOf(
        tableState: TableState,
        winner: MahjongPlayer,
        incomingTile: IdentifiedTile,
    ): MahjongPlayer? = tableState.players.firstOrNull { other ->
        other.id != winner.id && (other.actionHistory.lastOrNull() as? GameAction.Discard)?.tileId == incomingTile.id
    }

    /**
     * 放銃的牌是否為放銃者的立直宣言牌。
     *
     * @param discarder 放銃者。
     * @param incomingTile 榮和的牌。
     * @return 放銃者最後一張捨牌就是這張牌，且標記為立直宣言牌時為 true。
     */
    private fun isRiichiDeclarationDiscard(discarder: MahjongPlayer, incomingTile: IdentifiedTile): Boolean {
        val last = discarder.discardPile.entries.lastOrNull() as? RiichiDiscardEntry ?: return false
        return last.isRiichi && last.tile.id == incomingTile.id
    }

    /**
     * 放銃者是否在槓牌並補牌後打出這張牌；同一回合連續槓牌也算。
     *
     * @param discarder 放銃者。
     * @return 打出這張牌之前的動作依序為槓牌、補牌時為 true；中間的規則專屬宣告（例如立直）不影響判定。
     */
    private fun isDiscardAfterKan(discarder: MahjongPlayer): Boolean {
        val beforeDiscard = discarder.actionHistory.dropLast(1).dropLastWhile { it is GameAction.Extension }
        return beforeDiscard.size >= 2 && beforeDiscard.last() is GameAction.Draw && beforeDiscard[beforeDiscard.size - 2] is GameAction.Kan
    }
}
