package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
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
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證日麻成果改用事件判定後，結果仍與原本的事實判定相同。 */
class RetainedRuleAchievementEquivalenceTest {
    private val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
    private val resolvers = GameAchievementResolverRegistryImpl().apply {
        BundledRiichiMinecraftExtension.registerGameAchievementResolvers(this)
    }
    private val detector = GameAchievementDetector(modules, resolvers)

    /** 四人與三人日麻都應保留原本的成果與統計增量。 */
    @Test
    fun `retained riichi achievements remain equivalent for four and three players`() {
        val fourPlayerGame = game(RiichiRuleConfig())
        val fourPlayerWinner = fourPlayerGame.tableState.players.first()
        val threePlayerGame = game(ThreePlayerRiichiRuleConfig())
        val threePlayerWinner = threePlayerGame.tableState.players.first()
        val cases = listOf(
            facts(
                fourPlayerGame,
                HistoryFact.WinSettled(
                    outcomeId = BuiltInRoundOutcomeIds.TSUMO,
                    winDetails = listOf(HistoryWinDetails(fourPlayerWinner.id, nonYakumanFields(YakuType.Riichi))),
                ),
            ),
            facts(
                threePlayerGame,
                HistoryFact.WinSettled(
                    outcomeId = BuiltInRoundOutcomeIds.TSUMO,
                    winDetails = listOf(HistoryWinDetails(threePlayerWinner.id, yakumanFields(YakuType.Suuankou))),
                ),
            ),
            facts(
                fourPlayerGame,
                HistoryFact.MatchCompleted(
                    reasonId = "mahjongcraft:completed",
                    finalScoresByPlayerId = fourPlayerGame.tableState.players.associateWith { it.score }.mapKeys { it.key.id },
                ),
            ),
        )

        cases.forEachIndexed { index, facts -> assertEquivalent(index, facts) }
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

        assertEquivalent(0, facts)
    }

    /** 比較真人玩家成果的順序，以及七種通用統計是否一致。 */
    private fun assertEquivalent(caseIndex: Int, facts: CommittedGameFacts) {
        val module = modules.getModule((facts.game ?: checkNotNull(facts.previousGame)).tableState.config)
        val batch = checkNotNull(GameEventProjector(modules).projectBatch(facts))
        val oldMap = merge(LegacyGenericAchievementDetector.detect(facts, module), resolvers.resolve(module.id, facts))
        val old = ordered(batch, oldMap)
        val actual = detector.detect(batch, detector.resolveRule(facts))

        assertEquals(old, actual, "achievement result case $caseIndex")
        batch.players.filterNot { it.isAi }.forEach { player ->
            val oldStats = AchievementStatisticIds.statisticsFor(oldMap[player.playerId].orEmpty())
            val newStats = AchievementStatisticIds.statisticsFor(actual.firstOrNull { it.playerId == player.playerId }?.achievementIds.orEmpty())
            assertEquals(oldStats, newStats, "statistics case $caseIndex for ${player.playerId}")
            AchievementStatisticIds.ALL.forEach { statisticId ->
                assertEquals(statisticId in oldStats, statisticId in newStats, "statistic $statisticId case $caseIndex")
            }
        }
    }

    /** 合併搬移前的通用成果與規則專屬成果，不改變成果集合的語意。 */
    private fun merge(
        generic: Map<Uuid, Set<String>>,
        rule: Map<Uuid, Set<String>>,
    ): Map<Uuid, Set<String>> = (generic.keys + rule.keys).associateWith { playerId ->
        generic[playerId].orEmpty() + rule[playerId].orEmpty()
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
