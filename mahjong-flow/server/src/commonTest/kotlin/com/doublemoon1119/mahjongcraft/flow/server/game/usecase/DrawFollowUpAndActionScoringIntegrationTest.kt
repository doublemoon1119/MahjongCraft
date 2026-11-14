package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.api.event.ActionsPerformedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.PerformedActionKind
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStep
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStepEvent
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration.NetworkDtoRegistrars
import com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration.PersistenceRegistrars
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.runAsSingleWrite
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.api.registration.MahjongModuleRegistrar
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRoundTransition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionSource
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoring
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoringContext
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoringPolicy
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.MeldCompletedContext
import com.doublemoon1119.mahjongcraft.logic.module.NoOpActionScoringPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpDecision
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpPolicy
import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.NoOpWallRevealPolicy
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealPolicy
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.logic.tile.component.resolveTileAreas
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledWinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.tileRegion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 以登記的測試規則驗證摸牌後的自動移出與補牌，以及動作成立當下的點數收付。 */
class DrawFollowUpAndActionScoringIntegrationTest {
    /** 摸到一般牌時不追加任何動作。 */
    @Test
    fun `drawing an ordinary tile adds no follow up`() = runTest {
        val fixture = Fixture()
        val ordinary = tile(ORDINARY)
        fixture.repository.setTableState(fixture.table(wall = listOf(ordinary, tile(ORDINARY))))

        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        val drawer = fixture.drawer()
        assertEquals(ordinary, drawer.hand.lastDrawn)
        assertEquals(listOf(GameAction.Draw), fixture.recordedActions())
        assertTrue(followUps(fixture.presentation.getPublishedTableStepBatches(fixture.gameId).last()).isEmpty())
    }

