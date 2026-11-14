package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolver
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverDefinition
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.event.RulePublicEventMarker
import com.doublemoon1119.mahjongcraft.flow.common.game.event.WinPublicMarkerContext
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
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiGameEventPublicMarkerIds
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiGameEventPublicMarkerResolver
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementDetector
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.detect
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiDeclarationComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 [RiichiGameAchievementResolver] 從日麻胡牌詳情與終局事實判定的成果。 */
class RiichiGameAchievementResolverTest {
    /** 役種包含立直時取得立直後和牌；一般役種不產生役滿成果。 */
    @Test
    fun `riichi in the yaku list credits a riichi win`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, nonYakumanFields(han = 2, RiichiYakuType.Riichi, RiichiYakuType.Tanyao)))

        assertEquals(setOf(RiichiAchievementIds.RIICHI_WIN), result.getValue(winner.id))
    }

    /** 立直中和出役滿時，役種列表沒有立直與一發，改依和牌前的立直狀態判定。 */
    @Test
    fun `riichi state credits riichi and ippatsu on a yakuman win`() {
        val riichiState = riichiDeclarationComponents(declarationTile = FakeIdentifiedTileFactory.create(Tile.Honor.East), isIppatsu = true)
        val game = riichiGame(firstPlayerComponents = riichiState)
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, yakumanFields(RiichiYakuType.Suuankou)))

        assertTrue(RiichiAchievementIds.RIICHI_WIN in result.getValue(winner.id))
        assertTrue(RiichiAchievementIds.IPPATSU in result.getValue(winner.id))
    }

    /** 役種包含一發時取得一發入魂。 */
    @Test
    fun `ippatsu in the yaku list credits ippatsu`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, nonYakumanFields(han = 2, RiichiYakuType.Riichi, RiichiYakuType.Ippatsu)))

        assertEquals(setOf(RiichiAchievementIds.RIICHI_WIN, RiichiAchievementIds.IPPATSU), result.getValue(winner.id))
    }

    /** 累計役滿剛好 13 番與 14 番以上分別取得不同成果，12 番沒有累計役滿成果。 */
    @Test
    fun `counted yakuman distinguishes exactly thirteen han from more`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val exact = resolve(game, win(winner.id, nonYakumanFields(han = 13, RiichiYakuType.Chinitsu)))
        val over = resolve(game, win(winner.id, nonYakumanFields(han = 15, RiichiYakuType.Chinitsu)))
        val below = resolve(game, win(winner.id, nonYakumanFields(han = 12, RiichiYakuType.Chinitsu)))

        assertEquals(setOf(RiichiAchievementIds.COUNTED_YAKUMAN_EXACT), exact.getValue(winner.id))
        assertEquals(setOf(RiichiAchievementIds.COUNTED_YAKUMAN_OVER), over.getValue(winner.id))
        assertTrue(below.isEmpty())
    }

    /** 自然役滿取得役滿與該役種，不會因為累計役滿的番數規則產生其他成果。 */
    @Test
    fun `natural yakuman credits yakuman and its own yaku`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, yakumanFields(RiichiYakuType.Suuankou)))

        assertEquals(
            expected = setOf(RiichiAchievementIds.YAKUMAN, RiichiAchievementIds.yakuman(RiichiYakuType.Suuankou)),
            actual = result.getValue(winner.id),
        )
    }

    /** 雙倍役滿只給實際成立的役種，例如四暗刻單騎不連帶給四暗刻。 */
    @Test
    fun `double yakuman credits only the yaku that was won`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, yakumanFields(RiichiYakuType.SuuankouTanki)))

        assertEquals(
            expected = setOf(
                RiichiAchievementIds.YAKUMAN,
                RiichiAchievementIds.yakuman(RiichiYakuType.SuuankouTanki),
                RiichiAchievementIds.DOUBLE_YAKUMAN,
            ),
            actual = result.getValue(winner.id),
        )
    }

    /** 同一次和牌兩個一般役滿取得複合役滿，但不算雙倍役滿。 */
    @Test
    fun `two yakuman in one win credit multiple yakuman but not double yakuman`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val result = resolve(game, win(winner.id, yakumanFields(RiichiYakuType.Daisangen, RiichiYakuType.Tsuuiisou)))

        assertEquals(
            expected = setOf(
                RiichiAchievementIds.YAKUMAN,
                RiichiAchievementIds.yakuman(RiichiYakuType.Daisangen),
                RiichiAchievementIds.yakuman(RiichiYakuType.Tsuuiisou),
                RiichiAchievementIds.MULTIPLE_YAKUMAN,
            ),
            actual = result.getValue(winner.id),
        )
    }

    /** 古役役滿沒有個別進度，但計入役滿、雙倍役滿與複合役滿進度。 */
    @Test
    fun `local yakuman count toward the general yakuman achievements`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val single = resolve(game, win(winner.id, yakumanFields(RiichiYakuType.Renhou)))
        val double = resolve(game, win(winner.id, yakumanFields(RiichiYakuType.Daichisei)))
        val combined = resolve(game, win(winner.id, yakumanFields(RiichiYakuType.Renhou, RiichiYakuType.Daisangen)))

        assertEquals(setOf(RiichiAchievementIds.YAKUMAN), single.getValue(winner.id))
        assertEquals(setOf(RiichiAchievementIds.YAKUMAN, RiichiAchievementIds.DOUBLE_YAKUMAN), double.getValue(winner.id))
        assertEquals(
            expected = setOf(RiichiAchievementIds.YAKUMAN, RiichiAchievementIds.yakuman(RiichiYakuType.Daisangen), RiichiAchievementIds.MULTIPLE_YAKUMAN),
            actual = combined.getValue(winner.id),
        )
    }

    /** 每一種標準役滿都會取得通用役滿成果及對應的個別成果。 */
    @Test
    fun `all standard yakuman types are retained`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()
        val yakumanTypes = listOf(
            RiichiYakuType.KokushiMusou,
            RiichiYakuType.ChurenPoto,
            RiichiYakuType.Tsuuiisou,
            RiichiYakuType.Ryuuuiisou,
            RiichiYakuType.Suuankou,
            RiichiYakuType.Sukantsu,
            RiichiYakuType.Shousuushi,
            RiichiYakuType.Daisangen,
            RiichiYakuType.Chinroutou,
            RiichiYakuType.Tenhou,
            RiichiYakuType.Chiihou,
            RiichiYakuType.KokushiMusou13,
            RiichiYakuType.ChurenPoto9,
            RiichiYakuType.SuuankouTanki,
            RiichiYakuType.Daisuushii,
        )

        yakumanTypes.forEach { yaku ->
            val result = resolve(game, win(winner.id, yakumanFields(yaku))).getValue(winner.id)
            assertEquals(
                expected = buildSet {
                    add(RiichiAchievementIds.YAKUMAN)
                    add(checkNotNull(RiichiAchievementIds.yakuman(yaku)))
                    if (yaku in RiichiAchievementIds.doubleYakumanTypes) add(RiichiAchievementIds.DOUBLE_YAKUMAN)
                },
                actual = result,
                message = "unexpected achievements for $yaku",
            )
        }
    }

    /** 已提交詳情不允許重複欄位，建立時就拒絕相同欄位 ID。 */
    @Test
    fun `duplicate yaku fields are rejected by the source contract`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()
        val fields = yakumanFields(RiichiYakuType.Daisangen) + yakumanFields(RiichiYakuType.Tsuuiisou)
            .filter { it.id == RiichiWinSettlementIds.YAKU_FIELD }

        assertFailsWith<IllegalArgumentException> { win(winner.id, fields) }
    }

    /** 流局滿貫的成立者取得流局滿貫。 */
    @Test
    fun `nagashi mangan achievers get the nagashi mangan achievement`() {
        val game = riichiGame()
        val achiever = game.tableState.players.first()

        val result = resolve(
            game = game,
            fact = HistoryFact.RuleEffectResolved(
                reasonId = RiichiRoundOutcomeIds.NAGASHI_MANGAN,
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
            expected = setOf(
                RiichiAchievementIds.MATCH_COMPLETED,
                RiichiAchievementIds.BUSTED,
                RiichiAchievementIds.BUSTED_FAR,
                RiichiAchievementIds.BUSTED_IN_EAST_ONE,
            ),
            actual = result.getValue(busted.id),
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

    /** 日麻規則的中途終止不會誤發完成對局或擊飛成果。 */
    @Test
    fun `aborted riichi match produces no achievements`() {
        val game = riichiGame()

        val result = resolve(game, HistoryFact.MatchAborted("mahjongcraft:table_missing"))

        assertTrue(result.isEmpty())
    }

    /** 流局滿貫只接受規則結果事件；詳情、受益者摘要和多個結果都能合併。 */
    @Test
    fun `nagashi mangan uses rule results details and beneficiaries`() {
        val game = riichiGame()
        val (first, second) = game.tableState.players
        val details = HistoryFact.RuleEffectResolved(
            reasonId = RiichiRoundOutcomeIds.NAGASHI_MANGAN,
            roundCompletion = null,
            winDetails = listOf(HistoryWinDetails(first.id, emptyList())),
        )
        val summary = HistoryFact.RuleEffectResolved(
            reasonId = RiichiRoundOutcomeIds.NAGASHI_MANGAN,
            roundCompletion = RoundCompletionSummary(
                outcomeId = RiichiRoundOutcomeIds.NAGASHI_MANGAN,
                classification = RoundCompletionClassification.EXTENSION,
                beneficiaryPlayerIds = setOf(second.id),
                responsiblePlayerIds = emptySet(),
                transitionDirective = RoundTransitionDirective.PassToNextSeat,
                settledScoresByPlayerId = game.tableState.players.associate { it.id to it.score },
            ),
            winDetails = emptyList(),
        )

        assertEquals(setOf(RiichiAchievementIds.NAGASHI_MANGAN), resolveFacts(game, details, summary).getValue(first.id))
        assertEquals(setOf(RiichiAchievementIds.NAGASHI_MANGAN), resolveFacts(game, details, summary).getValue(second.id))
    }

    /** 相同交易中的普通和牌結果與規則 WIN 結果不應被誤算成流局滿貫。 */
    @Test
    fun `nagashi achievement ignores ordinary win and other rule results`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val facts = listOf(
            win(winner.id, nonYakumanFields(han = 1, RiichiYakuType.Riichi)),
            HistoryFact.RuleEffectResolved(
                reasonId = "example:win",
                roundCompletion = RoundCompletionSummary(
                    outcomeId = "example:win",
                    classification = RoundCompletionClassification.WIN,
                    beneficiaryPlayerIds = setOf(winner.id),
                    responsiblePlayerIds = emptySet(),
                    transitionDirective = RoundTransitionDirective.PassToNextSeat,
                    settledScoresByPlayerId = game.tableState.players.associate { it.id to it.score },
                ),
                winDetails = listOf(HistoryWinDetails(winner.id, yakumanFields(RiichiYakuType.Daisangen))),
            ),
        )

        val result = resolveFacts(game, *facts.toTypedArray())
        assertEquals(mapOf(winner.id to setOf(RiichiAchievementIds.RIICHI_WIN)), result)
    }

    /** 自然役滿只有公開的立直與一發標記都存在時才取得一發，不能只看役種名稱。 */
    @Test
    fun `natural yakuman does not infer ippatsu from riichi yaku alone`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()

        val fields = listOf(
            element = WinSettlementDetailField(
                id = RiichiWinSettlementIds.YAKU_FIELD,
                value = WinSettlementDetailValue.Entries(
                    entries = listOf(RiichiWinSettlementIds.yakumanEntry(RiichiYakuType.Suuankou, 1), RiichiWinSettlementIds.yakuEntry(RiichiYakuType.Riichi, 1)),
                ),
            ),
        ) + yakumanFields(RiichiYakuType.Suuankou).filter { it.id != RiichiWinSettlementIds.YAKU_FIELD }
        val result = resolveFacts(
            game = game,
            win(winner.id, fields),
            markerResolver = object : GameEventPublicMarkerResolver by RiichiGameEventPublicMarkerResolver {
                override fun resolveWinMarkers(context: WinPublicMarkerContext): List<RulePublicEventMarker> = listOf(RulePublicEventMarker(RiichiGameEventPublicMarkerIds.IPPATSU, context.winnerId))
            },
        )

        assertEquals(
            expected = setOf(RiichiAchievementIds.YAKUMAN, checkNotNull(RiichiAchievementIds.yakuman(RiichiYakuType.Suuankou)), RiichiAchievementIds.RIICHI_WIN),
            actual = result.getValue(winner.id),
        )
    }

    /** 重複役種以最後一筆倍數為準，避免重複欄位造成錯誤的役滿倍數。 */
    @Test
    fun `duplicate yakuman entries use the last multiplier`() {
        val game = riichiGame()
        val winner = game.tableState.players.first()
        val entries = listOf(
            RiichiWinSettlementIds.yakumanEntry(RiichiYakuType.Daisangen, 1),
            RiichiWinSettlementIds.yakumanEntry(RiichiYakuType.Daisangen, 2),
        )
        val fields = listOf(
            WinSettlementDetailField(RiichiWinSettlementIds.YAKU_FIELD, WinSettlementDetailValue.Entries(entries)),
            WinSettlementDetailField(
                id = RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD,
                value = WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(RiichiWinSettlementIds.YAKUMAN, 1))),
            ),
        )

        val result = resolve(game, win(winner.id, fields)).getValue(winner.id)
        assertEquals(
            expected = setOf(
                RiichiAchievementIds.YAKUMAN,
                checkNotNull(RiichiAchievementIds.yakuman(RiichiYakuType.Daisangen)),
                RiichiAchievementIds.DOUBLE_YAKUMAN,
            ),
            actual = result,
        )
    }

    /** 擊飛門檻的邊界依嚴格小於判定；終局資料與目前桌況不一致時不憑空增加擊飛。 */
    @Test
    fun `bust threshold boundaries use final public markers`() {
        val game = riichiGame()
        val (player, second, third, fourth) = game.tableState.players
        fun result(score: Int) = resolve(
            game = game,
            fact = HistoryFact.MatchCompleted(
                reasonId = BuiltInMatchEndReasonIds.PLAYER_BUSTED,
                finalScoresByPlayerId = mapOf(player.id to score, second.id to 10_000, third.id to 20_000, fourth.id to 30_000),
            ),
        ).getValue(player.id)

        assertTrue(RiichiAchievementIds.BUSTED !in result(0))
        assertTrue(RiichiAchievementIds.BUSTED in result(-1))
        assertTrue(RiichiAchievementIds.BUSTED in result(-30_001))
    }

    /** 同一個規則結果的詳情優先於摘要名單，不能把摘要受益者一併算入。 */
    @Test
    fun `nagashi details take precedence over different summary beneficiaries`() {
        val game = riichiGame()
        val (winner, other) = game.tableState.players
        val summary = RoundCompletionSummary(
            outcomeId = RiichiRoundOutcomeIds.NAGASHI_MANGAN,
            classification = RoundCompletionClassification.WIN,
            beneficiaryPlayerIds = setOf(other.id),
            transitionDirective = RoundTransitionDirective.PassToNextSeat,
            settledScoresByPlayerId = game.tableState.players.associate { it.id to it.score },
        )
        val result = resolve(
            game = game,
            fact = HistoryFact.RuleEffectResolved(RiichiRoundOutcomeIds.NAGASHI_MANGAN, summary, listOf(HistoryWinDetails(winner.id, emptyList()))),
        )
        assertEquals(mapOf(winner.id to setOf(RiichiAchievementIds.NAGASHI_MANGAN)), result)
    }

    /** 完成名單不由全體玩家補齊；擊飛使用提交分數並保留大差距門檻的含等號判定。 */
    @Test
    fun `partial completion and far bust boundaries retain exact committed scores`() {
        val game = riichiGame()
        val player = game.tableState.players.first()
        assertTrue(resolve(game, HistoryFact.MatchCompleted(BuiltInMatchEndReasonIds.PLAYER_BUSTED, emptyMap())).isEmpty())
        listOf(-29_999, -30_000, -30_001).forEach { score ->
            val result = resolve(game, HistoryFact.MatchCompleted(BuiltInMatchEndReasonIds.PLAYER_BUSTED, mapOf(player.id to score)))
            assertEquals(setOf(player.id), result.keys)
            assertEquals(score <= -30_000, RiichiAchievementIds.BUSTED_FAR in result.getValue(player.id))
        }
    }

    /** 沒有擊飛設定時不授予擊飛；負數門檻仍只接受嚴格低於門檻的分數。 */
    @Test
    fun `disabled and negative bust thresholds preserve the old rule`() {
        listOf<Int?>(null, -100).forEach { threshold ->
            val game = gameOf(RiichiRuleConfig(scoreConfig = RiichiScoreConfig(bustThreshold = threshold)))
            val player = game.tableState.players.first()
            listOf(-101, -100).forEach { score ->
                val result = resolve(game, HistoryFact.MatchCompleted(BuiltInMatchEndReasonIds.PLAYER_BUSTED, mapOf(player.id to score)))
                assertEquals(threshold != null && score < threshold, RiichiAchievementIds.BUSTED in result.getValue(player.id))
            }
        }
    }

    /** 三人日麻對局與四人日麻共用同一批日麻成果。 */
    @Test
    fun `three player riichi games credit the same riichi achievements`() {
        val registry = GameAchievementResolverRegistryImpl().apply { BundledRiichiMinecraftExtension.registerGameAchievementResolvers(this) }
        val game = gameOf(ThreePlayerRiichiRuleConfig())
        val winner = game.tableState.players.first()

        val result = GameAchievementDetector(registry)
            .detect(facts(game, win(winner.id, nonYakumanFields(han = 2, RiichiYakuType.Riichi))))

        assertTrue(result.single { it.playerId == winner.id }.achievementIds.contains(RiichiAchievementIds.RIICHI_WIN))
    }

    /** 非日麻對局不會交給日麻判定。 */
    @Test
    fun `non-riichi games produce no riichi achievements`() {
        val registry = GameAchievementResolverRegistryImpl().apply { BundledRiichiMinecraftExtension.registerGameAchievementResolvers(this) }
        val game = gameOf(TaiwanRuleConfig())
        val winner = game.tableState.players.first()

        val result = GameAchievementDetector(registry)
            .detect(facts(game, win(winner.id, nonYakumanFields(han = 2, RiichiYakuType.Riichi))))

        assertTrue(result.flatMap { it.achievementIds }.none { it.startsWith("mahjongcraft:riichi/") })
    }

    /**
     * 以日麻 resolver 判定單一測試事實。
     *
     * @param game 要判定的對局。
     * @param fact 要加入歷史資料的事實。
     * @return 玩家 UUID 對應的成果 ID 集合。
     */
    private fun resolve(game: Game, fact: HistoryFact): Map<Uuid, Set<String>> = resolveFacts(game, fact)

    /** 以同一交易的多筆事實建立事件批次並交給日麻成果判定。 */
    private fun resolveFacts(game: Game, vararg fact: HistoryFact, markerResolver: GameEventPublicMarkerResolver = RiichiGameEventPublicMarkerResolver): Map<Uuid, Set<String>> {
        val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val markerResolvers = GameEventPublicMarkerResolverRegistry().apply {
            register(GameEventPublicMarkerResolverDefinition(BuiltInRuleModuleIds.RIICHI, markerResolver))
            register(GameEventPublicMarkerResolverDefinition(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, markerResolver))
        }
        val committedFacts = CommittedGameFacts(
            venueId = game.id,
            previousGame = game,
            game = game,
            facts = fact.map { HistoryEventDraft(null, it) },
        )
        val batch = GameEventProjector(modules, markerResolvers = markerResolvers).projectBatch(committedFacts) ?: return emptyMap()
        return RiichiGameAchievementResolver().resolve(batch)
    }

    /**
     * 建立可覆寫牌局進度與莊家狀態的四人日麻測試對局。
     *
     * @param firstPlayerComponents 東家規則狀態；未提供時使用預設狀態。
     * @param prevalentWind 場風。
     * @param roundNumber 場風內局數。
     * @param comboCount 本場數。
     * @return 對應設定的測試對局。
     */
    private fun riichiGame(
        firstPlayerComponents: ComponentSet? = null,
        prevalentWind: Wind = Wind.EAST,
        roundNumber: Int = 1,
        comboCount: Int = 0,
    ): Game = gameOf(
        config = RiichiRuleConfig(),
        firstPlayerComponents = firstPlayerComponents,
        prevalentWind = prevalentWind,
        roundNumber = roundNumber,
        comboCount = comboCount,
    )

    /**
     * 以指定規則設定與牌局進度建立四人測試對局。
     *
     * @param config 對局規則設定。
     * @param firstPlayerComponents 東家規則狀態；其他玩家使用測試工廠預設值。
     * @param prevalentWind 場風。
     * @param roundNumber 場風內局數。
     * @param comboCount 本場數。
     * @return 建立完成的對局。
     */
    private fun gameOf(
        config: MahjongRuleConfig,
        firstPlayerComponents: ComponentSet? = null,
        prevalentWind: Wind = Wind.EAST,
        roundNumber: Int = 1,
        comboCount: Int = 0,
    ): Game {
        val players: List<MahjongPlayer> = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { wind ->
            FakeMahjongPlayerFactory.create(initialSeat = wind, components = firstPlayerComponents?.takeIf { wind == Wind.EAST } ?: ComponentSet.EMPTY)
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

    /**
     * 將單一歷史事實包裝為 resolver 輸入。
     *
     * @param game 事實所屬的對局。
     * @param fact 要包裝的歷史事實。
     * @return 對應的已提交對局事實。
     */
    private fun facts(game: Game, fact: HistoryFact) = CommittedGameFacts(
        venueId = game.id,
        previousGame = game,
        game = game,
        facts = listOf(HistoryEventDraft(null, fact)),
    )

    /**
     * 建立自摸結算事實。
     *
     * @param winnerId 和牌玩家 UUID。
     * @param fields 和牌結算欄位。
     * @return 對應的和牌歷史事實。
     */
    private fun win(winnerId: Uuid, fields: List<WinSettlementDetailField>) = HistoryFact.WinSettled(
        outcomeId = BuiltInRoundOutcomeIds.TSUMO,
        winDetails = listOf(HistoryWinDetails(winnerId, fields)),
    )

    /**
     * 建立一般役種與番符結算欄位。
     *
     * @param han 總番數。
     * @param yaku 和牌成立的一般役種。
     * @return 役種與番符欄位。
     */
    private fun nonYakumanFields(han: Int, vararg yaku: RiichiYakuType) = listOf(
        yakuField(yaku.toList()),
        WinSettlementDetailField(
            id = RiichiWinSettlementIds.HAN_FU_FIELD,
            value = WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(RiichiWinSettlementIds.HAN, han), WinSettlementQuantity(RiichiWinSettlementIds.FU, 30))),
        ),
    )

    /**
     * 建立役滿役種與役滿總數結算欄位。
     *
     * @param yaku 和牌成立的役滿役種。
     * @return 役種與役滿數量欄位。
     */
    private fun yakumanFields(vararg yaku: RiichiYakuType) = listOf(
        WinSettlementDetailField(
            id = RiichiWinSettlementIds.YAKU_FIELD,
            value = WinSettlementDetailValue.Entries(yaku.map { RiichiWinSettlementIds.yakumanEntry(it, if (it in doubleYakuman) 2 else 1) }),
        ),
        WinSettlementDetailField(
            id = RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD,
            value = WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(RiichiWinSettlementIds.YAKUMAN, 1))),
        ),
    )

    /**
     * 將一般役種轉為和牌役種結算欄位。
     *
     * @param yaku 成立的一般役種。
     * @return 對應的役種欄位。
     */
    private fun yakuField(yaku: List<RiichiYakuType>) = WinSettlementDetailField(
        id = RiichiWinSettlementIds.YAKU_FIELD,
        value = WinSettlementDetailValue.Entries(yaku.map { RiichiWinSettlementIds.yakuEntry(it, 1) }),
    )

    /** 測試用的雙倍役滿役種。 */
    private val doubleYakuman = setOf(RiichiYakuType.KokushiMusou13, RiichiYakuType.ChurenPoto9, RiichiYakuType.SuuankouTanki, RiichiYakuType.Daisuushii, RiichiYakuType.Daichisei)
}
