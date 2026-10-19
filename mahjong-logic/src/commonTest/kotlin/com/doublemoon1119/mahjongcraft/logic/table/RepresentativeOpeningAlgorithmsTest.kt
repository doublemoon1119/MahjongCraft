package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollSource
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRollRequest
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningCompletion
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningContext
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningProcedure
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningStep
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證代表性的起家與開門算法，以及測試自訂變體能由同一套開局程序表達。 */
class RepresentativeOpeningAlgorithmsTest {
    /** 驗證內建四人日麻的一次雙骰開門，涵蓋所有可能的點數總和。 */
    @Test
    fun `riichi single roll resolves every dice total`() {
        val module = RiichiRuleModule(id = "test:riichi", config = RiichiRuleConfig())
        val playerIds = List(4) { Uuid.random() }

        (2..12).forEach { total ->
            val first = minOf(6, total - 1)
            val second = total - first
            val table = GameInitializer.initialize(
                id = Uuid.random(),
                playerIds = playerIds,
                module = module,
                diceRollSource = queuedDiceSource(listOf(listOf(first, second))),
            ).tableState

            assertEquals(1, table.openingDiceRolls.size)
            assertEquals(total, table.openingDiceRolls.single().dice.total)
            assertEquals((total - 1) % 4, table.wallOpening?.wallSideOffsetFromDealer)
            assertEquals(total, table.wallOpening?.stacksFromRight)
        }
    }