    /** 連續摸到花時在同一筆寫入內依序移出並補牌，直到補到一般牌。 */
    @Test
    fun `consecutive flowers are replaced within a single write`() = runTest {
        val fixture = Fixture()
        val firstFlower = tile(FLOWER)
        val secondFlower = tile(FLOWER)
        val ordinary = tile(ORDINARY)
        fixture.repository.setTableState(fixture.table(wall = listOf(firstFlower, ordinary, secondFlower)))

        val result = fixture.repository.runAsSingleWrite(fixture.gameId) { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertIs<Outcome.Success<Unit>>(result)
        val drawer = fixture.drawer()
        assertEquals(ordinary, drawer.hand.lastDrawn)
        assertEquals(listOf(firstFlower, secondFlower), drawer.tileAreas["test:flowers"]?.tiles.orEmpty())
        assertEquals(listOf(GameAction.Draw, FLOWER_ACTION, GameAction.Draw, FLOWER_ACTION, GameAction.Draw), fixture.recordedActions())
        assertEquals(
            expected = listOf(GameAction.Draw, FLOWER_ACTION, GameAction.Draw, FLOWER_ACTION, GameAction.Draw),
            actual = fixture.events.getNotifiedActions(fixture.gameId, fixture.drawerId, fixture.drawerId),
        )
        // 摸牌一步，之後每朵花的移出與補牌各一步；最後留在摸牌位的是補到的一般牌。
        val steps = fixture.presentation.getPublishedTableStepBatches(fixture.gameId).single()
        assertEquals(TileRegion.DRAWN, steps.first().tableState.tileRegion(firstFlower.id))
        assertEquals(
            expected = listOf(firstFlower.id to setOf(secondFlower.id), secondFlower.id to setOf(ordinary.id)),
            actual = followUps(steps),
        )
        assertEquals(TileRegion.DRAWN, steps.last().tableState.tileRegion(ordinary.id))
        val publicActions = fixture.actionEvents().single().actions
        assertEquals(
            expected = listOf(PerformedActionKind.DECLARATION, PerformedActionKind.SUPPLEMENTAL_DRAW, PerformedActionKind.DECLARATION, PerformedActionKind.SUPPLEMENTAL_DRAW),
            actual = publicActions.map { it.kind },
        )
        val replacements = publicActions.filter { it.kind == PerformedActionKind.SUPPLEMENTAL_DRAW }
        assertEquals(listOf(1, 1), replacements.map { it.supplementalTileCount })
        assertEquals(listOf(FLOWER_ACTION.value.id, FLOWER_ACTION.value.id), replacements.map { it.supplementalSourceId })
        assertTrue(replacements.all { it.tiles.isEmpty() })
    }

    /** 移出手牌時規則更新的對局組件在補牌後仍然保留。 */
    @Test
    fun `table component updated by a set aside survives the replacement draw`() = runTest {
        val fixture = Fixture(TestBehavior(countsFlowersInTableComponent = true))
        fixture.repository.setTableState(fixture.table(wall = listOf(tile(FLOWER), tile(ORDINARY), tile(FLOWER))))

        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        assertEquals(TestPot(2), fixture.repository.getTableState(fixture.gameId)!!.components[testPotComponent])
    }

    /** 摸到花卻已經沒有牌可以補時，依規則以流局結束本局。 */
    @Test
    fun `a flower with nothing left to replace ends the round`() = runTest {
        val fixture = Fixture()
        fixture.repository.setTableState(fixture.table(wall = listOf(tile(FLOWER))))

        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        assertEquals(GameAction.ExhaustiveDraw(NoReplacementReason), fixture.drawer().actionHistory.last())
        assertEquals(listOf(GameAction.Draw, GameAction.ExhaustiveDraw(NoReplacementReason)), fixture.recordedActions())
    }

    /** 規則要求移出卻補不到牌時視為規則錯誤，桌況與副作用都不改變。 */
    @Test
    fun `a follow up that cannot draw a replacement is a contract violation`() = runTest {
        val fixture = Fixture(TestBehavior(ignoresEmptyWall = true))
        val table = fixture.table(wall = listOf(tile(FLOWER)))
        fixture.repository.setTableState(table)

        assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
        assertTrue(fixture.repository.historyDrafts.isEmpty())
        assertTrue(fixture.events.getNotifiedActions(fixture.gameId, fixture.drawerId, fixture.drawerId).isEmpty())
    }

    /** 移出手牌的結果改動了其他玩家時視為規則錯誤。 */
    @Test
    fun `a set aside result that changes another player is rejected`() = runTest {
        val fixture = Fixture(TestBehavior(setAsideChangesOthers = true))
        val table = fixture.table(wall = listOf(tile(FLOWER), tile(ORDINARY)))
        fixture.repository.setTableState(table)

        assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
    }

    /** 其他玩家可以搶花時開啟搶和視窗並暫停；全員放過後補牌並繼續判斷。 */
    @Test
    fun `a robbable flower pauses the chain until everyone passes`() = runTest {
        val behavior = TestBehavior(robbingPlayerIds = mutableSetOf())
        val fixture = Fixture(behavior)
        behavior.robbingPlayerIds += fixture.southId
        val flower = tile(FLOWER)
        val ordinary = tile(ORDINARY)
        fixture.repository.setTableState(fixture.table(wall = listOf(flower, ordinary)))

        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        val paused = assertNotNull(fixture.repository.getTableState(fixture.gameId))
        val pending = assertNotNull(paused.pendingRobbingReaction)
        assertEquals(flower, pending.robbedTile)
        assertEquals(setOf(fixture.southId), pending.eligiblePlayerIds)
        assertEquals(flower, paused.players.first { it.id == fixture.drawerId }.hand.lastDrawn)
        assertEquals(listOf(GameAction.Draw, FLOWER_ACTION), fixture.recordedActions())
        // 等待搶和時最後一步是花已移出、尚未補牌的樣子。
        assertEquals(listOf(flower.id to emptySet<Uuid>()), followUps(fixture.presentation.getPublishedTableStepBatches(fixture.gameId).single()))
        assertTrue(fixture.actionEvents().isEmpty())

        assertIs<Outcome.Success<Unit>>(fixture.respondToRobbing(fixture.gameId, fixture.southId, GameAction.Pass))

        val resumed = fixture.drawer()
        assertNull(fixture.repository.getTableState(fixture.gameId)!!.pendingRobbingReaction)
        assertEquals(ordinary, resumed.hand.lastDrawn)
        assertEquals(listOf(flower), resumed.tileAreas["test:flowers"]?.tiles.orEmpty())
        assertEquals(
            expected = listOf(PerformedActionKind.DECLARATION, PerformedActionKind.SUPPLEMENTAL_DRAW),
            actual = fixture.actionEvents().single().actions.map { it.kind },
        )
    }

    /** 槓後補到花時也會自動移出並再補牌；槓的收付可以把點數放進規則的點數池。 */
    @Test
    fun `a kan replacement flower is replaced and the kan can pay into a pot`() = runTest {
        val behavior = TestBehavior(
            scoring = { context ->
                (context.action as? GameAction.Kan)?.let {
                    ActionScoring(mapOf(context.actorPlayerId to -1000), potDeposit = 1000, tableComponents = ComponentPatch(values = ComponentSet.EMPTY.with(testPotComponent, TestPot(1000))))
                }
            },
        )
        val fixture = Fixture(behavior)
        val kanTile = tile(KAN_TILE)
        val flower = tile(FLOWER)
        val ordinary = tile(ORDINARY)
        val hand = Hand(tiles = List(3) { tile(KAN_TILE) } + fillers(9), lastDrawn = kanTile)
        fixture.repository.setTableState(fixture.table(drawerHand = hand, wall = listOf(ordinary, flower)))
        val kan = fixture.legalClosedKan()

        assertIs<Outcome.Success<Unit>>(fixture.kan(fixture.gameId, fixture.drawerId, kan.type, kan.tileId))

        val state = assertNotNull(fixture.repository.getTableState(fixture.gameId))
        val drawer = state.players.first { it.id == fixture.drawerId }
        assertEquals(ordinary, drawer.hand.lastDrawn)
        assertEquals(listOf(flower), drawer.tileAreas["test:flowers"]?.tiles.orEmpty())
        assertEquals(INITIAL_SCORE - 1000, drawer.score)
        assertEquals(TestPot(1000), state.components[testPotComponent])
        assertEquals(listOf(kan, GameAction.Draw, FLOWER_ACTION, GameAction.Draw), fixture.recordedActions())
        val scored = fixture.repository.historyDrafts.mapNotNull { it.fact as? HistoryFact.ActionScored }.single()
        assertEquals(kan, scored.action)
        assertEquals(mapOf(fixture.drawerId to -1000), scored.scoreDeltas)
        assertEquals(1000, scored.potDeposit)
        assertEquals(1000, fixture.presentation.getPublishedActionScorings(fixture.gameId).single().potDeposit)
    }

    /** 碰成立當下由被碰的玩家付點數，收付寫入分數、牌譜並通知平台。 */
    @Test
    fun `a pon can make the discarder pay immediately`() = runTest {
        val behavior = TestBehavior(
            scoring = { context ->
                if (context.action is GameAction.Pon) {
                    ActionScoring(
                        scoreDeltas = mapOf(context.actorPlayerId to 2000, requireNotNull(context.sourcePlayerId) to -2000),
                        potDeposit = 0,
                        tableComponents = ComponentPatch(),
                    )
                } else {
                    null
                }
            },
        )
        val fixture = Fixture(behavior)
        val discarded = tile(KAN_TILE)
        val matching = List(2) { tile(KAN_TILE) }
        fixture.repository.setTableState(fixture.ponTable(discarded, matching))

        assertIs<Outcome.Success<Unit>>(
            value = fixture.respondToDiscard(
                gameId = fixture.gameId,
                playerId = fixture.southId,
                action = GameAction.Pon(discarded.id, matching.map { it.id }),
                reactionId = fixture.repository.getTableState(fixture.gameId)!!.pendingReaction!!.id,
            ),
        )

        val state = assertNotNull(fixture.repository.getTableState(fixture.gameId))
        assertEquals(INITIAL_SCORE + 2000, state.players.first { it.id == fixture.southId }.score)
        assertEquals(INITIAL_SCORE - 2000, state.players.first { it.id == fixture.drawerId }.score)
        val scored = fixture.repository.historyDrafts.mapNotNull { it.fact as? HistoryFact.ActionScored }.single()
        assertEquals(mapOf(fixture.southId to 2000, fixture.drawerId to -2000), scored.scoreDeltas)
        assertEquals(fixture.southId, fixture.presentation.getPublishedActionScorings(fixture.gameId).single().actorPlayerId)
    }

    /** 收付不符合規定時視為規則錯誤，桌況不改變。 */
    @Test
    fun `invalid action scoring is a contract violation`() = runTest {
        val invalidScorings: List<(ActionScoringContext) -> ActionScoring> = listOf(
            { ActionScoring(mapOf(it.actorPlayerId to 1000), potDeposit = 0, tableComponents = ComponentPatch()) },
            { ActionScoring(mapOf(Uuid.random() to 1000, it.actorPlayerId to -1000), potDeposit = 0, tableComponents = ComponentPatch()) },
            { ActionScoring(mapOf(it.actorPlayerId to 0), potDeposit = 0, tableComponents = ComponentPatch()) },
            { ActionScoring(emptyMap(), potDeposit = 0, tableComponents = ComponentPatch()) },
            { ActionScoring(mapOf(it.actorPlayerId to Int.MAX_VALUE), potDeposit = Int.MIN_VALUE + 1, tableComponents = ComponentPatch()) },
        )
        invalidScorings.forEach { invalid ->
            val fixture = Fixture(TestBehavior(scoring = { context -> invalid(context).takeIf { context.action == FLOWER_ACTION } }))
            val table = fixture.table(wall = listOf(tile(FLOWER), tile(ORDINARY)))
            fixture.repository.setTableState(table)

            assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

            assertEquals(table, fixture.repository.getTableState(fixture.gameId))
        }
    }

    /** 收付讓玩家分數超出整數範圍時視為規則錯誤，桌況不改變。 */
    @Test
    fun `action scoring that overflows a score is a contract violation`() = runTest {
        val fixture = Fixture(
            behavior = TestBehavior(
                scoring = { context ->
                    ActionScoring(mapOf(context.actorPlayerId to 1000, context.tableStateAfterAction.players.first { it.id != context.actorPlayerId }.id to -1000), potDeposit = 0, tableComponents = ComponentPatch())
                        .takeIf { context.action == FLOWER_ACTION }
                },
            ),
        )
        val base = fixture.table(wall = listOf(tile(FLOWER), tile(ORDINARY)))
        val table = base.copy(players = base.players.map { if (it.id == fixture.drawerId) it.copy(score = Int.MAX_VALUE - 10) else it })
        fixture.repository.setTableState(table)

        assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
    }

    /** 暫停在搶和視窗的移出還沒有成立，不收付點數。 */
    @Test
    fun `a set aside waiting for robbing responses is not scored`() = runTest {
        var scoringCalls = 0
        val behavior = TestBehavior(
            robbingPlayerIds = mutableSetOf(),
            scoring = {
                scoringCalls++
                null
            },
        )
        val fixture = Fixture(behavior)
        behavior.robbingPlayerIds += fixture.southId
        fixture.repository.setTableState(fixture.table(wall = listOf(tile(FLOWER), tile(ORDINARY))))

        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        assertEquals(0, scoringCalls)
    }

    /** 摸牌後的掛勾回傳舊值已過時的變更時中止。 */
    @Test
    fun `a draw hook with a stale component change is rejected`() = runTest {
        val fixture = Fixture(TestBehavior(hooksSendStalePatch = true))
        val table = fixture.table(wall = listOf(tile(ORDINARY)))
        fixture.repository.setTableState(table)

        assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
    }

    /** 鳴牌後的掛勾回傳舊值已過時的變更時中止。 */
    @Test
    fun `a meld hook with a stale component change is rejected`() = runTest {
        val fixture = Fixture(TestBehavior(hooksSendStalePatch = true))
        val hand = Hand(tiles = List(3) { tile(KAN_TILE) } + fillers(9), lastDrawn = tile(KAN_TILE))
        val table = fixture.table(drawerHand = hand, wall = listOf(tile(ORDINARY), tile(ORDINARY)))
        fixture.repository.setTableState(table)
        val kan = fixture.legalClosedKan()

        assertFailsWith<RuleContractViolationException> { fixture.kan(fixture.gameId, fixture.drawerId, kan.type, kan.tileId) }

        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
    }

    /** 一次補到多張時依摸取順序逐張詢問；前面的花也會移出，最後摸到、留在手上的牌回到摸牌位。 */
    @Test
    fun `several replacement tiles are asked about in draw order`() = runTest {
        val fixture = Fixture(TestBehavior(replacementCount = 2))
        val (first, second) = tile(FLOWER) to tile(FLOWER)
        val (a, b, c) = Triple(tile(ORDINARY), tile(ORDINARY), tile(ORDINARY))
        fixture.repository.setTableState(fixture.table(wall = listOf(first, a, b, c, second)))

        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        val drawer = fixture.drawer()
        assertEquals(listOf(first, second), drawer.tileAreas["test:flowers"]?.tiles.orEmpty())
        assertEquals(a, drawer.hand.lastDrawn)
        assertTrue(drawer.hand.tiles.containsAll(listOf(b, c)))
        assertTrue(drawer.hand.standingTiles.none { it.tile == FLOWER })
        assertEquals(
            expected = listOf(first.id to setOf(second.id, c.id), second.id to setOf(b.id, a.id)),
            actual = followUps(fixture.presentation.getPublishedTableStepBatches(fixture.gameId).single()),
        )
    }

    /** 搶和視窗暫停時保留還沒詢問過的補牌，全員放過後連同新補到的牌繼續詢問。 */
    @Test
    fun `a robbing pause keeps the replacement tiles not asked about yet`() = runTest {
        val behavior = TestBehavior(replacementCount = 2)
        val fixture = Fixture(behavior)
        val (first, second, third) = Triple(tile(FLOWER), tile(FLOWER), tile(FLOWER))
        val ordinary = List(4) { tile(ORDINARY) }
        behavior.robbingPlayerIds += fixture.southId
        behavior.robbableTileIds += second.id
        fixture.repository.setTableState(fixture.table(wall = listOf(first) + ordinary + listOf(third, second)))

        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        val pending = assertNotNull(fixture.repository.getTableState(fixture.gameId)!!.pendingRobbingReaction)
        assertEquals(second, pending.robbedTile)
        assertEquals(listOf(third.id), pending.pendingFollowUpDraws.map { it.tileId })
        assertEquals(second, fixture.drawer().hand.lastDrawn)

        assertIs<Outcome.Success<Unit>>(fixture.respondToRobbing(fixture.gameId, fixture.southId, GameAction.Pass))

        val drawer = fixture.drawer()
        assertEquals(listOf(first, second, third), drawer.tileAreas["test:flowers"]?.tiles.orEmpty())
        assertEquals(ordinary.first(), drawer.hand.lastDrawn)
        assertTrue(drawer.hand.standingTiles.none { it.tile == FLOWER })
    }

    /** 搶和視窗暫停後繼續時，還沒詢問過的牌照原本的來源交給規則，不改成後來那次移出的補牌。 */
    @Test
    fun `a robbing pause keeps the source of the tiles not asked about yet`() = runTest {
        val behavior = TestBehavior(replacementCount = 2)
        val fixture = Fixture(behavior)
        val flower = tile(FLOWER)
        val (kept, later) = tile(ORDINARY) to List(2) { tile(ORDINARY) }
        behavior.robbingPlayerIds += fixture.southId
        behavior.robbableTileIds += flower.id
        val hand = Hand(tiles = List(3) { tile(KAN_TILE) } + fillers(9), lastDrawn = tile(KAN_TILE))
        fixture.repository.setTableState(fixture.table(drawerHand = hand, wall = List(2) { tile(ORDINARY) } + later + listOf(kept, flower)))
        val kan = fixture.legalClosedKan()

        assertIs<Outcome.Success<Unit>>(fixture.kan(fixture.gameId, fixture.drawerId, kan.type, kan.tileId))
        assertEquals(flower, assertNotNull(fixture.repository.getTableState(fixture.gameId)!!.pendingRobbingReaction).robbedTile)
        assertIs<Outcome.Success<Unit>>(fixture.respondToRobbing(fixture.gameId, fixture.southId, GameAction.Pass))

        val keptSource = behavior.askedSources.single { (tileId, _) -> tileId == kept.id }.second
        assertIs<GameAction.Kan>(assertIs<DrawSource.Supplemental>(keptSource).action)
    }

    /** 宣告移出後自動處理又移出補到的牌時，宣告與自動移出依序各自一步。 */
    @Test
    fun `a declared set aside followed by automatic ones presents each set aside in order`() = runTest {
        val fixture = Fixture(TestBehavior(declaresFlowers = true))
        val declared = tile(FLOWER)
        val replacement = tile(FLOWER)
        val ordinary = tile(ORDINARY)
        val hand = Hand(tiles = fillers(13), lastDrawn = declared)
        val before = fixture.table(drawerHand = hand, wall = listOf(ordinary, replacement))
        fixture.repository.setTableState(before)

        assertIs<Outcome.Success<Unit>>(fixture.declareSetAside(fixture.gameId, fixture.drawerId, FLOWER_ACTION))

        assertEquals(listOf(declared, replacement), fixture.drawer().tileAreas["test:flowers"]?.tiles.orEmpty())
        val steps = fixture.presentation.getPublishedTableStepBatches(fixture.gameId).single()
        assertEquals(TileRegion.SET_ASIDE, steps.first().tableState.tileRegion(declared.id))
        assertEquals(listOf(declared.id to setOf(replacement.id), replacement.id to setOf(ordinary.id)), followUps(steps, before = before))
    }

    /** 全員放過後，接續補牌與之後的自動移出，不再重複呈現已宣告的移出。 */
    @Test
    fun `resuming automatic set asides after a robbing pause continues from the replacement`() = runTest {
        val behavior = TestBehavior(replacementCount = 2)
        val fixture = Fixture(behavior)
        val (first, second, third) = Triple(tile(FLOWER), tile(FLOWER), tile(FLOWER))
        behavior.robbingPlayerIds += fixture.southId
        behavior.robbableTileIds += second.id
        fixture.repository.setTableState(fixture.table(wall = listOf(first) + List(4) { tile(ORDINARY) } + listOf(third, second)))
        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        assertIs<Outcome.Success<Unit>>(fixture.respondToRobbing(fixture.gameId, fixture.southId, GameAction.Pass))

        val resumed = fixture.presentation.getPublishedTableStepBatches(fixture.gameId).last()
        assertEquals(listOf(third.id), followUps(resumed).map { it.first })
        assertEquals(1, resumed.flatMap { it.events }.count { it is TableStepEvent.ActionDeclared }, "The paused set aside was already presented.")
    }

    /** 規則要移出的不是這次詢問的牌時視為規則錯誤，桌況不改變。 */
    @Test
    fun `a follow up must set aside the tile it asked about`() = runTest {
        val fixture = Fixture(TestBehavior(setsAsideOldestFlower = true))
        val table = fixture.table(drawerHand = Hand(tiles = listOf(tile(FLOWER)) + fillers(12)), wall = listOf(tile(FLOWER), tile(ORDINARY)))
        fixture.repository.setTableState(table)

        val failure = assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertTrue("must remove the drawn tile" in failure.message.orEmpty(), failure.message)
        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
    }

    /** 一次補入多張牌時，規則不能在仍有牌尚未處理前暫停，避免後面的牌被遺失。 */
    @Test
    fun `a rule decision cannot pause before the remaining drawn tiles are handled`() = runTest {
        val fixture = Fixture(TestBehavior(replacementCount = 2, ignoresEmptyWall = true, pausesAfterSetAside = true))
        val table = fixture.table(wall = listOf(tile(FLOWER), tile(ORDINARY), tile(FLOWER)))
        fixture.repository.setTableState(table)

        val failure = assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertTrue("cannot pause a draw batch" in failure.message.orEmpty(), failure.message)
        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
    }

    /** 規則指定移出的牌與手上同一張牌的牌面不同時視為規則錯誤，桌況不改變。 */
    @Test
    fun `a set aside tile with a different face is rejected`() = runTest {
        val fixture = Fixture(TestBehavior(changesSetAsideFace = true))
        val table = fixture.table(wall = listOf(tile(FLOWER), tile(ORDINARY)))
        fixture.repository.setTableState(table)

        assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
    }

    /** 搶花依一炮多響設定判定流局、規則卻沒有提供流局原因時視為規則錯誤，不會照常移出並補牌。 */
    @Test
    fun `a multi ron abortive draw without a reason stops a set aside`() = runTest {
        val behavior = TestBehavior(omitsMultiRonAbortiveDrawReason = true)
        val fixture = Fixture(behavior)
        behavior.robbingPlayerIds += setOf(fixture.southId, fixture.westId)
        val table = fixture.table(wall = listOf(tile(FLOWER), tile(ORDINARY)), config = ABORTIVE_DRAW_CONFIG)
        fixture.repository.setTableState(table)

        assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
    }

    /** 搶槓依一炮多響設定判定流局、規則卻沒有提供流局原因時視為規則錯誤，不會照常開槓並補牌。 */
    @Test
    fun `a multi ron abortive draw without a reason stops a kan`() = runTest {
        val behavior = TestBehavior(omitsMultiRonAbortiveDrawReason = true, robsKans = true)
        val fixture = Fixture(behavior)
        behavior.robbingPlayerIds += setOf(fixture.southId, fixture.westId)
        val hand = Hand(tiles = List(3) { tile(KAN_TILE) } + fillers(9), lastDrawn = tile(KAN_TILE))
        val table = fixture.table(drawerHand = hand, wall = listOf(tile(ORDINARY), tile(ORDINARY)), config = ABORTIVE_DRAW_CONFIG)
        fixture.repository.setTableState(table)
        val kan = fixture.legalClosedKan()

        assertFailsWith<RuleContractViolationException> { fixture.kan(fixture.gameId, fixture.drawerId, kan.type, kan.tileId) }

        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
    }

    /**
     * 從依序的步驟找出每次移出花與緊接著補到的牌：宣告花的那一步移入移出區的牌，以及下一步從牌牆或保留區移出的牌。
     *
     * @param steps 一次交易送出的步驟。
     * @param before 交易之前的桌況，供第一步就是宣告時比較；第一步不是宣告時可省略。
     * @return 依序排列的（移出的花, 補到的牌）。
     */
    private fun followUps(steps: List<TableStep>, before: TableState? = null): List<Pair<Uuid, Set<Uuid>>> = steps.withIndex()
        .filter { (index, step) -> (index > 0 || before != null) && step.events.any { it is TableStepEvent.ActionDeclared && it.action == FLOWER_ACTION } }
        .map { (index, step) ->
            val previous = if (index == 0) checkNotNull(before) else steps[index - 1].tableState
            val setAside = movedTiles(previous, step.tableState) { it == TileRegion.SET_ASIDE }.single()
            val next = steps.getOrNull(index + 1)?.takeUnless { candidate -> candidate.events.any { it is TableStepEvent.ActionDeclared } }
            val drawn = next?.let { movedTiles(step.tableState, it.tableState) { region -> region == TileRegion.HAND || region == TileRegion.DRAWN } }
                ?.filter { tileId -> step.tableState.tileRegion(tileId) == TileRegion.WALL || step.tableState.tileRegion(tileId) == TileRegion.RESERVED }
                .orEmpty()
            setAside to drawn.toSet()
        }

    /**
     * 從 [before] 到 [after] 移入符合 [into] 區域的牌。
     *
     * @return 區域改變且新區域符合條件的牌。
     */
    private fun movedTiles(before: TableState, after: TableState, into: (TileRegion) -> Boolean): List<Uuid> = after.resolveTileAreas().filter { entry -> into(entry.region) && before.tileRegion(entry.tile.id) != entry.region }.map { it.tile.id }

    /**
     * 測試規則的可調整行為。
     *
     * @property scoring 動作即時收付；null 時不收付。
     * @property robbingPlayerIds 可以搶花的玩家。
     * @property ignoresEmptyWall 摸到花時是否不檢查牌山，直接要求移出。
     * @property setAsideChangesOthers 移出手牌的結果是否違規改動其他玩家。
     * @property hooksSendStalePatch 摸牌與鳴牌後的掛勾是否回傳舊值已過時的變更。
     * @property countsFlowersInTableComponent 移出花牌時是否把已移出的張數寫進桌況組件。
     * @property replacementCount 每次補牌從牌山尾端補的張數。
     * @property robbableTileIds 可以被搶的花；空集合代表每張花都可以搶。
     * @property robsKans [robbingPlayerIds] 是否也可以搶槓。
     * @property omitsMultiRonAbortiveDrawReason 一炮多響判定流局時是否違規不提供流局原因。
     * @property setsAsideOldestFlower 是否違規移出手上最早的花，而不是摸牌位上的花。
     * @property changesSetAsideFace 是否違規把要移出的牌換成另一種牌面。
     * @property askedSources 依詢問順序記錄的牌與來源。
     * @property declaresFlowers 摸牌位上是花時，玩家是否可以自己宣告移出。
     * @property pausesAfterSetAside 完成至少一步後，下一張花是否要求先進入規則選擇。
     */
    private class TestBehavior(
        val scoring: ((ActionScoringContext) -> ActionScoring?)? = null,
        val robbingPlayerIds: MutableSet<Uuid> = mutableSetOf(),
        val ignoresEmptyWall: Boolean = false,
        val setAsideChangesOthers: Boolean = false,
        val hooksSendStalePatch: Boolean = false,
        val countsFlowersInTableComponent: Boolean = false,
        val replacementCount: Int = 1,
        val robbableTileIds: MutableSet<Uuid> = mutableSetOf(),
        val robsKans: Boolean = false,
        val omitsMultiRonAbortiveDrawReason: Boolean = false,
        val setsAsideOldestFlower: Boolean = false,
        val changesSetAsideFace: Boolean = false,
        val askedSources: MutableList<Pair<Uuid, DrawSource>> = mutableListOf(),
        val declaresFlowers: Boolean = false,
        val pausesAfterSetAside: Boolean = false,
    )

    /**
     * 測試用的三人桌與正式用例。
     *
     * @param behavior 測試規則的行為。
     */
    private class Fixture(behavior: TestBehavior = TestBehavior()) {
        /** 對局身分。 */
        val gameId = Uuid.random()

        /** 目前輪到摸牌的玩家。 */
        val drawerId = Uuid.random()

        /** 下家。 */
        val southId = Uuid.random()

        /** 第三位玩家。 */
        val westId = Uuid.random()

        /** 保存狀態與歷史的替身。 */
        val repository = FakeGameRepository()

        /** 記錄廣播動作的替身。 */
        val events = FakeGameEventPublisher()

        /** 記錄呈現要求的替身。 */
        val presentation = FakeGamePresentationPublisher()

        /** 透過公開 extension bootstrap 登記的規則。 */
        private val registries = bundledCoreExtensionRegistries(additionalExtensions = listOf(FlowerExtension(behavior)))

        /** 規則與組件共用同一份登記。 */
        private val modules = registries.moduleRegistry

        /** 共用的快照同步。 */
        private val snapshots = GameSnapshotSynchronizer(repository, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl(modules, componentRegistry = registries.componentRegistry))

        /** 正式摸牌用例。 */
        val draw = DrawTileUseCase(repository, modules, snapshots, events, presentation, componentRegistry = registries.componentRegistry)

        /** 正式暗槓與加槓用例。 */
        val kan = DeclareKanUseCase(repository, modules, snapshots, events, presentation, componentRegistry = registries.componentRegistry)

        /** 正式宣告移出用例。 */
        val declareSetAside = DeclareTileSetAsideUseCase(repository, modules, snapshots, events, presentation, componentRegistry = registries.componentRegistry)

        /** 正式搶和回應用例。 */
        val respondToRobbing = RespondToRobbingUseCase(
            gameRepository = repository,
            moduleRegistry = modules,
            snapshotSynchronizer = snapshots,
            eventPublisher = events,
            presentationPublisher = presentation,
            winPresentationHandoff = WinPresentationHandoff(),
            winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry(bundledCoreExtensionRegistries().componentRegistry).apply { freeze() },

            componentRegistry = registries.componentRegistry,
        )

        /** 正式捨牌反應用例。 */
        val respondToDiscard = RespondToDiscardUseCase(
            gameRepository = repository,
            moduleRegistry = modules,
            snapshotSynchronizer = snapshots,
            handSortPreferenceStore = HandSortPreferenceStore(),
            eventPublisher = events,
            presentationPublisher = presentation,
            winPresentationHandoff = WinPresentationHandoff(),
            winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry(bundledCoreExtensionRegistries().componentRegistry).apply { freeze() },
            postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry().apply { freeze() },

            componentRegistry = registries.componentRegistry,
        )

        /**
         * 建立輪到 [drawerId] 摸牌的桌況。
         *
         * @param drawerHand 摸牌玩家的手牌。
         * @param wall 活牌，依摸牌順序排列；補牌從尾端取。
         * @param config 測試規則的配置。
         * @return 測試桌況。
         */
        fun table(
            drawerHand: Hand = Hand(tiles = fillers(13)),
            wall: List<IdentifiedTile> = listOf(tile(ORDINARY)),
            config: FlowerRuleConfig = FlowerRuleConfig(),
        ): TableState = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(
                player(drawerId, Wind.EAST, drawerHand),
                player(southId, Wind.SOUTH, Hand(tiles = fillers(13))),
                player(westId, Wind.WEST, Hand(tiles = fillers(13))),
            ),
            config = config,
            tileWall = TileWall(wall),
            currentPlayerIndex = 0,
        )

        /**
         * 建立 [southId] 可以碰 [drawerId] 捨牌的桌況。
         *
         * @param discarded 被碰的捨牌。
         * @param matching 下家手上兩張相同的牌。
         * @return 測試桌況。
         */
        fun ponTable(discarded: IdentifiedTile, matching: List<IdentifiedTile>): TableState = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(
                player(drawerId, Wind.EAST, Hand(tiles = fillers(12))).copy(discardPile = FakeDiscardPile().discardTile(discarded)),
                player(southId, Wind.SOUTH, Hand(tiles = matching + fillers(11))),
                player(westId, Wind.WEST, Hand(tiles = fillers(13))),
            ),
            config = FlowerRuleConfig(),
            tileWall = TileWall(listOf(tile(ORDINARY))),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(drawerId, discarded.id, setOf(southId), ronPlayerIds = emptySet(), id = Uuid.random()),
        )

