package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.GameAction
import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.MeldType
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.judgment.HandValueContextCalculator
import com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.domain.table.TableState

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

        val doraIndicators = mutableListOf<Tile>()
        val uraDoraIndicators = mutableListOf<Tile>()
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

        // 牌桌上槓的總數
        val kanCount = tableState.players.sumOf { p ->
            p.hand.exposedMelds.count {
                it.type == MeldType.OPEN_KAN || it.type == MeldType.ADDED_KAN || it.type == MeldType.CLOSED_KAN
            }
        }

        // 取得王牌
        val wanPai = tableState.tileWall.getAllTiles()
            .takeLast(wanPaiCount)
            .reversed()  // 反轉後索引 0 轉為嶺上位置，便於由左至右計算

        // 根據槓數推算指示牌索引
        // 初始 0 槓 = 1 張 (索引 = (4 - kanCount))
        // 每多 1 槓 = 多 1 張 (索引 = (4 - kanCount) + n*2)
        val indicatorCount = (1 + kanCount).coerceAtMost(5)

        for (i in 0 until indicatorCount) {
            // 補償計算：(4 - kanCount) 抵消了因為 drawLast() 導致嶺上牌移除後的索引位移
            // i * 2 則用於跳過每一墩的下層牌（裏寶牌指示牌）
            val baseIndex = (4 - kanCount) + (i * 2)

            // 取得寶牌指示牌
            wanPai.getOrNull(baseIndex)?.let {
                doraIndicators.add(it.tile)
            }

            // 取得裏寶牌指示牌
            wanPai.getOrNull(baseIndex + 1)?.let {
                uraDoraIndicators.add(it.tile)
            }
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
                val lastTwoActions = actionHistory.takeLast(2)
                val firstAction = lastTwoActions.first()
                val secondAction = lastTwoActions.last()
                // TODO: 包牌邏輯應獨立到另外的模組處理
                //  當此役成立且為大明槓（OPEN_KAN）時，
                //  應由觸發大明槓的玩家（丟牌者）全付點數
                firstAction is GameAction.Kan && secondAction is GameAction.Draw && isTsumo
            } else {
                false
            },
            isFirstTurn = tableState.players.all { it.hand.exposedMelds.isEmpty() } &&
                    tableState.players.all { it.discardPile.entries.size <= 1 } &&
                    player.discardPile.entries.isEmpty()
        )
    }
}
