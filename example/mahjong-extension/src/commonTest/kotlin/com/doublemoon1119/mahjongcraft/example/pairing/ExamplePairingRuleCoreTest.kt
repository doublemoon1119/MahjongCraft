package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.example.exampleComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.initializeRuleComponents
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.RoundAdvancementResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileReadContext
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.logic.tile.component.resolveTileComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 覆蓋簡易配對規則的牌型、牌山與固定自摸結算核心行為。 */
class ExamplePairingRuleCoreTest {
    /** 下一局透過正式初始化流程清除上一局攤牌狀態，重新恢復立牌與手牌保密。 */
    @Test
    fun `next round initializer clears previous hand reveal state`() {
        val module = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, ExamplePairingRuleConfig())
        val initial = GameInitializer.initialize(
            id = Uuid.random(),
            playerIds = listOf(Uuid.random(), Uuid.random()),
            module = module,
        ).initializeRuleComponents(module.id, exampleComponentRegistry()).tableState
        val revealedPlayers = initial.players.map { it.copy(components = it.components.with(ExamplePairingComponents.HAND_REVEALED, Unit)) }
        val next = GameInitializer.startNextRound(
            gameId = initial.id,
            roundAdvancement = RoundAdvancementResult(
                players = revealedPlayers,
                dealerPlayerId = initial.dealerPlayerId,
                roundNumber = 2,
                comboCount = 0,
                prevalentWind = initial.prevalentWind,
                roundSerial = 1,
            ),
            module = module,
        ).initializeRuleComponents(module.id, exampleComponentRegistry(), initial.copy(players = revealedPlayers)).tableState