        /** @return 目前保存的摸牌玩家。 */
        suspend fun drawer(): MahjongPlayer = repository.getTableState(gameId)!!.players.first { it.id == drawerId }

        /** @return 牌譜依序記錄的已接受動作。 */
        fun recordedActions(): List<GameAction> = repository.historyDrafts.mapNotNull { (it.fact as? HistoryFact.ActionAccepted)?.action }

        /** @return 真正提交的交易所產生的公開動作事件。 */
        fun actionEvents(): List<ActionsPerformedEvent> = repository.committedFacts
            .flatMap(GameEventProjector(modules)::project).filterIsInstance<ActionsPerformedEvent>()

        /** @return 摸牌玩家目前可以宣告的暗槓。 */
        suspend fun legalClosedKan(): GameAction.Kan {
            val state = repository.getTableState(gameId)!!
            return modules.getModule(state.config).createLegalActionValidator()
                .getLegalActions(LegalActionContext.ownTurn(state, drawerId))
                .filterIsInstance<GameAction.Kan>()
                .single { it.type == GameAction.KanType.CLOSED_KAN }
        }
    }

    /**
     * 只記錄點數池餘額的測試組件值。
     *
     * @property balance 點數池餘額。
     */
    private data class TestPot(val balance: Int)

    /**
     * 測試規則的配置，委派日麻設定。
     *
     * @property delegate 提供與本測試無關的共用設定。
     */
    private data class FlowerRuleConfig(
        val delegate: RiichiRuleConfig = RiichiRuleConfig(),
    ) : RiichiFamilyRuleConfig by delegate

    /**
     * 透過公開 extension bootstrap 登記測試規則。
     *
     * @property behavior 測試規則的行為。
     */
    private class FlowerExtension(private val behavior: TestBehavior) : MahjongExtension {
        override val id: String = "test:flowers"

        override fun registerComponents(registry: ComponentRegistrar) {
            registry.register(testPotComponent)
            registry.registerLifecycle(
                definition = ComponentLifecycleDefinition(
                    component = testPotComponent,
                    ownerType = BuiltInComponentOwners.TABLE,
                    appliesTo = { _, config -> config is FlowerRuleConfig },
                    required = false,
                    roundTransition = ComponentRoundTransition.RESET,
                    initialValue = { null },
                ),
            )
        }

        override fun registerRuleModules(registry: MahjongModuleRegistrar) {
            registry.register(
                definition = MahjongModuleDefinition(FlowerRuleConfig::class, id) { config, moduleId ->
                    FlowerRule(RiichiRuleModule(moduleId, config.delegate), behavior)
                },
            )
        }

        override fun registerNetworkDtos(registries: NetworkDtoRegistrars) = Unit

        override fun registerPersistenceDtos(registries: PersistenceRegistrars) = Unit
    }

    /**
     * 摸到花自動移出、從牌山尾端補牌的測試規則，其他行為委派給日麻。
     *
     * @property delegate 提供測試未覆寫的行為；型別參數只用於已覆寫的 [config]。
     * @property behavior 測試規則的行為。
     */
    @Suppress("UNCHECKED_CAST")
    private class FlowerRule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
        private val behavior: TestBehavior,
    ) : MahjongRuleModule<FlowerRuleConfig> by (delegate as MahjongRuleModule<FlowerRuleConfig>) {
        override val config: FlowerRuleConfig = FlowerRuleConfig(delegate.config)

        override fun createWallRevealPolicy(): WallRevealPolicy = NoOpWallRevealPolicy

        override fun createDrawFollowUpPolicy(): DrawFollowUpPolicy = DrawFollowUpPolicy { context ->
            behavior.askedSources += context.drawnTile.id to context.source
            when {
                context.drawnTile.tile != FLOWER -> DrawFollowUpDecision.None
                context.tableState.tileWall.remainingCount == 0 && !behavior.ignoresEmptyWall -> DrawFollowUpDecision.EndRound(NoReplacementReason)
                behavior.pausesAfterSetAside && context.completedStepCount > 0 -> DrawFollowUpDecision.SetAsideThenDecide(FLOWER_ACTION)
                else -> DrawFollowUpDecision.SetAside(FLOWER_ACTION)
            }
        }

        override fun createSupplementalDrawPolicy(): SupplementalDrawPolicy = SupplementalDrawPolicy { context ->
            val candidate = context.tableStateAfterAction
            var wall = candidate.tileWall
            val replacements = List(behavior.replacementCount) {
                val (replacement, remaining) = wall.drawLast()
                wall = remaining
                replacement ?: return@SupplementalDrawPolicy SupplementalDrawDecision.Rejected(SupplementalDrawReasonIds.WALL_EXHAUSTED)
            }
            SupplementalDrawDecision.Completed(replacements, wall, candidate.reservedWallTiles, ComponentPatch())
        }

        override fun resolveMultiRonAbortiveDraw(): ExhaustiveDrawReason? = if (behavior.omitsMultiRonAbortiveDrawReason) null else delegate.resolveMultiRonAbortiveDraw()

        override fun createActionScoringPolicy(): ActionScoringPolicy = behavior.scoring?.let { scoring -> ActionScoringPolicy(scoring) } ?: NoOpActionScoringPolicy

        override fun tileSetAsideBy(player: MahjongPlayer, action: GameAction.Extension): IdentifiedTile? {
            if (action != FLOWER_ACTION) return null
            val drawnFlower = player.hand.lastDrawn?.takeIf { it.tile == FLOWER }
            return when {
                behavior.setsAsideOldestFlower -> player.hand.standingTiles.firstOrNull { it.tile == FLOWER }
                behavior.changesSetAsideFace -> drawnFlower?.copy(tile = ORDINARY)
                else -> drawnFlower
            }
        }

        override fun applyTileSetAsideAction(
            tableState: TableState,
            actorPlayerId: Uuid,
            action: GameAction.Extension,
        ): TableState? {
            val actor = tableState.players.first { it.id == actorPlayerId }
            val flower = tileSetAsideBy(actor, action) ?: return null
            val flowers = actor.tileAreas["test:flowers"]?.tiles.orEmpty() + flower
            // 移出規則選出的那張牌，其餘立牌（含摸牌位）都併入立牌。
            val remaining = actor.hand.standingTiles.filterNot { it.id == flower.id }
            val updated = actor.copy(
                hand = actor.hand.copy(tiles = remaining, lastDrawn = null),
                tileAreas = actor.tileAreas.withArea("test:flowers", flowers),
            ).recordAction(action)
            return tableState.copy(
                components = if (behavior.countsFlowersInTableComponent) tableState.components.with(testPotComponent, TestPot(flowers.size)) else tableState.components,
                players = tableState.players.map { player ->
                    when {
                        player.id == actorPlayerId -> updated
                        behavior.setAsideChangesOthers -> player.copy(score = player.score + 1)
                        else -> player
                    }
                },
            )
        }

        override fun onPlayerDrew(player: MahjongPlayer): ComponentPatch = if (behavior.hooksSendStalePatch) STALE_PATCH else delegate.onPlayerDrew(player)

        override fun onMeldCompleted(context: MeldCompletedContext): Map<Uuid, ComponentPatch> = if (behavior.hooksSendStalePatch) {
            context.playersAfter.associate { it.id to STALE_PATCH }
        } else {
            delegate.onMeldCompleted(context)
        }

        override fun createLegalActionValidator(): LegalActionValidator {
            val base = delegate.createLegalActionValidator()
            return object : LegalActionValidator by base {
                override fun getLegalActions(context: LegalActionContext): List<GameAction> {
                    val source = context.source
                    if (source is LegalActionSource.Declaration && (source.action == FLOWER_ACTION || behavior.robsKans && source.action is GameAction.Kan)) {
                        val robbable = behavior.robbableTileIds.isEmpty() || source.tile.id in behavior.robbableTileIds
                        return if (context.player.id in behavior.robbingPlayerIds && robbable) {
                            listOf(GameAction.Ron(source.tile.id), GameAction.Pass)
                        } else {
                            listOf(GameAction.Pass)
                        }
                    }
                    val drawnFlower = context.player.hand.lastDrawn?.tile == FLOWER
                    if (source == LegalActionSource.OwnTurn && behavior.declaresFlowers && drawnFlower) {
                        return base.getLegalActions(context) + FLOWER_ACTION
                    }
                    return base.getLegalActions(context)
                }
            }
        }
    }

    /** 測試規則自動移出花牌的動作。 */
    private data object FlowerAction : ExtensionGameAction {
        override val id: String = "test:flower"
    }

    /** 摸到花卻沒有牌可以補時的流局原因。 */
    private data object NoReplacementReason : ExhaustiveDrawReason {
        override val id: String = "test:no_flower_replacement"
    }

    /** 測試共用的牌面與常數。 */
    private companion object {
        /** 預期玩家已有永久振聽、實際沒有的過時變更。 */
        val STALE_PATCH = ComponentPatch(
            removals = setOf(RiichiPlayerComponents.PERMANENT_FURITEN),
            expected = ComponentSet.EMPTY.with(RiichiPlayerComponents.PERMANENT_FURITEN, Unit),
        )

        /** 測試規則獨立保存的點數池。 */
        val testPotComponent = ComponentType(
            id = "test:flower_pot",
            valueClass = TestPot::class,
            copyValue = { it.copy() },
            target = BuiltInComponentOwners.TABLE,
        )

        /** 測試規則當作花牌的牌面。 */
        val FLOWER: Tile = Tile.Honor.White

        /** 一般牌。 */
        val ORDINARY: Tile = Tile.Numeric(Tile.Suit.Character, 5)

        /** 暗槓與碰使用的牌面。 */
        val KAN_TILE: Tile = Tile.Numeric(Tile.Suit.Character, 1)

        /** 自動移出花牌的完整動作。 */
        val FLOWER_ACTION = GameAction.Extension(FlowerAction)

        /** 玩家的初始分數。 */
        const val INITIAL_SCORE: Int = 25000

        /** 兩家同時可以搶和時判定流局的配置。 */
        val ABORTIVE_DRAW_CONFIG = FlowerRuleConfig(
            delegate = RiichiRuleConfig(multiRonPolicy = MultiRonPolicy(doubleRonResolution = RonResolution.ABORTIVE_DRAW, tripleRonResolution = RonResolution.ABORTIVE_DRAW)),
        )

        /**
         * 建立一張實體牌。
         *
         * @param face 牌面。
         * @return 新的實體牌。
         */
        fun tile(face: Tile): IdentifiedTile = FakeIdentifiedTileFactory.create(face)

        /**
         * 建立不會成組的填充牌。
         *
         * @param count 張數。
         * @return 依序排列的填充牌。
         */
        fun fillers(count: Int): List<IdentifiedTile> = List(count) { index ->
            tile(Tile.Numeric(Tile.Suit.entries[index % Tile.Suit.entries.size], 2 + index / Tile.Suit.entries.size % 4 * 2))
        }

        /**
         * 建立測試玩家。
         *
         * @param id 玩家身分。
         * @param wind 座位風。
         * @param hand 手牌。
         * @return 測試玩家。
         */
        fun player(id: Uuid, wind: Wind, hand: Hand): MahjongPlayer = FakeMahjongPlayerFactory.create(initialSeat = wind, id = id, hand = hand).copy(score = INITIAL_SCORE)
    }
}
