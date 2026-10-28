package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.HandTileDescription
import com.doublemoon1119.mahjongcraft.logic.module.HandTilePose
import com.doublemoon1119.mahjongcraft.logic.module.HandTileState
import com.doublemoon1119.mahjongcraft.logic.module.HandTileVisibility
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPendingKanDoraReveal
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.visibleTiles
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [GameVisibilityPolicyImpl] 的觀看權限與手牌可見範圍測試。 */
class GameVisibilityPolicyTest {
    /** 使用內建規則建立快照的受測政策。 */
    private val policy = GameVisibilityPolicyImpl(MahjongModuleRegistryImpl().apply { registerBundledRuleModules() })

    /** 他家玩家取得的快照會帶出拔北牌，未拔北的玩家則為空清單。 */
    @Test
    fun `snapshot carries tiles every player set aside`() {
        val north = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val puller = FakeMahjongPlayerFactory.create(playerRuleState = RiichiPlayerState(nukiDoraTiles = listOf(north)))
        val others = List(2) { FakeMahjongPlayerFactory.create(playerRuleState = RiichiPlayerState()) }
        val game = Game(
            tableState = FakeTableStateFactory.create(players = listOf(puller) + others, config = ThreePlayerRiichiRuleConfig()),
            flowConfig = GameFlowConfig(),
        )

        val snapshot = policy.snapshotFor(game, others.first().id)

        assertEquals(listOf(north), snapshot.players.single { it.id == puller.id }.setAsideTiles)
        assertIs<RiichiPlayerStateSnapshot>(snapshot.players.single { it.id == puller.id }.playerRuleState)
        assertTrue(snapshot.players.filter { it.id != puller.id }.all { it.setAsideTiles.isEmpty() })
    }

    /** 參與玩家只能看見自己的手牌，不受旁觀者設定影響。 */
    @Test
    fun `player sees only own hand`() {
        val playerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val game = createGame(playerId, otherPlayerId)

        val snapshot = policy.snapshotFor(game, playerId)

        assertNotNull(snapshot.players.single { it.id == playerId }.hand.standingTiles.single().tile)
        assertNull(snapshot.players.single { it.id == otherPlayerId }.hand.standingTiles.single().tile)
    }

