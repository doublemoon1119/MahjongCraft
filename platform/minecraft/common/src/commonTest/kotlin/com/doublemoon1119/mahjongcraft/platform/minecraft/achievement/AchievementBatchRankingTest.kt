package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RankablePlayer
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證事件批次的終局排名與真人成果篩選。 */
class AchievementBatchRankingTest {
    /** 內建規則 registry，供事件投影使用。 */
    private val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }

    /** AI 排名第一、最後或同時存在時，AI 仍參與排名但不取得成果。 */
    @Test
    fun `ai participates in ranking but never receives first or last place`() {
        val players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH)
        val variants = listOf(
            setOf(players[0]) to "ai first",
            setOf(players[3]) to "ai last",
            setOf(players[0], players[3]) to "ai first and last",
        )

        variants.forEach { (aiWinds, label) ->
            val game = game(aiWinds)
            val playersByWind = game.tableState.players.associateBy { it.seatWind }
            val expected = when (aiWinds) {
                setOf(Wind.EAST) -> mapOf(
                    playersByWind.getValue(Wind.SOUTH).id to setOf(BuiltInAchievementIds.MATCH_COMPLETED),
                    playersByWind.getValue(Wind.WEST).id to setOf(BuiltInAchievementIds.MATCH_COMPLETED),
                    playersByWind.getValue(Wind.NORTH).id to setOf(BuiltInAchievementIds.MATCH_COMPLETED, BuiltInAchievementIds.LAST_PLACE),
                )
                setOf(Wind.NORTH) -> mapOf(
                    playersByWind.getValue(Wind.EAST).id to setOf(BuiltInAchievementIds.MATCH_COMPLETED, BuiltInAchievementIds.FIRST_PLACE),
                    playersByWind.getValue(Wind.SOUTH).id to setOf(BuiltInAchievementIds.MATCH_COMPLETED),
                    playersByWind.getValue(Wind.WEST).id to setOf(BuiltInAchievementIds.MATCH_COMPLETED),
                )
                else -> mapOf(
                    playersByWind.getValue(Wind.SOUTH).id to setOf(BuiltInAchievementIds.MATCH_COMPLETED),
                    playersByWind.getValue(Wind.WEST).id to setOf(BuiltInAchievementIds.MATCH_COMPLETED),
                )
            }
            val expectedStatistics = when (aiWinds) {
                setOf(Wind.EAST) -> mapOf(
                    playersByWind.getValue(Wind.SOUTH).id to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
                    playersByWind.getValue(Wind.WEST).id to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
                    playersByWind.getValue(Wind.NORTH).id to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
                )
                setOf(Wind.NORTH) -> mapOf(
                    playersByWind.getValue(Wind.EAST).id to setOf(AchievementStatisticIds.MATCHES_COMPLETED, AchievementStatisticIds.FIRST_PLACES),
                    playersByWind.getValue(Wind.SOUTH).id to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
                    playersByWind.getValue(Wind.WEST).id to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
                )
                else -> mapOf(
                    playersByWind.getValue(Wind.SOUTH).id to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
                    playersByWind.getValue(Wind.WEST).id to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
                )
            }
            assertExpected(label, committed(game), expected, expectedStatistics)
        }
    }

    /** 自訂規則排名與同分穩定排序使用規則提供的比較器。 */
    @Test
    fun `custom ranking comparator is used by event achievement projection`() {
        val registry = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition<RiichiRuleConfig>(
                    configClass = RiichiRuleConfig::class,
                    id = "example:low_score_first",
                    factory = { config, id ->
                        object : MahjongRuleModule<RiichiRuleConfig> by RiichiRuleModule(id, config) {
                            /** 以較低分者優先，平分時保留原本玩家順序。 */
                            override fun compareForMatchRanking(): Comparator<RankablePlayer> = compareBy { it.score }
                        }
                    },
                ),
            )
        }
        val game = game(emptySet(), tied = true)
        val humanIds = game.tableState.players.map { it.id }
        val expected = mapOf(
            humanIds[0] to setOf(BuiltInAchievementIds.MATCH_COMPLETED),
            humanIds[1] to setOf(BuiltInAchievementIds.MATCH_COMPLETED, BuiltInAchievementIds.LAST_PLACE),
            humanIds[2] to setOf(BuiltInAchievementIds.MATCH_COMPLETED, BuiltInAchievementIds.FIRST_PLACE),
            humanIds[3] to setOf(BuiltInAchievementIds.MATCH_COMPLETED),
        )
        val expectedStatistics = mapOf(
            humanIds[0] to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
            humanIds[1] to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
            humanIds[2] to setOf(AchievementStatisticIds.MATCHES_COMPLETED, AchievementStatisticIds.FIRST_PLACES),
            humanIds[3] to setOf(AchievementStatisticIds.MATCHES_COMPLETED),
        )
        assertExpected("custom low score ranking", committed(game), expected, expectedStatistics, registry)
    }

    /** 依固定的成果與統計預期驗證事件投影。 */
    private fun assertExpected(
        label: String,
        facts: CommittedGameFacts,
        expected: Map<Uuid, Set<String>>,
        expectedStatistics: Map<Uuid, Set<String>>,
        registry: MahjongModuleRegistryImpl = modules,
    ) {
        val batch = checkNotNull(GameEventProjector(registry).projectBatch(facts))
        val actual = GameAchievementDetector(GameAchievementResolverRegistryImpl()).detect(batch)

        assertEquals(
            expected.map { (playerId, achievementIds) ->
                PlayerAchievements(playerId, batch.matchId, batch.ruleModuleId, achievementIds)
            },
            actual,
            label,
        )
        batch.players.filterNot { it.isAi }.forEach { player ->
            assertEquals(
                expectedStatistics[player.playerId].orEmpty(),
                AchievementStatisticIds.statisticsFor(actual.firstOrNull { it.playerId == player.playerId }?.achievementIds.orEmpty()),
                "$label statistics for ${player.playerId}",
            )
        }
    }

    /** 建立指定 AI 座位與分數的終局對局。 */
    private fun game(
        aiWinds: Set<Wind>,
        tied: Boolean = false,
    ): Game {
        val winds = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH)
        val scores = if (tied) listOf(20_000, 20_000, 10_000, 10_000) else listOf(40_000, 30_000, 20_000, 10_000)
        val table = FakeTableStateFactory.create(
            players = winds.mapIndexed { index, wind ->
                FakeMahjongPlayerFactory.create(initialSeat = wind).copy(score = scores[index])
            },
            config = RiichiRuleConfig(),
        )
        return Game(
            tableState = table,
            flowConfig = GameFlowConfig(),
            aiPlayerStrategyKeys = winds.filter { it in aiWinds }.associate { wind ->
                table.players.first { it.seatWind == wind }.id to "test:ai"
            },
        )
    }

    /** 建立只包含終局完成事實的交易，刻意不加入 MatchStarted。 */
    private fun committed(game: Game): CommittedGameFacts = CommittedGameFacts(
        venueId = game.id,
        previousGame = game,
        game = game,
        facts = listOf(
            HistoryEventDraft(
                actorPlayerId = null,
                fact = HistoryFact.MatchCompleted(
                    reasonId = "mahjongcraft:completed",
                    finalScoresByPlayerId = game.tableState.players.associate { it.id to it.score },
                ),
            ),
        ),
    )
}
