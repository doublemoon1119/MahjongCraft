package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.GameLength
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.layout.ContinuousPhysicalWallLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.logic.table.layout.WallRingLayoutSupport
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningCompletion
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningContext
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningProcedure
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningStep
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/** 驗證牌山與牌牆布局使用整場對局的完整玩家人數，而不是規則配置的最大人數。 */
class GameInitializerPlayerCountTest {
    /** 驗證初次開局依完整玩家名單選擇 16、24 或 32 張牌山。 */
    @Test
    fun `initialize selects wall size from actual roster count`() {
        listOf(2 to 16, 3 to 24, 4 to 32).forEach { (playerCount, expectedTileCount) ->
            val module = VariableWallModule()
            val result = GameInitializer.initialize(
                id = Uuid.random(),
                playerIds = List(playerCount) { Uuid.random() },
                module = module,
            )

            assertEquals(expectedTileCount - playerCount, result.tableState.tileWall.remainingCount)
            assertEquals(expectedTileCount, result.initialPhysicalWallLayout?.placements?.size)
        }
    }

    /** 驗證換局時仍依上一局完整玩家名單建立相同大小的牌山。 */
    @Test
    fun `startNextRound selects wall size from actual roster count`() {
        listOf(2 to 16, 3 to 24, 4 to 32).forEach { (playerCount, expectedTileCount) ->
            val module = VariableWallModule()
            val players = List(playerCount) { index ->
                MahjongPlayer(
                    id = Uuid.random(),
                    initialSeatIndex = index,
                    discardPile = RiichiDiscardPile(),
                    seatWind = Wind.EAST,
                )
            }
            val advancement = RoundAdvancementResult(
                players = players,
                dealerPlayerId = players.first().id,
                roundNumber = 1,
                comboCount = 0,
                prevalentWind = Wind.EAST,
            )

            val result = GameInitializer.startNextRound(
                gameId = Uuid.random(),
                roundAdvancement = advancement,
                previousDynamicRuleState = null,
                module = module,
            )

            assertEquals(expectedTileCount - playerCount, result.tableState.tileWall.remainingCount)
            assertEquals(expectedTileCount, result.initialPhysicalWallLayout?.placements?.size)
        }
    }

    /** 驗證兩人牌牆使用相對的兩個標準側面。 */
    @Test
    fun `two player layout uses opposite physical sides`() {
        val module = VariableWallModule()
        val layout = assertNotNull(module.createWallLayout(2))

        assertEquals(listOf(0, 2), layout.sideArrangement(0).let { listOf(it.quarterTurnsOf(0), it.quarterTurnsOf(1)) })
        assertEquals(2, layout.sideArrangement(0).sideCount)
    }

    /** 只改變牌山大小與布局，其他規則服務沿用日麻測試實作的 fixture。 */
    private class VariableWallModule : MahjongRuleModule<VariableWallConfig> {
        /** 提供本測試不變更的計分與玩家狀態服務。 */
        private val delegate = RiichiRuleModule("test:variable-wall", RiichiRuleConfig())
        override val id: String = delegate.id
        override val config: VariableWallConfig = VariableWallConfig()
        override val tileOrder = delegate.tileOrder

        override fun createWallFactory(playerCount: Int): TileWallFactory = VariableWallFactory(playerCount)

        override fun createWallOpeningPolicy() = null

        override fun createRoundOpeningProcedure(playerCount: Int): RoundOpeningProcedure = FixedOpeningProcedure

        override fun createWallLayout(playerCount: Int): TileWallLayout = VariableWallLayout(playerCount)

        override fun createPhysicalWallLayoutPolicy(): PhysicalWallLayoutPolicy = ContinuousPhysicalWallLayoutPolicy

        override fun createDiscardPile() = delegate.createDiscardPile()
        override fun createShantenCalculator() = delegate.createShantenCalculator()
        override fun createLegalActionValidator() = delegate.createLegalActionValidator()
        override fun createHandValueCalculator() = delegate.createHandValueCalculator()
        override fun createHandValueContextCalculator() = delegate.createHandValueContextCalculator()
        override fun createInitialDynamicState() = delegate.createInitialDynamicState()
        override fun createNextRoundDynamicState(previous: DynamicRuleState?) = delegate.createNextRoundDynamicState(previous)
        override fun createInitialPlayerRuleState() = delegate.createInitialPlayerRuleState()
        override fun onPlayerDrew(player: MahjongPlayer) = delegate.onPlayerDrew(player)
        override fun onPlayerDiscarded(player: MahjongPlayer) = delegate.onPlayerDiscarded(player)
        override fun onMeldClaimed(players: List<MahjongPlayer>) = delegate.onMeldClaimed(players)
        override fun declareTsumo(tableState: TableState, player: MahjongPlayer): WinResolutionResult? = delegate.declareTsumo(tableState, player)

