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
 *
 * @param config 日本麻將規則配置。
 */
class RiichiHandValueContextCalculator(
    private val config: RiichiRuleConfig
) : HandValueContextCalculator<RiichiHandValueContext, RiichiHandValueContextCalculator.Input> {

    /**
     * 計算役種上下文所需的輸入參數。
     */
    data class Input(
        val tableState: TableState,
        val player: MahjongPlayer,
        val incomingTile: IdentifiedTile,
        val isTsumo: Boolean,
        val isRobbingKan: Boolean = false
    )

    override fun calculate(input: Input): RiichiHandValueContext {
        val (tableState, player, incomingTile, isTsumo, isRobbingKan) = input
        val hand = player.hand
        val isMenzen = hand.exposedMelds.isEmpty() || hand.exposedMelds.all { it.type == MeldType.CLOSED_KAN }
        val riichiState = player.playerRuleState as? RiichiPlayerState
        val actionHistory = player.actionHistory

        // 計算海底撈月或河底撈魚
        var isLastDraw = false
        var isLastDiscard = false

        // 日麻規則的王牌數量為 14
        val wanPaiCount = config.deadTileCount

        // 牌山剩餘的牌數量
        val tileWallRemainingCount = tableState.tileWall.remainingCount

        // 剩餘可摸牌數為 0 時，視為海底撈月或河底撈魚
        if ((tileWallRemainingCount - wanPaiCount) == 0) {
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
            seatWind = player.currentWind,
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
                val lastTwoActions = actionHistory.takeLast(2)
                val firstAction = lastTwoActions.first()
                val secondAction = lastTwoActions.last()
                firstAction is GameAction.Kan && secondAction is GameAction.Draw && isTsumo
            } else {
                false
            },
            isFirstTurn = tableState.players.all { it.hand.exposedMelds.isEmpty() } &&
                    tableState.players.all { it.discardPile.entries.size <= 1 } &&
                    player.discardPile.entries.isEmpty(),
            paoLiability = riichiState?.paoLiability
        )
    }
}
