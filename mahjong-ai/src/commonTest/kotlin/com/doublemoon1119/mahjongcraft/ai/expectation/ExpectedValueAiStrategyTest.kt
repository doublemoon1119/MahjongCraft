package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.context
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.hand
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.m
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.p
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.player
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.s
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.strategy
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.table
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 以日麻固定手牌驗證三個資訊等級的期望值決策。 */
class ExpectedValueAiStrategyTest {
    /** 三個內建等級。 */
    private val allLevels = listOf(InformationLevel.BEGINNER, InformationLevel.INTERMEDIATE, InformationLevel.ADVANCED)

    /** 初級與中級以基本深度讀牌；高級以進階深度讀牌，並且不計後續風險。 */
    @Test
    fun `only the advanced level reads at the advanced depth`() {
        assertEquals(ReadingDepth.BASIC, InformationLevel.BEGINNER.readingDepth)
        assertEquals(ReadingDepth.BASIC, InformationLevel.INTERMEDIATE.readingDepth)
        assertEquals(ReadingDepth.ADVANCED, InformationLevel.ADVANCED.readingDepth)
        assertEquals(false, InformationLevel.ADVANCED.considersFutureRisk)
    }

    /** 策略以自己的讀牌深度向 registry 建立對手模型。 */
    @Test
    fun `the strategy builds opponent models at its own reading depth`() = runTest {
        val requested = mutableListOf<ReadingDepth>()
        val registry = OpponentModelRegistry().apply {
            register(ExpectationFixtures.module.id) { _, depth -> NeutralOpponentModel(depth).also { requested += depth } }
        }
        val self = player(Wind.SOUTH, hand = hand(listOf(m(1), m(2), m(3), p(4), p(5), p(6), s(7), s(8), m(6), m(7), s(9), s(9), Tile.Honor.East), drawn = Tile.Honor.North))
        val table = fourPlayers(self)

        allLevels.forEach { level ->
            ExpectedValueAiStrategy(
                level = level,
                moduleRegistry = ExpectationFixtures.moduleRegistry,
                extensionActionRegistry = ExpectationFixtures.extensionRegistry,
                opponentModels = registry,
            ).decideGameCommand(context(table, self))
        }

        assertEquals(allLevels.map { it.readingDepth }, requested)
    }

    /** 一向聽時打出孤張字牌，不拆搭子。 */
    @Test
    fun `every level discards an isolated honor rather than breaking a shape`() = runTest {
        val self = player(
            Wind.SOUTH,
            hand = hand(
                listOf(m(1), m(2), m(3), p(4), p(5), p(6), s(7), s(8), m(6), m(7), s(9), s(9), Tile.Honor.East),
                drawn = Tile.Honor.North,
            ),
        )
        val table = fourPlayers(self)

        allLevels.forEach { level ->
            val tile = discardedTile(table, self, strategy(level).decideGameCommand(context(table, self)))
            assertTrue(tile == Tile.Honor.East || tile == Tile.Honor.North, "$level discarded $tile")
        }
    }

    /** 同樣聽牌時選擇聽牌張數多的兩面，而不是坎張。 */
    @Test
    fun `the intermediate level keeps the wider wait`() = runTest {
        val self = player(
            Wind.SOUTH,
            hand = hand(
                listOf(m(1), m(2), m(3), m(4), m(5), m(6), m(7), m(8), m(9), p(1), p(1), s(3), s(4)),
                drawn = s(6),
            ),
        )
        val table = fourPlayers(self)

        val command = strategy(InformationLevel.INTERMEDIATE).decideGameCommand(context(table, self))

        assertEquals(GameCommand.Discard(checkNotNull(self.hand.lastDrawn).id), command)
    }

    /** 莊家立直、自己兩向聽時，中級與高級改打現物；初級不防守，照樣打出孤張。 */
    @Test
    fun `against a riichi only the intermediate and advanced levels fold with a distant hand`() = runTest {
        val dealer = player(Wind.EAST, discards = listOf(Tile.Honor.North, Tile.Honor.White, p(7)), riichi = true)
        val self = player(
            Wind.SOUTH,
            hand = hand(
                listOf(m(1), m(2), m(3), s(7), s(8), s(9), p(4), p(6), p(7), p(7), m(5), s(5), m(8)),
                drawn = s(2),
            ),
        )
        val table = table(listOf(dealer, self, player(Wind.WEST), player(Wind.NORTH)), liveTiles = 24)

        listOf(InformationLevel.INTERMEDIATE, InformationLevel.ADVANCED).forEach { level ->
            assertEquals(p(7), discardedTile(table, self, strategy(level).decideGameCommand(context(table, self))), "$level")
        }
        assertNotEquals(p(7), discardedTile(table, self, strategy(InformationLevel.BEGINNER).decideGameCommand(context(table, self))))
    }

