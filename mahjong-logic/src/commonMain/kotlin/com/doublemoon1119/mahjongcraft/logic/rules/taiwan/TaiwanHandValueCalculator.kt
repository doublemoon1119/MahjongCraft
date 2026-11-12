package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandDecomposer
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandDecomposition
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandGroup
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.flower.calculateTaiwanFlowerTai
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.honor.calculateTaiwanHonorTai
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.situation.calculateTaiwanSituationTai
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.standard.calculateTaiwanAllExposed
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.standard.calculateTaiwanAllSequences
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.standard.calculateTaiwanAllTriplets
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.standard.calculateTaiwanConcealedTriplets
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.standard.calculateTaiwanFlush
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes

/**
 * 計算台灣麻將五面子一雀頭的台數，從全部拆法選出最高的合法結果。
 *
 * @property config 計算最低起胡台數的規則設定。
 */
class TaiwanHandValueCalculator(private val config: TaiwanRuleConfig) : HandValueCalculator<TaiwanHandValueContext, TaiwanHandValueResult> {
    override fun calculate(context: TaiwanHandValueContext): TaiwanHandValueResult {
        val invalid = TaiwanHandValueResult(
            isWinningHand = false,
            taiResults = emptyList(),
            totalTai = 0,
            decomposition = null,
        )
        if (!validTiles(context)) return invalid
        val tiles = context.hand.standingTiles.map { it.tile }
        val decompositions = StandardHandDecomposer.decompose(
            handTiles = tiles,
            winningTile = context.winningTile,
            exposedMeldCount = context.hand.melds.size,
            targetMelds = 5,
        )
        if (decompositions.isEmpty()) return invalid
        val owned = (context.hand.allTiles.map { it.tile } + context.winningTile).groupingBy { it }.eachCount()
        val waits = standardTiles.filter { tile ->
            (owned[tile] ?: 0) - (if (tile == context.winningTile) 1 else 0) < 4 &&
                StandardHandDecomposer.decompose(
                    handTiles = tiles,
                    winningTile = tile,
                    exposedMeldCount = context.hand.melds.size,
                    targetMelds = 5,
                ).isNotEmpty()
        }
        return decompositions.map { decomposition ->
            val items = score(
                context = context,
                decomposition = decomposition,
                waitCount = waits.size,
            )
            val total = items.sumOf { it.tai }
            TaiwanHandValueResult(
                isWinningHand = total >= config.minimumWinConstraint,
                taiResults = items,
                totalTai = total,
                decomposition = decomposition,
            )
        }.maxBy { it.totalTai }
    }

    /**
     * 檢查牌數、牌面上限、副露形狀與情境是否相符。
     *
     * @param context 計台輸入。
     * @return 資料可用於標準型計台時為true。
     */
    private fun validTiles(context: TaiwanHandValueContext): Boolean {
        val all = context.hand.allTiles.map { it.tile } + context.winningTile
        if (all.any { it !is Tile.Numeric && it !is Tile.Honor } || all.groupingBy { it }.eachCount().values.any { it > 4 }) return false
        if (context.flowers.distinct().size != context.flowers.size || context.flowers.any { !TaiwanTileTypes.isFlower(it) }) return false
        if (!config.useFlowerTiles && context.flowers.isNotEmpty()) return false
        val situation = context.situation
        if (situation.isRobbingKong && context.isTsumo) return false
        if ((situation.isReplacementWin || situation.isLastDraw) && !context.isTsumo) return false
        if (situation.openingWin == TaiwanTaiType.HumanWin && context.isTsumo) return false
        if ((situation.openingWin == TaiwanTaiType.HeavenlyWin || situation.openingWin == TaiwanTaiType.EarthlyWin) && !context.isTsumo) return false
        return context.hand.melds.all { meld ->
            val faces = meld.tiles.map { it.tile }
            when (meld.type) {
                MeldType.Chi -> {
                    val numbers = faces.filterIsInstance<Tile.Numeric>().sortedBy { it.value }
                    numbers.size == 3 &&
                        numbers.map { it.suit }.distinct().size == 1 &&
                        numbers.map { it.value } == (numbers.first().value..numbers.first().value + 2).toList()
                }
                MeldType.Pon -> faces.size == 3 && faces.distinct().size == 1
                MeldType.OpenKan, MeldType.ClosedKan, MeldType.AddedKan -> faces.size == 4 && faces.distinct().size == 1
                is MeldType.Extension -> false
            }
        }
    }

    /**
     * 計算一種拆法成立的台，依各類判定收集後按台種宣告順序排列。
     *
     * @param context 牌況與胡牌情境。
     * @param decomposition 這次選用的拆法。
     * @param waitCount 胡牌前整手可等的牌面種類數。
     * @return 成立的台種，不含莊家與連莊。
     */
    private fun score(
        context: TaiwanHandValueContext,
        decomposition: StandardHandDecomposition,
        waitCount: Int,
    ): List<TaiwanTaiResult> {
        val exposed = context.hand.melds
        val allFaces = context.hand.allTiles.map { it.tile } + context.winningTile
        val triplets = decomposition.groups.filterIsInstance<StandardHandGroup.Triplet>().map { it.tile } +
            exposed.filter { it.type != MeldType.Chi }.map { it.tiles.first().tile }
        val isSingleShape = when (val winningGroup = decomposition.winningGroupIndex?.let(decomposition.groups::get)) {
            null -> true
            is StandardHandGroup.Triplet -> false
            is StandardHandGroup.Sequence -> {
                val rank = (context.winningTile as Tile.Numeric).value
                rank == winningGroup.first.value + 1 ||
                    (rank == 3 && winningGroup.first.value == 1) ||
                    (rank == 7 && winningGroup.first.value == 7)
            }
        }
        val isAllExposed = exposed.size == 5 && exposed.none { it.type == MeldType.ClosedKan }
        val results = calculateTaiwanSituationTai(
            isMenzen = context.isMenzen,
            isTsumo = context.isTsumo,
            situation = context.situation,
            waitCount = waitCount,
            isSingleShape = isSingleShape,
            isAllExposed = isAllExposed,
        ) + calculateTaiwanHonorTai(
            triplets = triplets,
            pair = decomposition.pair,
            allFaces = allFaces,
            seatWind = context.seatWind,
            roundWind = context.roundWind,
        ) + calculateTaiwanFlowerTai(context.flowers, context.seatWind) + listOfNotNull(
            calculateTaiwanAllSequences(
                decomposition = decomposition,
                exposed = exposed,
                allFaces = allFaces,
                flowers = context.flowers,
                isTsumo = context.isTsumo,
                isSingleShape = isSingleShape,
            ),
            calculateTaiwanConcealedTriplets(decomposition, exposed, context.isTsumo),
            calculateTaiwanAllExposed(isAllExposed, context.isTsumo),
            calculateTaiwanAllTriplets(decomposition, exposed),
            calculateTaiwanFlush(allFaces),
        )
        return results.sortedBy { it.type.ordinal }
    }

    /** 固定的結構牌面集合，不受牌山剩餘張數或其他玩家暗牌影響。 */
    private val standardTiles: List<Tile> = Tile.Suit.entries.flatMap { suit -> (1..9).map { Tile.Numeric(suit, it) } } +
        listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North, Tile.Honor.Red, Tile.Honor.Green, Tile.Honor.White)
}
