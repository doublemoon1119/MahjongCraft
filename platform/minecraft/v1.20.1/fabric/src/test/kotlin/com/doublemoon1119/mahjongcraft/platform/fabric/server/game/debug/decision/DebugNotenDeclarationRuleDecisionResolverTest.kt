package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugDeclareNotenAction
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugNotenDeclarationRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugNotenDeclarationRiichiRuleModule
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 驗證開發用可宣告不聽規則的選擇時機、參與者與效果。 */
class DebugNotenDeclarationRuleDecisionResolverTest {
    /** 受測解析器。 */
    private val resolver = DebugNotenDeclarationRuleDecisionResolver()

    /** 對應的規則模組。 */
    private val module = DebugNotenDeclarationRiichiRuleModule(
        DebugNotenDeclarationRiichiRuleModule.ID,
        DebugNotenDeclarationRiichiRuleConfig(RiichiRuleConfig()),
    )

    /** 一般流局結算前只詢問沒有立直的聽牌玩家；已立直與不聽的玩家不參與。 */
    @Test
    fun `only tenpai players without riichi choose before the exhaustive draw`() {
        val state = state()

        val step = assertNotNull(resolver.begin(RuleDecisionTiming.BEFORE_EXHAUSTIVE_DRAW, state, module))

        assertEquals(DebugNotenDeclarationRuleDecisionResolver.NOTEN_DECLARATION_STEP, step.stepId)
        assertEquals(setOf(state.players[0].id), step.inputSpecsByPlayerId.keys)
        assertEquals(
            RuleDecisionInputSpec.SingleChoice(
                listOf(DebugNotenDeclarationRuleDecisionResolver.REVEAL_TENPAI_OPTION, DebugNotenDeclarationRuleDecisionResolver.DECLARE_NOTEN_OPTION),
            ),
            step.inputSpecsByPlayerId.getValue(state.players[0].id),
        )
    }

    /** 沒有可以宣告不聽的玩家時，一般流局直接結算。 */
    @Test
    fun `no choice opens when nobody may declare noten`() {
        val state = state().let { it.copy(players = it.players.map { player -> player.copy(hand = notenHand()) }) }

        assertNull(resolver.begin(RuleDecisionTiming.BEFORE_EXHAUSTIVE_DRAW, state, module))
    }

    /** 本局結算後請所有玩家確認；開局與胡牌後繼續本局不開啟選擇。 */
    @Test
    fun `every player confirms after the round and no other timing opens a choice`() {
        val state = state()

        val step = assertNotNull(resolver.begin(RuleDecisionTiming.ROUND_END, state, module))

        assertEquals(state.players.associate { it.id to RuleDecisionInputSpec.Confirmation }, step.inputSpecsByPlayerId)
        assertNull(resolver.begin(RuleDecisionTiming.ROUND_START, state, module))
        assertNull(resolver.begin(RuleDecisionTiming.WIN_CONTINUES, state, module))
    }

    /** 只有選擇宣告不聽的玩家記錄宣告；逾時或 AI 沒有選擇時預設公開聽牌。 */
    @Test
    fun `only a noten choice is recorded and revealing is the default`() {
        val state = state()
        val revealing = FakeMahjongPlayerFactory.create(Wind.SOUTH).copy(hand = tenpaiHand())
        val withSecondTenpai = state.copy(players = listOf(state.players[0], revealing) + state.players.drop(2))
        val step = assertNotNull(resolver.begin(RuleDecisionTiming.BEFORE_EXHAUSTIVE_DRAW, withSecondTenpai, module))
        val window = PendingRuleDecision.open(step = step, timing = RuleDecisionTiming.BEFORE_EXHAUSTIVE_DRAW)
        val declaredId = withSecondTenpai.players[0].id
        val complete = window.copy(
            submissionsByPlayerId = mapOf(
                declaredId to RuleDecisionSubmission.Choice(DebugNotenDeclarationRuleDecisionResolver.DECLARE_NOTEN_OPTION),
                revealing.id to resolver.fallbackSubmission(withSecondTenpai, window, revealing.id, module),
            ),
        )

        val resolved = resolver.resolve(withSecondTenpai, complete, module)

        assertEquals(
            RuleDecisionSubmission.Choice(DebugNotenDeclarationRuleDecisionResolver.REVEAL_TENPAI_OPTION),
            complete.submissionsByPlayerId.getValue(revealing.id),
        )
        assertNull(resolved.nextStep)
        val declare = GameAction.Extension(DebugDeclareNotenAction)
        assertEquals(listOf(declaredId), resolved.tableState.players.filter { declare in it.actionHistory }.map { it.id })
    }

    /**
     * 東家聽牌、南家立直聽牌、西家與北家不聽的四人桌。
     *
     * @return 牌山已摸完時的桌況。
     */
    private fun state(): TableState = FakeTableStateFactory.create(
        players = listOf(
            FakeMahjongPlayerFactory.create(Wind.EAST).copy(hand = tenpaiHand(), playerRuleState = RiichiPlayerState()),
            FakeMahjongPlayerFactory.create(Wind.SOUTH).copy(
                hand = tenpaiHand(),
                playerRuleState = RiichiPlayerState(riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.North)),
            ),
            FakeMahjongPlayerFactory.create(Wind.WEST).copy(hand = notenHand(), playerRuleState = RiichiPlayerState()),
            FakeMahjongPlayerFactory.create(Wind.NORTH).copy(hand = notenHand(), playerRuleState = RiichiPlayerState()),
        ),
        config = module.config,
    )

    /**
     * 十三張萬子立牌 1112345678999m；加入任一種萬子都能和牌。
     *
     * @return 聽牌的手牌。
     */
    private fun tenpaiHand(): Hand = hand(listOf(1, 1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 9, 9).map { Tile.Numeric(Tile.Suit.Character, it) })

    /**
     * 三種數牌各取 1、4、7，再加四種風牌。
     *
     * @return 不聽的手牌。
     */
    private fun notenHand(): Hand = hand(
        listOf(Tile.Suit.Character, Tile.Suit.Dot, Tile.Suit.Bamboo).flatMap { suit -> listOf(1, 4, 7).map { Tile.Numeric(suit, it) } } +
            listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North),
    )

    /**
     * 以指定牌種建立立牌。
     *
     * @param tiles 立牌的牌種。
     * @return 只有立牌的手牌。
     */
    private fun hand(tiles: List<Tile>): Hand = Hand(tiles = tiles.map { FakeIdentifiedTileFactory.create(it) })
}