    /** 碰三元牌後聽牌且有役，選擇碰。 */
    @Test
    fun `a pon that gives a yaku and tenpai is taken`() = runTest {
        val (table, self, pon) = ponSituation(ponTile = Tile.Honor.Red, others = listOf(Tile.Honor.Red, Tile.Honor.Red, m(9)))

        val command = strategy(InformationLevel.INTERMEDIATE).decideGameCommand(
            context(table, self, phase = AiDecisionPhase.RespondingToDiscard, legalActions = listOf(pon, GameAction.Pass)),
        )

        assertEquals(GameCommand.RespondToDiscard(pon), command)
    }

    /** 碰牌後沒有任何役可以和牌時，所有等級都不碰。 */
    @Test
    fun `a pon that leaves no yaku is declined`() = runTest {
        val (table, self, pon) = ponSituation(ponTile = s(1), others = listOf(Tile.Honor.West, Tile.Honor.North, m(9)))

        allLevels.forEach { level ->
            val command = strategy(level).decideGameCommand(
                context(table, self, phase = AiDecisionPhase.RespondingToDiscard, legalActions = listOf(pon, GameAction.Pass)),
            )
            assertEquals(GameCommand.RespondToDiscard(GameAction.Pass), command, "$level")
        }
    }

    /** 能自摸或榮和時一律和牌。 */
    @Test
    fun `a legal win is always taken`() = runTest {
        val self = player(
            Wind.SOUTH,
            hand = hand(listOf(m(1), m(2), m(3), m(4), m(5), m(6), m(7), m(8), m(9), p(1), p(1), s(3), s(4)), drawn = s(5)),
        )
        val table = fourPlayers(self)
        val ron = GameAction.Ron(checkNotNull(self.hand.lastDrawn).id)

        allLevels.forEach { level ->
            assertEquals(
                GameCommand.Tsumo,
                strategy(level).decideGameCommand(context(table, self, legalActions = listOf(GameAction.Tsumo))),
            )
            assertEquals(
                GameCommand.RespondToDiscard(ron),
                strategy(level).decideGameCommand(
                    context(table, self, phase = AiDecisionPhase.RespondingToDiscard, legalActions = listOf(GameAction.Pass, ron)),
                ),
            )
            assertEquals(
                GameCommand.RespondToKan(ron),
                strategy(level).decideGameCommand(
                    context(table, self, phase = AiDecisionPhase.RespondingToKan, legalActions = listOf(GameAction.Pass, ron)),
                ),
            )
        }
    }

    /** 沒有役的門前聽牌立直後才能榮和：打出同一張牌時，中級與高級評估立直優於默聽。 */
    @Test
    fun `riichi scores above dama for a closed tenpai without yaku`() {
        val self = player(
            Wind.SOUTH,
            hand = hand(
                listOf(m(2), m(3), m(4), p(4), p(5), p(6), p(7), p(8), p(9), s(2), s(3), s(4), s(5)),
                drawn = Tile.Honor.North,
            ),
        )
        val table = fourPlayers(self)
        val context = context(table, self, legalActions = listOf(RIICHI_GAME_ACTION))
        val north = checkNotNull(self.hand.lastDrawn)
        val dama = DecisionCandidate.Discard(tile = north, declaration = null, command = GameCommand.Discard(north.id))
        val riichi = DecisionCandidate.Discard(
            tile = north,
            declaration = RIICHI_GAME_ACTION,
            command = GameCommand.Extension(RiichiGameCommand(north.id)),
        )

        listOf(InformationLevel.INTERMEDIATE, InformationLevel.ADVANCED).forEach { level ->
            val (damaScore, riichiScore) = ExpectedValueEvaluator(level, ExpectationParameters.DEFAULT, ExpectationFixtures.module, ExpectationFixtures.opponentModel(level), context).scoreAll(listOf(dama, riichi))
            assertTrue(riichiScore.expectedValue > damaScore.expectedValue, "$level riichi $riichiScore dama $damaScore")
            assertTrue(riichiScore.winProbability > damaScore.winProbability)
        }
    }

