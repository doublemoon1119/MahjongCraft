package com.doublemoon1119.mahjongcraft.flow.server.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RankablePlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [HistoryResultProjector] 的終局結果投影測試。 */
class HistoryResultProjectorTest {
    /** 驗證相同分數仍依規則模組的起家座位比較器決定名次。 */
    @Test
    fun `test equal scores use rule comparator tie break`() {
        val table = tableWithScores(1000, 1000)
        val players = table.players
        val result = HistoryResultProjector.project(events(table, players.associate { it.id to it.score }), riichiRegistry())
        assertEquals(1, result.first { it.playerId == players[0].id }.finalRank)
        assertEquals(2, result.first { it.playerId == players[1].id }.finalRank)
    }

    /** 驗證規則模組可以覆寫終局排序。 */
    @Test
    fun `test custom comparator controls final rank`() {
        val table = tableWithScores(1000, 2000)
        val players = table.players
        val result = HistoryResultProjector.project(events(table, players.associate { it.id to it.score }), reverseRegistry())
        assertEquals(1, result.first { it.playerId == players[0].id }.finalRank)
        assertEquals(2, result.first { it.playerId == players[1].id }.finalRank)
    }

    /** 驗證規則模組未知時不產生未經授權的名次。 */
    @Test
    fun `test unknown module leaves ranks unknown`() {
        val table = tableWithScores(1000, 2000)
        val players = table.players
        val result = HistoryResultProjector.project(events(table, players.associate { it.id to it.score }), MahjongModuleRegistryImpl())
        assertTrue(result.all { it.finalRank == null }, "Unknown rule modules must not guess ranks")
        assertEquals(1000, result.first { it.playerId == players[0].id }.finalScore)
    }

    /** 驗證多局事件會以最後一個 [HistoryFact.RoundStarted] 作為重建基準。 */
    @Test
    fun `test later round started resets reconstruction state`() {
        val first = tableWithScores(1000, 1000)
        val second = tableWithScores(3000, 1000, first.id)
        val matchId = Uuid.random()
        val result = HistoryResultProjector.project(
            listOf(
                event(matchId, 1, HistoryFact.MatchStarted(first, GameFlowConfig(), emptyMap())),
                event(matchId, 2, HistoryFact.RoundStarted(second)),
                event(matchId, 3, HistoryFact.MatchCompleted("test:completed", second.players.associate { it.id to it.score })),
            ),
            riichiRegistry(),
        )
        assertEquals(3000, result.first { it.playerId == second.players[0].id }.finalScore)
    }

    /** 驗證缺少終局分數時不回退使用桌況內的分數。 */
    @Test
    fun `test missing final score stays unknown`() {
        val table = tableWithScores(1000, 2000)
        val result = HistoryResultProjector.project(events(table, mapOf(table.players[0].id to 1000)), riichiRegistry())
        assertNull(result.first { it.playerId == table.players[1].id }.finalScore)
        assertNull(result.first { it.playerId == table.players[1].id }.finalRank)
    }

    /** 驗證缺少終局事件時不產生投影。 */
    @Test
    fun `test missing match completion returns empty projection`() {
        assertEquals(emptyList(), HistoryResultProjector.project(emptyList(), MahjongModuleRegistryImpl()))
    }

    /** 建立包含兩位玩家的測試桌況。 */
    private fun tableWithScores(firstScore: Int, secondScore: Int, id: Uuid = Uuid.random()): TableState = FakeTableStateFactory.create(
        id = id,
        config = RiichiRuleConfig(),
        players = listOf(
            FakeMahjongPlayerFactory.create().copy(score = firstScore),
            FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
                .copy(score = secondScore),
        ),
    )

    /** 建立開局與終局事件。 */
    private fun events(table: TableState, scores: Map<Uuid, Int>): List<HistoryOutboxEvent> {
        val matchId = Uuid.random()
        return listOf(
            event(matchId, 1, HistoryFact.MatchStarted(table, GameFlowConfig(), emptyMap())),
            event(matchId, 2, HistoryFact.MatchCompleted("test:completed", scores)),
        )
    }

    /** 建立測試用歷史事件。 */
    private fun event(matchId: Uuid, sequence: Long, fact: HistoryFact): HistoryOutboxEvent = HistoryOutboxEvent(
        matchId = matchId,
        venueId = Uuid.random(),
        roundNumber = 1,
        sequence = sequence,
        occurredAtEpochMillis = sequence,
        actorPlayerId = null,
        fact = fact,
    )

    /** 建立使用正式日麻模組的 registry。 */
    private fun riichiRegistry(): MahjongModuleRegistry = MahjongModuleRegistryImpl().apply {
        register(RiichiRuleConfig::class, "test:riichi") { config, id ->
            RiichiRuleModule(id, config)
        }
        freeze()
    }

    /** 建立使用反向分數排序的 registry。 */
    private fun reverseRegistry(): MahjongModuleRegistry = MahjongModuleRegistryImpl().apply {
        register(RiichiRuleConfig::class, "test:reverse") { config, id ->
            ReverseRankingModule(RiichiRuleModule(id, config))
        }
        freeze()
    }

    /** 覆寫終局比較器的測試規則模組。 */
    private class ReverseRankingModule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        /** 依分數升序排列。 */
        override fun compareForMatchRanking(): Comparator<RankablePlayer> = compareBy { it.score }
    }
}
