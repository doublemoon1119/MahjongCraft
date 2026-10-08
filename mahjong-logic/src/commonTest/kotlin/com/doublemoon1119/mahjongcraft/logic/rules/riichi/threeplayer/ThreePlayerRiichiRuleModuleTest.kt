package com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiSupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionContext
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionDecision
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPhase
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundTransition
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawContext
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [ThreePlayerRiichiRuleModule] 與三人日本麻將專屬元件的單元測試。 */
class ThreePlayerRiichiRuleModuleTest {
    private val config = ThreePlayerRiichiRuleConfig()
    private val module = ThreePlayerRiichiRuleModule(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, config)
    private val eastModule = ThreePlayerRiichiRuleModule(
        id = BuiltInRuleModuleIds.RIICHI_THREE_PLAYER,
        config = ThreePlayerRiichiRuleConfig(gameLength = RiichiGameLength.East),
    )

    /** 驗證預設設定：三人、起始 35000、一位必要 40000、王牌 14 張其中 8 張嶺上牌。 */
    @Test
    fun `test default config describes a three player table`() {
        assertEquals(3, config.minPlayers)
        assertEquals(3, config.maxPlayers)
        assertEquals(35000, config.scoreConfig.initialScore)
        assertEquals(40000, config.scoreConfig.minPointsToWin)
        assertEquals(14, config.deadTileCount)
        assertEquals(8, config.rinshanTileCount)
        assertTrue(config.usesThreePlayerTiles)
    }

    /** 驗證赤寶牌只接受 0 或 2 張。 */
    @Test
    fun `test config rejects unsupported red dora counts`() {
        assertFailsWith<IllegalArgumentException> { ThreePlayerRiichiRuleConfig(redDoraCount = 3) }
        ThreePlayerRiichiRuleConfig(redDoraCount = 0)
    }

    /** 驗證牌山共 108 張，萬子只有一萬與九萬，赤牌為五筒與五條各一張。 */
    @Test
    fun `test wall has 108 tiles without middle characters`() {
        val tiles = module.createWallFactory().create().getAllTiles()

        assertEquals(108, tiles.size)
        val characters = tiles.map { it.tile.riichiCanonical }.filterIsInstance<Tile.Numeric>().filter { it.suit == Tile.Suit.Character }
        assertEquals(setOf(1, 9), characters.map { it.value }.toSet())
        assertEquals(8, characters.size)
        val redSuits = tiles.filter { RiichiTileInterpretationPolicy.isRedDora(it.tile) }
            .map { (it.tile.riichiCanonical as Tile.Numeric).suit }
        assertEquals(2, redSuits.size)
        assertEquals(setOf(Tile.Suit.Bamboo, Tile.Suit.Dot), redSuits.toSet())
    }

    /** 驗證不使用赤寶牌時牌山沒有赤牌。 */
    @Test
    fun `test wall without red dora has no red fives`() {
        val tiles = ThreePlayerRiichiWallFactory(ThreePlayerRiichiRuleConfig(redDoraCount = 0)).create().getAllTiles()

        assertTrue(tiles.none { RiichiTileInterpretationPolicy.isRedDora(it.tile) })
    }

    /** 驗證三面、每面 18 墩的布局：活牌 94 張（配牌 39 張後可摸 55 張）、王牌 14 張，座標不重複。 */
    @Test
    fun `test wall layout uses three sides of eighteen stacks`() {
        val tiles = List(108) { FakeIdentifiedTileFactory.create(Tile.Honor.East) }
        val result = module.createWallLayout().resolve(tiles, WallOpening(wallSideOffsetFromDealer = 2, stacksFromRight = 7))

        assertEquals(94, result.drawOrder.size)
        assertEquals(55, result.drawOrder.size - 3 * config.initialHandSize)
        assertEquals(14, result.reservedWallTiles.size)
        assertEquals(tiles.map { it.id }.toSet(), result.structure.keys)
        result.structure.values.forEach { position ->
            assertTrue(position.side in 0..2, "Unexpected side: ${position.side}")
            assertTrue(position.stack in 0..17, "Unexpected stack: ${position.stack}")
        }
        assertEquals(108, result.structure.values.toSet().size)
    }