        override fun declareRon(
            tableState: TableState,
            player: MahjongPlayer,
            winningTile: IdentifiedTile,
            discarderId: Uuid,
            isRobbingKan: Boolean,
        ): WinResolutionResult? = delegate.declareRon(
            tableState = tableState,
            player = player,
            winningTile = winningTile,
            discarderId = discarderId,
            isRobbingKan = isRobbingKan,
        )

        override fun collectStickPot(tableState: TableState): Pair<DynamicRuleState?, Int>? = delegate.collectStickPot(tableState)

        override fun resolveComboBonusPayments(
            tableState: TableState,
            winnerId: Uuid,
            discarderId: Uuid?,
            resolution: WinResolutionResult?,
        ): Map<Uuid, Int> = delegate.resolveComboBonusPayments(
            tableState = tableState,
            winnerId = winnerId,
            discarderId = discarderId,
            resolution = resolution,
        )

        override fun declareExhaustiveDraw(tableState: TableState): ExhaustiveDrawSettlementResult? = delegate.declareExhaustiveDraw(tableState)

        override fun resolveAbortiveDrawRevealedHands(
            tableState: TableState,
            declarerId: Uuid?,
            reason: ExhaustiveDrawReason,
        ): List<RevealedHandSettlement> = delegate.resolveAbortiveDrawRevealedHands(
            tableState = tableState,
            declarerId = declarerId,
            reason = reason,
        )

        override fun resolveMultiRonAbortiveDraw(): ExhaustiveDrawReason? = delegate.resolveMultiRonAbortiveDraw()
    }

    /**
     * 依完整玩家人數產生固定數量牌張的測試牌山。
     *
     * @property playerCount 本局完整玩家名單的人數。
     */
    private class VariableWallFactory(private val playerCount: Int) : TileWallFactory {
        override fun create(): TileWall = TileWall(
            List(playerCount * 8) { index ->
                IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Character, (index % 9) + 1))
            },
        )
    }

    /**
     * 依完整玩家人數選擇兩面或四面牌牆的測試布局。
     *
     * @param playerCount 本局完整玩家名單的人數。
     */
    private class VariableWallLayout(playerCount: Int) : TileWallLayout {
        /** 兩人使用兩面牌牆，其餘人數使用四面。 */
        private val sideCount = if (playerCount == 2) 2 else 4

        /** 每面的墩數與測試牌組總張數一致。 */
        private val stacksPerSide = when (playerCount) {
            2 -> 4
            3 -> 3
            else -> 4
        }

        override fun sideArrangement(dealerSeatIndex: Int): TileWallSideArrangement = TileWallSideArrangement(
            reference = TileWallSideReference.TABLE,
            quarterTurns = if (sideCount == 2) listOf(0, 2) else listOf(0, 1, 2, 3),
        )

        override fun resolve(
            shuffledTiles: List<IdentifiedTile>,
            opening: WallOpening,
        ): TileWallLayoutResult = WallRingLayoutSupport.resolve(
            shuffledTiles = shuffledTiles,
            opening = opening,
            stacksPerSide = stacksPerSide,
            deadWallStacks = 0,
            sideCount = sideCount,
        )
    }

    /** 不擲骰、直接使用固定開門位置的測試開局程序。 */
    private object FixedOpeningProcedure : RoundOpeningProcedure {
        override val supportedDiceCounts: Set<Int> = emptySet()

        override fun next(context: RoundOpeningContext): RoundOpeningStep = RoundOpeningStep.Complete(
            result = RoundOpeningCompletion(
                dealerPlayerId = context.playerIdsInTurnOrder.first(),
                wallOpening = WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 1),
            ),
        )
    }
}

/**
 * 供牌山人數測試使用的最小規則設定。
 *
 * @property initialHandSize 每位玩家開局取得的手牌張數。
 * @property deadTileCount 牌山尾端保留的牌張數。
 * @property scoreConfig 測試用的分數設定。
 * @property gameLength 測試用的局數設定。
 * @property minimumWinConstraint 測試用的最低和牌條件。
 * @property minPlayers 支援的最少玩家數。
 * @property maxPlayers 支援的最多玩家數。
 * @property multiRonPolicy 多家榮和的測試政策。
 * @property revealsClosedKanTiles 是否公開暗槓牌張。
 */
private data class VariableWallConfig(
    override val initialHandSize: Int = 1,
    override val deadTileCount: Int = 0,
    override val scoreConfig: ScoreConfig = object : ScoreConfig {
        override val initialScore: Int = 0
        override val bustThreshold: Int? = null
    },
    override val gameLength: GameLength = object : GameLength {
        override val totalRounds: Int = 1
    },
    override val minimumWinConstraint: Int = 0,
    override val minPlayers: Int = 2,
    override val maxPlayers: Int = 4,
    override val multiRonPolicy: MultiRonPolicy = MultiRonPolicy(RonResolution.ALL_WINNERS, RonResolution.ALL_WINNERS),
    override val revealsClosedKanTiles: Boolean = false,
) : MahjongRuleConfig
