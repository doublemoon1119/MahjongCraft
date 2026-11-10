package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.flow.api.event.PerformedAction
import com.doublemoon1119.mahjongcraft.flow.api.event.PerformedActionKind
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryActionResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證公開動作投影只通知已成立、且確實可公開的動作。 */
class GameActionEventProjectorTest {
    /** 執行公開動作的玩家。 */
    private val actorId = Uuid.random()

    /** 另一位參與反應的玩家。 */
    private val otherPlayerId = Uuid.random()

    /** 提供標準鳴牌與公開區資料的測試規則。 */
    private val module = RiichiRuleModule("test:riichi", RiichiRuleConfig())

    /** 一般摸牌沒有公開動作事件。 */
    @Test
    fun `ordinary draw is omitted`() {
        val before = game()
        assertEquals(
            emptyList(),
            project(before, before, draft(actorId, HistoryFact.ActionAccepted(GameAction.Draw, actionResult()))),
        )
    }

    /** 捨牌會公開牌面描述，但不公開實體牌 UUID。 */
    @Test
    fun `discard exposes its face`() {
        val discarded = tile(Tile.Numeric(Tile.Suit.Character, 1))
        val before = game()
        val after = replacePlayer(before, player(discardPile = FakeDiscardPile().discardTile(discarded)))
        val result = project(before, after, draft(actorId, accepted(GameAction.Discard(discarded.id))))

        assertEquals(listOf(PublicGameActionIds.DISCARD), result.map { it.actionId })
        assertEquals(PerformedActionKind.DISCARD, result.single().kind)
        assertEquals("mahjongcraft:tile/numeric/character/1", result.single().tiles.single()?.typeId)
    }

    /** 擴充宣告與其後捨牌依提交順序輸出。 */
    @Test
    fun `extension declaration and discard keep order`() {
        val discarded = tile(Tile.Numeric(Tile.Suit.Dot, 9))
        val declaration = GameAction.Extension(DeclarationAction)
        val before = game()
        val after = replacePlayer(
            before,
            player(
                actionHistory = listOf(declaration, GameAction.Discard(discarded.id)),
                discardPile = FakeDiscardPile().discardTile(discarded),
            ),
        )
        val result = project(
            before,
            after,
            draft(actorId, HistoryFact.ActionAccepted(declaration, actionResult())),
            draft(actorId, accepted(GameAction.Discard(discarded.id))),
        )

        assertEquals(listOf(DeclarationAction.id, PublicGameActionIds.DISCARD), result.map { it.actionId })
        assertEquals(PerformedActionKind.DECLARATION, result.first().kind)
        assertEquals(PerformedActionKind.DISCARD, result.last().kind)
    }

    /** 反應視窗內尚未裁定的鳴牌選擇不會被公開。 */
    @Test
    fun `unresolved response stays hidden`() {
        val discarded = tile(Tile.Numeric(Tile.Suit.Dot, 5))
        val base = game()
        val before = base.copy(
            tableState = base.tableState.copy(
                turnPhase = TurnPhase.AwaitingDiscardReaction(
                    PendingReaction(
                        discarderId = otherPlayerId,
                        tileId = discarded.id,
                        eligiblePlayerIds = setOf(actorId),
                        ronPlayerIds = emptySet(),
                        id = Uuid.random(),
                    ),
                ),
            ),
        )
        val after = replacePlayer(before, player())
        val submitted = GameAction.Pon(discarded.id, listOf(Uuid.random(), Uuid.random()))

        assertEquals(emptyList(), project(before, after, draft(actorId, accepted(submitted))))
    }

    /** 榮和事件的執行者使用實際贏家，不會誤用最後回應者。 */
    @Test
    fun `ron uses settled winner rather than last responder`() {
        val result = project(
            game(),
            game(),
            draft(otherPlayerId, HistoryFact.ReactionResolved(GameAction.Ron(Uuid.random()), otherPlayerId)),
            draft(null, HistoryFact.WinSettled("test:ron", listOf(HistoryWinDetails(actorId, emptyList())))),
        )

        assertEquals(listOf(actorId), result.map { it.actorPlayerId })
        assertEquals(listOf(PerformedActionKind.WIN), result.map { it.kind })
    }