    /** 莊家已立直、自己的單騎只剩一張時，計入後續風險的等級考慮立直後不能換牌的風險而不立直。 */
    @Test
    fun `future risk keeps a thin wait from riichi into a dealer riichi`() = runTest {
        val dealer = player(Wind.EAST, discards = listOf(Tile.Honor.North, Tile.Honor.West, s(1)), riichi = true)
        val self = player(
            Wind.SOUTH,
            hand = hand(
                listOf(m(2), m(3), m(4), p(4), p(5), p(6), p(6), p(7), p(8), s(2), s(3), s(4), s(5)),
                drawn = m(8),
            ),
        )
        val west = player(Wind.WEST, discards = listOf(s(5), s(5)))
        val table = table(listOf(dealer, self, west, player(Wind.NORTH)))

        val command = strategy(WITH_FUTURE_RISK).decideGameCommand(context(table, self, legalActions = listOf(RIICHI_GAME_ACTION)))

        assertIs<GameCommand.Discard>(command)
    }

    /** 沒有高威脅的對手時，後續風險只計高威脅對手就等於不計，同一手牌的期望值較高。 */
    @Test
    fun `future risk against high threats only ignores quiet opponents`() {
        val quietDiscards = listOf(Tile.Honor.North, Tile.Honor.West, s(1), p(9), m(9), Tile.Honor.White)
        val self = player(
            Wind.SOUTH,
            hand = hand(
                listOf(m(1), m(2), m(3), m(4), m(5), m(6), m(7), m(8), m(9), p(1), p(1), s(3), s(4)),
                drawn = s(6),
            ),
        )
        val table = table(
            listOf(
                player(Wind.EAST, discards = quietDiscards),
                self,
                player(Wind.WEST, discards = quietDiscards),
                player(Wind.NORTH, discards = quietDiscards),
            ),
        )
        val context = context(table, self)
        val six = checkNotNull(self.hand.lastDrawn)
        val discard = DecisionCandidate.Discard(tile = six, declaration = null, command = GameCommand.Discard(six.id))
        fun score(parameters: ExpectationParameters): Double = ExpectedValueEvaluator(WITH_FUTURE_RISK, parameters, ExpectationFixtures.module, ExpectationFixtures.opponentModel(WITH_FUTURE_RISK), context).scoreAll(listOf(discard)).single().expectedValue

        assertTrue(score(ExpectationParameters(futureRiskHighThreatOnly = true)) > score(ExpectationParameters.DEFAULT))
    }

    /**
     * 莊家已立直時：立直的後續風險折扣越低，立直的期望值越高；假設之後打出最安全的牌時，默聽的後續風險比全部平均小。
     */
    @Test
    fun `future risk parameters change how risky continuing looks`() {
        val dealer = player(Wind.EAST, discards = listOf(Tile.Honor.North, Tile.Honor.West, s(1)), riichi = true)
        val self = player(
            Wind.SOUTH,
            hand = hand(
                listOf(m(2), m(3), m(4), p(4), p(5), p(6), p(6), p(7), p(8), s(2), s(3), s(4), s(5)),
                drawn = m(8),
            ),
        )
        val table = table(listOf(dealer, self, player(Wind.WEST), player(Wind.NORTH)))
        val context = context(table, self, legalActions = listOf(RIICHI_GAME_ACTION))
        val eight = checkNotNull(self.hand.lastDrawn)
        val dama = DecisionCandidate.Discard(tile = eight, declaration = null, command = GameCommand.Discard(eight.id))
        val riichi = DecisionCandidate.Discard(tile = eight, declaration = RIICHI_GAME_ACTION, command = GameCommand.Extension(RiichiGameCommand(eight.id)))
        fun scores(parameters: ExpectationParameters): List<CandidateScore> = ExpectedValueEvaluator(WITH_FUTURE_RISK, parameters, ExpectationFixtures.module, ExpectationFixtures.opponentModel(WITH_FUTURE_RISK), context).scoreAll(listOf(dama, riichi))

        val (defaultDama, defaultRiichi) = scores(ExpectationParameters.DEFAULT)
        val (_, discountedRiichi) = scores(ExpectationParameters(lockedFutureRiskFactor = 0.0))
        val (safestDama, _) = scores(ExpectationParameters(futureRiskTiles = FutureRiskTiles.SAFEST))

        assertTrue(discountedRiichi.expectedValue > defaultRiichi.expectedValue, "discounted $discountedRiichi default $defaultRiichi")
        assertTrue(safestDama.expectedValue >= defaultDama.expectedValue, "safest $safestDama default $defaultDama")
    }

    /** 剛好九種么九牌、其他牌也連不起來的手牌和牌機率很低，宣告途中流局；接近聽牌的手牌則繼續。 */
    @Test
    fun `an abortive draw is declared only for a hopeless hand`() = runTest {
        val kyuushu = GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.KyuushuKyuuhai)
        val hopeless = player(
            Wind.SOUTH,
            hand = hand(
                listOf(
                    m(1), m(9), p(1), p(9), s(1),
                    Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North,
                    m(4), p(6), s(3), s(7),
                ),
                drawn = p(3),
            ),
        )
        val ready = player(
            Wind.SOUTH,
            hand = hand(
                listOf(m(1), m(2), m(3), m(4), m(5), m(6), m(7), m(8), m(9), p(1), p(1), s(3), s(4)),
                drawn = Tile.Honor.East,
            ),
        )