    /** 驗證測試用的兩輪選莊變體：第二輪同時決定正式莊家與開門位置。 */
    @Test
    fun `two roll selection chooses dealer and opening from second roll`() {
        val table = initializeAndVerify(
            procedure = twoRollSelectionProcedure(),
            dice = listOf(listOf(3, 3), listOf(1, 2)),
        )

        val rolls = table.openingDiceRolls
        assertEquals(2, rolls.size)
        assertEquals(6, rolls[0].dice.total)
        assertEquals(3, rolls[1].dice.total)
        assertEquals(table.players[0].id, rolls[0].playerId)
        assertEquals(table.players[1].id, rolls[1].playerId)
        assertEquals(table.players[3].id, table.dealerPlayerId)
        assertEquals(Wind.EAST, table.currentPlayer.seatWind)
        assertEquals(rolls[0].playerId, table.players.single { it.initialSeatIndex == 1 }.id)
        assertEquals(rolls[1].playerId, table.players.single { it.initialSeatIndex == 2 }.id)
        assertEquals(table.dealerPlayerId, table.currentPlayer.id)
        assertEquals(0, table.players.single { it.id == table.dealerPlayerId }.initialSeatIndex)
        assertEquals(WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 3), table.wallOpening)
        assertTrue(table.players.all { it.hand.tiles.size == 13 })
    }

    /** 確認兩輪選莊可以依骰子結果選出固定座位順序中的每一位正式莊家。 */
    @Test
    fun `two roll selection can choose every roster dealer`() {
        // 第一輪固定六點，從位置零數到位置一；第二輪二至五點分別選出位置二、三、零、一。
        listOf(2 to 2, 3 to 3, 4 to 0, 5 to 1).forEach { (secondTotal, dealerIndex) ->
            val table = initializeAndVerify(
                procedure = twoRollSelectionProcedure(),
                dice = listOf(listOf(3, 3), diceForTotal(secondTotal).values),
            )
            assertEquals(table.players[dealerIndex].id, table.dealerPlayerId)
            assertEquals(table.players[1].id, table.openingDiceRolls[1].playerId)
            assertEquals(WallOpening(0, secondTotal), table.wallOpening)
        }
    }

    /** 驗證兩次選莊後再由正式莊家擲骰開門的常見日麻流程。 */
    @Test
    fun `three roll initial dealer procedure records all actors and opens from formal dealer`() {
        val result = initializeAndVerify(
            procedure = threeRollInitialDealerProcedure(),
            dice = listOf(listOf(3, 3), listOf(1, 2), listOf(2, 5)),
        )

        val rolls = result.openingDiceRolls
        assertEquals(3, rolls.size)
        assertEquals(6, rolls[0].dice.total)
        assertEquals(3, rolls[1].dice.total)
        assertEquals(7, rolls[2].dice.total)
        assertEquals(rolls[0].playerId, result.players.single { it.initialSeatIndex == 1 }.id)
        assertEquals(rolls[1].playerId, result.players.single { it.initialSeatIndex == 2 }.id)
        assertEquals(result.dealerPlayerId, rolls[2].playerId)
        assertEquals(result.players[0].id, rolls[0].playerId)
        assertEquals(result.players[1].id, rolls[1].playerId)
        assertEquals(result.players[3].id, result.dealerPlayerId)
        assertEquals(2, result.wallOpening?.wallSideOffsetFromDealer)
        assertEquals(7, result.wallOpening?.stacksFromRight)
        assertEquals(result.dealerPlayerId, result.currentPlayer.id)
        assertEquals(0, result.players.single { it.id == result.dealerPlayerId }.initialSeatIndex)
        assertTrue(result.players.all { it.hand.tiles.isNotEmpty() })
    }

    /** 驗證測試用的兩輪合計切口跨過一面十七墩牌牆時，開門位置會移到前一面第一墩。 */
    @Test
    fun `two roll summed cut crosses a wall side and normalizes opening`() {
        val players = List(4) { FakeMahjongPlayerFactory.create(initialSeatIndex = it) }
        val dealerId = players.first().id
        var drawOrder = emptyList<IdentifiedTile>()
        val initialized = GameInitializer.startNextRound(
            gameId = Uuid.random(),
            roundAdvancement = RoundAdvancementResult(
                players = players,
                dealerPlayerId = dealerId,
                roundNumber = 2,
                comboCount = 0,
                prevalentWind = Wind.EAST,
            ),
            previousDynamicRuleState = null,
            module = module(twoRollSummedCutProcedure()) { drawOrder = it },
            diceRollSource = queuedDiceSource(
                listOf(
                    listOf(3, 3),
                    listOf(6, 6),
                ),
            ),
        )
        val result = initialized.tableState

        assertEquals(listOf(6, 12), result.openingDiceRolls.map { it.dice.total })
        assertEquals(players[0].id, result.openingDiceRolls[0].playerId)
        assertEquals(players[1].id, result.openingDiceRolls[1].playerId)
        assertEquals(dealerId, result.dealerPlayerId)
        assertEquals(WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 1), result.wallOpening)
        assertEquals(TileWallPosition(side = 0, stack = 1, layer = 1), initialized.wallStructure?.get(drawOrder.first().id))
        assertDealOrder(result, drawOrder)
    }

    /** 確認後續局只由既定莊家擲一次開門骰，不會重新選擇正式莊家。 */
    @Test
    fun `later round uses one roll with the progression dealer`() {
        listOf(twoRollSelectionProcedure(), threeRollInitialDealerProcedure()).forEach { procedure ->
            val players = List(4) { FakeMahjongPlayerFactory.create(initialSeatIndex = it) }
            val dealerId = players[2].id
            val result = GameInitializer.startNextRound(
                gameId = Uuid.random(),
                roundAdvancement = RoundAdvancementResult(
                    players = players,
                    dealerPlayerId = dealerId,
                    roundNumber = 2,
                    comboCount = 0,
                    prevalentWind = Wind.EAST,
                ),
                previousDynamicRuleState = null,
                module = module(procedure),
                diceRollSource = queuedDiceSource(listOf(listOf(2, 2))),
            ).tableState

            assertEquals(1, result.openingDiceRolls.size)
            assertEquals(dealerId, result.openingDiceRolls.single().playerId)
            assertEquals(dealerId, result.dealerPlayerId)
            assertEquals(dealerId, result.currentPlayer.id)
        }
    }

    /**
     * 由真實初始化器執行程序，核對固定名單、座風與從莊家開始的完整配牌順序。
     *
     * @param procedure 待驗證的開局程序。
     * @param dice 各輪固定骰子點數。
     * @return 已驗證的第一局桌況。
     */
    private fun initializeAndVerify(procedure: RoundOpeningProcedure, dice: List<List<Int>>): TableState {
        var roster = emptyList<Uuid>()
        var drawOrder = emptyList<IdentifiedTile>()
        val recordingProcedure = object : RoundOpeningProcedure by procedure {
            override fun next(context: RoundOpeningContext): RoundOpeningStep {
                roster = context.playerIdsInTurnOrder
                return procedure.next(context)
            }
        }
        val table = GameInitializer.initialize(
            id = Uuid.random(),
            playerIds = List(4) { Uuid.random() },
            module = module(recordingProcedure) { drawOrder = it },
            diceRollSource = queuedDiceSource(dice),
        ).tableState
        assertEquals(roster, table.players.map { it.id })
        assertEquals(table.dealerPlayerId, table.currentPlayer.id)
        val dealerIndex = roster.indexOf(table.dealerPlayerId)
        table.players.forEachIndexed { index, player ->
            assertEquals((index - dealerIndex).mod(4), player.initialSeatIndex)
            assertEquals(listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH)[(index - dealerIndex).mod(4)], player.seatWind)
        }
        assertDealOrder(table, drawOrder)
        return table
    }

    /**
     * 核對四、四、四、一張的四輪配牌，莊家必須先拿第一批，其他人依固定回合順序取牌。
     *
     * @param table 已配完牌、尚未執行莊家首次摸牌的桌況。
     * @param drawOrder 開門後尚未配牌的完整摸牌順序。
     */
    private fun assertDealOrder(table: TableState, drawOrder: List<IdentifiedTile>) {
        val expected = List(4) { mutableListOf<Uuid>() }
        var cursor = 0
        listOf(4, 4, 4, 1).forEach { batch ->
            expected.indices.forEach { offset ->
                expected[offset] += drawOrder.subList(cursor, cursor + batch).map { it.id }
                cursor += batch
            }
        }
        val dealerIndex = table.players.indexOfFirst { it.id == table.dealerPlayerId }
        expected.indices.forEach { offset ->
            assertEquals(expected[offset], table.players[(dealerIndex + offset) % 4].hand.tiles.map { it.id })
        }
    }

    /**
     * 建立只替換開局程序的測試規則，並在配牌前記錄實際摸牌順序。
     *
     * @param procedure 測試指定的開局程序。
     * @param onDrawOrder 接收開門後的完整摸牌順序。
     * @return 保留內建日麻其他行為的測試規則。
     */
    private fun module(
        procedure: RoundOpeningProcedure,
        onDrawOrder: (List<IdentifiedTile>) -> Unit = {},
    ): MahjongRuleModule<RiichiRuleConfig> {
        val delegate = RiichiRuleModule(id = "test:riichi", config = RiichiRuleConfig())
        return object : MahjongRuleModule<RiichiRuleConfig> by delegate {
            override fun createRoundOpeningProcedure(playerCount: Int): RoundOpeningProcedure = procedure

            override fun createWallLayout(playerCount: Int): TileWallLayout {
                val layout = requireNotNull(delegate.createWallLayout(playerCount))
                return object : TileWallLayout by layout {
                    override fun resolve(
                        shuffledTiles: List<IdentifiedTile>,
                        opening: WallOpening,
                    ): TileWallLayoutResult {
                        val result = layout.resolve(shuffledTiles, opening)
                        onDrawOrder(result.drawOrder)
                        return result
                    }
                }
            }
        }
    }

    /** 建立兩輪選莊並由第二輪同時決定開門的測試變體。 */
    private fun twoRollSelectionProcedure(): RoundOpeningProcedure = object : RoundOpeningProcedure {
        override val supportedDiceCounts: Set<Int> = setOf(2)

        override fun next(context: RoundOpeningContext): RoundOpeningStep {
            if (!context.firstRound) {
                return if (context.rolls.isEmpty()) {
                    RoundOpeningStep.Roll(
                        OpeningDiceRollRequest(requireNotNull(context.fixedDealerPlayerId), 2),
                    )
                } else {
                    RoundOpeningStep.Complete(
                        RoundOpeningCompletion(
                            dealerPlayerId = requireNotNull(context.fixedDealerPlayerId),
                            wallOpening = WallOpening(
                                wallSideOffsetFromDealer = countedIndex(
                                    startIndex = 0,
                                    count = context.rolls.single().dice.total,
                                    size = context.playerIdsInTurnOrder.size,
                                ),
                                stacksFromRight = context.rolls.single().dice.total,
                            ),
                        ),
                    )
                }
            }

            return when (context.rolls.size) {
                0 -> RoundOpeningStep.Roll(OpeningDiceRollRequest(context.playerIdsInTurnOrder[0], 2))
                1 -> {
                    val provisionalIndex = countedIndex(
                        startIndex = 0,
                        count = context.rolls[0].dice.total,
                        size = context.playerIdsInTurnOrder.size,
                    )
                    RoundOpeningStep.Roll(OpeningDiceRollRequest(context.playerIdsInTurnOrder[provisionalIndex], 2))
                }
                else -> {
                    val provisionalIndex = countedIndex(
                        startIndex = 0,
                        count = context.rolls[0].dice.total,
                        size = context.playerIdsInTurnOrder.size,
                    )
                    val dealerIndex = countedIndex(
                        startIndex = provisionalIndex,
                        count = context.rolls[1].dice.total,
                        size = context.playerIdsInTurnOrder.size,
                    )
                    RoundOpeningStep.Complete(
                        RoundOpeningCompletion(
                            dealerPlayerId = context.playerIdsInTurnOrder[dealerIndex],
                            wallOpening = WallOpening(
                                wallSideOffsetFromDealer = 0,
                                stacksFromRight = context.rolls[1].dice.total,
                            ),
                        ),
                    )
                }
            }
        }
    }

    /** 建立兩次點數合計決定切口的測試變體，並將跨面結果正規化。 */
    private fun twoRollSummedCutProcedure(): RoundOpeningProcedure = object : RoundOpeningProcedure {
        override val supportedDiceCounts: Set<Int> = setOf(2)

        override fun next(context: RoundOpeningContext): RoundOpeningStep = when (context.rolls.size) {
            0 -> RoundOpeningStep.Roll(OpeningDiceRollRequest(requireNotNull(context.fixedDealerPlayerId), 2))
            1 -> RoundOpeningStep.Roll(
                OpeningDiceRollRequest(
                    context.playerIdsInTurnOrder[
                        countedIndex(
                            startIndex = context.playerIdsInTurnOrder.indexOf(requireNotNull(context.fixedDealerPlayerId)),
                            count = context.rolls[0].dice.total,
                            size = context.playerIdsInTurnOrder.size,
                        ),
                    ],
                    2,
                ),
            )
            else -> {
                val total = context.rolls.sumOf { it.dice.total }
                val dealerIndex = context.playerIdsInTurnOrder.indexOf(requireNotNull(context.fixedDealerPlayerId))
                val openerIndex = context.playerIdsInTurnOrder.indexOf(context.rolls[1].playerId)
                val openerSide = (openerIndex - dealerIndex).mod(context.playerIdsInTurnOrder.size)
                // 每數完一面十七墩便繼續數上家的牌牆；回合順序與數墩方向相反。
                val crossedSides = (total - 1) / 17
                val side = (openerSide - crossedSides).mod(context.playerIdsInTurnOrder.size)
                RoundOpeningStep.Complete(
                    RoundOpeningCompletion(
                        dealerPlayerId = requireNotNull(context.fixedDealerPlayerId),
                        wallOpening = WallOpening(
                            wallSideOffsetFromDealer = side,
                            stacksFromRight = (total - 1) % 17 + 1,
                        ),
                    ),
                )
            }
        }
    }

    /** 建立第一局先擲兩次選莊、第三次由正式莊家擲骰開門的測試程序。 */
    private fun threeRollInitialDealerProcedure(): RoundOpeningProcedure = object : RoundOpeningProcedure {
        override val supportedDiceCounts: Set<Int> = setOf(2)

        override fun next(context: RoundOpeningContext): RoundOpeningStep = if (!context.firstRound) {
            twoRollSelectionProcedure().next(context)
        } else {
            selectInitialDealer(context)
        }

        /** 依前兩輪點數選出起家，再由起家擲第三輪決定開門。 */
        private fun selectInitialDealer(context: RoundOpeningContext): RoundOpeningStep = when (context.rolls.size) {
            0 -> RoundOpeningStep.Roll(OpeningDiceRollRequest(context.playerIdsInTurnOrder[0], 2))
            1 -> RoundOpeningStep.Roll(
                OpeningDiceRollRequest(
                    context.playerIdsInTurnOrder[
                        countedIndex(
                            startIndex = 0,
                            count = context.rolls[0].dice.total,
                            size = context.playerIdsInTurnOrder.size,
                        ),
                    ],
                    2,
                ),
            )
            2 -> {
                val provisionalIndex = countedIndex(
                    startIndex = 0,
                    count = context.rolls[0].dice.total,
                    size = context.playerIdsInTurnOrder.size,
                )
                val dealerIndex = countedIndex(
                    startIndex = provisionalIndex,
                    count = context.rolls[1].dice.total,
                    size = context.playerIdsInTurnOrder.size,
                )
                RoundOpeningStep.Roll(OpeningDiceRollRequest(context.playerIdsInTurnOrder[dealerIndex], 2))
            }
            else -> {
                val provisionalIndex = countedIndex(
                    startIndex = 0,
                    count = context.rolls[0].dice.total,
                    size = context.playerIdsInTurnOrder.size,
                )
                val dealerIndex = countedIndex(
                    startIndex = provisionalIndex,
                    count = context.rolls[1].dice.total,
                    size = context.playerIdsInTurnOrder.size,
                )
                RoundOpeningStep.Complete(
                    RoundOpeningCompletion(
                        dealerPlayerId = context.playerIdsInTurnOrder[dealerIndex],
                        wallOpening = WallOpening(
                            wallSideOffsetFromDealer = countedIndex(
                                startIndex = 0,
                                count = context.rolls[2].dice.total,
                                size = context.playerIdsInTurnOrder.size,
                            ),
                            stacksFromRight = context.rolls[2].dice.total,
                        ),
                    ),
                )
            }
        }
    }

    /** 建立兩顆骰子且總和固定的測試結果。 */
    private fun diceForTotal(total: Int): DiceRollResult {
        require(total in 2..12)
        val first = minOf(6, total - 1)
        return DiceRollResult.of(listOf(first, total - first))
    }

    /** 從固定順序中的起點，依點數往前數並回到牌桌玩家清單的範圍內。 */
    private fun countedIndex(startIndex: Int, count: Int, size: Int): Int = (startIndex + count - 1).mod(size)

    /** 建立依序取用固定點數的測試骰子來源。 */
    private fun queuedDiceSource(values: List<List<Int>>): DiceRollSource {
        val queue = values.toMutableList()
        return DiceRollSource { diceCount ->
            val next = queue.removeFirst()
            assertEquals(diceCount, next.size)
            DiceRollResult.of(next)
        }
    }
}