        assertTrue(next.players.all { it.components[ExamplePairingComponents.HAND_REVEALED] == null })
        assertTrue(module.resolveTileComponents(next, exampleComponentRegistry()).filter { it.area.region == TileRegion.HAND || it.area.region == TileRegion.DRAWN }.all { it.pose == TilePose.UPRIGHT && !it.isPublic })
    }

    /** 攤牌玩家的現有手牌與摸牌位都公開，未攤牌玩家仍維持隱藏。 */
    @Test
    fun `revealed hand exposes standing and drawn tile ids without exposing another player`() {
        val revealedId = Uuid.random()
        val hiddenId = Uuid.random()
        val standing = identified(Tile.Numeric(Tile.Suit.Character, 1))
        val drawn = identified(Tile.Numeric(Tile.Suit.Dot, 1))
        val hiddenTile = identified(Tile.Numeric(Tile.Suit.Bamboo, 1))
        val table = FakeTableStateFactory.create(
            config = ExamplePairingRuleConfig(),
            players = listOf(
                FakeMahjongPlayerFactory.create(
                    id = revealedId,
                    hand = Hand(tiles = listOf(standing), lastDrawn = drawn),
                    components = ComponentSet.EMPTY.with(ExamplePairingComponents.HAND_REVEALED, Unit),
                ),
                FakeMahjongPlayerFactory.create(
                    id = hiddenId,
                    hand = Hand(tiles = listOf(hiddenTile)),
                    components = ComponentSet.EMPTY,
                ),
            ),
        )
        val module = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, ExamplePairingRuleConfig())

        val descriptions = module.resolveTileComponents(table, exampleComponentRegistry()).filter { it.isPublic && (it.area.region == TileRegion.HAND || it.area.region == TileRegion.DRAWN) }
        assertEquals(setOf(standing.id, drawn.id), descriptions.map { it.area.tile.id }.toSet())
        assertTrue(descriptions.all { it.isPublic && it.pose == TilePose.FACE_UP })
    }

    /** 公開牌面關閉時沒有標記；開啟時每種點數剛好三張帶標記，第四張與倒置一萬保持普通。 */
    @Test
    fun `public faces mark three of four copies only when enabled`() {
        assertTrue(ExamplePairingWallFactory(2, setOf(1, 2)).create().getAllTiles().none { it.components[ExamplePairingComponents.PUBLIC_FACE] != null })

        val tiles = ExamplePairingWallFactory(2, setOf(1, 2), publicFacesEnabled = true).create().getAllTiles()

        val marked = tiles.filter { it.components[ExamplePairingComponents.PUBLIC_FACE] != null }
        assertEquals(mapOf<Tile, Int>(Tile.Numeric(Tile.Suit.Character, 1) to 3, Tile.Numeric(Tile.Suit.Character, 2) to 3), marked.groupingBy { it.tile }.eachCount())
        val invertedOneMan = tiles.single { it.tile is Tile.Extension }
        assertTrue(invertedOneMan.components[ExamplePairingComponents.PUBLIC_FACE] == null)
        assertEquals(8, tiles.size)
    }

    /** 帶公開牌面標記的牌在牌山朝下時，其他玩家與旁觀者也讀得到牌面與公開牌面樣式；普通牌讀不到。 */
    @Test
    fun `public face tiles are readable by everyone while face down`() {
        val marked = IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Character, 2), ComponentSet.EMPTY.with(ExamplePairingComponents.PUBLIC_FACE, Unit))
        val plain = identified(Tile.Numeric(Tile.Suit.Character, 2))
        val config = ExamplePairingRuleConfig(publicFacesEnabled = true)
        val table = FakeTableStateFactory.create(config = config, tileWall = TileWall(listOf(marked, plain)))
        val module = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, config)

        val resolved = module.resolveTileComponents(table, exampleComponentRegistry()).associateBy { it.area.tile.id }

        val publicTile = resolved.getValue(marked.id)
        assertEquals(TilePose.FACE_DOWN, publicTile.pose)
        assertTrue(publicTile.canRead(TileReadContext.Public()))
        assertEquals(ExamplePairingComponentIds.PUBLIC_FACE_APPEARANCE, publicTile.appearanceFor(TileReadContext.Player(table.players.first().id)))
        assertFalse(resolved.getValue(plain.id).canRead(TileReadContext.Public()))
    }

    /** 四同牌可拆成兩對，不成對與非本規則牌面不可胡牌。 */
    @Test
    fun `four equal tiles complete while unsupported and unpaired hands do not`() {
        val calculator = ExamplePairingShantenCalculator()
        val one = Tile.Numeric(Tile.Suit.Character, 1)
        assertIs<ShantenResult.Complete>(calculator.calculate(Hand(tiles = List(4) { identified(one) })))
        assertIs<ShantenResult.NotTenpai>(
            calculator.calculate(Hand(tiles = (1..4).map { identified(Tile.Numeric(Tile.Suit.Character, it)) })),
        )
        assertIs<ShantenResult.NotTenpai>(calculator.calculate(Hand(tiles = List(4) { identified(Tile.Honor.East) })))
        assertIs<ShantenResult.Tenpai>(calculator.calculate(Hand(tiles = List(3) { identified(one) })))
    }

    /** 不接受超出範圍的付款、場長或不足以開局的牌山點數。 */
    @Test
    fun `configuration rejects unsupported payment and round limits`() {
        assertFailsWith<IllegalArgumentException> { ExamplePairingRuleConfig(paymentPerPlayer = 0) }
        assertFailsWith<IllegalArgumentException> { ExamplePairingRuleConfig(paymentPerPlayer = Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { ExamplePairingRuleConfig(roundCount = 0) }
        assertFailsWith<IllegalArgumentException> { ExamplePairingRuleConfig(roundCount = Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { ExamplePairingRuleConfig(wallTileValues = setOf(1)) }
        assertFailsWith<IllegalArgumentException> { ExamplePairingRuleConfig(wallTileValues = setOf(1, 9)) }
    }

    /** 倒置一萬應與普通一萬使用相同的配對語意。 */
    @Test
    fun `inverted one man joins the ordinary one man pair`() {
        val tiles = listOf(
            identified(Tile.Numeric(Tile.Suit.Character, 1)),
            identified(ExamplePairingTileType.invertedOneMan()),
            identified(Tile.Numeric(Tile.Suit.Character, 2)),
            identified(Tile.Numeric(Tile.Suit.Character, 2)),
        )

        assertIs<ShantenResult.Complete>(
            ExamplePairingShantenCalculator().calculate(Hand(tiles = tiles)),
        )
    }

    /** 預設八種牌面會產生完整牌山，玩家人數只影響牌牆面數。 */
    @Test
    fun `default wall size and wall side count follow player count`() {
        for ((playerCount, expectedSides) in listOf(2 to 2, 3 to 4, 4 to 4)) {
            val expectedTileCount = 32
            val tiles = ExamplePairingWallFactory(playerCount).create().getAllTiles()
            assertEquals(expectedTileCount, tiles.size)
            val layout = ExamplePairingWallLayout(playerCount)
            assertEquals(expectedSides, layout.sideArrangement(0).sideCount)
            if (playerCount == 2) {
                assertEquals(0, layout.sideArrangement(0).quarterTurnsOf(0))
                assertEquals(2, layout.sideArrangement(0).quarterTurnsOf(1))
            }
            val result = layout.resolve(tiles, WallOpening(0, 1))
            assertEquals(expectedTileCount, result.drawOrder.size)
            assertEquals(expectedTileCount, result.structure.size)
        }
    }

    /** 選取的牌面才會加入牌山，且倒置一萬只在選取一萬時出現。 */
    @Test
    fun `selected wall values control tile contents`() {
        val tiles = ExamplePairingWallFactory(2, setOf(3, 4)).create().getAllTiles()
        assertEquals(8, tiles.size)
        assertEquals(4, tiles.count { it.tile == Tile.Numeric(Tile.Suit.Character, 3) })
        assertEquals(4, tiles.count { it.tile == Tile.Numeric(Tile.Suit.Character, 4) })
        assertEquals(0, tiles.count { it.tile == ExamplePairingTileType.invertedOneMan() })
    }

    /** 四面牌牆可用奇數種類分配成相對兩面較長的牌牆。 */
    @Test
    fun `odd selected wall values distribute across four sides`() {
        val tiles = ExamplePairingWallFactory(4, (1..5).toSet()).create().getAllTiles()
        val layout = ExamplePairingWallLayout(4, 5)
        val result = layout.resolve(tiles, WallOpening(0, 1))
        assertEquals(20, result.structure.size)
        assertEquals(setOf(0, 1, 2, 3), result.structure.values.map { it.side }.toSet())
        assertEquals(20, result.drawOrder.map { it.id }.toSet().size)
    }

    /** 所有可用牌面數與玩家人數都能讓擲骰開門位置套用到牌牆。 */
    @Test
    fun `all valid selected counts resolve every wall side`() {
        for (playerCount in 2..4) {
            for (valueCount in playerCount..8) {
                val tiles = ExamplePairingWallFactory(playerCount, (1..valueCount).toSet()).create().getAllTiles()
                val layout = ExamplePairingWallLayout(playerCount, valueCount)
                for (side in 0 until layout.sideArrangement(0).sideCount) {
                    val result = layout.resolve(tiles, WallOpening(side, 1))
                    assertEquals(tiles.size, result.drawOrder.size)
                    assertEquals(tiles.size, result.structure.size)
                }
            }
        }
    }

    /** 選取牌面數量會限制可開始的玩家人數，避免牌山不足以完成起手。 */
    @Test
    fun `selected wall values limit player count`() {
        assertEquals(2, ExamplePairingRuleConfig(wallTileValues = setOf(1, 2)).maxPlayers)
        assertEquals(3, ExamplePairingRuleConfig(wallTileValues = setOf(1, 2, 3)).maxPlayers)
        assertEquals(4, ExamplePairingRuleConfig(wallTileValues = setOf(1, 2, 3, 4)).maxPlayers)
    }

    /** 自摸時應向每一位其他玩家收取固定點數。 */
    @Test
    fun `self draw settlement charges every other player`() {
        val winnerId = Uuid.random()
        val otherId = Uuid.random()
        val winner = MahjongPlayer(
            id = winnerId,
            initialSeatIndex = 0,
            hand = Hand(
                tiles = listOf(
                    identified(Tile.Numeric(Tile.Suit.Character, 1)),
                    identified(Tile.Numeric(Tile.Suit.Character, 1)),
                    identified(Tile.Numeric(Tile.Suit.Character, 2)),
                ),
                lastDrawn = identified(Tile.Numeric(Tile.Suit.Character, 2)),
            ),
            discardPile = ExamplePairingDiscardPile(),
            seatWind = Wind.EAST,
        )
        val other = MahjongPlayer(
            id = otherId,
            initialSeatIndex = 1,
            discardPile = ExamplePairingDiscardPile(),
            seatWind = Wind.SOUTH,
        )
        val table = TableState(
            id = Uuid.random(),
            players = listOf(winner, other),
            config = ExamplePairingRuleConfig(),
            tileWall = TileWall(),
            dealerPlayerId = winnerId,
            components = ComponentSet.EMPTY.with(ExamplePairingComponents.BONUS_TILES, ExamplePairingBonusTiles(setOf(8))),
            turnPhase = TurnPhase.AwaitingAction,
        )

        val result = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, ExamplePairingRuleConfig())
            .declareTsumo(table, winner)

        assertNotNull(result)
        assertEquals(10, result.settlement.totalGained)
        assertEquals(10, result.settlement.paymentsByPlayerId[otherId])
    }

    /** 每局選出牌種數量比牌山少一種，自摸時每種加分牌只計一次；加分牌資料不交付觀察者。 */
    @Test
    fun `round bonus tiles are distinct and counted once per kind`() {
        val module = ExamplePairingRuleModule(
            ExamplePairingIds.RULE_MODULE,
            ExamplePairingRuleConfig(wallTileValues = setOf(1, 2, 3, 4)),
        )
        val initial = GameInitializer.initialize(Uuid.random(), listOf(Uuid.random(), Uuid.random()), module)
            .initializeRuleComponents(module.id, exampleComponentRegistry()).tableState
        val randomlySelectedState = assertNotNull(initial.components[ExamplePairingComponents.BONUS_TILES])
        assertEquals(3, randomlySelectedState.values.size)
        assertTrue(randomlySelectedState.values.all { it in 1..4 })
        val next = GameInitializer.startNextRound(
            gameId = initial.id,
            roundAdvancement = RoundAdvancementResult(initial.players, initial.dealerPlayerId, 2, 0, initial.prevalentWind, roundSerial = 1),
            module = module,
        ).initializeRuleComponents(module.id, exampleComponentRegistry(), initial).tableState
        val nextRandomlySelectedState = assertNotNull(next.components[ExamplePairingComponents.BONUS_TILES])
        assertEquals(3, nextRandomlySelectedState.values.size)
        assertTrue(nextRandomlySelectedState.values.all { it in 1..4 })

        val scoringState = ExamplePairingBonusTiles(setOf(1, 2, 3))

        val winnerId = Uuid.random()
        val otherId = Uuid.random()
        val winner = MahjongPlayer(
            id = winnerId,
            initialSeatIndex = 0,
            hand = Hand(
                tiles = listOf(
                    identified(Tile.Numeric(Tile.Suit.Character, 1)),
                    identified(Tile.Numeric(Tile.Suit.Character, 1)),
                    identified(Tile.Numeric(Tile.Suit.Character, 2)),
                ),
                lastDrawn = identified(Tile.Numeric(Tile.Suit.Character, 2)),
            ),
            discardPile = ExamplePairingDiscardPile(),
            seatWind = Wind.EAST,
        )
        val table = TableState(
            id = Uuid.random(),
            players = listOf(
                winner,
                MahjongPlayer(
                    id = otherId,
                    initialSeatIndex = 1,
                    discardPile = ExamplePairingDiscardPile(),
                    seatWind = Wind.SOUTH,
                ),
            ),
            config = module.config,
            tileWall = TileWall(),
            dealerPlayerId = winnerId,
            components = ComponentSet.EMPTY.with(ExamplePairingComponents.BONUS_TILES, scoringState),
            turnPhase = TurnPhase.AwaitingAction,
        )

        val result = assertNotNull(module.declareTsumo(table, winner))
        assertEquals(2, (result.handValueResult as ExamplePairingHandValueResult).bonusTileCount)
        assertEquals(12, result.settlement.paymentsByPlayerId[otherId])
        val projected = exampleComponentRegistry().project(
            ComponentProjectionContext(module.id, table, BuiltInComponentOwners.TABLE, table, ComponentReadContext.Player(winnerId)),
            table.components,
        )
        assertTrue(projected.isEmpty)
    }

    /** 只有兩種牌時仍會選出一種加分牌，並可把倒置一萬算作一萬。 */
    @Test
    fun `minimum wall selects one bonus kind and inverted one man matches`() {
        val module = ExamplePairingRuleModule(
            ExamplePairingIds.RULE_MODULE,
            ExamplePairingRuleConfig(wallTileValues = setOf(1, 2)),
        )
        val initial = GameInitializer.initialize(Uuid.random(), listOf(Uuid.random(), Uuid.random()), module)
            .initializeRuleComponents(module.id, exampleComponentRegistry()).tableState
        val randomlySelectedState = assertNotNull(initial.components[ExamplePairingComponents.BONUS_TILES])
        assertEquals(1, randomlySelectedState.values.size)
        assertTrue(randomlySelectedState.values.all { it in 1..2 })
        val scoringState = ExamplePairingBonusTiles(setOf(1))

        val winnerId = Uuid.random()
        val otherId = Uuid.random()
        val winner = MahjongPlayer(
            id = winnerId,
            initialSeatIndex = 0,
            hand = Hand(
                tiles = listOf(
                    identified(ExamplePairingTileType.invertedOneMan()),
                    identified(ExamplePairingTileType.invertedOneMan()),
                    identified(Tile.Numeric(Tile.Suit.Character, 2)),
                ),
                lastDrawn = identified(Tile.Numeric(Tile.Suit.Character, 2)),
            ),
            discardPile = ExamplePairingDiscardPile(),
            seatWind = Wind.EAST,
        )
        val table = TableState(
            id = Uuid.random(),
            players = listOf(
                winner,
                MahjongPlayer(
                    id = otherId,
                    initialSeatIndex = 1,
                    discardPile = ExamplePairingDiscardPile(),
                    seatWind = Wind.SOUTH,
                ),
            ),
            config = module.config,
            tileWall = TileWall(),
            dealerPlayerId = winnerId,
            components = ComponentSet.EMPTY.with(ExamplePairingComponents.BONUS_TILES, scoringState),
            turnPhase = TurnPhase.AwaitingAction,
        )

        val result = assertNotNull(module.declareTsumo(table, winner))
        assertEquals(1, (result.handValueResult as ExamplePairingHandValueResult).bonusTileCount)
        assertEquals(11, result.settlement.paymentsByPlayerId[otherId])
    }

    /**
     * 建立具有獨立身分的測試牌。
     *
     * @param tile 測試牌面。
     * @return 身分不與其他測試牌重複的牌張。
     */
    private fun identified(tile: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), tile)
}
