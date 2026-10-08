package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.logic.judgment.HandReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 日本麻將的手牌分析器：分析目前手牌或逐張立牌假想捨牌，且只以呼叫端傳入的可見牌估算等待牌餘量。
 *
 * @property shantenCalculator 向聽數計算器，用於判斷假想捨牌後是否聽牌。
 * @property legalActionValidator 合法動作判定器，用於分析各等待牌的和牌資格。
 */
class RiichiDiscardReadinessAnalyzer(
    private val shantenCalculator: RiichiShantenCalculator,
    private val legalActionValidator: RiichiLegalActionValidator,
) : DiscardReadinessAnalyzer {
    override fun analyzeCurrentHand(
        tableState: TableState,
        player: MahjongPlayer,
        visibleTiles: List<IdentifiedTile>,
    ): HandReadinessAnalysis? {
        if (player.hand.standingTiles.size % COMPLETE_GROUP_SIZE != 1) return null
        val tenpai = shantenCalculator.calculate(Hand(player.hand.standingTiles, player.hand.melds)) as? ShantenResult.Tenpai
            ?: return null
        val waits = tenpai.winningTiles.map(Tile::riichiCanonical).distinct()
        if (waits.isEmpty()) return null
        return HandReadinessAnalysis(
            waitingTiles = waitingTileAvailability(
                tableState = tableState,
                visibleTiles = visibleTiles,
                winPlayer = player,
                waits = waits,
            ),
            statusIndicatorId = readinessStatus(player, waits),
        )
    }

    override fun analyze(
        tableState: TableState,
        player: MahjongPlayer,
        visibleTiles: List<IdentifiedTile>,
    ): List<DiscardReadinessAnalysis> = analyzeWithProjection(tableState, player, visibleTiles) { _, hypotheticalPlayer -> hypotheticalPlayer }

    /** 立直選牌期間以正式宣告邏輯投影每張候選，其他動作維持一般分析。 */
    override fun analyzeForAction(
        tableState: TableState,
        player: MahjongPlayer,
        action: GameAction,
        visibleTiles: List<IdentifiedTile>,
    ): List<DiscardReadinessAnalysis> {
        if (action != RIICHI_GAME_ACTION) return analyze(tableState = tableState, player = player, visibleTiles = visibleTiles)
        return analyzeWithProjection(tableState, player, visibleTiles) { discardResult, _ ->
            applyRiichiDeclaration(tableState, player, discardResult)?.player
        }
    }

    /** 以每張捨牌各自的玩家狀態投影計算共用聽牌、振聽、餘量與和牌資格。 */
    private fun analyzeWithProjection(
        tableState: TableState,
        player: MahjongPlayer,
        visibleTiles: List<IdentifiedTile>,
        projectPlayer: (Hand.DiscardResult, MahjongPlayer) -> MahjongPlayer?,
    ): List<DiscardReadinessAnalysis> {
        val riichiState = player.playerRuleState as? RiichiPlayerState
        return player.hand.standingTiles.mapNotNull { discard ->
            val result = player.hand.discardById(discard.id) ?: return@mapNotNull null
            val hypotheticalPlayer = player.copy(hand = result.hand)
            val projectedPlayer = projectPlayer(result, hypotheticalPlayer) ?: return@mapNotNull null
            val tenpai = shantenCalculator.calculate(Hand(result.hand.tiles, result.hand.melds)) as? ShantenResult.Tenpai
                ?: return@mapNotNull null
            val waits = tenpai.winningTiles.map(Tile::riichiCanonical).distinct()
            val status = when {
                riichiState?.isPermanentlyFuriten == true -> PERMANENT_FURITEN
                waits.any { it in discardedTiles(player) + discard.tile.riichiCanonical } -> DISCARD_FURITEN
                waits.any { it in passedTiles(player) } -> TEMPORARY_FURITEN
                else -> null
            }
            DiscardReadinessAnalysis(
                discardTileId = discard.id,
                waitingTiles = waitingTileAvailability(
                    tableState = tableState,
                    visibleTiles = visibleTiles,
                    winPlayer = projectedPlayer,
                    waits = waits,
                ),
                statusIndicatorId = status,
            )
        }
    }

    /** 以 [visibleTiles] 估算每張等待牌的剩餘數量，並判定 [winPlayer] 的和牌資格。 */
    private fun waitingTileAvailability(
        tableState: TableState,
        visibleTiles: List<IdentifiedTile>,
        winPlayer: MahjongPlayer,
        waits: List<Tile>,
    ): List<WaitingTileAvailability> {
        val visibleCounts = visibleTiles.distinctBy { it.id }
            .groupingBy { it.tile.riichiCanonical }
            .eachCount()
        return waits.map { tile ->
            WaitingTileAvailability(
                tile = tile,
                remainingCount = (COPIES_PER_TILE - (visibleCounts[tile] ?: 0)).coerceAtLeast(0),
                winAvailability = legalActionValidator.analyzeWinAvailability(
                    tableState,
                    winPlayer,
                    IdentifiedTile(Uuid.random(), tile),
                ).toStatusId(),
            )
        }
    }

    /** 目前手牌的振聽狀態；永久振聽優先於捨牌與同巡振聽。 */
    private fun readinessStatus(player: MahjongPlayer, waits: List<Tile>): String? = when {
        (player.playerRuleState as? RiichiPlayerState)?.isPermanentlyFuriten == true -> PERMANENT_FURITEN
        waits.any { it in discardedTiles(player) } -> DISCARD_FURITEN
        waits.any { it in passedTiles(player) } -> TEMPORARY_FURITEN
        else -> null
    }

    private fun discardedTiles(player: MahjongPlayer): Set<Tile> = player.discardPile.entries
        .mapTo(linkedSetOf()) { it.tile.tile.riichiCanonical }

    private fun passedTiles(player: MahjongPlayer): Set<Tile> = player.passedTilesInRound
        .mapTo(linkedSetOf(), Tile::riichiCanonical)

    /** 將日麻的和牌可用性判定轉為 [WaitingTileAvailability.winAvailability] 用的命名字串。 */
    private fun RiichiWinAvailability.toStatusId(): String = when (this) {
        RiichiWinAvailability.AVAILABLE -> StatusIds.WIN_AVAILABLE
        RiichiWinAvailability.TSUMO_ONLY -> StatusIds.WIN_TSUMO_ONLY
        RiichiWinAvailability.NO_YAKU -> StatusIds.WIN_NO_YAKU
        RiichiWinAvailability.BELOW_MINIMUM -> StatusIds.WIN_BELOW_MINIMUM
    }

    /**
     * 這個分析器產生的狀態命名字串。
     *
     * 呈現層依這些 ID 決定顯示什麼文字，因此是公開契約的一部分；[StatusIds.WIN_AVAILABLE] 是所有規則共用的
     * 中立預設，見 [DiscardReadinessAnalysis.statusIndicatorId] 與 [WaitingTileAvailability.winAvailability]。
     */
    object StatusIds {
        /** 捨牌振聽。 */
        const val DISCARD_FURITEN = "mahjongcraft:riichi/discard_furiten"

        /** 同巡振聽。 */
        const val TEMPORARY_FURITEN = "mahjongcraft:riichi/temporary_furiten"

        /** 立直後振聽，直到本局結束。 */
        const val PERMANENT_FURITEN = "mahjongcraft:riichi/permanent_furiten"

        /** 和牌資格沒有特殊限制。 */
        const val WIN_AVAILABLE = "mahjongcraft:win_available"

        /** 只能自摸和牌。 */
        const val WIN_TSUMO_ONLY = "mahjongcraft:riichi/win_tsumo_only"

        /** 無役，不能和牌。 */
        const val WIN_NO_YAKU = "mahjongcraft:riichi/win_no_yaku"

        /** 未達最低翻符要求。 */
        const val WIN_BELOW_MINIMUM = "mahjongcraft:riichi/win_below_minimum"
    }

    private companion object {
        const val COPIES_PER_TILE = 4
        const val DISCARD_FURITEN = StatusIds.DISCARD_FURITEN
        const val TEMPORARY_FURITEN = StatusIds.TEMPORARY_FURITEN
        const val PERMANENT_FURITEN = StatusIds.PERMANENT_FURITEN
        const val COMPLETE_GROUP_SIZE = 3
    }
}
