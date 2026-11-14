package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.revealHandsOf
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType
import com.doublemoon1119.mahjongcraft.logic.component.initializeRuleComponents
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPendingKanDoraReveal
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerComponentViews
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTableComponentViews
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiWallProgress
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.registerRiichiTableComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiDeclaration
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileAreas
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.visibleTiles
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileComponentDescription
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePoseState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiDeclarationComponents
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.testRoundRandomness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [GameVisibilityPolicyImpl] 的觀看權限與手牌可見範圍測試。 */
class GameVisibilityPolicyTest {
    /** 使用內建規則建立快照的受測政策。 */
    private val policy = GameVisibilityPolicyImpl(MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }, componentRegistry = bundledCoreExtensionRegistries().componentRegistry)

    /** 副露的私人值僅本人取得；旁觀者與未獲准觀察者不能藉由快照讀取。 */
    @Test
    fun `meld component projections follow the authenticated reader`() {
        val component = ComponentType(
            id = "test:private_meld_value",
            valueClass = Int::class,
            copyValue = { it },
            target = BuiltInComponentOwners.MELD,
        )
        val view = ComponentViewType(
            id = "test:meld_value_view",
            valueClass = Int::class,
            copyValue = { it },
        )
        val components = ComponentRegistryImpl().apply {
            BuiltInComponentOwners.ALL.forEach(::registerOwner)
            register(BuiltInTileComponents.FACE_ACCESS)
            register(BuiltInTileComponents.POSE)
            register(BuiltInTileComponents.APPEARANCE)
            registerRiichiTableComponents(this)
            register(component)
            registerView(view)
            registerProjection(
                definition = ComponentProjectionDefinition(
                    id = "test:meld_projection",
                    ownerType = BuiltInComponentOwners.MELD,
                    component = component,
                    viewType = view,
                    appliesTo = { _, _ -> true },
                    project = { context, value ->
                        val reader = context.reader as? ComponentReadContext.Player
                        value.takeIf {
                            reader != null &&
                                context.tableState.players.any { player ->
                                    player.id == reader.playerId && player.hand.melds.any { it.id == context.owner?.id }
                                }
                        }
                    },
                ),
            )
            freeze()
        }
        val ownerId = Uuid.random()
        val otherId = Uuid.random()
        val tiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.East) }
        val meld = Meld(
            type = MeldType.ClosedKan,
            tiles = tiles,
            sourceDirection = RelativeDirection.Self,
            publiclyRevealedTileIds = emptySet(),
            faceDownTileIds = tiles.mapTo(mutableSetOf()) { it.id },
            components = ComponentSet.EMPTY.with(component, 7),
        )
        val game = createGame(ownerId, otherId).let { original ->
            original.copy(
                tableState = original.tableState.copy(
                    players = original.tableState.players.map {
                        if (it.id == ownerId) it.copy(hand = it.hand.copy(melds = listOf(meld))) else it
                    },
                ),
            )
        }
        val subject = GameVisibilityPolicyImpl(
            moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() },
            componentRegistry = components,
        )
        val ownerSnapshot = subject.snapshotFor(game, ownerId).players.single { it.id == ownerId }.hand.melds.single()
        assertEquals(meld.id, ownerSnapshot.id)
        assertEquals(7, ownerSnapshot.componentViews[view])
        for (readerId in listOf(otherId, Uuid.random())) {
            val snapshot = subject.snapshotFor(game, readerId).players.single { it.id == ownerId }.hand.melds.single()
            assertTrue(snapshot.componentViews.types.isEmpty())
        }
    }

    /** 牌山的私人授權不變成全桌公開；公共證據在換持有人與牌區後仍有效。 */
    @Test
    fun `private wall access and cross region public evidence remain distinct`() {
        val authorized = Uuid.random()
        val other = Uuid.random()
        val spectator = Uuid.random()
        val tile = FakeIdentifiedTileFactory.create(Tile.Honor.Red).copy(
            components = ComponentSet.EMPTY
                .with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Players(setOf(authorized)))
                .with(BuiltInTileComponents.APPEARANCE, "test:private_style"),
        )
        val game = createGame(authorized, other).let {
            it.copy(tableState = it.tableState.copy(tileWall = TileWall(listOf(tile)), reservedWallTiles = emptyList()))
        }
        val authorizedView = policy.snapshotFor(game, authorized)
        assertEquals(tile.tile, authorizedView.tileWall.tiles.single().tile)
        assertEquals("test:private_style", authorizedView.tileWall.tiles.single().face?.appearanceId)
        assertTrue(tile.id !in authorizedView.publicTileIds)
        for (reader in listOf(other, spectator)) {
            val view = policy.snapshotFor(game, reader)
            assertNull(view.tileWall.tiles.single().face)
            assertTrue(tile.id !in view.publicTileIds)
        }
        val moved = game.copy(
            tableState = game.tableState.copy(
                tileWall = TileWall(),
                publicTileEvidenceIds = setOf(tile.id),
                players = game.tableState.players.map {
                    if (it.id == other) it.copy(hand = it.hand.addTile(tile)) else it
                },
            ),
        )
        val publicView = policy.snapshotFor(moved, spectator)
        assertTrue(tile.id in publicView.publicTileIds)
        assertEquals(tile.tile, publicView.players.single { it.id == other }.hand.standingTiles.single { it.id == tile.id }.tile)
    }

    /** 手牌有部分牌所有人都讀得到時，其他人收到的立牌仍是與牌面無關的固定順序，不透露持有人的理牌順序。 */
    @Test
    fun `public faces in a hand do not reveal the owner's tile order`() {
        val observer = Uuid.random()
        val owner = Uuid.random()
        val public = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public)
        val ownerOrder = (1..9).map { value ->
            val tile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, value))
            if (value % 3 == 0) tile.copy(components = public) else tile
        }
        val game = createGame(observer, owner).let { game ->
            game.copy(
                tableState = game.tableState.copy(
                    players = game.tableState.players.map { player -> if (player.id == owner) player.copy(hand = Hand(tiles = ownerOrder)) else player },
                ),
            )
        }

        val snapshot = policy.snapshotFor(game, observer)
        val standing = snapshot.players.single { it.id == owner }.hand.standingTiles
        val publicIds = ownerOrder.filter { it.components == public }.map { it.id }.toSet()

        assertEquals(ownerOrder.map { it.id }.sortedBy(Uuid::toString), standing.map { it.id })
        assertEquals(publicIds, standing.filter { it.tile != null }.map { it.id }.toSet())
        assertTrue(snapshot.publicTileIds.containsAll(publicIds))
        assertTrue(snapshot.publicEvidenceTileIds.intersect(ownerOrder.map { it.id }.toSet()).isEmpty())
    }

    /** 流局攤開的手牌記為事件公開的牌，其他人收到的快照可依此還原持有人的排列。 */
    @Test
    fun `revealed hands are recorded as public evidence`() {
        val observer = Uuid.random()
        val owner = Uuid.random()
        val game = createGame(observer, owner).let { game -> game.copy(tableState = game.tableState.revealHandsOf(listOf(owner))) }
        val handIds = game.tableState.players.single { it.id == owner }.hand.tiles.map { it.id }.toSet()

        val snapshot = policy.snapshotFor(game, observer)

        assertTrue(handIds.isNotEmpty())
        assertTrue(snapshot.publicEvidenceTileIds.containsAll(handIds))
    }

    /** 三人與四人日麻的起始指示牌，都經正式快照入口公開，裏指示牌保持隱藏。 */
    @Test
    fun `both riichi variants expose only their initial active indicator`() {
        val cases = listOf(
            RiichiRuleModule("test:four", RiichiRuleConfig()) to 4,
            ThreePlayerRiichiRuleModule("test:three", ThreePlayerRiichiRuleConfig()) to 3,
        )
        for ((module, count) in cases) {
            val players = List(count) { Uuid.random() }
            val table = GameInitializer.initialize(Uuid.random(), players, module, randomness = testRoundRandomness()).initializeRuleComponents(module.id, bundledCoreExtensionRegistries().componentRegistry, random = testRoundRandomness().components).tableState
            val config = table.config as RiichiFamilyRuleConfig
            val indicator = table.reservedWallTiles[config.rinshanTileCount]
            val ura = table.reservedWallTiles[config.rinshanTileCount + 1]
            val snapshot = policy.snapshotFor(Game(tableState = table, flowConfig = GameFlowConfig()), players.first())
            assertEquals(indicator.tile, snapshot.tileWall.tiles.single { it.id == indicator.id }.tile)
            assertNull(snapshot.tileWall.tiles.single { it.id == ura.id }.face)
            assertEquals(setOf(indicator.id), snapshot.publicTileIds)
        }
    }

    /** 他家玩家取得的快照會帶出拔北牌，未拔北的玩家則為空清單。 */
    @Test
    fun `snapshot carries tiles every player set aside`() {
        val north = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val puller = FakeMahjongPlayerFactory.create(components = riichiDeclarationComponents(), tileAreas = PlayerTileAreas.EMPTY.withArea(RiichiPlayerTileAreaIds.NORTH, listOf(north)))
        val others = List(2) { FakeMahjongPlayerFactory.create(components = riichiDeclarationComponents()) }
        val game = Game(
            tableState = FakeTableStateFactory.create(players = listOf(puller) + others, config = ThreePlayerRiichiRuleConfig()),
            flowConfig = GameFlowConfig(),
        )

        val snapshot = policy.snapshotFor(game, others.first().id)

        assertEquals(listOf(north.toSnapshot(true)), snapshot.players.single { it.id == puller.id }.setAsideTiles)
        assertNull(snapshot.players.single { it.id == puller.id }.riichiDeclaration)
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
                tiles = mutableListOf(
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1), firstId),
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 2), secondId),
                ),
            ),
            components = riichiDeclarationComponents(),
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
                tiles = mutableListOf(
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9), secondId),
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 8), firstId),
                ),
            ),
            components = ComponentSet.EMPTY.with(RiichiPlayerComponents.PERMANENT_FURITEN, Unit).merge(riichiDeclarationComponents()),
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
            expected = firstSnapshot.players.first { it.id == hiddenPlayerId }.hand.standingTiles.map { it.id },
            actual = changedSnapshot.players.first { it.id == hiddenPlayerId }.hand.standingTiles.map { it.id },
        )
    }

    /** 預設與明確的預設直立描述產生相同觀察者資料。 */
    @Test
    fun `default descriptions do not alter visibility or pose`() {
        val first = Uuid.random()
        val second = Uuid.random()
        val game = createGame(first, second)
        val tileId = game.tableState.players.last().hand.standingTiles.single().id
        val explicit = policyFor(listOf(description(tileId, TileFaceAccess.None, TilePose.UPRIGHT)))
        assertTrue(RiichiRuleModule("test:default", RiichiRuleConfig()).describeTileComponents(game.tableState).isEmpty())
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
        val snapshot = policyFor(listOf(description(first.id, TileFaceAccess.Public, TilePose.UPRIGHT)))
            .snapshotFor(game, observer)
        val hand = snapshot.players.single { it.id == owner }.hand
        assertEquals(first.tile, hand.standingTiles.single { it.id == first.id }.tile)
        assertNull(hand.standingTiles.single { it.id == second.id }.tile)
        assertTrue(snapshot.tilePoseStates.isEmpty())
        assertTrue(first.id in snapshot.publicTileIds)
        assertTrue(snapshot.visibleTiles(RiichiTileInterpretationPolicy).any { it.id == first.id })
        assertTrue(snapshot.visibleTiles(RiichiTileInterpretationPolicy).none { it.id == second.id })
    }

    /** 規則衍生的外觀經同一個解算交給本人；同一份結果與快照一致，其他人與公共讀取者都拿不到未公開的牌。 */
    @Test
    fun `tile faces follow derived appearance and the reader permission`() {
        val owner = Uuid.random()
        val other = Uuid.random()
        val styled = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val plain = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val game = gameWithOwnerHand(owner, other, listOf(styled, plain))
        val policy = policyFor(listOf(appearanceDescription(styled.id)))
        val ids = listOf(styled.id, plain.id)

        val ownFaces = policy.tileFacesFor(game, owner, ids)

        assertEquals(TEST_STYLE, ownFaces.getValue(styled.id).appearanceId)
        assertNull(ownFaces.getValue(plain.id).appearanceId)
        val snapshotFace = policy.snapshotFor(game, owner).players.single { it.id == owner }.hand.standingTiles.single { it.id == styled.id }.face
        assertEquals(snapshotFace, ownFaces[styled.id])
        assertTrue(policy.tileFacesFor(game, other, ids).isEmpty())
        assertTrue(policy.publicTileFaces(game.tableState, ids).isEmpty())
    }

    /** 公共讀取者只拿到全桌公開的牌，並帶著該牌的外觀；未公開的同種牌不列入。 */
    @Test
    fun `public tile faces include only publicly readable tiles`() {
        val owner = Uuid.random()
        val other = Uuid.random()
        val revealed = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val hidden = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val game = gameWithOwnerHand(owner, other, listOf(revealed, hidden))
        val description = TileComponentDescription(
            tileId = revealed.id,
            components = appearanceDescription(revealed.id).components.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public),
        )
        val policy = policyFor(listOf(description))

        val publicFaces = policy.publicTileFaces(game.tableState, listOf(revealed.id, hidden.id))

        assertEquals(setOf(revealed.id), publicFaces.keys)
        assertEquals(TEST_STYLE, publicFaces.getValue(revealed.id).appearanceId)
        assertEquals(publicFaces, policy.tileFacesFor(game, other, listOf(revealed.id, hidden.id)))
    }

    /** 不能觀戰的外部讀取者，即使牌已公開也拿不到牌面。 */
    @Test
    fun `tile faces are empty for an observer who may not watch`() {
        val owner = Uuid.random()
        val revealed = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val game = gameWithOwnerHand(owner, Uuid.random(), listOf(revealed), GameFlowConfig(spectatingPolicy = SpectatingPolicy.DISABLED))
        val description = TileComponentDescription(revealed.id, ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public))

        assertTrue(policyFor(listOf(description)).tileFacesFor(game, Uuid.random(), listOf(revealed.id)).isEmpty())
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
        val snapshot = policyFor(listOf(description(first.id, TileFaceAccess.Public, TilePose.FACE_UP)))
            .snapshotFor(game, observer)
        val hand = snapshot.players.single { it.id == owner }.hand
        assertEquals(listOf(TilePoseState(first.id, TilePose.FACE_UP)), snapshot.tilePoseStates)
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
        val policy = policyFor(listOf(description(tile.id, TileFaceAccess.Public, TilePose.FACE_UP)))
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
        val snapshot = policyFor(listOf(description(tile.id, TileFaceAccess.Public, TilePose.UPRIGHT)))
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
            listOf(description(Uuid.random(), TileFaceAccess.Public, TilePose.UPRIGHT)),
            listOf(description(tile, TileFaceAccess.None, TilePose.FACE_UP)),
            List(2) { description(tile, TileFaceAccess.Public, TilePose.UPRIGHT) },
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
            type = MeldType.ClosedKan,
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

        val snapshot = taiwanPolicyFor(listOf(description(standingTile.id, TileFaceAccess.Public, TilePose.UPRIGHT))).snapshotFor(game, observerId)
        val meld = snapshot.players.single { it.id == otherId }.hand.melds.single()

        assertTrue(meld.tiles.all { it.tile == null })
    }

    /** 未符合公開投影條件時，不公開其他玩家的私人組件。 */
    @Test
    fun `default player projection hides private rule data`() {
        val config = RiichiRuleConfig()
        val registry = MahjongModuleRegistryImpl().apply {
            register(
                definition = MahjongModuleDefinition(RiichiRuleConfig::class, "test:default-projection") { moduleConfig, id ->
                    DefaultProjectionRuleModule(RiichiRuleModule(id, moduleConfig))
                },
            )
            freeze()
        }
        val module = GameVisibilityPolicyImpl(registry, componentRegistry = bundledCoreExtensionRegistries().componentRegistry)
        val observerId = Uuid.random()
        val otherId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(
                    FakeMahjongPlayerFactory.create(id = observerId),
                    FakeMahjongPlayerFactory.create(
                        id = otherId,
                        components = ComponentSet.EMPTY.with(RiichiPlayerComponents.PERMANENT_FURITEN, Unit).merge(riichiDeclarationComponents()),
                    ),
                ),
                config = config,
                components = riichiTableComponents(potPoints = 2000),
            ),
            flowConfig = GameFlowConfig(),
        )

        val snapshot = module.snapshotFor(game, observerId)

        assertNull(snapshot.players.first { it.id == otherId }.riichiDeclaration)
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
                        components = ComponentSet.EMPTY.with(RiichiPlayerComponents.PERMANENT_FURITEN, Unit).merge(
                            higher = riichiDeclarationComponents(
                                declarationTile = riichiTile,
                                isIppatsu = true,
                            ),
                        ),
                    ),
                    FakeMahjongPlayerFactory.create(
                        id = otherPlayerId,
                        initialSeat = Wind.SOUTH,
                        components = ComponentSet.EMPTY.with(RiichiPlayerComponents.PERMANENT_FURITEN, Unit).merge(
                            higher = riichiDeclarationComponents(
                                declarationTile = riichiTile,
                                isIppatsu = true,
                            ),
                        ),
                    ),
                ),
                config = RiichiRuleConfig(),
            ),
            flowConfig = GameFlowConfig(),
        )

        val selfState = assertNotNull(policy.snapshotFor(game, playerId).players.first { it.id == playerId }.riichiDeclaration)
        assertEquals(true, selfState.isIppatsu)
        assertEquals(Unit, policy.snapshotFor(game, playerId).players.first { it.id == playerId }.componentViews[RiichiPlayerComponentViews.PERMANENT_FURITEN])

        val otherState = assertNotNull(policy.snapshotFor(game, playerId).players.first { it.id == otherPlayerId }.riichiDeclaration)
        assertEquals(riichiTile.id, otherState.discardTileId)
        // 一發資格可由公開的立直、鳴牌與回合流程推導，因此不是私有資訊。
        assertEquals(true, otherState.isIppatsu)
        assertNull(policy.snapshotFor(game, playerId).players.first { it.id == otherPlayerId }.componentViews[RiichiPlayerComponentViews.PERMANENT_FURITEN])
    }

    /** 觀察者只收到供託；牌山進度（含尚未公開的槓寶牌項目）不交付。 */
    @Test
    fun `observers receive the pot but not the wall progress`() {
        val game = createGame(Uuid.random()).let { baseGame ->
            baseGame.copy(
                tableState = baseGame.tableState.copy(
                    components = riichiTableComponents(
                        potPoints = 1000,
                        wallProgress = RiichiWallProgress(
                            completedSupplementalDrawCount = 1,
                            revealedKanDoraCount = 0,
                            pendingKanDoraReveals = listOf(
                                element = RiichiPendingKanDoraReveal(
                                    actorPlayerId = Uuid.random(),
                                    sourceKanType = GameAction.KanType.CLOSED_KAN,
                                    supplementalDrawNumber = 1,
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        val state = policy.snapshotFor(game, Uuid.random()).componentViews
        assertEquals(1000, state[RiichiTableComponentViews.POT_POINTS])
        assertEquals(1, state.types.size)
    }

    /** 公開旁觀手牌時，外部觀看者可以看見所有玩家手牌。 */
    @Test
    fun `spectator sees all hands when configured as revealed`() {
        val playerIds = listOf(Uuid.random(), Uuid.random())
        val game = createGame(*playerIds.toTypedArray())

        assertEquals(
            expected = playerIds.toSet(),
            actual = policy.snapshotFor(game, Uuid.random()).players
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
            expected = emptyList(),
            actual = policy.snapshotFor(game, Uuid.random()).players
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
            expected = emptyList(),
            actual = policy.snapshotFor(game, Uuid.random()).players
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

    /**
     * 建立第一位玩家持有指定手牌的對局。
     *
     * @param owner 持有 [tiles] 的玩家。
     * @param other 另一位參與者。
     * @param tiles 擁有者的手牌。
     * @param flowConfig 控制旁觀權限的流程設定。
     * @return 測試對局。
     */
    private fun gameWithOwnerHand(
        owner: Uuid,
        other: Uuid,
        tiles: List<IdentifiedTile>,
        flowConfig: GameFlowConfig = GameFlowConfig(),
    ): Game = createGame(owner, other, flowConfig = flowConfig).let { game ->
        game.copy(
            tableState = game.tableState.copy(
                players = game.tableState.players.map { player -> if (player.id == owner) player.copy(hand = Hand(tiles = tiles)) else player },
            ),
        )
    }

    /**
     * 建立只提供外觀樣式的規則描述，例如規則依目前情況改變一張牌的外觀。
     *
     * @param tileId 目標牌張。
     * @return 只含外觀樣式的描述。
     */
    private fun appearanceDescription(tileId: Uuid): TileComponentDescription = TileComponentDescription(tileId, ComponentSet.EMPTY.with(BuiltInTileComponents.APPEARANCE, TEST_STYLE))

    /**
     * 建立測試用的逐張權限與姿態描述。
     *
     * @param tileId 目標牌張。
     * @param access 額外讀取權限。
     * @param pose 共同姿態。
     * @return 使用正式組件型別的描述。
     */
    private fun description(
        tileId: Uuid,
        access: TileFaceAccess,
        pose: TilePose,
    ): TileComponentDescription = TileComponentDescription(
        tileId = tileId,
        components = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, access).with(BuiltInTileComponents.POSE, pose),
    )

    /** 建立只改動手牌可見性 hook 的測試政策。 */
    private fun policyFor(
        descriptions: List<TileComponentDescription> = emptyList(),
    ): GameVisibilityPolicyImpl {
        val registry = MahjongModuleRegistryImpl().apply {
            register(
                definition = MahjongModuleDefinition(RiichiRuleConfig::class, "test:visibility") { moduleConfig, id ->
                    VisibilityRuleModule(RiichiRuleModule(id, moduleConfig), descriptions)
                },
            )
            freeze()
        }
        return GameVisibilityPolicyImpl(registry, componentRegistry = bundledCoreExtensionRegistries().componentRegistry)
    }

    /** 建立台灣規則政策，供暗槓預設不公開的測試使用。 */
    private fun taiwanPolicyFor(descriptions: List<TileComponentDescription>): GameVisibilityPolicyImpl {
        val registry = MahjongModuleRegistryImpl().apply {
            register(
                definition = MahjongModuleDefinition(TaiwanRuleConfig::class, "test:taiwan-visibility") { moduleConfig, id ->
                    TaiwanVisibilityRuleModule(TaiwanRuleModule(id, moduleConfig), descriptions)
                },
            )
            freeze()
        }
        return GameVisibilityPolicyImpl(registry, componentRegistry = bundledCoreExtensionRegistries().componentRegistry)
    }

    /**
     * 只用來驗證 [MahjongRuleModule] 預設投影契約的測試規則模組。
     *
     * @property delegate 提供與受測投影無關的日麻規則行為。
     */
    private class DefaultProjectionRuleModule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate

    /**
     * 只覆寫逐張描述的日麻測試規則。
     *
     * @property delegate 提供其餘規則行為。
     * @property descriptions 這次查詢要回傳的描述。
     */
    private class VisibilityRuleModule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
        private val descriptions: List<TileComponentDescription>,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        override fun describeTileComponents(tableState: TableState): List<TileComponentDescription> = descriptions
    }

    /**
     * 保留暗槓不公開政策的台灣測試規則。
     *
     * @property delegate 提供其餘規則行為。
     * @property descriptions 這次查詢要回傳的描述。
     */
    private class TaiwanVisibilityRuleModule(
        private val delegate: MahjongRuleModule<TaiwanRuleConfig>,
        private val descriptions: List<TileComponentDescription>,
    ) : MahjongRuleModule<TaiwanRuleConfig> by delegate {
        override fun describeTileComponents(tableState: TableState): List<TileComponentDescription> = descriptions
    }

    /** 測試共用的固定值。 */
    private companion object {
        /** 規則衍生的測試外觀樣式。 */
        const val TEST_STYLE = "test:style"
    }
}
