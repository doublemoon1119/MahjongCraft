package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.PositionRules
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.module.WinValue
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy
import kotlin.uuid.Uuid

/**
 * 一手等待摸牌的手牌的評估結果。
 *
 * @property shanten 向聽數；聽牌為 0。
 * @property outlook 本局剩餘時間內的和牌前景。
 * @property tenpaiProfile 聽牌時的和牌率與點數；未聽牌時為 null。
 * @property estimatedValuePerWin 兩向聽以上時假設的每次和牌點數；以實際手牌計算打點時為 null。
 */
internal data class HandAssessment(
    val shanten: Int,
    val outlook: WinOutlook,
    val tenpaiProfile: TenpaiProfile?,
    val estimatedValuePerWin: Double? = null,
) {
    /** 把兩向聽以上假設的每次和牌點數降到不超過 [valuePerWin]；以實際手牌計算打點的評估不變。 */
    fun cappedAt(valuePerWin: Double): HandAssessment {
        val estimated = estimatedValuePerWin ?: return this
        if (estimated <= valuePerWin) return this
        return copy(
            outlook = outlook.copy(expectedValue = outlook.winProbability * valuePerWin),
            estimatedValuePerWin = valuePerWin,
        )
    }
}

/**
 * 以評估者本人為觀察者的假設視角：評估者的手牌與牌河換成假設動作完成後的樣子。
 *
 * @property id 在同一次決策內區分不同假設的編號，用於快取。
 * @property view 假設後的局面視角。
 */
internal class HypotheticalView(
    val id: Int,
    val view: PositionView,
)

/**
 * 評估等待摸牌的手牌在本局的和牌前景。
 *
 * 聽牌時逐張詢問規則自摸與榮和的點數；一向聽時列舉每種讓手牌聽牌的進張與最佳捨牌；兩向聽以上以有效牌張數遞推。
 * 同一次決策內快取向聽數與點數計算結果。
 *
 * 一向聽列舉聽牌後的點數時，使用列舉起點的假設視角，不另外把之後打出的牌加入牌河，因此不計入由那張牌造成的振聽。
 *
 * @property level 可使用的資訊範圍。
 * @property parameters 估計參數。
 * @property selfId 評估者本人。
 * @property rules 本局規則的規則查詢。
 * @property opponentModel 本局規則的對手模型，提供無法具體估算時的基準打點。
 * @property shantenCalculator 規則的向聽計算。
 * @property interpretation 規則的牌面正規化。
 * @property tileOrder 規則牌序，用於穩定排序。
 * @property unseen 評估者眼中的未見牌。
 * @property outlook 本局剩餘的和牌機會。
 * @property placement 點數與名次的換算。
 * @property flatWinValue 不區分打點時，每一種和牌共用的點數。
 */
