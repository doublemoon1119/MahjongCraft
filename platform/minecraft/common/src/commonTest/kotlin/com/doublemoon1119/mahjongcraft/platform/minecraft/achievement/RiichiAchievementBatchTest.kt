package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverDefinition
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiGameEventPublicMarkerResolver
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement.RiichiAchievementIds
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證日麻公開批次在四人、三人與終局案例中授予完整成果與統計。 */
class RiichiAchievementBatchTest {
    private val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
    private val resolvers = GameAchievementResolverRegistryImpl().apply {
        BundledRiichiMinecraftExtension.registerGameAchievementResolvers(this)
    }
    private val detector = GameAchievementDetector(resolvers)
    private val markerResolvers = GameEventPublicMarkerResolverRegistry().apply {
        register(GameEventPublicMarkerResolverDefinition(BuiltInRuleModuleIds.RIICHI, RiichiGameEventPublicMarkerResolver))
        register(GameEventPublicMarkerResolverDefinition(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, RiichiGameEventPublicMarkerResolver))
    }

    /** 四人與三人日麻都應產生預期的成果與統計增量。 */
    @Test
    fun `riichi achievements retain expected outcomes for four and three players`() {
        val fourPlayerGame = game(RiichiRuleConfig())
        val fourPlayerWinner = fourPlayerGame.tableState.players.first()
        val threePlayerGame = game(ThreePlayerRiichiRuleConfig())
        val threePlayerWinner = threePlayerGame.tableState.players.first()
        val fourPlayerWin = facts(
            fourPlayerGame,
            HistoryFact.WinSettled(
                outcomeId = BuiltInRoundOutcomeIds.TSUMO,
                winDetails = listOf(HistoryWinDetails(fourPlayerWinner.id, nonYakumanFields(YakuType.Riichi))),
            ),
        )
        val threePlayerWin = facts(
            threePlayerGame,
            HistoryFact.WinSettled(
                outcomeId = BuiltInRoundOutcomeIds.TSUMO,
                winDetails = listOf(HistoryWinDetails(threePlayerWinner.id, yakumanFields(YakuType.Suuankou))),
            ),
        )
        val completedMatch = facts(
            fourPlayerGame,
            HistoryFact.MatchCompleted(
                reasonId = "mahjongcraft:completed",
                finalScoresByPlayerId = fourPlayerGame.tableState.players.associateWith { it.score }.mapKeys { it.key.id },
            ),
        )

        assertExpected(
            caseIndex = 0,
            facts = fourPlayerWin,
            expectedAchievements = mapOf(
                fourPlayerWinner.id to setOf(
                    BuiltInAchievementIds.WIN,
                    BuiltInAchievementIds.SELF_DRAW_WIN,
                    RiichiAchievementIds.RIICHI_WIN,
                ),
            ),
            expectedStatistics = mapOf(fourPlayerWinner.id to setOf(AchievementStatisticIds.WINS, AchievementStatisticIds.SELF_DRAW_WINS)),
        )
        assertExpected(
            caseIndex = 1,
            facts = threePlayerWin,
            expectedAchievements = mapOf(
                threePlayerWinner.id to setOf(
                    BuiltInAchievementIds.WIN,
                    BuiltInAchievementIds.SELF_DRAW_WIN,
                    RiichiAchievementIds.YAKUMAN,
                    checkNotNull(RiichiAchievementIds.yakuman(YakuType.Suuankou)),
                ),
            ),
            expectedStatistics = mapOf(threePlayerWinner.id to setOf(AchievementStatisticIds.WINS, AchievementStatisticIds.SELF_DRAW_WINS)),
        )
        val completedPlayers = fourPlayerGame.tableState.players
        val completed = setOf(BuiltInAchievementIds.MATCH_COMPLETED, RiichiAchievementIds.MATCH_COMPLETED)
        assertExpected(
            caseIndex = 2,
            facts = completedMatch,
            expectedAchievements = mapOf(
                completedPlayers[0].id to completed + BuiltInAchievementIds.FIRST_PLACE,
                completedPlayers[1].id to completed,
                completedPlayers[2].id to completed,
                completedPlayers[3].id to completed + BuiltInAchievementIds.LAST_PLACE,
            ),
            expectedStatistics = mapOf(
                completedPlayers[0].id to setOf(AchievementStatisticIds.MATCHES_COMPLETED, AchievementStatisticIds.FIRST_PLACES),
                completedPlayers[1].id to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
                completedPlayers[2].id to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
                completedPlayers[3].id to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
            ),
        )
    }

