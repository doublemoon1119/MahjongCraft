package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.logic.judgment.HandReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailability
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailabilityIds
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationDiscardPolicy
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import kotlin.uuid.Uuid

/**
 * 台灣麻將的手牌分析器，列出目前手牌或假想捨牌後的等待牌、剩餘張數與胡牌資格。
 *
 * @param config 本局台灣麻將規則設定。
 * @property declarationDiscardPolicy 投影宣告聽牌後玩家狀態的規則政策。
 * @property onPlayerDiscarded 投影正式捨牌後玩家組件的規則掛勾。
 */
class TaiwanDiscardReadinessAnalyzer internal constructor(
    config: TaiwanRuleConfig,
    private val declarationDiscardPolicy: DeclarationDiscardPolicy,
    private val onPlayerDiscarded: (MahjongPlayer) -> ComponentPatch,
) : DiscardReadinessAnalyzer {
    private val shantenCalculator = TaiwanShantenCalculator()
    private val winEvaluation = TaiwanWinEvaluation(config)
    override fun analyzeCurrentHand(
        tableState: TableState,
        player: MahjongPlayer,
        visibleTiles: List<IdentifiedTile>,
    ): HandReadinessAnalysis? {
        if (player.hand.standingTiles.size % COMPLETE_GROUP_SIZE != 1) return null
        val waits = waits(player.hand)
        if (waits.isEmpty()) return null
        return HandReadinessAnalysis(
            waitingTiles = waitingTileAvailability(tableState, player, visibleTiles, waits),
            statusIndicatorId = StatusIds.PASSED_WIN.takeIf {
                player.components[TaiwanPlayerComponents.PASSED_WIN] != null
            },
        )
    }

    override fun analyze(
        tableState: TableState,
        player: MahjongPlayer,
        visibleTiles: List<IdentifiedTile>,
    ): List<DiscardReadinessAnalysis> = analyzeWithProjection(
        tableState = tableState,
        player = player,
        visibleTiles = visibleTiles,
    ) { _, hypotheticalPlayer -> hypotheticalPlayer }

    /** 宣告聽牌時以正式宣告政策投影一般宣告、天聽或地聽，再計算每張等待牌的台數資格。 */
    override fun analyzeForAction(
        tableState: TableState,
        player: MahjongPlayer,
        action: GameAction,
        visibleTiles: List<IdentifiedTile>,
    ): List<DiscardReadinessAnalysis> {
        if (action != TAIWAN_DECLARE_READY_ACTION) {
            return analyze(tableState = tableState, player = player, visibleTiles = visibleTiles)
        }
        return analyzeWithProjection(
            tableState = tableState,
            player = player,
            visibleTiles = visibleTiles,
        ) { discardResult, hypotheticalPlayer ->
            val declaringPlayer = hypotheticalPlayer.copy(
                hand = player.hand,
                discardPile = player.discardPile,
                actionHistory = player.actionHistory,
            )
            val effect = declarationDiscardPolicy.resolve(
                tableState = tableState,
                player = declaringPlayer,
                action = TAIWAN_DECLARE_READY_ACTION,
                discardResult = discardResult,
            ) ?: return@analyzeWithProjection null
            hypotheticalPlayer.copy(
                components = effect.playerComponents.applyTo(
                    hypotheticalPlayer.components,
                    BuiltInComponentOwners.PLAYER,
                ),
            )
        }
    }

    /**
     * 逐張投影捨牌後的手牌與玩家狀態。
     *
     * @param tableState 目前權威桌況。
     * @param player 要分析的玩家。
     * @param visibleTiles 這位玩家看得到牌面的牌。
     * @param projectPlayer 套用動作專屬狀態的投影；回傳 `null` 時略過該候選。
     * @return 捨牌後仍聽牌的候選。
     */
    private fun analyzeWithProjection(
        tableState: TableState,
        player: MahjongPlayer,
        visibleTiles: List<IdentifiedTile>,
        projectPlayer: (Hand.DiscardResult, MahjongPlayer) -> MahjongPlayer?,
    ): List<DiscardReadinessAnalysis> = player.hand.standingTiles.mapNotNull { discard ->
        val discardResult = player.hand.discardById(discard.id) ?: return@mapNotNull null
        val waits = waits(discardResult.hand)
        if (waits.isEmpty()) return@mapNotNull null
        val discardedPlayer = player.copy(
            hand = discardResult.hand,
            discardPile = player.discardPile.discardTile(discardResult.tile),
        )
        val hypotheticalPlayer = discardedPlayer.copy(
            components = onPlayerDiscarded(discardedPlayer).applyTo(
                discardedPlayer.components,
                BuiltInComponentOwners.PLAYER,
            ),
        )
        val projectedPlayer = projectPlayer(discardResult, hypotheticalPlayer) ?: return@mapNotNull null
        DiscardReadinessAnalysis(
            discardTileId = discard.id,
            waitingTiles = waitingTileAvailability(tableState, projectedPlayer, visibleTiles, waits),
            statusIndicatorId = StatusIds.PASSED_WIN.takeIf {
                projectedPlayer.components[TaiwanPlayerComponents.PASSED_WIN] != null
            },
        )
    }

    /**
     * 建立各等待牌的剩餘張數與胡牌資格。
     *
     * @param tableState 目前權威桌況。
     * @param player 捨牌與動作投影後的玩家。
     * @param visibleTiles 這位玩家看得到牌面的牌。
     * @param waits 要分析的等待牌。
     * @return 各等待牌的分析結果。
     */
    private fun waitingTileAvailability(
        tableState: TableState,
        player: MahjongPlayer,
        visibleTiles: List<IdentifiedTile>,
        waits: List<Tile>,
    ): List<WaitingTileAvailability> {
        val visibleCounts = visibleTiles.distinctBy { it.id }
            .groupingBy { it.tile }
            .eachCount()
        return waits.map { tile ->
            val availability = winEvaluation.analyzeAvailability(
                tableState = tableState,
                player = player,
                winningTile = IdentifiedTile(Uuid.random(), tile),
            )
            WaitingTileAvailability(
                tile = tile,
                remainingCount = (COPIES_PER_TILE - (visibleCounts[tile] ?: 0)).coerceAtLeast(0),
                winAvailability = availability.toStatusId(),
            )
        }
    }

    /** 取得一手尚未加入胡牌張的手牌目前等待的牌。 */
    private fun waits(hand: Hand): List<Tile> = (shantenCalculator.calculate(Hand(hand.standingTiles, hand.melds)) as? ShantenResult.Tenpai)
        ?.winningTiles
        ?.distinct()
        .orEmpty()

    /** 將台麻胡牌資格轉為呈現用的狀態識別碼。 */
    private fun TaiwanWinAvailability.toStatusId(): String = when (this) {
        TaiwanWinAvailability.AVAILABLE -> WaitingTileAvailabilityIds.WIN_AVAILABLE
        TaiwanWinAvailability.TSUMO_ONLY -> StatusIds.WIN_TSUMO_ONLY
        TaiwanWinAvailability.BELOW_MINIMUM -> StatusIds.WIN_BELOW_MINIMUM
        TaiwanWinAvailability.NOT_WINNING -> StatusIds.WIN_NOT_AVAILABLE
    }

    /** 這個分析器產生的台灣麻將狀態識別碼。 */
    object StatusIds {
        /** 玩家目前仍在過水中。 */
        val PASSED_WIN: String = MahjongCraftMetadata.id("taiwan/readiness/passed_win")

        /** 這張等待牌目前只能自摸。 */
        val WIN_TSUMO_ONLY: String = MahjongCraftMetadata.id("taiwan/readiness/win_tsumo_only")

        /** 牌型成立，但尚未達到最低台數。 */
        val WIN_BELOW_MINIMUM: String = MahjongCraftMetadata.id("taiwan/readiness/win_below_minimum")

        /** 目前桌況下不能用這張牌完成有效牌型。 */
        val WIN_NOT_AVAILABLE: String = MahjongCraftMetadata.id("taiwan/readiness/win_not_available")
    }

    private companion object {
        const val COMPLETE_GROUP_SIZE: Int = 3
        const val COPIES_PER_TILE: Int = 4
    }
}