internal class HandAssessor(
    private val level: InformationLevel,
    private val parameters: ExpectationParameters,
    private val selfId: Uuid,
    private val rules: PositionRules,
    private val opponentModel: OpponentModel,
    private val shantenCalculator: ShantenCalculator,
    private val interpretation: TileInterpretationPolicy,
    private val tileOrder: TileOrder,
    private val unseen: UnseenTileCounts,
    private val outlook: DrawOutlook,
    private val placement: PlacementUtility,
    private val flatWinValue: Double,
) {
    /** 手牌內容相同即視為相同的快取鍵。 */
    private data class HandKey(
        val tiles: List<Tile>,
        val meldTypes: List<MeldType>,
        val meldTiles: List<List<Tile>>,
    )

    /**
     * 一次和牌點數詢問的快取鍵。
     *
     * @property viewId 假設視角的編號。
     * @property hand 不含和牌張的手牌。
     * @property tile 和牌張。
     * @property isTsumo 是否自摸。
     * @property declarations 假設已宣告的動作。
     */
    private data class WinValueKey(
        val viewId: Int,
        val hand: HandKey,
        val tile: Tile,
        val isTsumo: Boolean,
        val declarations: Set<GameAction.Extension>,
    )

    /** 已計算的向聽結果。 */
    private val shantenByHand = HashMap<HandKey, ShantenResult>()

    /** 已詢問的和牌點數。 */
    private val winValueByKey = HashMap<WinValueKey, WinValue>()

    /**
     * 評估 [hand] 的和牌前景。
     *
     * @param view 評估時使用的假設視角。
     * @param declarations 假設和牌前已經宣告的動作。
     */
    fun assess(
        hand: Hand,
        view: HypotheticalView,
        declarations: Set<GameAction.Extension>,
    ): HandAssessment = when (val result = shanten(hand)) {
        ShantenResult.Complete -> HandAssessment(shanten = 0, outlook = WinOutlook.NONE, tenpaiProfile = null)

        is ShantenResult.Tenpai -> {
            val profile = tenpaiProfile(hand, view, result.winningTiles, declarations, unseen)
            HandAssessment(shanten = 0, outlook = profile.outlook(outlook.ownDraws, outlook), tenpaiProfile = profile)
        }

        is ShantenResult.NotTenpai -> if (result.shanten == 1) {
            HandAssessment(
                shanten = 1,
                outlook = oneShantenOutlook(tenpaiBranches(hand, view, declarations), outlook),
                tenpaiProfile = null,
            )
        } else {
            distantHandAssessment(hand, view, result.shanten)
        }
    }

    /** [hand] 的向聽數；已和牌為 -1、聽牌為 0。 */
    fun shantenOf(hand: Hand): Int = when (val result = shanten(hand)) {
        ShantenResult.Complete -> -1
        is ShantenResult.Tenpai -> 0
        is ShantenResult.NotTenpai -> result.shanten
    }

    /** 依規則正規化的牌面。 */
    fun canonical(tile: Tile): Tile = interpretation.canonicalize(tile)

    /** 聽牌手牌的和牌率與點數。 */
    private fun tenpaiProfile(
        hand: Hand,
        view: HypotheticalView,
        waits: List<Tile>,
        declarations: Set<GameAction.Extension>,
        counts: UnseenTileCounts,
    ): TenpaiProfile {
        val waitValues = waits.distinctBy { canonical(it) }.map { wait ->
            WaitValue(
                count = counts[canonical(wait)],
                tsumoValue = winValue(hand, view, wait, isTsumo = true, declarations = declarations),
                ronValue = winValue(hand, view, wait, isTsumo = false, declarations = declarations),
            )
        }
        return TenpaiProfile.of(waitValues, counts.total)
    }

    /**
     * 一向聽手牌每一種讓它聽牌的進張，以及進張後打出最佳一張牌的聽牌結果。
     *
     * 聽牌時可選擇默聽，或宣告規則回報的 [PositionRules.prospectiveDeclarations] 之一並付出宣告成本。
     */
    private fun tenpaiBranches(
        hand: Hand,
        view: HypotheticalView,
        declarations: Set<GameAction.Extension>,
    ): List<TenpaiBranch> {
        if (unseen.total == 0) return emptyList()
        val choices = listOf(declarations) + rules.prospectiveDeclarations(view.view, hand)
            .filterNot { it in declarations }
            .map { declarations + it }
        return unseen.kinds.mapNotNull { kind ->
            val count = unseen[kind]
            if (count == 0) return@mapNotNull null
            val afterDraw = unseen.without(kind)
            val drawn = hand.addTile(IdentifiedTile(Uuid.NIL, kind))
            distinctDiscardIndices(drawn)
                .mapNotNull { index ->
                    val rest = drawn.withoutTileAt(index)
                    val waits = (shanten(rest) as? ShantenResult.Tenpai)?.winningTiles ?: return@mapNotNull null
                    TenpaiBranch(
                        drawProbability = count.toDouble() / unseen.total,
                        options = choices.map { choice ->
                            TenpaiOption(
                                profile = tenpaiProfile(rest, view, waits, choice, afterDraw),
                                cost = declarationCost(view, choice - declarations),
                            )
                        },
                    )
                }
                .maxByOrNull { it.outlook(outlook.ownDraws - 1, outlook).expectedValue }
        }
    }

    /** 宣告 [declarations] 立即付出的點數；和牌時會以打點的一部分收回，因此不換算名次。 */
    private fun declarationCost(view: HypotheticalView, declarations: Set<GameAction.Extension>): Double = declarations.sumOf { declaration ->
        rules.declarationEffect(view.view, declaration).cost.toDouble()
    }

    /**
     * 兩向聽以上：第一步使用實際的有效牌張數，之後每一步使用一般有效牌張數（實際較少時取實際值），
     * 聽牌後使用一般聽牌張數，打點使用規則的基準打點。
     */
    private fun distantHandAssessment(hand: Hand, view: HypotheticalView, shanten: Int): HandAssessment {
        val value = if (level.valuesWins) {
            placement.gain(opponentModel.baselineWinValue(view.view, selfId).toDouble())
        } else {
            flatWinValue
        }
        if (unseen.total == 0) {
            return HandAssessment(shanten = shanten, outlook = WinOutlook.NONE, tenpaiProfile = null, estimatedValuePerWin = value)
        }
        val advancingCount = unseen.kinds
            .filter { kind -> unseen[kind] > 0 && advances(hand, kind, shanten) }
            .sumOf { unseen[it] }
        val firstAdvanceRate = advancingCount.toDouble() / unseen.total
        val typicalRate = parameters.typicalWaitTiles.toDouble() / unseen.total
        return HandAssessment(
            shanten = shanten,
            outlook = distantOutlook(
                shanten = shanten,
                firstAdvanceRate = firstAdvanceRate,
                laterAdvanceRate = minOf(firstAdvanceRate, parameters.typicalAdvanceTiles.toDouble() / unseen.total),
                tenpaiProfile = TenpaiProfile(tsumoRate = typicalRate, ronRate = typicalRate, tsumoValue = value, ronValue = value),
                outlook = outlook,
            ),
            tenpaiProfile = null,
            estimatedValuePerWin = value,
        )
    }

    /** 摸進 [kind] 並打出最佳一張牌後，向聽數是否低於 [shanten]。 */
    private fun advances(hand: Hand, kind: Tile, shanten: Int): Boolean {
        val drawn = hand.addTile(IdentifiedTile(Uuid.NIL, kind))
        return distinctDiscardIndices(drawn).any { shantenOf(drawn.withoutTileAt(it)) < shanten }
    }

    /** 評估者以 [tile] 和牌的點數；依規則不能以此方式和牌時為 null。 */
    private fun winValue(
        hand: Hand,
        view: HypotheticalView,
        tile: Tile,
        isTsumo: Boolean,
        declarations: Set<GameAction.Extension>,
    ): Double? {
        val key = WinValueKey(view.id, keyOf(hand), tile, isTsumo, declarations)
        val result = winValueByKey.getOrPut(key) {
            rules.winValue(
                view = view.view,
                hand = hand,
                winningTile = tile,
                isTsumo = isTsumo,
                declarations = declarations,
            )
        }
        return when (result) {
            is WinValue.Points -> if (level.valuesWins) placement.gain(result.points.toDouble()) else flatWinValue
            WinValue.Unknown -> if (level.valuesWins) {
                placement.gain(opponentModel.baselineWinValue(view.view, selfId).toDouble())
            } else {
                flatWinValue
            }
            WinValue.NotWinnable -> null
        }
    }

    /** 已快取的向聽計算。 */
    private fun shanten(hand: Hand): ShantenResult = shantenByHand.getOrPut(keyOf(hand)) { shantenCalculator.calculate(hand) }

    /** [hand] 的快取鍵。 */
    private fun keyOf(hand: Hand): HandKey = HandKey(
        tiles = hand.standingTiles.map { it.tile }.sortedWith(tileOrder),
        meldTypes = hand.melds.map { it.type },
        meldTiles = hand.melds.map { meld -> meld.tiles.map { it.tile }.sortedWith(tileOrder) },
    )
}

/** 每種牌面各取第一張的位置，作為打出一張牌的候選。 */
internal fun distinctDiscardIndices(hand: Hand): List<Int> = hand.tiles.withIndex().distinctBy { it.value.tile }.map { it.index }

/** 拿掉第 [index] 張立牌後的手牌。 */
internal fun Hand.withoutTileAt(index: Int): Hand = copy(tiles = tiles.filterIndexed { i, _ -> i != index })