    /** 自然役滿仍應依和牌前的玩家狀態取得立直與一發成果。 */
    @Test
    fun `natural yakuman retains riichi and ippatsu from the prior state`() {
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            playerRuleState = RiichiPlayerState(
                riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East),
                isIppatsu = true,
            ),
        )
        val game = game(RiichiRuleConfig(), winner)
        val facts = facts(
            game,
            HistoryFact.WinSettled(
                outcomeId = BuiltInRoundOutcomeIds.TSUMO,
                winDetails = listOf(HistoryWinDetails(winner.id, yakumanFields(YakuType.Suuankou))),
            ),
        )

        assertExpected(
            caseIndex = 3,
            facts = facts,
            expectedAchievements = mapOf(
                winner.id to setOf(
                    BuiltInAchievementIds.WIN,
                    BuiltInAchievementIds.SELF_DRAW_WIN,
                    RiichiAchievementIds.RIICHI_WIN,
                    RiichiAchievementIds.IPPATSU,
                    RiichiAchievementIds.YAKUMAN,
                    checkNotNull(RiichiAchievementIds.yakuman(YakuType.Suuankou)),
                ),
            ),
            expectedStatistics = mapOf(winner.id to setOf(AchievementStatisticIds.WINS, AchievementStatisticIds.SELF_DRAW_WINS)),
        )
    }

    /** 比較真人玩家成果與明確列出的統計結果。 */
    private fun assertExpected(
        caseIndex: Int,
        facts: CommittedGameFacts,
        expectedAchievements: Map<Uuid, Set<String>>,
        expectedStatistics: Map<Uuid, Set<String>>,
    ) {
        val batch = checkNotNull(GameEventProjector(modules, markerResolvers = markerResolvers).projectBatch(facts))
        val actual = detector.detect(batch)

        assertEquals(ordered(batch, expectedAchievements), actual, "achievement result case $caseIndex")
        batch.players.filterNot { it.isAi }.forEach { player ->
            val actualStats = AchievementStatisticIds.statisticsFor(actual.firstOrNull { it.playerId == player.playerId }?.achievementIds.orEmpty())
            assertEquals(expectedStatistics[player.playerId].orEmpty(), actualStats, "statistics case $caseIndex for ${player.playerId}")
        }
    }

    /** 依事件判定器使用的玩家順序，只保留真人玩家的成果。 */
    private fun ordered(batch: CommittedMatchEventBatch, achievements: Map<Uuid, Set<String>>): List<PlayerAchievements> = batch.players.filterNot { it.isAi }.mapNotNull { player ->
        achievements[player.playerId]?.takeIf { it.isNotEmpty() }?.let {
            PlayerAchievements(player.playerId, batch.matchId, batch.ruleModuleId, it)
        }
    }

    /** 依規則設定建立四人或三人的測試對局。 */
    private fun game(config: MahjongRuleConfig, firstPlayer: MahjongPlayer? = null): Game {
        val winds = if (config is ThreePlayerRiichiRuleConfig) listOf(Wind.EAST, Wind.SOUTH, Wind.WEST) else listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH)
        val players = winds.map { wind ->
            if (wind == Wind.EAST && firstPlayer != null) firstPlayer else FakeMahjongPlayerFactory.create(initialSeat = wind)
        }
        return Game(
            tableState = FakeTableStateFactory.create(players = players, config = config),
            flowConfig = GameFlowConfig(),
        )
    }

    /** 將事實包成交易前後相同的已提交對局資料。 */
    private fun facts(game: Game, fact: HistoryFact) = CommittedGameFacts(
        venueId = game.id,
        previousGame = game,
        game = game,
        facts = listOf(HistoryEventDraft(null, fact)),
    )

    /** 建立一番的一般日麻役種欄位。 */
    private fun nonYakumanFields(vararg yaku: YakuType) = listOf(
        WinSettlementDetailField(
            RiichiWinSettlementIds.YAKU_FIELD,
            WinSettlementDetailValue.Entries(yaku.map { RiichiWinSettlementIds.yakuEntry(it, 1) }),
        ),
    )

    /** 建立自然役滿欄位。 */
    private fun yakumanFields(vararg yaku: YakuType) = listOf(
        WinSettlementDetailField(
            RiichiWinSettlementIds.YAKU_FIELD,
            WinSettlementDetailValue.Entries(yaku.map { RiichiWinSettlementIds.yakumanEntry(it, 1) }),
        ),
        WinSettlementDetailField(
            RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD,
            WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(RiichiWinSettlementIds.YAKUMAN, 1))),
        ),
    )
}
