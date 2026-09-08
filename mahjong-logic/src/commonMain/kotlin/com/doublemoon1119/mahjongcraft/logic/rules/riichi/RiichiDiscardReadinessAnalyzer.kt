package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 日本麻將的打牌分析器：逐張立牌假想捨牌，且只以自身與公開資訊估算等待牌餘量。
 *
 * @property shantenCalculator 向聽數計算器，用於判斷假想捨牌後是否聽牌。
 * @property legalActionValidator 合法動作判定器，用於分析各等待牌的和牌資格。
 */
class RiichiDiscardReadinessAnalyzer(
    private val shantenCalculator: RiichiShantenCalculator,
    private val legalActionValidator: RiichiLegalActionValidator,
) : DiscardReadinessAnalyzer {
    override fun analyze(tableState: TableState, player: MahjongPlayer): List<DiscardReadinessAnalysis> {
        val visibleTiles = buildList {
            addAll(player.hand.tiles.map { it.tile })
            tableState.players.forEach { tablePlayer ->
                addAll(tablePlayer.discardPile.entries.map { it.tile.tile })
                addAll(tablePlayer.hand.exposedMelds.flatMap { meld -> meld.tiles.map { it.tile } })
            }
            (tableState.dynamicRuleState as? RiichiDynamicState)?.getDoraIndicators(tableState)?.first?.let { indicators ->
                addAll(indicators.map { it.tile })
            }
        }.groupingBy { it.riichiCanonical }.eachCount()
        val riichiState = player.playerRuleState as? RiichiPlayerState
        return player.hand.standingTiles.mapNotNull { discard ->
            val result = player.hand.discardById(discard.id) ?: return@mapNotNull null
            val hypotheticalPlayer = player.copy(hand = result.hand)
            val tenpai = shantenCalculator.calculate(Hand(result.hand.tiles, result.hand.melds)) as? ShantenResult.Tenpai
                ?: return@mapNotNull null
            val waits = tenpai.winningTiles.map(Tile::riichiCanonical).distinct()
            val discarded = player.discardPile.entries.map { it.tile.tile.riichiCanonical }.toSet() + discard.tile.riichiCanonical
            val passed = player.passedTilesInRound.map(Tile::riichiCanonical).toSet()
            val status = when {
                riichiState?.isPermanentlyFuriten == true -> PERMANENT_FURITEN
                waits.any { it in discarded } -> DISCARD_FURITEN
                waits.any { it in passed } -> TEMPORARY_FURITEN
                else -> null
            }
            DiscardReadinessAnalysis(
                discardTileId = discard.id,
                waitingTiles = waits.map { tile ->
                    WaitingTileAvailability(
                        tile = tile,
                        remainingCount = (COPIES_PER_TILE - (visibleTiles[tile] ?: 0)).coerceAtLeast(0),
                        winAvailability = legalActionValidator.analyzeWinAvailability(
                            tableState,
                            hypotheticalPlayer,
                            IdentifiedTile(Uuid.random(), tile),
                        ).toStatusId(),
                    )
                },
                statusIndicatorId = status,
            )
        }
    }

    /** 將日麻的和牌可用性判定轉為 [WaitingTileAvailability.winAvailability] 用的命名字串。 */
    private fun RiichiWinAvailability.toStatusId(): String = when (this) {
        RiichiWinAvailability.AVAILABLE -> WIN_AVAILABLE
        RiichiWinAvailability.TSUMO_ONLY -> WIN_TSUMO_ONLY
        RiichiWinAvailability.NO_YAKU -> WIN_NO_YAKU
        RiichiWinAvailability.BELOW_MINIMUM -> WIN_BELOW_MINIMUM
    }

    private companion object {
        const val COPIES_PER_TILE = 4
        const val DISCARD_FURITEN = "mahjongcraft:discard_furiten"
        const val TEMPORARY_FURITEN = "mahjongcraft:temporary_furiten"
        const val PERMANENT_FURITEN = "mahjongcraft:permanent_furiten"
        const val WIN_AVAILABLE = "mahjongcraft:win_available"
        const val WIN_TSUMO_ONLY = "mahjongcraft:win_tsumo_only"
        const val WIN_NO_YAKU = "mahjongcraft:win_no_yaku"
        const val WIN_BELOW_MINIMUM = "mahjongcraft:win_below_minimum"
    }
}
