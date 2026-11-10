package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 從權威桌況整理台灣麻將胡牌情境並計台，供合法動作判定與正式結算共用同一套判斷。
 *
 * @param config 規則設定。
 */
internal class TaiwanWinEvaluation(config: TaiwanRuleConfig) {
    private val contextCalculator = TaiwanHandValueContextCalculator()
    private val handValueCalculator = TaiwanHandValueCalculator(config)

    /**
     * 計算一次胡牌的台數。
     *
     * @param tableState 胡牌前的權威桌況。
     * @param player 手牌不含胡牌張的玩家。
     * @param winningTile 胡牌張。
     * @param isTsumo 是否為自摸。
     * @param isRobbingKong 是否搶加槓。
     * @return 計台結果；牌型不成立或未達最低台數時 [TaiwanHandValueResult.isWinningHand] 為 false。
     */
    fun evaluate(
        tableState: TableState,
        player: MahjongPlayer,
        winningTile: IdentifiedTile,
        isTsumo: Boolean,
        isRobbingKong: Boolean,
    ): TaiwanHandValueResult = handValueCalculator.calculate(
        contextCalculator.calculate(
            TaiwanHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = winningTile,
                isTsumo = isTsumo,
                flowers = player.tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles.orEmpty().map { it.tile },
                situation = situation(tableState, player, isTsumo, isRobbingKong),
            ),
        ),
    )

    /**
     * 分析指定牌作為胡牌張時，胡牌與自摸是否達到起胡台數。
     *
     * 兩種判定都沿用正式結算的計台情境；呼叫端另行處理過水等玩家資格限制。
     *
     * @param tableState 胡牌前的權威桌況。
     * @param player 手牌不含胡牌張的玩家。
     * @param winningTile 要分析的胡牌張。
     * @return 這張等待牌的胡牌資格。
     */
    fun analyzeAvailability(
        tableState: TableState,
        player: MahjongPlayer,
        winningTile: IdentifiedTile,
    ): TaiwanWinAvailability {
        val ron = evaluate(tableState, player, winningTile, isTsumo = false, isRobbingKong = false)
        val tsumo = evaluate(tableState, player, winningTile, isTsumo = true, isRobbingKong = false)
        return when {
            ron.isWinningHand -> TaiwanWinAvailability.AVAILABLE
            tsumo.isWinningHand -> TaiwanWinAvailability.TSUMO_ONLY
            ron.decomposition != null || tsumo.decomposition != null -> TaiwanWinAvailability.BELOW_MINIMUM
            else -> TaiwanWinAvailability.NOT_WINNING
        }
    }

    /**
     * 依桌況判斷補牌胡、海底、起手胡與宣告聽牌資格。
     *
     * 起手胡以「全桌沒有任何副露，且胡牌者尚未打出任何牌」判斷：莊家自摸為天胡、閒家自摸為地胡、閒家胡別人的牌為人胡。
     *
     * @param tableState 胡牌前的權威桌況。
     * @param player 胡牌的玩家。
     * @param isTsumo 是否為自摸。
     * @param isRobbingKong 是否搶加槓。
     * @return 胡牌情境。
     */
    private fun situation(
        tableState: TableState,
        player: MahjongPlayer,
        isTsumo: Boolean,
        isRobbingKong: Boolean,
    ): TaiwanWinSituation {
        val previousAction = player.actionHistory.dropLast(1).lastOrNull()
        val isReplacementWin = isTsumo &&
            player.actionHistory.lastOrNull() == GameAction.Draw &&
            (previousAction is GameAction.Kan || previousAction == TAIWAN_REVEAL_FLOWER_ACTION)
        val isOpening = tableState.players.all { it.hand.melds.isEmpty() } && player.discardPile.entries.isEmpty()
        val isDealer = player.id == tableState.dealerPlayerId
        val openingWin = when {
            !isOpening -> null
            isTsumo && isDealer -> TaiwanTaiType.HEAVENLY_WIN
            isTsumo -> TaiwanTaiType.EARTHLY_WIN
            !isDealer -> TaiwanTaiType.HUMAN_WIN
            else -> null
        }
        return TaiwanWinSituation(
            isRobbingKong = isRobbingKong,
            isReplacementWin = isReplacementWin,
            isLastDraw = isTsumo && !isReplacementWin && tableState.tileWall.remainingCount == 0,
            openingWin = openingWin,
            readyQualification = player.components[TaiwanPlayerComponents.READY_DECLARATION]?.taiType,
        )
    }
}