    /** 相同公開局面下，其他玩家的手牌內容、順序與私有振聽不應影響觀察者快照。 */
    @Test
    fun `hidden hand differences do not change another players snapshot`() {
        val observerId = Uuid.random()
        val hiddenPlayerId = Uuid.random()
        val firstId = Uuid.random()
        val secondId = Uuid.random()
        val observerTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val hiddenPlayer = FakeMahjongPlayerFactory.create(
            id = hiddenPlayerId,
            initialSeat = Wind.SOUTH,
            hand = Hand(
                mutableListOf(
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1), firstId),
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 2), secondId),
                ),
            ),
            playerRuleState = RiichiPlayerState(),
        )
        val baseGame = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(
                    FakeMahjongPlayerFactory.create(id = observerId, hand = Hand(mutableListOf(observerTile))),
                    hiddenPlayer,
                ),
                config = RiichiRuleConfig(),
            ),
            flowConfig = GameFlowConfig(),
        )
        val changedHiddenPlayer = hiddenPlayer.copy(
            hand = Hand(
                mutableListOf(
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9), secondId),
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 8), firstId),
                ),
            ),
            playerRuleState = RiichiPlayerState(isPermanentlyFuriten = true),
        )
        val changedGame = baseGame.copy(
            tableState = baseGame.tableState.copy(
                players = baseGame.tableState.players.map { player ->
                    if (player.id == hiddenPlayerId) changedHiddenPlayer else player
                },
            ),
        )

        val firstSnapshot = policy.snapshotFor(baseGame, observerId)
        val changedSnapshot = policy.snapshotFor(changedGame, observerId)

        assertEquals(firstSnapshot, changedSnapshot)
        assertEquals(observerTile.tile, changedSnapshot.players.first { it.id == observerId }.hand.standingTiles.single().tile)
        assertEquals(
            firstSnapshot.players.first { it.id == hiddenPlayerId }.hand.standingTiles.map { it.id },
            changedSnapshot.players.first { it.id == hiddenPlayerId }.hand.standingTiles.map { it.id },
        )
    }

    /** 預設與明確的預設直立描述產生相同觀察者資料。 */
    @Test
    fun `default descriptions do not alter visibility or pose`() {
        val first = Uuid.random()
        val second = Uuid.random()
        val game = createGame(first, second)
        val tileId = game.tableState.players.last().hand.standingTiles.single().id
        val explicit = policyFor(listOf(HandTileDescription(tileId, HandTileVisibility.Default, HandTilePose.UPRIGHT)))
        assertTrue(RiichiRuleModule("test:default", RiichiRuleConfig()).describeHandTiles(game.tableState).isEmpty())
        assertEquals(policyFor().snapshotFor(game, first), explicit.snapshotFor(game, first))
    }

    /** 同種牌只公開指定 UUID，公開但直立不會附帶平放要求。 */
    @Test
    fun `rule can reveal one tile among identical tile types without laying it down`() {
        val observer = Uuid.random()
        val owner = Uuid.random()
        val first = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val second = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val game = createGame(observer, owner).let { game ->
            game.copy(
                tableState = game.tableState.copy(
                    players = game.tableState.players.map { player ->
                        if (player.id == owner) player.copy(hand = Hand(tiles = listOf(first, second))) else player
                    },
                ),
            )
        }
        val snapshot = policyFor(listOf(HandTileDescription(first.id, HandTileVisibility.Public, HandTilePose.UPRIGHT)))
            .snapshotFor(game, observer)
        val hand = snapshot.players.single { it.id == owner }.hand
        assertEquals(first.tile, hand.standingTiles.single { it.id == first.id }.tile)
        assertNull(hand.standingTiles.single { it.id == second.id }.tile)
        assertTrue(snapshot.handTileStates.isEmpty())
        assertTrue(snapshot.revealedHandTiles.isEmpty())
        assertTrue(snapshot.visibleTiles().any { it.id == first.id })
        assertTrue(snapshot.visibleTiles().none { it.id == second.id })
    }

    /** 平放與隱藏立牌可存在於同一位玩家手中，不把整手視為已公開。 */
    @Test
    fun `one face up tile does not reveal or lay down the rest of the hand`() {
        val observer = Uuid.random()
        val owner = Uuid.random()
        val first = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val second = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val game = createGame(observer, owner).let { game ->
            game.copy(
                tableState = game.tableState.copy(
                    players = game.tableState.players.map { player ->
                        if (player.id == owner) player.copy(hand = Hand(tiles = listOf(first), lastDrawn = second)) else player
                    },
                ),
            )
        }
        val snapshot = policyFor(listOf(HandTileDescription(first.id, HandTileVisibility.Public, HandTilePose.FACE_UP)))
            .snapshotFor(game, observer)
        val hand = snapshot.players.single { it.id == owner }.hand
        assertEquals(listOf(HandTileState(first.id, HandTilePose.FACE_UP)), snapshot.handTileStates)
        assertEquals(first.tile, hand.standingTiles.single { it.id == first.id }.tile)
        assertNull(hand.lastDrawn?.tile)
    }

    /** 規則公開牌面不能讓未獲觀戰資格的讀取者取得額外資訊。 */
    @Test
    fun `public hand descriptions do not grant spectator permission`() {
        val game = createGame(
            Uuid.random(),
            Uuid.random(),
            flowConfig = GameFlowConfig(spectatingPolicy = SpectatingPolicy.DISABLED),
        )
        val observer = Uuid.random()
        val tile = game.tableState.players.last().hand.standingTiles.single()
        val policy = policyFor(listOf(HandTileDescription(tile.id, HandTileVisibility.Public, HandTilePose.FACE_UP)))
        assertEquals(policyFor(emptyList()).snapshotFor(game, observer), policy.snapshotFor(game, observer))
    }

    /** 公開牌對隱藏手牌的合法旁觀者仍公開，其他牌保持隱藏。 */
    @Test
    fun `hidden spectator sees only rule public tiles`() {
        val owner = Uuid.random()
        val game = createGame(Uuid.random(), owner).let {
            it.copy(flowConfig = it.flowConfig.copy(spectatingPolicy = SpectatingPolicy.ENABLED, spectatorHandVisibility = SpectatorHandVisibility.HIDDEN))
        }
        val tile = game.tableState.players.single { it.id == owner }.hand.standingTiles.single()
        val snapshot = policyFor(listOf(HandTileDescription(tile.id, HandTileVisibility.Public, HandTilePose.UPRIGHT)))
            .snapshotFor(game, Uuid.random())
        assertEquals(tile.tile, snapshot.players.single { it.id == owner }.hand.standingTiles.single().tile)
        assertTrue(snapshot.players.filterNot { it.id == owner }.flatMap { it.hand.standingTiles }.all { it.tile == null })
    }

    /** 錯誤牌 UUID 與非公開平放由同一驗證路徑拒絕。 */
    @Test
    fun `invalid descriptions fail with the rule contract reason`() {
        val observer = Uuid.random()
        val game = createGame(observer, Uuid.random())
        val tile = game.tableState.players.last().hand.standingTiles.single().id
        val invalid = listOf(
            listOf(HandTileDescription(Uuid.random(), HandTileVisibility.Public, HandTilePose.UPRIGHT)),
            listOf(HandTileDescription(tile, HandTileVisibility.Default, HandTilePose.FACE_UP)),
            List(2) { HandTileDescription(tile, HandTileVisibility.Public, HandTilePose.UPRIGHT) },
        )
        invalid.forEach { descriptions ->
            val failure = assertFailsWith<RuleContractViolationException> { policyFor(descriptions).snapshotFor(game, observer) }
            assertEquals(GameAbortReasonIds.RULE_EXECUTION_FAILED, failure.reasonId)
        }
    }

    /** 逐張公開牌面不會繞過暗槓既有的保密規則。 */
    @Test
    fun `rule tile visibility does not disclose a closed kan`() {
        val observerId = Uuid.random()
        val otherId = Uuid.random()
        val kanTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.East) }
        val standingTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val hand = Hand(tiles = kanTiles + standingTile).call(
            type = MeldType.CLOSED_KAN,
            tiles = kanTiles,
            direction = RelativeDirection.Self,
            publiclyRevealedTileIds = emptySet(),
            faceDownTileIds = emptySet(),
        )
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(
                    FakeMahjongPlayerFactory.create(id = observerId),
                    FakeMahjongPlayerFactory.create(id = otherId, hand = hand),
                ),
                config = TaiwanRuleConfig(),
            ),
            flowConfig = GameFlowConfig(),
        )

        val snapshot = taiwanPolicyFor(listOf(HandTileDescription(standingTile.id, HandTileVisibility.Public, HandTilePose.UPRIGHT))).snapshotFor(game, observerId)
        val meld = snapshot.players.single { it.id == otherId }.hand.melds.single()

        assertTrue(meld.tiles.all { it.tile == null })
    }

    /** 未覆寫規則投影 hook 時，其他玩家狀態與規則動態狀態預設都不公開。 */
    @Test
    fun `default rule projection hides player and dynamic state`() {
        val config = RiichiRuleConfig()
        val registry = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(RiichiRuleConfig::class, "test:default-projection") { moduleConfig, id ->
                    DefaultProjectionRuleModule(RiichiRuleModule(id, moduleConfig))
                },
            )
            freeze()
        }
        val module = GameVisibilityPolicyImpl(registry)
        val observerId = Uuid.random()
        val otherId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(
                    FakeMahjongPlayerFactory.create(id = observerId),
                    FakeMahjongPlayerFactory.create(
                        id = otherId,
                        playerRuleState = RiichiPlayerState(isPermanentlyFuriten = true),
                    ),
                ),
                config = config,
                dynamicRuleState = RiichiDynamicState(riichiStickCount = 2),
            ),
            flowConfig = GameFlowConfig(),
        )

        val snapshot = module.snapshotFor(game, observerId)

        assertNull(snapshot.players.first { it.id == otherId }.playerRuleState)
        assertNull(snapshot.dynamicRuleState)
    }

    /** 規則專屬玩家狀態只向本人提供完整內容，其他玩家只收到明確的公開投影。 */
    @Test
    fun `rule player state is projected per observer`() {
        val riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val playerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(
                    FakeMahjongPlayerFactory.create(
                        id = playerId,
                        playerRuleState = RiichiPlayerState(
                            riichiTile = riichiTile,
                            isIppatsu = true,
                            isPermanentlyFuriten = true,
                        ),
                    ),
                    FakeMahjongPlayerFactory.create(
                        id = otherPlayerId,
                        initialSeat = Wind.SOUTH,
                        playerRuleState = RiichiPlayerState(
                            riichiTile = riichiTile,
                            isIppatsu = true,
                            isPermanentlyFuriten = true,
                        ),
                    ),
                ),
                config = RiichiRuleConfig(),
            ),
            flowConfig = GameFlowConfig(),
        )

        val selfState = assertIs<RiichiPlayerStateSnapshot>(
            policy.snapshotFor(game, playerId).players.first { it.id == playerId }.playerRuleState,
        )
        assertEquals(true, selfState.isIppatsu)
        assertEquals(true, selfState.isPermanentlyFuriten)

        val otherState = assertIs<RiichiPlayerStateSnapshot>(
            policy.snapshotFor(game, playerId).players.first { it.id == otherPlayerId }.playerRuleState,
        )
        assertEquals(riichiTile, otherState.riichiTile)
        // 一發資格可由公開的立直、鳴牌與回合流程推導，因此不是私有資訊。
        assertEquals(true, otherState.isIppatsu)
        assertNull(otherState.isPermanentlyFuriten)
    }

    /** 規則專屬動態狀態只保留明確公開欄位，不把尚未公開的槓寶牌項目送給觀察者。 */
    @Test
    fun `rule dynamic state hides pending private entries`() {
        val game = createGame(Uuid.random()).let { baseGame ->
            baseGame.copy(
                tableState = baseGame.tableState.copy(
                    dynamicRuleState = RiichiDynamicState(
                        riichiStickCount = 1,
                        pendingKanDoraReveals = listOf(
                            RiichiPendingKanDoraReveal(
                                actorPlayerId = Uuid.random(),
                                sourceKanType = GameAction.KanType.CLOSED_KAN,
                                supplementalDrawNumber = 1,
                            ),
                        ),
                    ),
                ),
            )
        }

        val state = assertIs<RiichiDynamicState>(policy.snapshotFor(game, Uuid.random()).dynamicRuleState)
        assertEquals(1, state.riichiStickCount)
        assertTrue(state.pendingKanDoraReveals.isEmpty())
    }

    /** 公開旁觀手牌時，外部觀看者可以看見所有玩家手牌。 */
    @Test
    fun `spectator sees all hands when configured as revealed`() {
        val playerIds = listOf(Uuid.random(), Uuid.random())
        val game = createGame(*playerIds.toTypedArray())

        assertEquals(
            playerIds.toSet(),
            policy.snapshotFor(game, Uuid.random()).players
                .filter { it.hand.standingTiles.single().tile != null }
                .mapTo(mutableSetOf()) { it.id },
        )
    }

    /** 隱藏旁觀手牌時，外部觀看者仍可取得只含公開資料的快照。 */
    @Test
    fun `spectator sees no hands when configured as hidden`() {
        val game = createGame(
            Uuid.random(),
            flowConfig = GameFlowConfig(spectatorHandVisibility = SpectatorHandVisibility.HIDDEN),
        )

        assertEquals(
            emptyList(),
            policy.snapshotFor(game, Uuid.random()).players
                .filter { it.hand.standingTiles.single().tile != null },
        )
    }

    /** 禁止旁觀時，外部讀取者仍可取得隱藏所有手牌的公開快照。 */
    @Test
    fun `external observer sees no hands when spectating is disabled`() {
        val game = createGame(
            Uuid.random(),
            flowConfig = GameFlowConfig(spectatingPolicy = SpectatingPolicy.DISABLED),
        )

        assertEquals(
            emptyList(),
            policy.snapshotFor(game, Uuid.random()).players
                .filter { it.hand.standingTiles.single().tile != null },
        )
    }

    /**
     * 建立指定玩家與流程設定的測試遊戲。
     *
     * @param playerIds 依順序建立的玩家識別碼。
     * @param flowConfig 控制旁觀權限與手牌可見性的流程設定。
     * @return 每位玩家各持有一張東風的遊戲。
     */
    private fun createGame(
        vararg playerIds: Uuid,
        flowConfig: GameFlowConfig = GameFlowConfig(),
    ): Game = Game(
        tableState = FakeTableStateFactory.create(
            players = playerIds.map {
                FakeMahjongPlayerFactory.create(
                    id = it,
                    hand = FakeHandFactory.create(listOf(Tile.Honor.East)),
                )
            },
            config = RiichiRuleConfig(),
        ),
        flowConfig = flowConfig,
    )

    /** 建立只改動手牌可見性 hook 的測試政策。 */
    private fun policyFor(
        descriptions: List<HandTileDescription> = emptyList(),
    ): GameVisibilityPolicyImpl {
        val registry = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(RiichiRuleConfig::class, "test:visibility") { moduleConfig, id ->
                    VisibilityRuleModule(RiichiRuleModule(id, moduleConfig), descriptions)
                },
            )
            freeze()
        }
        return GameVisibilityPolicyImpl(registry)
    }

    /** 建立台灣規則政策，供暗槓預設不公開的回歸測試使用。 */
    private fun taiwanPolicyFor(descriptions: List<HandTileDescription>): GameVisibilityPolicyImpl {
        val registry = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(TaiwanRuleConfig::class, "test:taiwan-visibility") { moduleConfig, id ->
                    TaiwanVisibilityRuleModule(TaiwanRuleModule(id, moduleConfig), descriptions)
                },
            )
            freeze()
        }
        return GameVisibilityPolicyImpl(registry)
    }

    /**
     * 只用來驗證 [MahjongRuleModule] 預設投影契約的測試規則模組。
     *
     * @property delegate 提供與受測投影無關的日麻規則行為。
     */
    private class DefaultProjectionRuleModule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        override fun projectPlayerRuleState(
            tableState: TableState,
            player: MahjongPlayer,
            observerId: Uuid,
        ) = super<MahjongRuleModule>.projectPlayerRuleState(tableState, player, observerId)

        override fun projectDynamicRuleState(
            tableState: TableState,
            observerId: Uuid,
        ) = super<MahjongRuleModule>.projectDynamicRuleState(tableState, observerId)
    }

    /**
     * 只覆寫逐張描述的日麻測試規則。
     *
     * @property delegate 提供其餘規則行為。
     * @property descriptions 這次查詢要回傳的描述。
     */
    private class VisibilityRuleModule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
        private val descriptions: List<HandTileDescription>,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        override fun describeHandTiles(tableState: TableState): List<HandTileDescription> = descriptions
    }

    /**
     * 保留暗槓不公開政策的台灣測試規則。
     *
     * @property delegate 提供其餘規則行為。
     * @property descriptions 這次查詢要回傳的描述。
     */
    private class TaiwanVisibilityRuleModule(
        private val delegate: MahjongRuleModule<TaiwanRuleConfig>,
        private val descriptions: List<HandTileDescription>,
    ) : MahjongRuleModule<TaiwanRuleConfig> by delegate {
        override fun describeHandTiles(tableState: TableState): List<HandTileDescription> = descriptions
    }
}
