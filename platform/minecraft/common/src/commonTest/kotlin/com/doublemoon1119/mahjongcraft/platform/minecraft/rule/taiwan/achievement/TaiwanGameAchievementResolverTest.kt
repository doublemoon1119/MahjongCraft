package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.achievement

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
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 [TaiwanGameAchievementResolver] 從台灣麻將胡牌台種、花牌結果與終局事實判定的成果。 */
class TaiwanGameAchievementResolverTest {
    /** 有專屬成果的台種各自取得成果。沒有專屬成果的台種不產生成果。 */
    @Test
    fun `tai with their own achievements credit the winner`() {
        val game = taiwanGame()
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, TaiwanTaiType.AllExposed, TaiwanTaiType.FullFlush, TaiwanTaiType.Dragon))

        assertEquals(mapOf(winner.id to setOf(TaiwanAchievementIds.ALL_EXPOSED, TaiwanAchievementIds.FULL_FLUSH)), result)
    }

    /** 每一種有專屬成果的台種都對應到自己的成果。 */
    @Test
    fun `every tai with an achievement maps to it`() {
        val game = taiwanGame()
        val winner = game.tableState.players.first()
        val expected = mapOf(
            TaiwanTaiType.AllExposed to TaiwanAchievementIds.ALL_EXPOSED,
            TaiwanTaiType.FullFlush to TaiwanAchievementIds.FULL_FLUSH,
            TaiwanTaiType.FiveConcealedTriplets to TaiwanAchievementIds.FIVE_CONCEALED_TRIPLETS,
            TaiwanTaiType.BigDragons to TaiwanAchievementIds.BIG_DRAGONS,
            TaiwanTaiType.BigWinds to TaiwanAchievementIds.BIG_WINDS,
            TaiwanTaiType.AllHonors to TaiwanAchievementIds.ALL_HONORS,
            TaiwanTaiType.HeavenlyWin to TaiwanAchievementIds.HEAVENLY_WIN,
            TaiwanTaiType.EarthlyWin to TaiwanAchievementIds.EARTHLY_WIN,
            TaiwanTaiType.HumanWin to TaiwanAchievementIds.HUMAN_WIN,
            TaiwanTaiType.EightFlowers to TaiwanAchievementIds.EIGHT_FLOWERS,
            TaiwanTaiType.SevenFlowers to TaiwanAchievementIds.SEVEN_FLOWERS,
        )

        expected.forEach { (type, achievement) ->
            assertEquals(mapOf(winner.id to setOf(achievement)), resolve(game, win(winner.id, type)), "unexpected achievements for $type")
        }
    }

    /** 花胡與其他胡牌一樣由胡牌結算的台種授予：八仙過海與七搶一各自取得成果。 */
    @Test
    fun `flower wins credit their winners`() {
        val game = taiwanGame()
        val (first, second) = game.tableState.players

        val eightFlowers = win(first.id, TaiwanTaiType.EightFlowers, outcomeId = TaiwanRoundOutcomeIds.EIGHT_FLOWERS)
        val sevenFlowers = win(second.id, TaiwanTaiType.SevenFlowers, outcomeId = TaiwanRoundOutcomeIds.SEVEN_FLOWERS)

        assertEquals(mapOf(first.id to setOf(TaiwanAchievementIds.EIGHT_FLOWERS)), resolve(game, eightFlowers))
        assertEquals(mapOf(second.id to setOf(TaiwanAchievementIds.SEVEN_FLOWERS)), resolve(game, sevenFlowers))
    }

    /** 其他規則結果不會被誤算成花牌成果。 */
    @Test
    fun `other rule results produce no flower achievements`() {
        val game = taiwanGame()
        val player = game.tableState.players.first()

        val result = resolve(
            game = game,
            fact = HistoryFact.RuleEffectResolved(
                reasonId = "example:win",
                roundCompletion = null,
                winDetails = listOf(HistoryWinDetails(player.id, emptyList())),
            ),
        )

        assertTrue(result.isEmpty())
    }

    /** 正常打完的對局讓每位完成的玩家解鎖台灣麻將頁籤。中途中止不授予。 */
    @Test
    fun `completed matches unlock the tab and aborted matches do not`() {
        val game = taiwanGame()
        val scores = game.tableState.players.associate { it.id to it.score }

        val completed = resolve(game, HistoryFact.MatchCompleted(BuiltInMatchEndReasonIds.SCHEDULE_COMPLETED, scores))
        val aborted = resolve(game, HistoryFact.MatchAborted("mahjongcraft:table_missing"))

        assertEquals(game.tableState.players.associate { it.id to setOf(TaiwanAchievementIds.MATCH_COMPLETED) }, completed)
        assertTrue(aborted.isEmpty())
    }

    /**
     * 以台灣麻將 resolver 判定單一測試事實。
     *
     * @param game 要判定的對局。
     * @param fact 要加入歷史資料的事實。
     * @return 玩家 UUID 對應的成果 ID 集合。
     */
    private fun resolve(game: Game, fact: HistoryFact): Map<Uuid, Set<String>> {
        val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val committedFacts = CommittedGameFacts(
            venueId = game.id,
            previousGame = game,
            game = game,
            facts = listOf(HistoryEventDraft(null, fact)),
        )
        val batch = GameEventProjector(modules).projectBatch(committedFacts) ?: return emptyMap()
        return TaiwanGameAchievementResolver().resolve(batch)
    }

    /**
     * 建立四人台灣麻將測試對局。
     *
     * @return 測試對局。
     */
    private fun taiwanGame(): Game {
        val players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { wind -> FakeMahjongPlayerFactory.create(initialSeat = wind) }
        return Game(FakeTableStateFactory.create(players = players, config = TaiwanRuleConfig()), GameFlowConfig())
    }

    /**
     * 建立胡牌結算事實，台數欄位列出指定台種。
     *
     * @param winnerId 胡牌玩家 UUID。
     * @param types 成立的台種。
     * @param outcomeId 胡牌原因，預設為自摸。
     * @return 對應的胡牌歷史事實。
     */
    private fun win(winnerId: Uuid, vararg types: TaiwanTaiType, outcomeId: String = BuiltInRoundOutcomeIds.TSUMO) = HistoryFact.WinSettled(
        outcomeId = outcomeId,
        winDetails = listOf(
            element = HistoryWinDetails(
                playerId = winnerId,
                detailFields = listOf(
                    element = WinSettlementDetailField(
                        id = TaiwanWinSettlementIds.TAI_FIELD,
                        value = WinSettlementDetailValue.Entries(types.map { TaiwanWinSettlementIds.taiEntry(it, 1) }),
                    ),
                ),
            ),
        ),
    )
}