    /** 驗證雙骰總和從莊家起逆時針在三面牌牆之間數：總和減一除以三的餘數。 */
    @Test
    fun `test dice totals cycle through three wall sides`() {
        (2..12).forEach { total ->
            val first = (total - 1).coerceAtMost(6)
            val opening = module.createWallOpeningPolicy().resolve(DiceRollResult.of(listOf(first, total - first)))

            assertEquals((total - 1) % 3, opening.wallSideOffsetFromDealer, "Unexpected wall side for total $total")
            assertEquals(total, opening.stacksFromRight)
        }
    }

    /** 驗證上家打出可以吃的牌時不會提供吃。 */
    @Test
    fun `test chi is never offered`() {
        val player = FakeMahjongPlayerFactory.create(
            hand = FakeHandFactory.create(listOf(dot(2), dot(3))),
            playerRuleState = RiichiPlayerState(),
        )
        val table = threePlayerTable(listOf(player, other(Wind.SOUTH), other(Wind.WEST)))
        val incoming = FakeIdentifiedTileFactory.create(dot(1))

        val actions = module.createLegalActionValidator().getLegalActions(
            tableState = table,
            player = player,
            sourceAction = GameAction.Discard(incoming.id),
            sourceDirection = RelativeDirection.Left,
            incomingTile = incoming,
        )

        assertTrue(actions.none { it is GameAction.Chi })
    }

    /** 驗證摸牌後手中有北時可以拔北，即使剛摸到的不是北。 */
    @Test
    fun `test pull north is offered with a north in hand after drawing`() {
        val player = FakeMahjongPlayerFactory.create(
            hand = FakeHandFactory.create(listOf(Tile.Honor.North, dot(1), dot(5)), lastDrawn = bamboo(7)),
            playerRuleState = RiichiPlayerState(),
        )

        assertTrue(PULL_NORTH_GAME_ACTION in selfTurnActions(player))
    }

    /** 驗證立直後只能拔剛摸到的北。 */
    @Test
    fun `test riichi player can only pull the drawn north`() {
        val riichiState = RiichiPlayerState(riichiTile = riichiTile())
        val heldNorth = FakeMahjongPlayerFactory.create(
            hand = FakeHandFactory.create(listOf(Tile.Honor.North, dot(1), dot(5)), lastDrawn = bamboo(7)),
            playerRuleState = riichiState,
        )
        val drawnNorth = FakeMahjongPlayerFactory.create(
            hand = FakeHandFactory.create(listOf(dot(1), dot(5), bamboo(7)), lastDrawn = Tile.Honor.North),
            playerRuleState = riichiState,
        )

        assertFalse(PULL_NORTH_GAME_ACTION in selfTurnActions(heldNorth))
        assertTrue(PULL_NORTH_GAME_ACTION in selfTurnActions(drawnNorth))
    }

    /** 驗證四張北都拔完補牌後不再提供拔北。 */
    @Test
    fun `test pull north is not offered after four north draws`() {
        val player = FakeMahjongPlayerFactory.create(
            hand = FakeHandFactory.create(listOf(dot(1), dot(5), bamboo(7)), lastDrawn = Tile.Honor.North),
            playerRuleState = RiichiPlayerState(),
        )

        val actions = selfTurnActions(player, RiichiDynamicState(completedNorthDrawCount = RiichiSupplementalDrawPolicy.MAX_SUPPLEMENTAL_DRAWS))

        assertFalse(PULL_NORTH_GAME_ACTION in actions)
    }