    /** 明槓、暗槓與加槓只公開副露中明確標記的牌面。 */
    @Test
    fun `meld faces follow public reveal ids`() {
        assertEquals(4, meldScenario(MeldType.OpenKan, revealAll = true).single().tiles.count { it != null })
        assertEquals(0, meldScenario(MeldType.ClosedKan, revealAll = false).single().tiles.count { it != null })
        assertEquals(4, meldScenario(MeldType.AddedKan, revealAll = true).single().tiles.count { it != null })
    }

    /** 槓後補牌只公開張數與來源，不公開補進來的牌 UUID 或牌面。 */
    @Test
    fun `supplemental draw exposes count without tile identity`() {
        val before = game()
        val after = afterMeld(before, MeldType.OpenKan, revealAll = true)
        val tiles = after.tableState.players.first { it.id == actorId }.hand.melds.single().tiles
        val kan = GameAction.Kan(GameAction.KanType.OPEN_KAN, tiles.first().id, tiles.drop(1).map { it.id })
        val result = project(
            before,
            after,
            draft(actorId, HistoryFact.ReactionResolved(kan, actorId)),
            draft(
                actorId,
                HistoryFact.ActionAccepted(GameAction.Draw, actionResult(listOf(Uuid.random(), Uuid.random()))),
            ),
        )

        val supplemental = result.last()
        assertEquals(PerformedActionKind.SUPPLEMENTAL_DRAW, supplemental.kind)
        assertEquals(2, supplemental.supplementalTileCount)
        assertEquals(emptyList(), supplemental.tiles)
        assertEquals(PublicGameActionIds.KAN_OPEN, supplemental.supplementalSourceId)
    }

    /** 搶槓反應中的槓尚未成立，不會提前公開成槓。 */
    @Test
    fun `robbed kan is not emitted`() {
        val before = game()
        val action = GameAction.Kan(GameAction.KanType.ADDED_KAN, Uuid.random(), emptyList())
        val after = before.copy(
            tableState = before.tableState.copy(
                turnPhase = TurnPhase.AwaitingRobbingReaction(
                    PendingRobbingReaction(
                        declarerId = actorId,
                        declaredAction = action,
                        robbedTile = tile(Tile.Numeric(Tile.Suit.Dot, 5)),
                        eligiblePlayerIds = setOf(otherPlayerId),
                    ),
                ),
            ),
        )

        assertEquals(emptyList(), project(before, after, draft(actorId, accepted(action))))
    }

    /** 規則選擇流程提交的擴充動作不當作一般公開動作。 */
    @Test
    fun `private rule decision action is omitted`() {
        val declaration = GameAction.Extension(DeclarationAction)
        val before = game()
        val after = replacePlayer(before, player(actionHistory = listOf(declaration)))
        val facts = arrayOf(
            draft(
                null,
                HistoryFact.RuleDecisionAutomaticallyResolved(RuleDecisionTiming.ROUND_START, "test:step", 0, null),
            ),
            draft(actorId, HistoryFact.ActionAccepted(declaration, actionResult())),
        )

        assertEquals(emptyList(), project(before, after, *facts))
    }

    /** 建立指定公開政策的成槓交易並取得動作。 */
    private fun meldScenario(type: MeldType, revealAll: Boolean): List<PerformedAction> {
        val before = game()
        val after = afterMeld(before, type, revealAll)
        return project(before, after, draft(actorId, accepted(kanAction(type, after))))
    }

