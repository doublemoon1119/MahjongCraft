package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
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
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementDetector
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 [RiichiGameAchievementResolver] 從日麻胡牌詳情與終局事實判定的成果。 */
class RiichiGameAchievementResolverTest {
    /** 役種包含立直時取得立直後和牌；一般役種不產生役滿成果。 */
    @Test
    fun `riichi in the yaku list credits a riichi win`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, nonYakumanFields(han = 2, YakuType.Riichi, YakuType.Tanyao)))

        assertEquals(setOf(RiichiAchievementIds.RIICHI_WIN), result.getValue(winner.id))
    }

    /** 立直中和出役滿時，役種列表沒有立直與一發，改依和牌前的立直狀態判定。 */
    @Test
    fun `riichi state credits riichi and ippatsu on a yakuman win`() {
        val riichiState = RiichiPlayerState(riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East), isIppatsu = true)
        val game = riichiGame(firstPlayerRuleState = riichiState)
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, yakumanFields(YakuType.Suuankou)))

        assertTrue(RiichiAchievementIds.RIICHI_WIN in result.getValue(winner.id))
        assertTrue(RiichiAchievementIds.IPPATSU in result.getValue(winner.id))
    }

    /** 役種包含一發時取得一發入魂。 */
    @Test
    fun `ippatsu in the yaku list credits ippatsu`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, nonYakumanFields(han = 2, YakuType.Riichi, YakuType.Ippatsu)))

        assertEquals(setOf(RiichiAchievementIds.RIICHI_WIN, RiichiAchievementIds.IPPATSU), result.getValue(winner.id))
    }

    /** 累計役滿剛好 13 番與 14 番以上分別取得不同成果，12 番沒有累計役滿成果。 */
    @Test
    fun `counted yakuman distinguishes exactly thirteen han from more`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val exact = resolve(game, win(winner.id, nonYakumanFields(han = 13, YakuType.Chinitsu)))
        val over = resolve(game, win(winner.id, nonYakumanFields(han = 15, YakuType.Chinitsu)))
        val below = resolve(game, win(winner.id, nonYakumanFields(han = 12, YakuType.Chinitsu)))

        assertEquals(setOf(RiichiAchievementIds.COUNTED_YAKUMAN_EXACT), exact.getValue(winner.id))
        assertEquals(setOf(RiichiAchievementIds.COUNTED_YAKUMAN_OVER), over.getValue(winner.id))
        assertTrue(below.isEmpty())
    }

    /** 自然役滿取得役滿與該役種，不會因為累計役滿的番數規則產生其他成果。 */
    @Test
    fun `natural yakuman credits yakuman and its own yaku`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, yakumanFields(YakuType.Suuankou)))

        assertEquals(
            setOf(RiichiAchievementIds.YAKUMAN, RiichiAchievementIds.yakuman(YakuType.Suuankou)),
            result.getValue(winner.id),
        )
    }

    /** 雙倍役滿只給實際成立的役種，例如四暗刻單騎不連帶給四暗刻。 */
    @Test
    fun `double yakuman credits only the yaku that was won`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, yakumanFields(YakuType.SuuankouTanki)))

        assertEquals(
            setOf(
                RiichiAchievementIds.YAKUMAN,
                RiichiAchievementIds.yakuman(YakuType.SuuankouTanki),
                RiichiAchievementIds.DOUBLE_YAKUMAN,
            ),
            result.getValue(winner.id),
        )
    }

    /** 同一次和牌兩個一般役滿取得複合役滿，但不算雙倍役滿。 */
    @Test
    fun `two yakuman in one win credit multiple yakuman but not double yakuman`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, yakumanFields(YakuType.Daisangen, YakuType.Tsuuiisou)))

        assertEquals(
            setOf(
                RiichiAchievementIds.YAKUMAN,
                RiichiAchievementIds.yakuman(YakuType.Daisangen),
                RiichiAchievementIds.yakuman(YakuType.Tsuuiisou),
                RiichiAchievementIds.MULTIPLE_YAKUMAN,
            ),
            result.getValue(winner.id),
        )
    }

    /** 流局滿貫的成立者取得流局滿貫。 */
    @Test
    fun `nagashi mangan achievers get the nagashi mangan achievement`() {
        val game = riichiGame()
        val achiever = game.tableState.players.first()

        val result = resolve(
            game,
            HistoryFact.RuleEffectResolved(
                reasonId = BuiltInRoundOutcomeIds.NAGASHI_MANGAN,
                roundCompletion = null,
                winDetails = listOf(HistoryWinDetails(achiever.id, emptyList())),
            ),
        )

        assertEquals(mapOf(achiever.id to setOf(RiichiAchievementIds.NAGASHI_MANGAN)), result)
    }

    /** 東一局被擊飛：點數低於門檻的玩家取得擊飛與東一局擊飛，−30000 以下另外取得大差距擊飛；其他人只完成對局。 */
    @Test
    fun `bust in east one credits the busted player`() {
        val game = riichiGame(prevalentWind = Wind.EAST, roundNumber = 1, comboCount = 1)
        val (busted, second, third, fourth) = game.tableState.players
        val scores = mapOf(busted.id to -35_000, second.id to 10_000, third.id to 50_000, fourth.id to 75_000)

        val result = resolve(game, HistoryFact.MatchCompleted(BuiltInMatchEndReasonIds.PLAYER_BUSTED, scores))

        assertEquals(
            setOf(
                RiichiAchievementIds.MATCH_COMPLETED,
                RiichiAchievementIds.BUSTED,
                RiichiAchievementIds.BUSTED_FAR,
                RiichiAchievementIds.BUSTED_IN_EAST_ONE,
            ),
            result.getValue(busted.id),
        )
        assertEquals(setOf(RiichiAchievementIds.MATCH_COMPLETED), result.getValue(second.id))
    }

    /** 東一局以外、差距不大的擊飛只取得擊飛。 */
    @Test
    fun `small bust outside east one credits only the bust`() {
        val game = riichiGame(prevalentWind = Wind.SOUTH, roundNumber = 2)
        val (busted, second, third, fourth) = game.tableState.players
        val scores = mapOf(busted.id to -5_000, second.id to 30_000, third.id to 35_000, fourth.id to 40_000)

        val result = resolve(game, HistoryFact.MatchCompleted(BuiltInMatchEndReasonIds.PLAYER_BUSTED, scores))

        assertEquals(setOf(RiichiAchievementIds.MATCH_COMPLETED, RiichiAchievementIds.BUSTED), result.getValue(busted.id))
    }

    /** 非日麻對局不會交給日麻判定。 */
    @Test
    fun `non-riichi games produce no riichi achievements`() {
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val registry = GameAchievementResolverRegistryImpl().apply { registerBuiltInRiichiAchievements() }
        val game = gameOf(TaiwanRuleConfig())
        val winner = game.tableState.players.first()

        val result = GameAchievementDetector(moduleRegistry, registry)
            .detect(facts(game, win(winner.id, nonYakumanFields(han = 2, YakuType.Riichi))))

        assertTrue(result.flatMap { it.achievementIds }.none { it.startsWith("mahjongcraft:riichi/") })
    }

    private fun resolve(game: Game, fact: HistoryFact): Map<Uuid, Set<String>> = RiichiGameAchievementResolver.resolve(facts(game, fact))

    private fun riichiGame(
        firstPlayerRuleState: RiichiPlayerState? = null,
        prevalentWind: Wind = Wind.EAST,
        roundNumber: Int = 1,
        comboCount: Int = 0,
    ): Game = gameOf(
        config = RiichiRuleConfig(),
        firstPlayerRuleState = firstPlayerRuleState,
        prevalentWind = prevalentWind,
        roundNumber = roundNumber,
        comboCount = comboCount,
    )

    private fun gameOf(
        config: MahjongRuleConfig,
        firstPlayerRuleState: RiichiPlayerState? = null,
        prevalentWind: Wind = Wind.EAST,
        roundNumber: Int = 1,
        comboCount: Int = 0,
    ): Game {
        val players: List<MahjongPlayer> = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { wind ->
            FakeMahjongPlayerFactory.create(initialSeat = wind, playerRuleState = firstPlayerRuleState.takeIf { wind == Wind.EAST })
        }
        val tableState = FakeTableStateFactory.create(
            players = players,
            config = config,
            prevalentWind = prevalentWind,
            roundNumber = roundNumber,
            comboCount = comboCount,
        )
        return Game(tableState, GameFlowConfig())
    }

    private fun facts(game: Game, fact: HistoryFact) = CommittedGameFacts(
        venueId = game.id,
        previousGame = game,
        game = game,
        facts = listOf(HistoryEventDraft(null, fact)),
    )

    private fun win(winnerId: Uuid, fields: List<WinSettlementDetailField>) = HistoryFact.WinSettled(
        outcomeId = BuiltInRoundOutcomeIds.TSUMO,
        winDetails = listOf(HistoryWinDetails(winnerId, fields)),
    )

    private fun nonYakumanFields(han: Int, vararg yaku: YakuType) = listOf(
        yakuField(yaku.toList()),
        WinSettlementDetailField(
            RiichiWinSettlementIds.HAN_FU_FIELD,
            WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(RiichiWinSettlementIds.HAN, han), WinSettlementQuantity(RiichiWinSettlementIds.FU, 30))),
        ),
    )

    private fun yakumanFields(vararg yaku: YakuType) = listOf(
        yakuField(yaku.toList()),
        WinSettlementDetailField(
            RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD,
            WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(RiichiWinSettlementIds.YAKUMAN, 1))),
        ),
    )

    private fun yakuField(yaku: List<YakuType>) = WinSettlementDetailField(
        RiichiWinSettlementIds.YAKU_FIELD,
        WinSettlementDetailValue.Entries(yaku.map { RiichiWinSettlementIds.yakuEntry(it, 1) }),
    )
}