        assertEquals(
            GameCommand.DeclareExhaustiveDraw(RiichiExhaustiveDrawReason.KyuushuKyuuhai),
            strategy(InformationLevel.INTERMEDIATE).decideGameCommand(context(fourPlayers(hopeless), hopeless, legalActions = listOf(kyuushu))),
        )
        assertIs<GameCommand.Discard>(
            strategy(InformationLevel.INTERMEDIATE).decideGameCommand(context(fourPlayers(ready), ready, legalActions = listOf(kyuushu))),
        )
        val neverDeclares = ExpectationParameters(abortiveDrawWinProbability = 0.0)
        assertIs<GameCommand.Discard>(
            strategy(InformationLevel.INTERMEDIATE, neverDeclares)
                .decideGameCommand(context(fourPlayers(hopeless), hopeless, legalActions = listOf(kyuushu))),
            "the strategy must use the parameters it was given",
        )
    }

    /** 立直後規則強制打出摸到的牌，所有等級都只打出那張牌。 */
    @Test
    fun `a forced discard is obeyed`() = runTest {
        val self = player(
            Wind.SOUTH,
            hand = hand(
                listOf(m(2), m(3), m(4), p(4), p(5), p(6), p(7), p(8), p(9), s(2), s(3), s(4), s(5)),
                drawn = m(5),
            ),
            riichi = true,
        )
        val table = fourPlayers(self)

        allLevels.forEach { level ->
            assertEquals(GameCommand.Discard(checkNotNull(self.hand.lastDrawn).id), strategy(level).decideGameCommand(context(table, self)))
        }
    }

    /** 同一局面重複詢問得到相同決策，且打出的牌一定在自己手中。 */
    @Test
    fun `decisions are deterministic and use own tiles`() = runTest {
        val self = player(
            Wind.SOUTH,
            hand = hand(
                listOf(m(1), m(3), m(5), p(2), p(4), p(8), s(1), s(6), s(9), Tile.Honor.East, Tile.Honor.Red, m(9), p(9)),
                drawn = s(4),
            ),
        )
        val table = fourPlayers(self)
        val ownIds = self.hand.standingTiles.map { it.id }.toSet()

        allLevels.forEach { level ->
            val first = strategy(level).decideGameCommand(context(table, self))
            val second = strategy(level).decideGameCommand(context(table, self))
            assertEquals(first, second)
            assertTrue(assertIs<GameCommand.Discard>(first).tileId in ownIds)
        }
    }

    /** 以 [self] 為南家、其他三家沒有手牌的四人桌。 */
    private fun fourPlayers(self: MahjongPlayer): TableState = table(listOf(player(Wind.EAST), self, player(Wind.WEST), player(Wind.NORTH)))

    /** 命令打出的牌面。 */
    private fun discardedTile(
        table: TableState,
        self: MahjongPlayer,
        command: GameCommand,
    ): Tile {
        val tileId = assertIs<GameCommand.Discard>(command).tileId
        return table.players.first { it.id == self.id }.hand.standingTiles.first { it.id == tileId }.tile
    }

    /**
     * 西家剛打出 [ponTile]、南家可以用手中兩張碰的局面。
     *
     * 南家手牌：一二三萬、七八九筒、五六條、一一條，加上三張 [others]。
     */
    private fun ponSituation(ponTile: Tile, others: List<Tile>): Triple<TableState, MahjongPlayer, GameAction.Pon> {
        val self = player(Wind.SOUTH, hand = hand(listOf(m(1), m(2), m(3), p(7), p(8), p(9), s(5), s(6), s(1), s(1)) + others))
        val west = player(Wind.WEST, discards = listOf(ponTile))
        val table = table(listOf(player(Wind.EAST), self, west, player(Wind.NORTH)))
        val seatedSelf = table.players.first { it.id == self.id }
        val claimed = table.players.first { it.id == west.id }.discardPile.entries.last().tile
        val withTiles = seatedSelf.hand.tiles.filter { it.tile == ponTile }.take(2).map { it.id }
        return Triple(table, seatedSelf, GameAction.Pon(tileId = claimed.id, withTiles = withTiles))
    }

    /** 測試常數。 */
    private companion object {
        /** 高級另外計入後續風險，用於驗證後續風險的計算。 */
        val WITH_FUTURE_RISK: InformationLevel = InformationLevel.ADVANCED.copy(considersFutureRisk = true)
    }
}
