package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
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

/** 驗證事件批次的終局排名與凍結事實判定在真人篩選後保持一致。 */
class AchievementBatchRankingTest {
    /** 內建規則 registry，供事件投影與舊判定共用。 */
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
            assertEquivalent(label, committed(game))
        }
    }

    /** 自訂規則排名、同分穩定排序與凍結舊判定使用相同規則比較器。 */
    @Test
    fun `custom ranking comparator is used by event and legacy achievement paths`() {
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
        assertEquivalent("custom low score ranking", committed(game), registry)
    }

    /** 比較真正事件投影與凍結的事實判定，並確認七種統計一致。 */
    private fun assertEquivalent(
        label: String,
        facts: CommittedGameFacts,
        registry: MahjongModuleRegistryImpl = modules,
    ) {
        val game = facts.game ?: checkNotNull(facts.previousGame)
        val module = registry.getModule(game.tableState.config)
        val batch = checkNotNull(GameEventProjector(registry).projectBatch(facts))
        val oldMap = LegacyGenericAchievementDetector.detect(facts, module)
        val expected = ordered(batch, oldMap)
        val actual = GameAchievementDetector(GameAchievementResolverRegistryImpl()).detect(batch)

        assertEquals(expected, actual, label)
        batch.players.filterNot { it.isAi }.forEach { player ->
            assertEquals(
                AchievementStatisticIds.statisticsFor(oldMap[player.playerId].orEmpty()),
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

    /** 依事件批次的玩家順序建立真人成果結果。 */
    private fun ordered(batch: CommittedMatchEventBatch, achievements: Map<Uuid, Set<String>>): List<PlayerAchievements> = batch.players
        .filterNot { it.isAi }
        .mapNotNull { player ->
            achievements[player.playerId]?.takeIf { it.isNotEmpty() }?.let {
                PlayerAchievements(player.playerId, batch.matchId, batch.ruleModuleId, it)
            }
        }
}
