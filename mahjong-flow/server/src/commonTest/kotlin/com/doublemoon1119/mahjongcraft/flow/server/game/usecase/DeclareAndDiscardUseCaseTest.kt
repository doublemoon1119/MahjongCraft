package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
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
import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.api.registration.MahjongModuleRegistrar
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction.Extension
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentOwnerType
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRoundTransition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.judgment.TileSelectionRequirement
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationDiscardEffect
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationDiscardPolicy
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTableComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiPotPoints
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealCheckpoint
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealDecision
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealPolicy
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileComponentDescription
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證第三方宣告 policy 能使用共用流程，且未授權的玩家變更會被拒絕。 */
class DeclareAndDiscardUseCaseTest {
    /** 第三方宣告動作。 */
    private data object DeclarationAction : ExtensionGameAction {
        override val id: String = "test:declare"
    }

    /**
     * 建立可重複使用的第三方宣告測試環境。
     *
     * @param malicious 是否要求規則把對局組件當成玩家組件交回。
     * @param cost 本次宣告需支付的點數。
     * @param rejectWallReveal 是否讓宣告後的牌牆公開檢查拒絕。
     * @param mustDiscardAfterMeld 是否模擬剛完成自訂鳴牌、等待捨牌的玩家。
     * @param revealWallTile 是否讓宣告後的牌牆公開檢查公開一張牌山牌。
     * @param tsumoAvailable 宣告者是否同時可以自摸。
     * @param hookMode 放棄自摸與打牌後掛勾的測試行為；null 時不覆寫這兩個掛勾。
     * @param discardComponents 宣告效果寫在新捨牌紀錄上的組件。
     * @param staleTablePatch 宣告效果是否以過時的供託值作為預期舊值。
     */
    private class Fixture(
        malicious: Boolean = false,
        cost: Int = 0,
        rejectWallReveal: Boolean = false,
        mustDiscardAfterMeld: Boolean = false,
        revealWallTile: Boolean = false,
        tsumoAvailable: Boolean = false,
        hookMode: HookMode? = null,
        discardComponents: ComponentSet = ComponentSet.EMPTY,
        staleTablePatch: Boolean = false,
    ) {
        /** 宣告者身分。 */
        val playerId = Uuid.random()

        /** 對局身分。 */
        val gameId = Uuid.random()

        /** 宣告時選取的捨牌。 */
        val tile = FakeIdentifiedTileFactory.create(Tile.Honor.White)

        /** 記錄共用宣告流程實際執行的牌牆公開 checkpoint。 */
        val wallRevealCheckpoints = mutableListOf<WallRevealCheckpoint>()

        /** 宣告前的玩家狀態。 */
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            hand = Hand(lastDrawn = tile),
            discardPile = RiichiDiscardPile(),
        ).copy(score = 100, mustDiscardAfterMeld = mustDiscardAfterMeld)