    /** 由提交後副露組出對應槓命令。 */
    private fun kanAction(type: MeldType, after: Game): GameAction.Kan {
        val tiles = after.tableState.players.first { it.id == actorId }.hand.melds.last().tiles
        return when (type) {
            MeldType.OpenKan -> GameAction.Kan(
                GameAction.KanType.OPEN_KAN,
                tiles.first().id,
                tiles.drop(1).map { it.id },
            )
            MeldType.ClosedKan -> GameAction.Kan(
                GameAction.KanType.CLOSED_KAN,
                tiles.first().id,
                tiles.drop(1).map { it.id },
            )
            MeldType.AddedKan -> GameAction.Kan(
                GameAction.KanType.ADDED_KAN,
                tiles.last().id,
                listOf(tiles.first().id),
            )
            else -> error("Test only covers kan melds")
        }
    }

    /** 建立包含新副露的提交後桌況。 */
    private fun afterMeld(before: Game, type: MeldType, revealAll: Boolean): Game {
        val tiles = List(4) { tile(Tile.Numeric(Tile.Suit.Dot, 5)) }
        val oldMelds = if (type == MeldType.AddedKan) listOf(meld(MeldType.Pon, tiles.take(3), true)) else emptyList()
        return replacePlayer(before, player(hand = Hand(melds = oldMelds + meld(type, tiles, revealAll))))
    }

    /** 建立明確指定公開牌位置的副露。 */
    private fun meld(type: MeldType, tiles: List<IdentifiedTile>, revealAll: Boolean) = Meld(
        type = type,
        tiles = tiles,
        sourceTile = if (type == MeldType.OpenKan) tiles.first() else null,
        sourceDirection = if (type == MeldType.OpenKan) RelativeDirection.Left else RelativeDirection.Self,
        publiclyRevealedTileIds = if (revealAll) tiles.map { it.id }.toSet() else emptySet(),
        faceDownTileIds = emptySet(),
    )

    /** 將測試前後桌況與來源事實交給正式投影器。 */
    private fun project(before: Game, after: Game, vararg facts: HistoryEventDraft) = GameActionEventProjector.project(CommittedGameFacts(after.id, before, after, facts.toList()), module)

    /** 建立兩位玩家的測試場次。 */
    private fun game(): Game = Game(
        tableState = FakeTableStateFactory.create(
            players = listOf(player(), player(otherPlayerId, initialSeatIndex = 1)),
            config = RiichiRuleConfig(),
        ),
        flowConfig = GameFlowConfig(),
        matchId = Uuid.random(),
    )

    /** 只替換指定玩家，保留其餘對局資料。 */
    private fun replacePlayer(game: Game, replacement: MahjongPlayer): Game = game.copy(
        tableState = game.tableState.copy(
            players = game.tableState.players.map { if (it.id == replacement.id) replacement else it },
        ),
    )

    /** 建立可指定手牌、動作與牌河的玩家。 */
    private fun player(
        id: Uuid = actorId,
        initialSeatIndex: Int = 0,
        hand: Hand = Hand(),
        actionHistory: List<GameAction> = emptyList(),
        discardPile: FakeDiscardPile = FakeDiscardPile(),
    ): MahjongPlayer = FakeMahjongPlayerFactory.create(
        id = id,
        initialSeatIndex = initialSeatIndex,
        hand = hand,
        discardPile = discardPile,
    ).copy(actionHistory = actionHistory)

    /** 建立已接受的動作事實。 */
    private fun accepted(action: GameAction) = HistoryFact.ActionAccepted(action, actionResult())

    /** 附上事實所屬的動作者。 */
    private fun draft(actor: Uuid?, fact: HistoryFact) = HistoryEventDraft(actor, fact)

    /** 建立不含額外公開變化的動作結果。 */
    private fun actionResult(affectedTileIds: List<Uuid> = emptyList()) = HistoryActionResult(
        affectedTileIds = affectedTileIds,
        newlyRevealedTileIds = emptyList(),
        remainingWallTileCount = 50,
        reservedWallTileIds = emptyList(),
        scoresByPlayerId = emptyMap(),
        nextPlayerId = otherPlayerId,
    )

    /** 建立具有獨立實體識別碼的測試牌。 */
    private fun tile(type: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), type)

    /** 測試第三方公開宣告使用的動作。 */
    private object DeclarationAction : ExtensionGameAction {
        override val id: String = "test:declaration"
    }
}
