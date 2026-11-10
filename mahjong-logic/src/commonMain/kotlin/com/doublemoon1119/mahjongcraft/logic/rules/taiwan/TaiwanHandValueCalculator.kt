package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandDecomposer
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandDecomposition
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandGroup
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
            val total = items.sumOf { it.totalTai }
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
        if (situation.openingWin == TaiwanTaiType.HUMAN_WIN && context.isTsumo) return false
        if (situation.openingWin in setOf(TaiwanTaiType.HEAVENLY_WIN, TaiwanTaiType.EARTHLY_WIN) && !context.isTsumo) return false
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
     * 計算一種拆法，互斥項在同一次計算中處理。
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
        val result = mutableListOf<TaiwanTaiResult>()

        /**
         * 加入成立的項目，零次不產生明細。
         *
         * @param type 成立的台種。
         * @param count 成立次數。
         */
        fun add(type: TaiwanTaiType, count: Int = 1) {
            if (count > 0) result += TaiwanTaiResult(type, count)
        }
        val opening = context.situation.openingWin
        if (context.isMenzen && opening == null) add(TaiwanTaiType.CONCEALED_HAND)
        if (context.isTsumo) add(TaiwanTaiType.SELF_DRAW)
        if (context.isMenzen && context.isTsumo && opening !in setOf(TaiwanTaiType.HEAVENLY_WIN, TaiwanTaiType.EARTHLY_WIN)) {
            add(TaiwanTaiType.FULLY_CONCEALED_HAND)
        }
        val exposed = context.hand.melds
        val triplets = decomposition.groups.filterIsInstance<StandardHandGroup.Triplet>().map { it.tile } +
            exposed.filter { it.type != MeldType.Chi }.map { it.tiles.first().tile }
        val dragonCount = dragons.count { it in triplets }
        val littleDragons = dragonCount == 2 && decomposition.pair in dragons
        val bigDragons = dragonCount == 3
        val windCount = winds.count { it in triplets }
        val littleWinds = windCount == 3 && decomposition.pair in winds
        val bigWinds = windCount == 4
        if (!littleWinds && !bigWinds) {
            if (winds[context.seatWind.ordinal] in triplets) add(TaiwanTaiType.SEAT_WIND)
            if (winds[context.roundWind.ordinal] in triplets) add(TaiwanTaiType.ROUND_WIND)
        }
        if (!littleDragons && !bigDragons) add(TaiwanTaiType.DRAGON, dragonCount)
        add(TaiwanTaiType.SEAT_FLOWER, TaiwanFlowerTai.seatFlowerCount(context.flowers, context.seatWind))
        add(TaiwanTaiType.FLOWER_SET, TaiwanFlowerTai.completeSetCount(context.flowers))
        val winningGroup = decomposition.winningGroupIndex?.let(decomposition.groups::get)
        val singleShape = when (winningGroup) {
            null -> true
            is StandardHandGroup.Triplet -> false
            is StandardHandGroup.Sequence -> {
                val rank = (context.winningTile as Tile.Numeric).value
                rank == winningGroup.first.value + 1 ||
                    (rank == 3 && winningGroup.first.value == 1) ||
                    (rank == 7 && winningGroup.first.value == 7)
            }
        }
        val allExposed = exposed.size == 5 && exposed.none { it.type == MeldType.ClosedKan }
        if (waitCount == 1 && singleShape && !allExposed && opening != TaiwanTaiType.HEAVENLY_WIN) add(TaiwanTaiType.SINGLE_WAIT)
        if (context.situation.isRobbingKong) add(TaiwanTaiType.ROBBING_KONG)
        if (context.situation.isReplacementWin && opening != TaiwanTaiType.HEAVENLY_WIN) add(TaiwanTaiType.REPLACEMENT_WIN)
        if (context.situation.isLastDraw) add(TaiwanTaiType.LAST_DRAW)
        val allFaces = context.hand.allTiles.map { it.tile } + context.winningTile
        val allSequences = decomposition.groups.all { it is StandardHandGroup.Sequence } && exposed.all { it.type == MeldType.Chi }
        if (allSequences &&
            allFaces.all { it is Tile.Numeric } &&
            context.flowers.isEmpty() &&
            !context.isTsumo &&
            winningGroup is StandardHandGroup.Sequence &&
            !singleShape
        ) {
            add(TaiwanTaiType.ALL_SEQUENCES)
        }
        val concealedCount = decomposition.groups.withIndex().count { (index, group) ->
            group is StandardHandGroup.Triplet && (context.isTsumo || index != decomposition.winningGroupIndex)
        } + exposed.count { it.type == MeldType.ClosedKan }
        when (concealedCount) {
            3 -> add(TaiwanTaiType.THREE_CONCEALED_TRIPLETS)
            4 -> add(TaiwanTaiType.FOUR_CONCEALED_TRIPLETS)
            5 -> add(TaiwanTaiType.FIVE_CONCEALED_TRIPLETS)
        }
        if (allExposed && !context.isTsumo) add(TaiwanTaiType.ALL_EXPOSED)
        if (decomposition.groups.all { it is StandardHandGroup.Triplet } && exposed.none { it.type == MeldType.Chi }) add(TaiwanTaiType.ALL_TRIPLETS)
        if (bigDragons) {
            add(TaiwanTaiType.BIG_DRAGONS)
        } else if (littleDragons) {
            add(TaiwanTaiType.LITTLE_DRAGONS)
        }
        if (bigWinds) {
            add(TaiwanTaiType.BIG_WINDS)
        } else if (littleWinds) {
            add(TaiwanTaiType.LITTLE_WINDS)
        }
        val suits = allFaces.filterIsInstance<Tile.Numeric>().map { it.suit }.toSet()
        val honors = allFaces.any { it is Tile.Honor }
        if (suits.size == 1) add(if (honors) TaiwanTaiType.HALF_FLUSH else TaiwanTaiType.FULL_FLUSH)
        if (suits.isEmpty()) add(TaiwanTaiType.ALL_HONORS)
        if (opening != null) add(opening) else context.situation.readyQualification?.let { add(it) }
        return result.toList()
    }

    /** 固定的結構牌面集合，不受牌山剩餘張數或其他玩家暗牌影響。 */
    private val standardTiles: List<Tile> = Tile.Suit.entries.flatMap { suit -> (1..9).map { Tile.Numeric(suit, it) } } +
        listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North, Tile.Honor.Red, Tile.Honor.Green, Tile.Honor.White)

    /** 風牌依門風順序排列。 */
    private val winds: List<Tile> = listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North)

    /** 三元牌集合。 */
    private val dragons: List<Tile> = listOf(Tile.Honor.Red, Tile.Honor.Green, Tile.Honor.White)
}