    /** 驗證拔北優先拔剛摸到的北、記錄動作，並讓所有玩家的一發失效。 */
    @Test
    fun `test pulling the drawn north records the action and clears everyone's ippatsu`() {
        val drawnNorth = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val heldNorth = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val puller = FakeMahjongPlayerFactory.create(
            hand = Hand(tiles = listOf(heldNorth, FakeIdentifiedTileFactory.create(dot(1))), lastDrawn = drawnNorth),
            playerRuleState = RiichiPlayerState(riichiTile = riichiTile(), isIppatsu = true),
        )
        val ippatsuOther = other(Wind.SOUTH).copy(playerRuleState = RiichiPlayerState(riichiTile = riichiTile(), isIppatsu = true))
        val table = threePlayerTable(listOf(puller, ippatsuOther, other(Wind.WEST)))

        assertEquals(drawnNorth, module.tileSetAsideBy(puller, PULL_NORTH_GAME_ACTION))
        val after = assertNotNull(module.applyTileSetAsideAction(table, puller.id, PULL_NORTH_GAME_ACTION))

        val pullerAfter = after.players.first { it.id == puller.id }
        assertNull(pullerAfter.hand.lastDrawn)
        assertEquals(listOf(heldNorth.id), pullerAfter.hand.tiles.filter { it.tile == Tile.Honor.North }.map { it.id })
        assertEquals(listOf(drawnNorth), (pullerAfter.playerRuleState as RiichiPlayerState).nukiDoraTiles)
        assertEquals(PULL_NORTH_GAME_ACTION, pullerAfter.actionHistory.last())
        assertTrue(after.players.none { (it.playerRuleState as? RiichiPlayerState)?.isIppatsu == true })
    }

    /** 驗證剛摸到的牌不是北時拔手中的北，剛摸到的牌併入手牌。 */
    @Test
    fun `test pulling a held north merges the drawn tile into the hand`() {
        val heldNorth = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val drawn = FakeIdentifiedTileFactory.create(bamboo(7))
        val puller = FakeMahjongPlayerFactory.create(
            hand = Hand(tiles = listOf(heldNorth, FakeIdentifiedTileFactory.create(dot(1))), lastDrawn = drawn),
            playerRuleState = RiichiPlayerState(),
        )
        val table = threePlayerTable(listOf(puller, other(Wind.SOUTH), other(Wind.WEST)))

        val after = assertNotNull(module.applyTileSetAsideAction(table, puller.id, PULL_NORTH_GAME_ACTION))

        val pullerAfter = after.players.first { it.id == puller.id }
        assertTrue(drawn in pullerAfter.hand.tiles)
        assertTrue(pullerAfter.hand.tiles.none { it.id == heldNorth.id })
        assertEquals(listOf(heldNorth), (pullerAfter.playerRuleState as RiichiPlayerState).nukiDoraTiles)
    }

    /** 驗證其他擴充動作不會被當成拔北，手中沒有北時也不會拔。 */
    @Test
    fun `test set aside requires a north and the pull north action`() {
        val player = FakeMahjongPlayerFactory.create(
            hand = FakeHandFactory.create(listOf(dot(1)), lastDrawn = dot(2)),
            playerRuleState = RiichiPlayerState(),
        )
        val table = threePlayerTable(listOf(player, other(Wind.SOUTH), other(Wind.WEST)))

        assertNull(module.tileSetAsideBy(player, PULL_NORTH_GAME_ACTION))
        assertNull(module.applyTileSetAsideAction(table, player.id, PULL_NORTH_GAME_ACTION))
    }

    /** 驗證拔北補牌從嶺上牌前端取牌、只增加拔北補牌次數，不影響槓的補牌次數。 */
    @Test
    fun `test north draw takes a rinshan tile and counts separately from kan draws`() {
        val deadWall = List(14) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }
        val liveWall = List(4) { FakeIdentifiedTileFactory.create(bamboo(it + 1)) }
        val state = FakeTableStateFactory.create(
            config = config,
            tileWall = TileWall(liveWall),
            reservedWallTiles = deadWall,
            dynamicRuleState = RiichiDynamicState(completedSupplementalDrawCount = 2),
        )

