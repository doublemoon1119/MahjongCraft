package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationEffect
import com.doublemoon1119.mahjongcraft.logic.module.PositionRules
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.module.RonExclusions
import com.doublemoon1119.mahjongcraft.logic.module.WinValue
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora.getNextDora
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import kotlin.uuid.Uuid

/**
 * 日本麻將的規則查詢。
 *
 * - 和牌價值與正式結算使用同一套役種、點數與起胡判定，並加上場上可收下的立直棒與本場點數。
 * - 振聽只依評估者的牌河與立直後振聽判定：快照不包含本巡放過的牌，因此同巡振聽不列入。
 * - 不能榮和的牌：對手自己打過的牌因振聽不能榮和；對手立直後其他玩家打出的牌，對手放過後因立直後振聽也不能榮和。
 *   快照沒有跨玩家的打牌先後，因此只把一定在宣告之後打出的牌算進去：每次鳴牌最多讓立直者少打一巡，
 *   也最多讓鳴牌者多打一張，因此其他玩家牌河中位置超過「立直宣告牌的位置加上兩倍全桌鳴牌次數」的牌必定在宣告之後打出。
 * - 寶牌：寶牌指示牌的下一張，加上赤寶牌。
 *
 * @property config 本局日麻規則設定。
 * @property handValueCalculator 役種與點數計算。
 * @property shantenCalculator 判斷手牌是否構成和牌型與聽哪些牌。
 */
class RiichiPositionRules(
    private val config: RiichiRuleConfig,
    private val handValueCalculator: RiichiHandValueCalculator,
    private val shantenCalculator: RiichiShantenCalculator,
) : PositionRules {
    override fun winValue(
        view: PositionView,
        hand: Hand,
        winningTile: Tile,
        isTsumo: Boolean,
        declarations: Set<GameAction.Extension>,
    ): WinValue {
        val completed = Hand(tiles = hand.tiles + IdentifiedTile(Uuid.NIL, winningTile), melds = hand.melds)
        if (shantenCalculator.calculate(completed) != ShantenResult.Complete) return WinValue.NotWinnable

        val self = view.evaluator
        val riichiState = self.playerRuleState as? RiichiPlayerState ?: RiichiPlayerState()
        val declaresRiichi = !riichiState.isRiichi && declarations.any { it.value == RiichiGameAction.Riichi }
        if (!isTsumo && isFuriten(self, riichiState, hand)) return WinValue.NotWinnable

        val result = handValueCalculator.calculate(
            RiichiHandValueContext(
                hand = hand,
                winningTile = winningTile,
                isTsumo = isTsumo,
                isMenzen = hand.melds.all { it.type == MeldType.CLOSED_KAN },
                roundWind = view.snapshot.prevalentWind,
                seatWind = self.seatWind,
                isDealer = view.snapshot.dealerPlayerId == self.id,
                isRiichi = riichiState.isRiichi || declaresRiichi,
                isDoubleRiichi = riichiState.isDoubleRiichi,
                allowOpenTanyao = config.allowOpenTanyao,
                doraIndicators = visibleDoraIndicators(view),
            ),
        )
        if (!result.qualifyingHan().satisfies(config.minimumWinConstraint)) return WinValue.NotWinnable

        val sticks = riichiStickCount(view) + if (declaresRiichi) 1 else 0
        return WinValue.Points(result.totalPoint + sticks * RIICHI_STICK_POINTS + comboBonus(view, isTsumo))
    }

    override fun declarationEffect(view: PositionView, action: GameAction.Extension): DeclarationEffect = if (action.value == RiichiGameAction.Riichi) {
        DeclarationEffect(cost = RIICHI_STICK_POINTS, locksHand = true)
    } else {
        DeclarationEffect.NONE
    }

    /** 尚未立直、門清（暗槓不算副露）且點數足夠支付立直棒時，聽牌後可以立直。 */
    override fun prospectiveDeclarations(view: PositionView, hand: Hand): Set<GameAction.Extension> {
        val self = view.evaluator
        val isRiichi = (self.playerRuleState as? RiichiPlayerState)?.isRiichi == true
        val isClosed = hand.melds.all { it.type == MeldType.CLOSED_KAN }
        return if (!isRiichi && isClosed && self.score >= RIICHI_STICK_POINTS) setOf(RIICHI_GAME_ACTION) else emptySet()
    }

    override fun ronExclusions(view: PositionView, opponentId: Uuid): RonExclusions {
        val opponent = view.player(opponentId)
        val ownDiscards = opponent.discardPile.entries.mapTo(mutableSetOf()) { it.tile.tile.riichiCanonical }
        val declarationIndex = opponent.discardPile.entries.indexOfFirst { (it as? RiichiDiscardEntry)?.isRiichi == true }
        val isRiichi = (opponent.playerRuleState as? RiichiPlayerState)?.isRiichi == true
        if (!isRiichi || declarationIndex < 0) return RonExclusions(ownDiscards = ownDiscards)
        val callCount = view.snapshot.players.sumOf { player -> player.hand.melds.count { it.sourceTile != null } }
        val passed = view.snapshot.players
            .filter { it.id != opponentId }
            .flatMap { player -> player.discardPile.entries.drop(declarationIndex + 1 + 2 * callCount) }
            .mapTo(mutableSetOf()) { it.tile.tile.riichiCanonical }
        return RonExclusions(ownDiscards = ownDiscards, passedAfterDeclaration = passed)
    }

    override fun bonusTileCount(view: PositionView, tile: Tile): Int {
        val doraTiles = visibleDoraIndicators(view).map { getNextDora(it) }
        return doraTiles.count { it == tile.riichiCanonical } + if (RiichiTileInterpretationPolicy.isRedDora(tile)) 1 else 0
    }

    /** 評估者以此次和牌收取的本場點數；自摸時其他每位玩家各付一份。 */
    private fun comboBonus(view: PositionView, isTsumo: Boolean): Int {
        val comboCount = view.snapshot.comboCount
        return if (isTsumo) {
            comboCount * RIICHI_COMBO_BONUS_TSUMO_POINTS_PER_PAYER * (view.snapshot.players.size - 1)
        } else {
            comboCount * RIICHI_COMBO_BONUS_RON_POINTS
        }
    }

    /** 評估者的牌河或立直後振聽是否讓 [hand] 不能榮和。 */
    private fun isFuriten(
        self: MahjongPlayerSnapshot,
        riichiState: RiichiPlayerState,
        hand: Hand,
    ): Boolean {
        if (riichiState.isPermanentlyFuriten) return true
        val waits = (shantenCalculator.calculate(hand) as? ShantenResult.Tenpai)?.winningTiles ?: return false
        val furitenTiles = riichiState.getFuritenTiles(self.discardPile, passedTilesInRound = emptySet())
        return waits.any { it.riichiCanonical in furitenTiles }
    }

    /** 牌山中公開的牌；日麻只公開寶牌指示牌。 */
    private fun visibleDoraIndicators(view: PositionView): List<Tile> = view.snapshot.tileWall.tiles.mapNotNull { it.tile }

    /** 場上尚未被收下的立直棒數。 */
    private fun riichiStickCount(view: PositionView): Int = (view.snapshot.dynamicRuleState as? RiichiDynamicState)?.riichiStickCount ?: 0
}
