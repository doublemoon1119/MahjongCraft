package com.doublemoon1119.mahjongcraft.ai.taiwan

import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModel
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.ai.expectation.ReadingDepth
import com.doublemoon1119.mahjongcraft.ai.expectation.ThreatEstimate
import com.doublemoon1119.mahjongcraft.ai.expectation.WaitShapeDanger
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanFlowerTai
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPaymentCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerComponentViews
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

/**
 * 台灣麻將的對手模型，只使用公開資訊。
 *
 * - 捨牌危險度：依聽牌型估計（[WaitShapeDanger]）。台灣麻將沒有振聽，對手自己打過的牌仍可能讓他榮和，
 *   因此不排除任何牌。
 * - 對手威脅：宣告聽牌者視為確定聽牌；其餘依副露數與捨牌數估計聽牌可能性。
 * - 打點：依宣告聽牌、門清或副露估計基本台數，加上看得到的花台與副露中的三元牌、門風、圈風台，
 *   再以本局的付款計算換成分數，因此包含莊家台與連莊台。
 *
 * 所有規則共用的讀牌由 [OpponentModelRegistry] 在進階深度時另外套用，不在這裡處理。
 *
 * @property scoreConfig 本局的底分與每台分數。
 * @property readingDepth 推測對手手牌的深度。
 */
class TaiwanOpponentModel(
    private val scoreConfig: TaiwanScoreConfig,
    override val readingDepth: ReadingDepth,
) : OpponentModel {
    private val waitShapeDanger = WaitShapeDanger(tileKinds = TILE_KINDS, copiesPerTile = COPIES_PER_TILE)

    /** [playerId] 是評估者本人時，取每位對手放槍時收入的平均值；否則為評估者放槍時 [playerId] 的收入。 */
    override fun baselineWinValue(view: PositionView, playerId: Uuid): Int {
        val playerIds = view.snapshot.players.map { it.id }
        val payers = if (playerId == view.evaluatorId) playerIds.filter { it != playerId } else listOf(view.evaluatorId)
        val tai = estimatedTai(view, view.player(playerId))
        val total = payers.sumOf { payer ->
            TaiwanPaymentCalculator.calculate(
                config = scoreConfig,
                playerIds = playerIds,
                winnerId = playerId,
                dealerId = view.snapshot.dealerPlayerId,
                handTai = tai,
                dealerStreak = view.snapshot.comboCount,
                discarderId = payer,
            ).totalGained.toLong()
        }
        return (total / payers.size).toInt()
    }

    override fun discardDanger(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double = waitShapeDanger.estimate(view, tile, unwinnableTiles = emptySet())

    override fun threat(view: PositionView, opponentId: Uuid): ThreatEstimate {
        val opponent = view.player(opponentId)
        val readyProbability = if (opponent.componentViews[TaiwanPlayerComponentViews.READY_DECLARATION] != null) {
            1.0
        } else {
            val openMelds = opponent.hand.melds.count { it.type != MeldType.ClosedKan }
            val profile = READINESS_BY_OPEN_MELDS[openMelds.coerceAtMost(READINESS_BY_OPEN_MELDS.lastIndex)]
            (profile.base + profile.perDiscard * opponent.discardPile.entries.size).coerceAtMost(profile.cap)
        }
        return ThreatEstimate(
            readyProbability = readyProbability,
            expectedWinValue = baselineWinValue(view, opponentId),
        )
    }

    /**
     * 估計 [player] 一手胡牌的台數，不含莊家台與連莊台。
     *
     * @param view 評估者可見資訊所構成的局面視角。
     * @param player 要估計的玩家。
     * @return 估計台數。
     */
    private fun estimatedTai(view: PositionView, player: MahjongPlayerSnapshot): Int {
        val base = when {
            player.componentViews[TaiwanPlayerComponentViews.READY_DECLARATION] != null -> DECLARED_BASELINE_TAI
            player.hand.melds.all { it.type == MeldType.ClosedKan } -> CLOSED_BASELINE_TAI
            else -> OPEN_BASELINE_TAI
        }
        val flowers = player.tileAreas.firstOrNull { it.id == TaiwanPlayerTileAreaIds.FLOWERS }?.tiles.orEmpty().mapNotNull { it.tile }
        val flowerTai = TaiwanFlowerTai.seatFlowerCount(flowers, player.seatWind) + TaiwanFlowerTai.completeSetCount(flowers)
        val valuedHonors = DRAGONS + windTile(player.seatWind) + windTile(view.snapshot.prevalentWind)
        val honorTai = player.hand.melds.sumOf { meld ->
            val tiles = meld.tiles.mapNotNull { it.tile }.distinct()
            val honor = tiles.singleOrNull()?.takeIf { meld.type != MeldType.Chi } ?: return@sumOf 0
            valuedHonors.count { it == honor }
        }
        return base + flowerTai + honorTai
    }

    /**
     * 依副露數估計聽牌可能性的參數。
     *
     * @property base 沒有捨牌時的聽牌可能性。
     * @property perDiscard 每一張捨牌增加的聽牌可能性。
     * @property cap 聽牌可能性的上限。
     */
    private data class ReadinessProfile(
        val base: Double,
        val perDiscard: Double,
        val cap: Double,
    )

    /** [TaiwanOpponentModel] 的估計參數。 */
    private companion object {
        /** 每種牌的張數。 */
        const val COPIES_PER_TILE = 4

        /** 宣告聽牌者的基本台數，含宣告聽牌的一台。 */
        const val DECLARED_BASELINE_TAI = 3

        /** 門清未宣告者的基本台數。 */
        const val CLOSED_BASELINE_TAI = 2

        /** 有副露者的基本台數。 */
        const val OPEN_BASELINE_TAI = 1

        /** 三元牌。 */
        val DRAGONS: List<Tile> = listOf(Tile.Honor.White, Tile.Honor.Green, Tile.Honor.Red)

        /** 依公開副露數（0、1、2、3、4 組以上）估計聽牌可能性的參數；十六張手牌比十三張需要更多巡數。 */
        val READINESS_BY_OPEN_MELDS: List<ReadinessProfile> = listOf(
            ReadinessProfile(base = 0.02, perDiscard = 0.015, cap = 0.4),
            ReadinessProfile(base = 0.08, perDiscard = 0.025, cap = 0.55),
            ReadinessProfile(base = 0.2, perDiscard = 0.03, cap = 0.7),
            ReadinessProfile(base = 0.4, perDiscard = 0.03, cap = 0.8),
            ReadinessProfile(base = 0.65, perDiscard = 0.03, cap = 0.9),
        )

        /** 會被打出的 34 種一般牌；花牌補出後不會被打出，不列入。 */
        val TILE_KINDS: List<Tile> = Tile.Suit.entries.flatMap { suit -> (1..9).map { Tile.Numeric(suit, it) } } +
            listOf(
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.West,
                Tile.Honor.North,
                Tile.Honor.White,
                Tile.Honor.Green,
                Tile.Honor.Red,
            )

        /**
         * 取得風牌。
         *
         * @param wind 風位。
         * @return 對應的風牌。
         */
        fun windTile(wind: Wind): Tile = when (wind) {
            Wind.EAST -> Tile.Honor.East
            Wind.SOUTH -> Tile.Honor.South
            Wind.WEST -> Tile.Honor.West
            Wind.NORTH -> Tile.Honor.North
        }
    }
}
