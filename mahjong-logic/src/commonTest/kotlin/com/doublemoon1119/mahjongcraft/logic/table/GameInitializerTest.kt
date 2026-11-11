package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.initializeRuleComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiWallProgress
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiDeclaration
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiPotPoints
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiWallProgress
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer.dealInitialHands
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiComponentRegistryForTests
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiDeclarationComponents
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 針對 [GameInitializer] 進行單元測試。
 *
 * 驗證開局時座位分配、初始手牌張數、牌山剩餘數量與初始分數是否正確。
 */
class GameInitializerTest {

    /** 提供開局與換局政策的日麻規則模組。 */
    private val module = RiichiRuleModule(id = "mahjongcraft:riichi", config = RiichiRuleConfig())

    /**
     * 驗證每位玩家皆被分配到唯一的座位，且座位數量與玩家人數一致。
     */
    @Test
    fun `test initialize assigns one unique seat per player`() {
        val playerIds = List(4) { Uuid.random() }
        val gameId = Uuid.random()

        val table = GameInitializer.initialize(gameId, playerIds, module).tableState

        assertEquals(4, table.players.size)
        assertEquals((0..3).toSet(), table.players.map { it.initialSeatIndex }.toSet())
        assertEquals(playerIds.toSet(), table.players.map { it.id }.toSet())
    }

    /**
     * 驗證每位玩家的初始手牌張數符合規則配置。
     */
    @Test
    fun `test initialize deals initial hand size per rule config`() {
        val playerIds = List(4) { Uuid.random() }
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module).tableState