        /** 保有對局組件的宣告前桌況。 */
        val table: TableState = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            components = riichiTableComponents(potPoints = 2000),
        )

        /** 記錄權威寫入與歷史的測試倉庫。 */
        val repository = FakeGameRepository()

        /** 登記第三方宣告規則的正式模組註冊表。 */
        private val modules = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(RiichiRuleConfig::class, DECLARATION_RULE_ID) { config, _ ->
                    DeclarationRule(
                        delegate = RiichiRuleModule(DECLARATION_RULE_ID, config),
                        malicious = malicious,
                        cost = cost,
                        wallRevealCheckpoints = wallRevealCheckpoints,
                        rejectWallReveal = rejectWallReveal,
                        revealWallTile = revealWallTile,
                        tsumoAvailable = tsumoAvailable,
                        hookMode = hookMode,
                        discardComponents = discardComponents,
                        staleTablePatch = staleTablePatch,
                    )
                },
            )
            freeze()
        }

        /** 接收正式流程事件的替身。 */
        val eventPublisher = FakeGameEventPublisher()

        /** 接收正式呈現要求的替身。 */
        val presentationPublisher = FakeGamePresentationPublisher()

        /** 保存同步快照的替身。 */
        val snapshots = FakeGameSnapshotRepository()

        /** 掛勾與牌牆公開使用同一份組件登記。 */
        private val componentRegistry = bundledCoreExtensionRegistries(additionalExtensions = listOf(hookExtension)).componentRegistry

        /** 使用正式可見性投影的同步服務。 */
        private val snapshotSynchronizer = GameSnapshotSynchronizer(
            repository,
            snapshots,
            GameVisibilityPolicyImpl(modules, componentRegistry = componentRegistry),
        )

        /** 受測的共用宣告用例。 */
        val useCase = DeclareAndDiscardUseCase(
            gameRepository = repository,
            moduleRegistry = modules,
            snapshotSynchronizer = snapshotSynchronizer,
            handSortPreferenceStore = HandSortPreferenceStore(),
            postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry().apply { freeze() },
            eventPublisher = eventPublisher,
            presentationPublisher = presentationPublisher,

            componentRegistry = componentRegistry,
        )
    }

    /** 第三方 policy 的合法效果可以透過共用流程完成宣告與捨牌。 */
    @Test
    fun `third party declaration succeeds through shared flow`() = runTest {
        val fixture = Fixture(cost = 15)
        fixture.repository.setTableState(fixture.table)
        fixture.snapshots.setSnapshot(
            fixture.playerId,
            fixture.table.toSnapshot(setOf(fixture.playerId)),
        )

        val result = fixture.repository.runAsSingleWrite(fixture.gameId) {
            fixture.useCase(
                gameId = fixture.gameId,
                playerId = fixture.playerId,
                action = Extension(DeclarationAction),
                tileId = fixture.tile.id,
            )
        }

        assertIs<Outcome.Success<Unit>>(result)
        val updated = fixture.repository.getTableState(fixture.gameId)!!.players.single()
        assertEquals(listOf(GameAction.Extension(DeclarationAction), GameAction.Discard(fixture.tile.id)), updated.actionHistory)
        assertEquals(listOf(fixture.tile.id), updated.discardPile.entries.map { it.tile.id })
        assertEquals(85, updated.score)
        assertEquals(fixture.table.components, fixture.repository.getTableState(fixture.gameId)?.components)
        val actions = fixture.repository.historyDrafts.map { assertIs<HistoryFact.ActionAccepted>(it.fact).action }
        assertEquals(updated.actionHistory, actions)
        assertEquals(actions, fixture.eventPublisher.getNotifiedActions(fixture.gameId, fixture.playerId, fixture.playerId))
        assertNotNull(fixture.snapshots.getSnapshot(fixture.gameId, fixture.playerId))
        assertEquals(1, fixture.presentationPublisher.getRuleStateUpdateCount(fixture.gameId))
        assertNotNull(fixture.presentationPublisher.getPublishedPlayerTiles(fixture.gameId))
        assertNotNull(fixture.presentationPublisher.getPublishedRoundInfo(fixture.gameId))
        assertEquals(listOf(WallRevealCheckpoint.AFTER_DISCARD_REACTIONS), fixture.wallRevealCheckpoints)
    }

    /** 牌牆公開被規則拒絕時，宣告與捨牌不得留下半套寫入或副作用。 */
    @Test
    fun `rejected post declaration wall reveal is atomic`() = runTest {
        val fixture = Fixture(rejectWallReveal = true)
        fixture.repository.setTableState(fixture.table)

        val result = fixture.useCase(
            gameId = fixture.gameId,
            playerId = fixture.playerId,
            action = Extension(DeclarationAction),
            tileId = fixture.tile.id,
        )

        assertIs<Outcome.Error<*>>(result)
        assertEquals(fixture.table, fixture.repository.getTableState(fixture.gameId))
        assertEquals(listOf(WallRevealCheckpoint.AFTER_DISCARD_REACTIONS), fixture.wallRevealCheckpoints)
        assertTrue(fixture.repository.historyDrafts.isEmpty())
        assertTrue(fixture.eventPublisher.getNotifiedActions(fixture.gameId, fixture.playerId, fixture.playerId).isEmpty())
        assertNull(fixture.snapshots.getSnapshot(fixture.gameId, fixture.playerId))
        assertTrue(fixture.presentationPublisher.getDeclaredGameActions(fixture.gameId).isEmpty())
    }

    /** 宣告後的捨牌會清除自訂鳴牌留下的必須直接捨牌狀態。 */
    @Test
    fun `declaration discard clears must discard after meld`() = runTest {
        val fixture = Fixture(mustDiscardAfterMeld = true)
        fixture.repository.setTableState(fixture.table)

        val result = fixture.useCase(
            gameId = fixture.gameId,
            playerId = fixture.playerId,
            action = Extension(DeclarationAction),
            tileId = fixture.tile.id,
        )

        assertIs<Outcome.Success<Unit>>(result)
        assertEquals(false, fixture.repository.getTableState(fixture.gameId)!!.players.single().mustDiscardAfterMeld)
    }

    /** 宣告後的牌牆公開檢查公開新牌時，與一般捨牌一樣通知桌面呈現。 */
    @Test
    fun `wall tiles revealed after the declaration discard are published`() = runTest {
        val fixture = Fixture(revealWallTile = true)
        fixture.repository.setTableState(fixture.table)

        val result = fixture.useCase(
            gameId = fixture.gameId,
            playerId = fixture.playerId,
            action = Extension(DeclarationAction),
            tileId = fixture.tile.id,
        )

        assertIs<Outcome.Success<Unit>>(result)
        assertEquals(
            setOf(fixture.table.tileWall.getAllTiles().first().id),
            fixture.presentationPublisher.getPublishedDeadWallReveal(fixture.gameId),
        )
    }

    /** 宣告附帶的捨牌與一般捨牌相同：先套用放棄自摸與打牌後的規則掛勾，再套用宣告效果。 */
    @Test
    fun `declaration discard runs the discard hooks before the declaration effect`() = runTest {
        val fixture = Fixture(cost = 15, tsumoAvailable = true, hookMode = HookMode.RECORD_ORDER)
        fixture.repository.setTableState(fixture.table)

        val result = fixture.useCase(
            gameId = fixture.gameId,
            playerId = fixture.playerId,
            action = Extension(DeclarationAction),
            tileId = fixture.tile.id,
        )

        assertIs<Outcome.Success<Unit>>(result)
        val player = fixture.repository.getTableState(fixture.gameId)!!.players.single()
        assertEquals(HookTrail(listOf(DECLINED_WIN_MARK, DISCARDED_MARK, DECLARED_MARK)), player.components[hookTrailComponent])
        assertEquals(100 - 15, player.score)
    }

    /** 放棄自摸或打牌後的掛勾回傳舊值已過時的變更時中止，桌況保持不變。 */
    @Test
    fun `discard hook with a stale component change is rejected atomically`() = runTest {
        val fixture = Fixture(tsumoAvailable = true, hookMode = HookMode.STALE_PATCH)
        fixture.repository.setTableState(fixture.table)

        assertFailsWith<RuleContractViolationException> {
            fixture.useCase(
                gameId = fixture.gameId,
                playerId = fixture.playerId,
                action = Extension(DeclarationAction),
                tileId = fixture.tile.id,
            )
        }
        assertEquals(fixture.table, fixture.repository.getTableState(fixture.gameId))
        assertTrue(fixture.repository.historyDrafts.isEmpty())
    }

    /** 宣告效果的玩家變更帶著其他對象的組件時中止，桌況保持不變。 */
    @Test
    fun `declaration effect with a component of another owner is rejected atomically`() = runTest {
        val fixture = Fixture(malicious = true)
        fixture.repository.setTableState(fixture.table)

        assertFailsWith<RuleContractViolationException> {
            fixture.useCase(
                gameId = fixture.gameId,
                playerId = fixture.playerId,
                action = Extension(DeclarationAction),
                tileId = fixture.tile.id,
            )
        }
        assertEquals(fixture.table, fixture.repository.getTableState(fixture.gameId))
        assertTrue(fixture.repository.historyDrafts.isEmpty())
        assertTrue(fixture.eventPublisher.getNotifiedActions(fixture.gameId, fixture.playerId, fixture.playerId).isEmpty())
        assertNull(fixture.snapshots.getSnapshot(fixture.gameId, fixture.playerId))
        assertTrue(fixture.presentationPublisher.getDeclaredGameActions(fixture.gameId).isEmpty())
    }

    /** 新捨牌紀錄沒有規則寫入的值時，帶上登記的初值。 */
    @Test
    fun `declaration discard record receives registered initial values`() = runTest {
        val fixture = Fixture()
        fixture.repository.setTableState(fixture.table)

        val result = fixture.declare()

        assertIs<Outcome.Success<Unit>>(result)
        val entry = fixture.repository.getTableState(fixture.gameId)!!.players.single().discardPile.entries.single()
        assertEquals(INITIAL_DISCARD_NOTE, entry.components[discardNoteComponent])
    }

    /** 宣告打牌與一般打牌相同：打牌後的掛勾看到的新捨牌紀錄已帶登記的初值。 */
    @Test
    fun `discard hook sees the initialized declaration discard record`() = runTest {
        val fixture = Fixture(hookMode = HookMode.RECORD_DISCARD_NOTE)
        fixture.repository.setTableState(fixture.table)

        val result = fixture.declare()

        assertIs<Outcome.Success<Unit>>(result)
        val player = fixture.repository.getTableState(fixture.gameId)!!.players.single()
        assertEquals(HookTrail(listOf("note:$INITIAL_DISCARD_NOTE", DECLARED_MARK)), player.components[hookTrailComponent])
    }

    /** 規則寫在新捨牌紀錄上的值優先於登記的初值。 */
    @Test
    fun `declaration discard record keeps the value written by the rule`() = runTest {
        val fixture = Fixture(discardComponents = ComponentSet.EMPTY.with(discardNoteComponent, "declared"))
        fixture.repository.setTableState(fixture.table)

        val result = fixture.declare()

        assertIs<Outcome.Success<Unit>>(result)
        val entry = fixture.repository.getTableState(fixture.gameId)!!.players.single().discardPile.entries.single()
        assertEquals("declared", entry.components[discardNoteComponent])
    }

    /** 新捨牌紀錄帶著不屬於目前規則的資料時中止，桌況保持不變。 */
    @Test
    fun `declaration discard record with a component of another rule is rejected atomically`() = runTest {
        val fixture = Fixture(discardComponents = ComponentSet.EMPTY.with(otherRuleDiscardComponent, true))
        fixture.repository.setTableState(fixture.table)

        val failure = assertFailsWith<RuleContractViolationException> { fixture.declare() }

        assertTrue(failure.message.orEmpty().contains("declarationDiscardPolicy"))
        assertEquals(fixture.table, fixture.repository.getTableState(fixture.gameId))
        assertTrue(fixture.repository.historyDrafts.isEmpty())
    }

    /** 打牌後掛勾寫入不屬於目前規則的玩家資料時，在掛勾當下就中止。 */
    @Test
    fun `discard hook writing a player component of another rule is rejected at the hook`() = runTest {
        val fixture = Fixture(hookMode = HookMode.WRITE_OTHER_RULE_COMPONENT)
        fixture.repository.setTableState(fixture.table)

        val failure = assertFailsWith<RuleContractViolationException> { fixture.declare() }

        assertTrue(failure.message.orEmpty().contains("onPlayerDiscarded"))
        assertEquals(fixture.table, fixture.repository.getTableState(fixture.gameId))
    }

    /** 宣告效果的對局變更帶著過時的舊值時中止，不覆寫目前的供託。 */
    @Test
    fun `stale declaration table patch is rejected atomically`() = runTest {
        val fixture = Fixture(staleTablePatch = true)
        fixture.repository.setTableState(fixture.table)

        val failure = assertFailsWith<RuleContractViolationException> { fixture.declare() }

        assertTrue(failure.message.orEmpty().contains("Stale component patch"))
        assertEquals(fixture.table, fixture.repository.getTableState(fixture.gameId))
    }

    /**
     * 以測試宣告動作打出選取的牌。
     *
     * @return 用例結果。
     */
    private suspend fun Fixture.declare(): Outcome<Unit, *> = useCase(
        gameId = gameId,
        playerId = playerId,
        action = Extension(DeclarationAction),
        tileId = tile.id,
    )

    /**
     * 記錄規則額外翻開的牌山牌張。
     *
     * @property revealedTileIds 已公開的牌山牌張 ID。
     */
    private data class RevealedWallState(val revealedTileIds: Set<Uuid>)

    /** 放棄自摸與打牌後掛勾的測試行為。 */
    private enum class HookMode {
        /** 在規則狀態依序記下執行過的掛勾。 */
        RECORD_ORDER,

        /** 違反契約，回傳舊值已過時的變更。 */
        STALE_PATCH,

        /** 打牌後寫入只屬於其他規則的玩家資料。 */
        WRITE_OTHER_RULE_COMPONENT,

        /** 打牌後記下新捨牌紀錄當時帶的標記。 */
        RECORD_DISCARD_NOTE,
    }

    /**
     * 依序記錄規則掛勾與宣告效果執行順序的測試規則狀態。
     *
     * @property marks 依執行順序排列的記號。
     */
    private data class HookTrail(val marks: List<String>)

    /** [HookTrail] 使用的記號。 */
    private companion object {
        /** 記錄規則額外翻開的牌山牌張。 */
        val wallRevealComponent = ComponentType(
            id = "test:wall_reveals",
            valueClass = RevealedWallState::class,
            copyValue = { it.copy(revealedTileIds = it.revealedTileIds.toSet()) },
            target = BuiltInComponentOwners.TABLE,
        )

        /** 掛勾順序的私人測試資料，不投影到觀察者快照。 */
        val hookTrailComponent = ComponentType(
            id = "test:hook_trail",
            valueClass = HookTrail::class,
            copyValue = { it.copy(marks = it.marks.toList()) },
            target = BuiltInComponentOwners.PLAYER,
        )

        /** 新捨牌紀錄的測試資料，有登記初值。 */
        val discardNoteComponent = ComponentType(
            id = "test:discard_note",
            valueClass = String::class,
            copyValue = { it },
            target = BuiltInComponentOwners.DISCARD_ENTRY,
        )

        /** 只適用其他規則的捨牌紀錄資料。 */
        val otherRuleDiscardComponent = ComponentType(
            id = "test:other_rule_discard",
            valueClass = Boolean::class,
            copyValue = { it },
            target = BuiltInComponentOwners.DISCARD_ENTRY,
        )

        /** 只適用其他規則的玩家資料。 */
        val otherRulePlayerComponent = ComponentType(
            id = "test:other_rule_player",
            valueClass = Boolean::class,
            copyValue = { it },
            target = BuiltInComponentOwners.PLAYER,
        )

        /** 新捨牌紀錄的登記初值。 */
        const val INITIAL_DISCARD_NOTE = "initial"

        /** 受測宣告規則的識別碼。 */
        const val DECLARATION_RULE_ID = "test:declaration"

        /** 透過正式登記入口加入受測組件與生命週期。 */
        val hookExtension = object : MahjongExtension {
            override val id: String = "test:hook_trail_extension"
            override fun registerRuleModules(registry: MahjongModuleRegistrar) = Unit
            override fun registerNetworkDtos(registries: NetworkDtoRegistrars) = Unit
            override fun registerPersistenceDtos(registries: PersistenceRegistrars) = Unit
            override fun registerComponents(registry: ComponentRegistrar) {
                registry.register(hookTrailComponent)
                registry.register(wallRevealComponent)
                registry.register(discardNoteComponent)
                registry.register(otherRuleDiscardComponent)
                registry.register(otherRulePlayerComponent)
                registry.registerLifecycle(lifecycle(hookTrailComponent, BuiltInComponentOwners.PLAYER, appliesToDeclarationRule = true))
                registry.registerLifecycle(lifecycle(wallRevealComponent, BuiltInComponentOwners.TABLE, appliesToDeclarationRule = true))
                registry.registerLifecycle(
                    lifecycle(
                        component = discardNoteComponent,
                        ownerType = BuiltInComponentOwners.DISCARD_ENTRY,
                        appliesToDeclarationRule = true,
                        initialValue = INITIAL_DISCARD_NOTE,
                    ),
                )
                registry.registerLifecycle(lifecycle(otherRuleDiscardComponent, BuiltInComponentOwners.DISCARD_ENTRY, appliesToDeclarationRule = false))
                registry.registerLifecycle(lifecycle(otherRulePlayerComponent, BuiltInComponentOwners.PLAYER, appliesToDeclarationRule = false))
            }
        }

        /**
         * 建立只在指定規則適用的選用組件生命週期。
         *
         * @param O 對象型別。
         * @param V 值型別。
         * @param component 組件定義。
         * @param ownerType 對象定義。
         * @param appliesToDeclarationRule 是否適用受測的宣告規則；否則只適用其他規則。
         * @param initialValue 新對象的初值；null 時不建立。
         * @return 每局重建的生命週期。
         */
        fun <O : Any, V : Any> lifecycle(
            component: ComponentType<V>,
            ownerType: ComponentOwnerType<O>,
            appliesToDeclarationRule: Boolean,
            initialValue: V? = null,
        ): ComponentLifecycleDefinition<O, V> = ComponentLifecycleDefinition(
            component = component,
            ownerType = ownerType,
            appliesTo = { ruleModuleId, _ -> (ruleModuleId == DECLARATION_RULE_ID) == appliesToDeclarationRule },
            required = false,
            roundTransition = ComponentRoundTransition.RESET,
            initialValue = { initialValue },
        )

        /** 放棄自摸掛勾的記號。 */
        const val DECLINED_WIN_MARK = "declined_win"

        /** 打牌後掛勾的記號。 */
        const val DISCARDED_MARK = "discarded"

        /** 宣告效果的記號。 */
        const val DECLARED_MARK = "declared"

        /**
         * 建立在玩家的 [HookTrail] 後面加上一個記號的變更。
         *
         * @param mark 要加上的記號。
         * @return 帶著目前記號作為舊值的變更。
         */
        fun MahjongPlayer.markPatch(mark: String): ComponentPatch = ComponentPatch.basedOn(
            current = components,
            values = ComponentSet.EMPTY.with(hookTrailComponent, HookTrail(components[hookTrailComponent]?.marks.orEmpty() + mark)),
        )

        /** 預期玩家已有記號、實際沒有的過時變更。 */
        val STALE_MARK_PATCH = ComponentPatch(
            removals = setOf(hookTrailComponent),
            expected = ComponentSet.EMPTY.with(hookTrailComponent, HookTrail(listOf("stale"))),
        )
    }

    /**
     * 只覆寫第三方宣告接點，其餘規則行為委派給日麻模組。
     *
     * @property delegate 提供其他純規則能力的模組。
     * @property malicious 是否故意把對局組件當成玩家組件交回。
     * @property cost 宣告的實際成本。
     * @property wallRevealCheckpoints 記錄共用流程實際執行的牌牆公開檢查點。
     * @property rejectWallReveal 是否由規則拒絕本次牌牆公開檢查。
     * @property revealWallTile 是否在牌牆公開檢查公開牌山第一張牌。
     * @property tsumoAvailable 合法動作是否包含自摸。
     * @property hookMode 放棄自摸與打牌後掛勾的測試行為；null 時沿用日麻掛勾。
     * @property discardComponents 宣告效果寫在新捨牌紀錄上的組件。
     * @property staleTablePatch 宣告效果是否以過時的供託值作為預期舊值。
     */
    private class DeclarationRule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
        private val malicious: Boolean,
        private val cost: Int,
        private val wallRevealCheckpoints: MutableList<WallRevealCheckpoint>,
        private val rejectWallReveal: Boolean,
        private val revealWallTile: Boolean,
        private val tsumoAvailable: Boolean,
        private val hookMode: HookMode?,
        private val discardComponents: ComponentSet,
        private val staleTablePatch: Boolean,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        override fun describeTileComponents(tableState: TableState): List<TileComponentDescription> = delegate.describeTileComponents(tableState) + tableState.components[wallRevealComponent]?.revealedTileIds.orEmpty().map { tileId ->
            TileComponentDescription(
                tileId = tileId,
                components = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public)
                    .with(BuiltInTileComponents.POSE, TilePose.FACE_UP),
            )
        }

        override fun createWallRevealPolicy(): WallRevealPolicy = WallRevealPolicy { context ->
            wallRevealCheckpoints += context.checkpoint
            when {
                rejectWallReveal -> WallRevealDecision.Rejected("test:wall_reveal_rejected")
                revealWallTile -> context.tableState.tileWall.getAllTiles().first().id.let { revealedId ->
                    WallRevealDecision.Updated(
                        tableComponents = ComponentPatch(values = ComponentSet.EMPTY.with(wallRevealComponent, RevealedWallState(setOf(revealedId)))),
                        newlyRevealedTileIds = setOf(revealedId),
                    )
                }
                else -> WallRevealDecision.NoChange
            }
        }

        override fun onPlayerDeclinedWin(player: MahjongPlayer): ComponentPatch = when (hookMode) {
            HookMode.RECORD_ORDER -> player.markPatch(DECLINED_WIN_MARK)
            HookMode.STALE_PATCH -> STALE_MARK_PATCH
            HookMode.WRITE_OTHER_RULE_COMPONENT, HookMode.RECORD_DISCARD_NOTE -> ComponentPatch()
            null -> delegate.onPlayerDeclinedWin(player)
        }

        override fun onPlayerDiscarded(player: MahjongPlayer): ComponentPatch = when (hookMode) {
            HookMode.RECORD_ORDER -> player.markPatch(DISCARDED_MARK)
            HookMode.STALE_PATCH -> ComponentPatch()
            HookMode.WRITE_OTHER_RULE_COMPONENT -> ComponentPatch(values = ComponentSet.EMPTY.with(otherRulePlayerComponent, true))
            HookMode.RECORD_DISCARD_NOTE -> player.markPatch("note:${player.discardPile.entries.last().components[discardNoteComponent]}")
            null -> delegate.onPlayerDiscarded(player)
        }

        override fun createLegalActionValidator(): LegalActionValidator = object : LegalActionValidator {
            override fun getLegalActions(context: LegalActionContext): List<GameAction> = listOfNotNull(Extension(DeclarationAction), GameAction.Tsumo.takeIf { tsumoAvailable })

            override fun tileSelectionRequirement(
                tableState: TableState,
                player: MahjongPlayer,
                action: GameAction,
            ): TileSelectionRequirement? = if (action is Extension && action.value == DeclarationAction) {
                TileSelectionRequirement(setOf(player.hand.lastDrawn!!.id))
            } else {
                null
            }
        }

        override fun declarationDiscardPolicy(): DeclarationDiscardPolicy = DeclarationDiscardPolicy { state, player, _, _ ->
            DeclarationDiscardEffect(
                tableComponents = if (staleTablePatch) {
                    ComponentPatch(
                        values = ComponentSet.EMPTY.with(RiichiTableComponents.POT_POINTS, 0),
                        expected = ComponentSet.EMPTY.with(RiichiTableComponents.POT_POINTS, state.riichiPotPoints() + 1000),
                    )
                } else {
                    ComponentPatch()
                },
                cost = cost,
                playerComponents = when {
                    malicious -> ComponentPatch(values = ComponentSet.EMPTY.with(wallRevealComponent, RevealedWallState(emptySet())))
                    player.components[hookTrailComponent] != null -> player.markPatch(DECLARED_MARK)
                    else -> ComponentPatch()
                },
                discardComponents = discardComponents,
            )
        }
    }
}
