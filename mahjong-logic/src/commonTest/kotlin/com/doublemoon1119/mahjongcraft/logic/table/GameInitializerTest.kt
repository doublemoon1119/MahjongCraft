package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 針對 [GameInitializer] 進行單元測試。
 *
 * 驗證開局時座位分配、初始手牌張數、牌山剩餘數量與初始分數是否正確。
 */
class GameInitializerTest {

    private val module = RiichiRuleModule(id = "mahjongcraft:riichi", config = RiichiRuleConfig())

    /**
     * 驗證每位玩家皆被分配到唯一的座位，且座位數量與玩家人數一致。
     */
    @Test
    fun `test initialize assigns one unique seat per player`() {
        val playerIds = List(4) { Uuid.random() }
        val gameId = Uuid.random()

        val table = GameInitializer.initialize(gameId, playerIds, module)

        assertEquals(4, table.players.size)
        assertEquals(setOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH), table.players.map { it.initialSeat }.toSet())
        assertEquals(playerIds.toSet(), table.players.map { it.id }.toSet())
    }

    /**
     * 驗證每位玩家的初始手牌張數符合規則配置。
     */
    @Test
    fun `test initialize deals initial hand size per rule config`() {
        val playerIds = List(4) { Uuid.random() }
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module)

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
        val totalTileCount = module.createWallFactory().create().remainingCount
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module)

        val expectedRemaining = totalTileCount - module.config.deadTileCount - playerIds.size * module.config.initialHandSize
        assertEquals(expectedRemaining, table.tileWall.remainingCount)
    }

    /**
     * 驗證支援開門流程的規則（如日麻）開局時會產生非 null 的 [TableState.wallOpening]，且
     * [TableState.initialDeadWall] 張數等於規則配置的王牌張數。
     */
    @Test
    fun `test initialize resolves wall opening and dead wall for a rule that supports it`() {
        val playerIds = List(4) { Uuid.random() }
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module)

        assertTrue(table.wallOpening != null, "Riichi supports wall opening, wallOpening should not be null")
        assertEquals(module.config.deadTileCount, table.initialDeadWall.size)
    }

    /**
     * 驗證活牌摸牌堆、已發出的手牌、王牌三者互不重疊，且合計等於牌山總張數——確認開門重排沒有
     * 遺失或重複任何一張牌。
     */
    @Test
    fun `test initialize accounts for every tile across hands, remaining wall, and dead wall`() {
        val playerIds = List(4) { Uuid.random() }
        val totalTileCount = module.createWallFactory().create().remainingCount
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module)

        val dealtTileIds = table.players.flatMap { it.hand.tiles }.map { it.id }
        val remainingTileIds = table.tileWall.getAllTiles().map { it.id }
        val deadWallTileIds = table.initialDeadWall.map { it.id }
        val allTileIds = dealtTileIds + remainingTileIds + deadWallTileIds

        assertEquals(totalTileCount, allTileIds.size)
        assertEquals(totalTileCount, allTileIds.toSet().size)
    }

    /**
     * 驗證 [TableState.init] 已在開局時套用，所有玩家分數皆為規則配置的初始分數。
     */
    @Test
    fun `test initialize sets initial score from config`() {
        val playerIds = List(4) { Uuid.random() }
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module)

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
                .players.first { it.id == playerIds[0] }.initialSeat
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

    /**
     * 驗證開局時會透過規則模組建立初始動態桌況狀態，而不是維持預設的 null
     * （否則寶牌指示器等依賴 dynamicRuleState 的功能會永遠無法顯示）。
     */
    @Test
    fun `test initialize sets dynamic rule state from module`() {
        val playerIds = List(4) { Uuid.random() }
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module)

        assertIs<RiichiDynamicState>(table.dynamicRuleState)
    }

    /**
     * 驗證開局時會透過規則模組為每位玩家建立初始玩家規則狀態，而不是維持預設的 null
     * （否則立直宣告等需要寫入 playerRuleState 的功能會沒有初始狀態可以 copy）。
     */
    @Test
    fun `test initialize sets player rule state from module for every player`() {
        val playerIds = List(4) { Uuid.random() }
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module)

        table.players.forEach { player ->
            assertIs<RiichiPlayerState>(player.playerRuleState)
        }
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
            roundNumber = 2,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            isMatchOver = false,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, previousDynamicRuleState = null, module)

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
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            isMatchOver = false,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, previousDynamicRuleState = null, module)

        assertEquals(listOf(p1.id, p2.id, p3.id, p4.id), table.players.map { it.id })
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 正確套用連莊/過莊判定的局數、本場數、場風與各玩家方位。
     */
    @Test
    fun `test startNextRound applies round advancement result`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST).copy(currentWind = Wind.NORTH)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH).copy(currentWind = Wind.EAST)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST).copy(currentWind = Wind.SOUTH)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH).copy(currentWind = Wind.WEST)
        val roundAdvancement = RoundAdvancementResult(
            players = listOf(p1, p2, p3, p4),
            roundNumber = 3,
            comboCount = 1,
            prevalentWind = Wind.SOUTH,
            isMatchOver = false,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, previousDynamicRuleState = null, module)

        assertEquals(3, table.roundNumber)
        assertEquals(1, table.comboCount)
        assertEquals(Wind.SOUTH, table.prevalentWind)
        assertEquals(Wind.NORTH, table.players.first { it.id == p1.id }.currentWind)
        assertEquals(Wind.EAST, table.players.first { it.id == p2.id }.currentWind)
        assertEquals(Wind.SOUTH, table.players.first { it.id == p3.id }.currentWind)
        assertEquals(Wind.WEST, table.players.first { it.id == p4.id }.currentWind)
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 的 `currentPlayerIndex` 指向新莊家（`currentWind == EAST`）。
     */
    @Test
    fun `test startNextRound sets currentPlayerIndex to the new dealer`() {
        val p1 = FakeMahjongPlayerFactory.create(Wind.EAST).copy(currentWind = Wind.SOUTH)
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH).copy(currentWind = Wind.WEST)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST).copy(currentWind = Wind.NORTH)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH).copy(currentWind = Wind.EAST)
        val roundAdvancement = RoundAdvancementResult(
            players = listOf(p1, p2, p3, p4),
            roundNumber = 2,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            isMatchOver = false,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, previousDynamicRuleState = null, module)

        assertEquals(3, table.currentPlayerIndex, "P4 (index 3) holds East, so they are the new dealer.")
        assertEquals(p4.id, table.currentPlayer.id)
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 正確重置每局狀態：手牌重新發放、牌河清空、規則特有的
     * 玩家狀態重新建立、當巡放過的牌與動作歷史皆清空——即使傳入的玩家先前帶有立直等「髒」狀態。
     */
    @Test
    fun `test startNextRound resets per-hand player state`() {
        val dirtyPlayer = FakeMahjongPlayerFactory.create(
            Wind.EAST,
            hand = Hand(tiles = listOf(FakeIdentifiedTileFactory.create(Tile.Honor.East))),
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(isIppatsu = true),
        ).copy(
            passedTilesInRound = setOf(Tile.Honor.West),
            actionHistory = listOf(GameAction.Draw),
        )
        val p2 = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create(Wind.WEST)
        val p4 = FakeMahjongPlayerFactory.create(Wind.NORTH)
        val roundAdvancement = RoundAdvancementResult(
            players = listOf(dirtyPlayer, p2, p3, p4),
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            isMatchOver = false,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, previousDynamicRuleState = null, module)

        val updated = table.players.first { it.id == dirtyPlayer.id }
        assertEquals(module.config.initialHandSize, updated.hand.tiles.size)
        assertTrue(updated.discardPile.entries.isEmpty())
        assertEquals(RiichiPlayerState(), updated.playerRuleState)
        assertTrue(updated.passedTilesInRound.isEmpty())
        assertTrue(updated.actionHistory.isEmpty())
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 單純延續傳入的動態桌況狀態，不會重置或另外處理
     * （例如供託是否歸零，已經在胡牌結算階段決定好，這裡只負責套用）。
     */
    @Test
    fun `test startNextRound carries over dynamic rule state unchanged`() {
        val players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(it) }
        val roundAdvancement = RoundAdvancementResult(
            players = players,
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            isMatchOver = false,
        )
        val carriedOverState = RiichiDynamicState(riichiStickCount = 2)

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, carriedOverState, module)

        assertEquals(carriedOverState, table.dynamicRuleState)
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 產生的新一局沒有反應視窗。
     */
    @Test
    fun `test startNextRound clears pending reaction`() {
        val players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(it) }
        val roundAdvancement = RoundAdvancementResult(
            players = players,
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            isMatchOver = false,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, previousDynamicRuleState = null, module)

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
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            isMatchOver = false,
        )
        val totalTileCount = module.createWallFactory().create().remainingCount

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, previousDynamicRuleState = null, module)

        val expectedRemaining = totalTileCount - module.config.deadTileCount - players.size * module.config.initialHandSize
        assertEquals(expectedRemaining, table.tileWall.remainingCount)
    }

    /**
     * 驗證 [GameInitializer.startNextRound] 連莊仍會重新擲骰開門，產生非 null 的
     * [TableState.wallOpening] 與符合規則配置張數的 [TableState.initialDeadWall]。
     */
    @Test
    fun `test startNextRound resolves a fresh wall opening and dead wall`() {
        val players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(it) }
        val roundAdvancement = RoundAdvancementResult(
            players = players,
            roundNumber = 1,
            comboCount = 0,
            prevalentWind = Wind.EAST,
            isMatchOver = false,
        )

        val table = GameInitializer.startNextRound(Uuid.random(), roundAdvancement, previousDynamicRuleState = null, module)

        assertTrue(table.wallOpening != null, "Riichi supports wall opening, wallOpening should not be null")
        assertEquals(module.config.deadTileCount, table.initialDeadWall.size)
    }
}