        table.players.forEach { player ->
            assertEquals(module.config.initialHandSize, player.hand.tiles.size)
        }
    }

    /**
     * 驗證發牌後牌山剩餘數量等於「總牌數扣除王牌」再扣除所有玩家已發出的手牌——日麻已支援開門
     * 流程，發牌用的牌山不包含王牌。
     */
    @Test
    fun `test initialize leaves correct remaining wall count`() {
        val playerIds = List(4) { Uuid.random() }
        val totalTileCount = module.createWallFactory(module.config.maxPlayers).create().remainingCount
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module).tableState

        val expectedRemaining = totalTileCount - module.config.reservedTileCount - playerIds.size * module.config.initialHandSize
        assertEquals(expectedRemaining, table.tileWall.remainingCount)
    }

    /**
     * 驗證支援開門流程的規則（如日麻）開局時會產生非 null 的 [TableState.wallOpening]，且
     * [TableState.reservedWallTiles] 張數等於規則配置的保留牌張數。
     */
    @Test
    fun `test initialize resolves wall opening and reserved wall for a rule that supports it`() {
        val playerIds = List(4) { Uuid.random() }
        val result = GameInitializer.initialize(Uuid.random(), playerIds, module)
        val table = result.tableState

        assertTrue(table.wallOpening != null, "Riichi supports wall opening, wallOpening should not be null")
        assertEquals(module.config.reservedTileCount, table.reservedWallTiles.size)
        assertEquals(136, result.initialPhysicalWallLayout?.placements?.size)
        assertEquals(
            (table.tileWall.getAllTiles() + table.reservedWallTiles).map { it.id }.toSet(),
            table.physicalWallLayout?.placements?.keys,
        )
    }

    /**
     * 驗證活牌摸牌堆、已發出的手牌、王牌三者互不重疊，且合計等於牌山總張數——確認開門重排沒有
     * 遺失或重複任何一張牌。
     */
    @Test
    fun `test initialize accounts for every tile across hands, remaining wall, and reserved wall`() {
        val playerIds = List(4) { Uuid.random() }
        val totalTileCount = module.createWallFactory(module.config.maxPlayers).create().remainingCount
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module).tableState

        val dealtTileIds = table.players.flatMap { it.hand.tiles }.map { it.id }
        val remainingTileIds = table.tileWall.getAllTiles().map { it.id }
        val reservedWallTileIds = table.reservedWallTiles.map { it.id }
        val allTileIds = dealtTileIds + remainingTileIds + reservedWallTileIds

        assertEquals(totalTileCount, allTileIds.size)
        assertEquals(totalTileCount, allTileIds.toSet().size)
    }

    /**
     * 驗證 [GameInitializer.dealInitialHands] 真的是「輪流各摸一批」（真實麻將的發牌方式），不是讓
     * 某位玩家一次連續摸完整手牌——用可控制牌張順序的 [TileWall] 直接測試，牌山依序是索引
     * `0..51`（52 張，剛好夠 4 人 × 13 張），日麻批次節奏固定 `[4, 4, 4, 1]`：每位玩家應該拿到
     * 4 個不連續的區段（索引間隔 16，最後一輪間隔 4），不是單一連續的 13 張區段。
     */
    @Test
    fun `test dealInitialHands deals in rotation instead of one player at a time`() {
        val orderedTiles = List(52) { index -> FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (index % 9) + 1)) }
        val wall = TileWall(orderedTiles)
        val playerIds = List(4) { Uuid.random() }

        val (hands, remainingWall) = module.dealInitialHands(wall, playerIds)

        val indexById = orderedTiles.withIndex().associate { (index, tile) -> tile.id to index }
        val handIndices = hands.map { hand -> hand.map { tile -> indexById.getValue(tile.id) } }
        assertEquals(listOf(0, 1, 2, 3, 16, 17, 18, 19, 32, 33, 34, 35, 48), handIndices[0])
        assertEquals(listOf(4, 5, 6, 7, 20, 21, 22, 23, 36, 37, 38, 39, 49), handIndices[1])
        assertEquals(listOf(8, 9, 10, 11, 24, 25, 26, 27, 40, 41, 42, 43, 50), handIndices[2])
        assertEquals(listOf(12, 13, 14, 15, 28, 29, 30, 31, 44, 45, 46, 47, 51), handIndices[3])
        assertEquals(0, remainingWall.remainingCount)
    }

    /**
     * 驗證 [TableState.init] 已在開局時套用，所有玩家分數皆為規則配置的初始分數。
     */
    @Test
    fun `test initialize sets initial score from config`() {
        val playerIds = List(4) { Uuid.random() }
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module).tableState

        table.players.forEach { player ->
            assertEquals(module.config.scoreConfig.initialScore, player.score)
        }
    }

    /**
     * 驗證座位分配確實隨機，而不是每次都固定把 EAST 分配給玩家列表的第一位。
     */
    @Test
    fun `test initialize randomizes seat assignment across calls`() {
        val playerIds = List(4) { Uuid.random() }

        val firstPlayerSeats = List(20) {
            GameInitializer.initialize(Uuid.random(), playerIds, module)
                .tableState.players.first { it.id == playerIds[0] }.initialSeatIndex
        }

        assertTrue(firstPlayerSeats.toSet().size > 1, "Seat assignment should vary across multiple initializations.")
    }

    /**
     * 驗證玩家人數不在規則允許範圍內時會拋出例外。
     */
    @Test
    fun `test initialize throws when player count out of range`() {
        val tooFewPlayerIds = listOf(Uuid.random())

        assertFailsWith<IllegalArgumentException> {
            GameInitializer.initialize(Uuid.random(), tooFewPlayerIds, module)
        }
    }

    /** 正式組件建立後具有零供託與本局初始進度。 */
    @Test
    fun `initialization creates required riichi table components`() {
        val table = initializeComponents(GameInitializer.initialize(Uuid.random(), List(4) { Uuid.random() }, module))
        assertEquals(0, table.riichiPotPoints())
        assertEquals(RiichiWallProgress(), table.riichiWallProgress())
    }

    /**
     * 尚未宣告的玩家沒有立直組件，也不需要整包日麻玩家狀態。
     */
    @Test
    fun `initial riichi players have no declaration or monolithic state`() {
        val playerIds = List(4) { Uuid.random() }
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module).tableState

        table.players.forEach { player ->
            assertNull(player.riichiDeclaration)
        }
    }

    /** 開局與換局後都從莊家等摸牌開始，不留上一局的階段。 */
    @Test
    fun `initialize and next round start awaiting the dealer draw`() {
        val opened = GameInitializer.initialize(Uuid.random(), List(4) { Uuid.random() }, module).tableState
        assertEquals(TurnPhase.AwaitingDraw, opened.turnPhase)
        assertEquals(opened.dealerIndex, opened.currentPlayerIndex)

        val advancement = roundAdvancement()
        val previous = FakeTableStateFactory.create(
            players = advancement.players,
            config = module.config,
            turnPhase = TurnPhase.RoundOver(RoundConclusion.Win(setOf(advancement.players.first().id))),
        )
        val next = GameInitializer.startNextRound(previous.id, advancement, module).tableState
        assertEquals(TurnPhase.AwaitingDraw, next.turnPhase)
        assertEquals(next.dealerIndex, next.currentPlayerIndex)
    }

    /** 換局只保留供託，補牌及指示牌進度重新開始。 */
    @Test
    fun `new round retains pot points and resets wall progress`() {
        val advancement = roundAdvancement()
        val previous = FakeTableStateFactory.create(
            players = advancement.players,
            config = module.config,
            components = riichiTableComponents(
                potPoints = 2000,
                wallProgress = RiichiWallProgress(completedSupplementalDrawCount = 1, revealedKanDoraCount = 1),
            ),
        )
        val table = initializeComponents(
            GameInitializer.startNextRound(previous.id, advancement, module),
            previous,
        )
        assertEquals(2000, table.riichiPotPoints())
        assertEquals(RiichiWallProgress(), table.riichiWallProgress())
        assertEquals(1, previous.riichiWallProgress().completedSupplementalDrawCount)
    }

    /** 上一局槓牌不會改變新一局指示牌在保留區中的位置。 */
    @Test
    fun `new round indicator positions do not depend on previous kan draws`() {
        val advancement = roundAdvancement()
        val previous = FakeTableStateFactory.create(
            players = advancement.players,
            config = module.config,
            components = riichiTableComponents(
                wallProgress = RiichiWallProgress(completedSupplementalDrawCount = 1, revealedKanDoraCount = 1),
            ),
        )
        val afterKan = initializeComponents(
            GameInitializer.startNextRound(previous.id, advancement, module),
            previous,
        )
        val withoutKan = initializeComponents(
            GameInitializer.startNextRound(previous.id, advancement, module),
            previous.copy(components = riichiTableComponents()),
        )
        assertEquals(listOf(module.config.rinshanTileCount), indicatorPositions(afterKan))
        assertEquals(indicatorPositions(withoutKan), indicatorPositions(afterKan))
    }

    /**
     * 取得公開指示牌在王牌區中的位置，與牌張 UUID 無關以便跨局比較。
     *
     * @param table 要檢查的桌況。
     * @return 目前公開指示牌在王牌區中的索引。
     */
    private fun indicatorPositions(table: TableState): List<Int> {
        val visibleIds = table.riichiWallProgress().getDoraIndicators(table).first.mapTo(mutableSetOf()) { it.id }
        return table.reservedWallTiles.withIndex().filter { it.value.id in visibleIds }.map { it.index }
    }

    /**
     * 建立測試用的連莊/過莊判定結果。
     *
     * @return 含四位玩家及預設局數、場風的測試結果。
     */
    private fun roundAdvancement(): RoundAdvancementResult {
        val players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map(FakeMahjongPlayerFactory::create)
        return RoundAdvancementResult(
            players = players,
            dealerPlayerId = players.first().id,
            roundNumber = 2,
            comboCount = 0,
            prevalentWind = Wind.EAST,
        )
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 延續玩家分數，不像 [GameInitializer.initialize] 那樣重置。
     */
    @Test
    fun `test startNextRound preserves player scores`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST).copy(score = 32000)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH).copy(score = 18000)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST).copy(score = 25000)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH).copy(score = 25000)
        val roundAdvancement = RoundAdvancementResult(
            players = listOf(p1, p2, p3, p4),
            dealerPlayerId = p1.id,
            roundNumber = 2,
            comboCount = 0,
            prevalentWind = Wind.EAST,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, module).tableState

        assertEquals(32000, table.players.first { it.id == p1.id }.score)
        assertEquals(18000, table.players.first { it.id == p2.id }.score)
        assertEquals(25000, table.players.first { it.id == p3.id }.score)
        assertEquals(25000, table.players.first { it.id == p4.id }.score)
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 不會重新洗座位順序，維持 [RoundAdvancementResult.players] 傳入的順序。
     */
    @Test
    fun `test startNextRound does not reshuffle seat order`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p2 = FakeMahjongPlayerFactory.create(Wind.EAST)
        val p3 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val p4 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val roundAdvancement = RoundAdvancementResult(
            players = listOf(p1, p2, p3, p4),
            dealerPlayerId = p2.id,
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, module).tableState

        assertEquals(listOf(p1.id, p2.id, p3.id, p4.id), table.players.map { it.id })
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 正確套用連莊/過莊判定的局數、本場數、場風與各玩家方位。
     */
    @Test
    fun `test startNextRound applies round advancement result`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST).copy(seatWind = Wind.NORTH)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH).copy(seatWind = Wind.EAST)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST).copy(seatWind = Wind.SOUTH)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH).copy(seatWind = Wind.WEST)
        val roundAdvancement = RoundAdvancementResult(
            players = listOf(p1, p2, p3, p4),
            dealerPlayerId = p2.id,
            roundNumber = 3,
            comboCount = 1,
            prevalentWind = Wind.SOUTH,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, module).tableState

        assertEquals(3, table.roundNumber)
        assertEquals(1, table.comboCount)
        assertEquals(Wind.SOUTH, table.prevalentWind)
        assertEquals(Wind.NORTH, table.players.first { it.id == p1.id }.seatWind)
        assertEquals(Wind.EAST, table.players.first { it.id == p2.id }.seatWind)
        assertEquals(Wind.SOUTH, table.players.first { it.id == p3.id }.seatWind)
        assertEquals(Wind.WEST, table.players.first { it.id == p4.id }.seatWind)
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 的 `currentPlayerIndex` 指向新莊家（`seatWind == EAST`）。
     */
    @Test
    fun `test startNextRound sets currentPlayerIndex to the new dealer`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST).copy(seatWind = Wind.SOUTH)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH).copy(seatWind = Wind.WEST)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST).copy(seatWind = Wind.NORTH)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH).copy(seatWind = Wind.EAST)
        val roundAdvancement = RoundAdvancementResult(
            players = listOf(p1, p2, p3, p4),
            dealerPlayerId = p4.id,
            roundNumber = 2,
            comboCount = 0,
            prevalentWind = Wind.EAST,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, module).tableState

        assertEquals(3, table.currentPlayerIndex, "P4 (index 3) holds East, so they are the new dealer.")
        assertEquals(p4.id, table.currentPlayer.id)
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 正確重置每局狀態：手牌重新發放、牌河清空、規則特有的
     * 玩家狀態重新建立、當巡放過的牌與動作歷史皆清空——即使傳入的玩家先前帶有立直等「髒」狀態。
     */
    @Test
    fun `test startNextRound resets per-hand player state`() {
        val declarationTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val dirtyPlayer = FakeMahjongPlayerFactory.create(
            Wind.EAST,
            hand = Hand(tiles = listOf(FakeIdentifiedTileFactory.create(Tile.Honor.East))),
            discardPile = RiichiDiscardPile().discardTile(declarationTile),
            components = riichiDeclarationComponents(declarationTile, isIppatsu = true),
        ).copy(
            passedTilesInRound = setOf(Tile.Honor.West),
            actionHistory = listOf(GameAction.Draw),
        )
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val roundAdvancement = RoundAdvancementResult(
            players = listOf(dirtyPlayer, p2, p3, p4),
            dealerPlayerId = dirtyPlayer.id,
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
        )

        val previous = FakeTableStateFactory.create(players = roundAdvancement.players, config = module.config)
        val components = riichiComponentRegistryForTests()
        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, module)
            .initializeRuleComponents(module.id, components, previous).tableState

        val updated = table.players.first { it.id == dirtyPlayer.id }
        assertEquals(module.config.initialHandSize, updated.hand.tiles.size)
        assertTrue(updated.discardPile.entries.isEmpty())
        assertNull(updated.riichiDeclaration)
        assertTrue(updated.passedTilesInRound.isEmpty())
        assertTrue(updated.actionHistory.isEmpty())
    }

    /** 供託跨局保留時，不會在新局再次扣除玩家點數。 */
    @Test
    fun `retaining pot points does not charge players again`() {
        val advancement = roundAdvancement()
        val previous = FakeTableStateFactory.create(
            players = advancement.players,
            config = module.config,
            components = riichiTableComponents(potPoints = 2000),
        )
        val table = initializeComponents(
            GameInitializer.startNextRound(previous.id, advancement, module),
            previous,
        )
        assertEquals(2000, table.riichiPotPoints())
        assertEquals(previous.players.associate { it.id to it.score }, table.players.associate { it.id to it.score })
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 產生的新一局沒有反應視窗。
     */
    @Test
    fun `test startNextRound clears pending reaction`() {
        val players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(it) }
        val roundAdvancement = RoundAdvancementResult(
            players = players,
            dealerPlayerId = players.first().id,
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, module).tableState

        assertNull(table.pendingReaction)
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 發牌後牌山剩餘數量等於「總牌數扣除王牌」再扣除所有
     * 玩家的發牌數——日麻已支援開門流程，發牌用的牌山不包含王牌。
     */
    @Test
    fun `test startNextRound leaves correct remaining wall count`() {
        val players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(it) }
        val roundAdvancement = RoundAdvancementResult(
            players = players,
            dealerPlayerId = players.first().id,
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
        )
        val totalTileCount = module.createWallFactory(module.config.maxPlayers).create().remainingCount

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, module).tableState

        val expectedRemaining = totalTileCount - module.config.reservedTileCount - players.size * module.config.initialHandSize
        assertEquals(expectedRemaining, table.tileWall.remainingCount)
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 連莊仍會重新擲骰開門，產生非 null 的
     * [TableState.wallOpening] 與符合規則配置張數的 [TableState.reservedWallTiles]。
     */
    @Test
    fun `test startNextRound resolves a fresh wall opening and reserved wall`() {
        val players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(it) }
        val roundAdvancement = RoundAdvancementResult(
            players = players,
            dealerPlayerId = players.first().id,
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, module).tableState

        assertTrue(table.wallOpening != null, "Riichi supports wall opening, wallOpening should not be null")
        assertEquals(module.config.reservedTileCount, table.reservedWallTiles.size)
    }

    /**
     * 以正式組件生命週期完成已建立的開局候選資料。
     *
     * @param initialization 已建立牌山、座位及手牌的候選結果。
     * @param previous 上一局的權威桌況；第一局為 null。
     * @return 已建立或換局轉換組件的桌況。
     */
    private fun initializeComponents(initialization: GameInitializationResult, previous: TableState? = null): TableState {
        val registry = riichiComponentRegistryForTests()
        return initialization.initializeRuleComponents(module.id, registry, previous).tableState
    }
}
