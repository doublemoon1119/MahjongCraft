package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationEffect
import com.doublemoon1119.mahjongcraft.logic.module.PositionRules
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.module.WinValue
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot

/**
 * 台灣麻將的規則查詢。
 *
 * - 和牌價值與正式結算使用同一套計台與付款計算，包含花台、莊家台與連莊台。榮和時付款者未定，
 *   取每位對手放槍時收入的平均值。
 * - 需要出牌先後才能判斷的情境不列入：補牌胡（槓上開花、花上自摸）、起手胡（天胡、地胡、人胡）、搶槓與海底。
 * - 已宣告聽牌時以宣告種類計台；假設的宣告一律以一般宣告聽牌計台，因為假設局面無法分辨宣告會在哪一巡發生。
 * - 過水只有在評估者最後打出的牌仍是自己聽的牌時才算持續；打出其他牌的假設局面視為已解除。
 * - 宣告聽牌不付分數，宣告後只能打出摸到的牌。
 * - 對手自己打過的牌仍可榮和，因此沒有不能榮和的牌；也沒有依牌張加計的打點。
 *
 * @property config 本局台灣麻將規則設定。
 */
class TaiwanPositionRules(private val config: TaiwanRuleConfig) : PositionRules {
    private val handValueCalculator = TaiwanHandValueCalculator(config)
    private val shantenCalculator = TaiwanShantenCalculator()

    override fun winValue(
        view: PositionView,
        hand: Hand,
        winningTile: TileValue,
        isTsumo: Boolean,
        declarations: Set<GameAction.Extension>,
    ): WinValue {
        val self = view.evaluator
        if (isPassedWin(self, hand)) return WinValue.NotWinnable
        val declared = self.componentViews[TaiwanPlayerComponentViews.READY_DECLARATION]
            ?: TaiwanReadyKind.GENERAL.takeIf { declarations.any { it.value == TaiwanGameAction.DeclareReady } }
        val result = handValueCalculator.calculate(
            TaiwanHandValueContext(
                hand = hand,
                winningTileValue = winningTile,
                isTsumo = isTsumo,
                roundWind = view.snapshot.prevalentWind,
                seatWind = self.seatWind,
                flowers = self.tileAreas.firstOrNull { it.id == TaiwanPlayerTileAreaIds.FLOWERS }?.tiles.orEmpty().mapNotNull { it.tile },
                situation = TaiwanWinSituation(readyQualification = declared?.taiType),
            ),
        )
        if (!result.isWinningHand) return WinValue.NotWinnable
        val playerIds = view.snapshot.players.map { it.id }
        val payers = if (isTsumo) listOf(null) else playerIds.filter { it != self.id }
        val total = payers.sumOf { payer ->
            TaiwanPaymentCalculator.calculate(
                config = config.scoreConfig,
                playerIds = playerIds,
                winnerId = self.id,
                dealerId = view.snapshot.dealerPlayerId,
                handTai = result.totalTai,
                dealerStreak = view.snapshot.comboCount,
                discarderId = payer,
            ).totalGained.toLong()
        }
        return WinValue.Points((total / payers.size).toInt())
    }

    override fun declarationEffect(view: PositionView, action: GameAction.Extension): DeclarationEffect = if (action.value == TaiwanGameAction.DeclareReady) {
        DeclarationEffect(locksHand = true)
    } else {
        DeclarationEffect.NONE
    }

    /** 尚未宣告且牌牆還有牌時，聽牌後可以宣告聽牌。 */
    override fun prospectiveDeclarations(view: PositionView, hand: Hand): Set<GameAction.Extension> {
        val isDeclared = view.evaluator.componentViews[TaiwanPlayerComponentViews.READY_DECLARATION] != null
        return if (!isDeclared && view.snapshot.tileWall.tiles.isNotEmpty()) setOf(TAIWAN_DECLARE_READY_ACTION) else emptySet()
    }

    /**
     * 判斷評估者在這個局面是否仍在過水中。
     *
     * @param self 評估者的公開及自身資料。
     * @param hand 尚未加入胡牌張的手牌。
     * @return 有過水標記，且最後打出的牌仍是 [hand] 聽的牌時為 true。
     */
    private fun isPassedWin(self: MahjongPlayerSnapshot, hand: Hand): Boolean {
        if (self.componentViews[TaiwanPlayerComponentViews.PASSED_WIN] == null) return false
        val lastDiscard = self.discardPile.entries.lastOrNull()?.tile?.tile ?: return true
        val waits = (shantenCalculator.calculate(Hand(tiles = hand.standingTiles, melds = hand.melds)) as? ShantenResult.Tenpai)?.winningTiles ?: return true
        return lastDiscard in waits
    }
}