        val result = assertIs<SupplementalDrawDecision.Completed>(
            module.createSupplementalDrawPolicy().resolve(
                SupplementalDrawContext(state, state, state.currentPlayer.id, PULL_NORTH_GAME_ACTION),
            ),
        )

        assertEquals(listOf(deadWall.first()), result.drawnTiles)
        assertEquals(deadWall.drop(1) + liveWall.last(), result.reservedWallTiles)
        val dynamicState = result.dynamicRuleState as RiichiDynamicState
        assertEquals(1, dynamicState.completedNorthDrawCount)
        assertEquals(2, dynamicState.completedSupplementalDrawCount)
        assertEquals(2, dynamicState.revealedKanDoraCount)
    }

    /** 驗證四次槓加四次拔北共八次補牌都取原本的嶺上牌，寶牌指示牌位置隨之左移且不被摸走。 */
    @Test
    fun `test eight replacement draws never reach the dora indicators`() {
        val rinshan = List(8) { FakeIdentifiedTileFactory.create(Tile.Honor.Green) }
        val firstIndicator = FakeIdentifiedTileFactory.create(Tile.Honor.West)
        val deadWall = rinshan + firstIndicator + List(5) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }
        val liveWall = List(10) { FakeIdentifiedTileFactory.create(bamboo((it % 9) + 1)) }
        var state = FakeTableStateFactory.create(
            config = config,
            tileWall = TileWall(liveWall),
            reservedWallTiles = deadWall,
            dynamicRuleState = RiichiDynamicState(),
        )
        val drawn = mutableListOf<IdentifiedTile>()
        val actions = List(4) { PULL_NORTH_GAME_ACTION } + List(4) { GameAction.Kan(GameAction.KanType.CLOSED_KAN, Uuid.random(), emptyList()) }

        actions.forEach { action ->
            val result = assertIs<SupplementalDrawDecision.Completed>(
                module.createSupplementalDrawPolicy().resolve(SupplementalDrawContext(state, state, state.currentPlayer.id, action)),
            )
            drawn += result.drawnTiles
            state = state.copy(
                tileWall = result.tileWall,
                reservedWallTiles = result.reservedWallTiles,
                dynamicRuleState = result.dynamicRuleState,
            )
        }

        assertEquals(rinshan, drawn)
        assertEquals(14, state.reservedWallTiles.size)
        val dynamicState = state.dynamicRuleState as RiichiDynamicState
        assertEquals(firstIndicator, dynamicState.getDoraIndicators(state).first.first())
    }

    /**
     * 驗證四次槓翻開的五組指示牌：前三組是開局王牌的第 5～7 墩（上層寶牌、下層裏寶牌），第 4、5 組落在補牌時
     * 從活牌尾端補進來的牌上；補進來的每墩先補下層、再補上層，寶牌指示牌取上層那張。
     */
    @Test
    fun `test later kan dora indicators come from replenished tiles`() {
        val rinshan = List(8) { FakeIdentifiedTileFactory.create(Tile.Honor.Green) }
        val indicatorStacks = List(6) { FakeIdentifiedTileFactory.create(dot(it + 1)) }
        val liveWall = List(10) { FakeIdentifiedTileFactory.create(bamboo((it % 9) + 1)) }
        var state = FakeTableStateFactory.create(
            config = config,
            tileWall = TileWall(liveWall),
            reservedWallTiles = rinshan + indicatorStacks,
            dynamicRuleState = RiichiDynamicState(),
        )
        repeat(4) {
            val kan = GameAction.Kan(GameAction.KanType.CLOSED_KAN, Uuid.random(), emptyList())
            val result = assertIs<SupplementalDrawDecision.Completed>(
                module.createSupplementalDrawPolicy().resolve(SupplementalDrawContext(state, state, state.currentPlayer.id, kan)),
            )
            state = state.copy(
                tileWall = result.tileWall,
                reservedWallTiles = result.reservedWallTiles,
                dynamicRuleState = result.dynamicRuleState,
            )
        }
        val revealed = (state.dynamicRuleState as RiichiDynamicState).copy(revealedKanDoraCount = 4)

        val (dora, ura) = revealed.getDoraIndicators(state.copy(dynamicRuleState = revealed))

        // 補進王牌的依序是活牌最後一張、倒數第二張……；每兩張組成一墩，後補的那張在上層。
        val replenished = liveWall.takeLast(4).reversed()
        assertEquals(
            listOf(indicatorStacks[0], indicatorStacks[2], indicatorStacks[4], replenished[1], replenished[3]),
            dora,
        )
        assertEquals(
            listOf(indicatorStacks[1], indicatorStacks[3], indicatorStacks[5], replenished[0], replenished[2]),
            ura,
        )
    }

    /** 驗證閒家自摸時少一家支付：莊家與另一位閒家各付自己的份額，總收入為兩份之和。 */
    @Test
    fun `test non dealer tsumo loses the missing player's share`() {
        val dealer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val winner = daisangenTsumoPlayer(Wind.SOUTH)
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)
        val table = threePlayerTable(listOf(dealer, winner, west))

        val result = module.declareTsumo(table, winner)

        assertEquals(
            WinSettlementResult(totalGained = 24000, paymentsByPlayerId = mapOf(dealer.id to 16000, west.id to 8000)),
            result?.settlement,
        )
    }

    /** 驗證莊家自摸時由另外兩家各付相同份額。 */
    @Test
    fun `test dealer tsumo is paid by the two other players`() {
        val winner = daisangenTsumoPlayer(Wind.EAST)
        val south = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)
        val table = threePlayerTable(listOf(winner, south, west))

        val result = module.declareTsumo(table, winner)

        assertEquals(
            WinSettlementResult(totalGained = 32000, paymentsByPlayerId = mapOf(south.id to 16000, west.id to 16000)),
            result?.settlement,
        )
    }

    /** 驗證拔北寶牌每張一番，且拔出的北也計入寶牌。 */
    @Test
    fun `test pulled norths count as nuki dora and regular dora`() {
        val norths = List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.North) }
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = FakeHandFactory.create(
                listOf(dot(2), dot(3), dot(4), dot(5), dot(6), dot(7), bamboo(3), bamboo(4), bamboo(5), bamboo(6), bamboo(7), bamboo(8), dot(9)),
                lastDrawn = dot(9),
            ),
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East)),
            playerRuleState = RiichiPlayerState(nukiDoraTiles = norths),
        )
        // 已拔兩次北，第一張寶牌指示牌從第 8 - 2 = 6 張開始；西的下一張是北。
        val deadWall = List(6) { FakeIdentifiedTileFactory.create(Tile.Honor.White) } +
            FakeIdentifiedTileFactory.create(Tile.Honor.West) +
            List(7) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }
        val table = threePlayerTable(
            players = listOf(FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST), winner, other(Wind.WEST)),
            dynamicRuleState = RiichiDynamicState(completedNorthDrawCount = 2),
            reservedWallTiles = deadWall,
        )

        val result = assertNotNull(module.declareTsumo(table, winner))

        val yaku = (result.handValueResult as RiichiHandValueResult).yakuResults.associate { it.yaku to it.han }
        assertEquals(2, yaku[YakuType.NukiDora])
        assertEquals(2, yaku[YakuType.Dora])
    }

    /** 驗證萬子寶牌在一萬與九萬之間循環。 */
    @Test
    fun `test character dora wraps between one and nine`() {
        assertTrue(module.isBonusTile(character(9), listOf(character(1))))
        assertTrue(module.isBonusTile(character(1), listOf(character(9))))
        assertFalse(module.isBonusTile(character(2), listOf(character(1))))
        assertTrue(module.isBonusTile(dot(2), listOf(dot(1))))
    }

    /** 驗證不聽罰符總額 2000：一人聽牌時從另外兩家各收 1000。 */
    @Test
    fun `test single tenpai player collects 2000 in total`() {
        val tenpai = FakeMahjongPlayerFactory.create(hand = tenpaiHand())
        val noten = List(2) { FakeMahjongPlayerFactory.create(hand = notenHand()) }
        val table = threePlayerTable(listOf(tenpai) + noten)

        val result = module.declareExhaustiveDraw(table)

        assertEquals(mapOf(tenpai.id to 2000) + noten.associate { it.id to -1000 }, result.scoreDeltas)
    }

    /** 驗證兩人聽牌時唯一不聽者付 2000，由兩位聽牌者平分。 */
    @Test
    fun `test two tenpai players split the single noten payment`() {
        val tenpai = List(2) { FakeMahjongPlayerFactory.create(hand = tenpaiHand()) }
        val noten = FakeMahjongPlayerFactory.create(hand = notenHand())
        val table = threePlayerTable(tenpai + noten)

        val result = module.declareExhaustiveDraw(table)

        assertEquals(tenpai.associate { it.id to 1000 } + mapOf(noten.id to -2000), result.scoreDeltas)
    }

    /** 驗證每本場 200 點：榮和由放銃者支付，自摸由另外兩家各付 100。 */
    @Test
    fun `test combo bonus is 200 per count`() {
        val players = listOf(other(Wind.EAST), other(Wind.SOUTH), other(Wind.WEST))
        val table = threePlayerTable(players, comboCount = 2)

        assertEquals(
            mapOf(players[1].id to 400),
            module.resolveComboBonusPayments(table, winnerId = players[0].id, discarderId = players[1].id, resolution = null),
        )
        assertEquals(
            mapOf(players[1].id to 200, players[2].id to 200),
            module.resolveComboBonusPayments(table, winnerId = players[0].id, discarderId = null, resolution = null),
        )
    }

    /** 驗證莊家流局滿貫由另外兩家各付 4000。 */
    @Test
    fun `test dealer nagashi mangan is paid by two players`() {
        val dealer = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = notenHand(),
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East)),
        )
        val others = listOf(Wind.SOUTH, Wind.WEST).map { FakeMahjongPlayerFactory.create(initialSeat = it, hand = notenHand()) }
        val table = threePlayerTable(listOf(dealer) + others)

        val result = assertNotNull(module.resolveNagashiMangan(table))

        assertEquals(mapOf(dealer.id to 8000) + others.associate { it.id to -4000 }, result.scoreDeltas)
    }

    /** 驗證每圈三局：東風戰東三局過莊且第一名達一位必要點數時終局。 */
    @Test
    fun `test east game ends after east three`() {
        val context = progressionContext(roundNumber = 3, scores = listOf(40_000, 35_000, 30_000))

        assertEquals(
            MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.TARGET_SCORE_REACHED),
            eastModule.createMatchProgressionPolicy().decide(context),
        )
    }

    /** 驗證東風戰東三局過莊但無人達一位必要點數時南入。 */
    @Test
    fun `test east three below target enters south one`() {
        val context = progressionContext(roundNumber = 3, scores = listOf(39_900, 35_100, 30_000))

        assertEquals(
            MatchProgressionDecision.ContinueMatch(
                MatchRoundTransition.AdvanceTo(MatchRoundPosition(3, Wind.SOUTH, 1, MatchRoundPhase.EXTRA)),
            ),
            eastModule.createMatchProgressionPolicy().decide(context),
        )
    }

    /** 驗證東二局過莊進入東三局，而不是東風戰結束。 */
    @Test
    fun `test east two advances to east three`() {
        val context = progressionContext(roundNumber = 2, scores = listOf(45_000, 35_000, 25_000))

        assertEquals(
            MatchProgressionDecision.ContinueMatch(
                MatchRoundTransition.AdvanceTo(MatchRoundPosition(2, Wind.EAST, 3, MatchRoundPhase.REGULAR)),
            ),
            eastModule.createMatchProgressionPolicy().decide(context),
        )
    }

    /** 建立東風戰指定局數、閒家連莊結束後的 progression context。 */
    private fun progressionContext(roundNumber: Int, scores: List<Int>): MatchProgressionContext {
        val players = scores.mapIndexed { index, score ->
            FakeMahjongPlayerFactory.create(initialSeat = Wind.entries[index]).copy(score = score)
        }
        val state = FakeTableStateFactory.create(
            players = players,
            dealerPlayerId = players[(roundNumber - 1) % 3].id,
            config = eastModule.config,
            roundNumber = roundNumber,
        ).let { it.copy(roundPosition = MatchRoundPosition(roundNumber - 1, Wind.EAST, roundNumber, MatchRoundPhase.REGULAR)) }
        return MatchProgressionContext(
            tableState = state,
            completion = RoundCompletionSummary(
                outcomeId = "test:round_completed",
                classification = RoundCompletionClassification.WIN,
                beneficiaryPlayerIds = emptySet(),
                transitionDirective = RoundTransitionDirective.ADVANCE_DEALER,
                settledScoresByPlayerId = state.players.associate { it.id to it.score },
            ),
            rankedPlayerIds = players.sortedByDescending { it.score }.map { it.id },
        )
    }

    /** 以三人日麻設定建立桌況。 */
    private fun threePlayerTable(
        players: List<MahjongPlayer>,
        dynamicRuleState: RiichiDynamicState = RiichiDynamicState(),
        reservedWallTiles: List<IdentifiedTile> = emptyList(),
        comboCount: Int = 0,
    ): TableState = FakeTableStateFactory.create(
        players = players,
        config = config,
        dynamicRuleState = dynamicRuleState,
        reservedWallTiles = reservedWallTiles,
        comboCount = comboCount,
    )

    /** 以自摸前狀態詢問自己回合的合法動作。 */
    private fun selfTurnActions(
        player: MahjongPlayer,
        dynamicRuleState: RiichiDynamicState = RiichiDynamicState(),
    ): List<GameAction> {
        val table = threePlayerTable(listOf(player, other(Wind.SOUTH), other(Wind.WEST)), dynamicRuleState = dynamicRuleState)
        return module.createLegalActionValidator().getLegalActions(
            tableState = table,
            player = table.players.first(),
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = null,
        )
    }

    /** 帶有日麻玩家狀態的其他玩家。 */
    private fun other(seat: Wind) = FakeMahjongPlayerFactory.create(initialSeat = seat, playerRuleState = RiichiPlayerState())

    /** 摸到最後一張紅中、自摸大三元的玩家；已經打過牌，不會成立天和或地和。 */
    private fun daisangenTsumoPlayer(seat: Wind) = FakeMahjongPlayerFactory.create(
        initialSeat = seat,
        hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.White, Tile.Honor.White, Tile.Honor.White,
                Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
                Tile.Honor.Red, Tile.Honor.Red,
                dot(2), dot(3), dot(4),
                dot(5), dot(5),
            ),
            lastDrawn = Tile.Honor.Red,
        ),
        discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
        playerRuleState = RiichiPlayerState(),
    )

    /** 聽一筒與四筒的手牌。 */
    private fun tenpaiHand() = FakeHandFactory.create(
        listOf(dot(2), dot(3), bamboo(1), bamboo(2), bamboo(3), bamboo(4), bamboo(5), bamboo(6), bamboo(7), bamboo(8), bamboo(9), Tile.Honor.East, Tile.Honor.East),
    )

    /** 互不相關的孤立牌，明顯不聽。 */
    private fun notenHand() = FakeHandFactory.create(
        listOf(character(1), character(9), dot(1), dot(4), dot(7), bamboo(1), bamboo(4), bamboo(7), Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.White, Tile.Honor.Green),
    )

    /** 立直宣言牌。 */
    private fun riichiTile() = FakeIdentifiedTileFactory.create(Tile.Honor.East)

    private fun dot(value: Int) = Tile.Numeric(Tile.Suit.Dot, value)

    private fun bamboo(value: Int) = Tile.Numeric(Tile.Suit.Bamboo, value)

    private fun character(value: Int) = Tile.Numeric(Tile.Suit.Character, value)
}
