package com.doublemoon1119.mahjongcraft.flow.persistence.format.game
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.persistenceJson
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPendingKanDoraReveal
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOrientation
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 驗證完整權威桌況經存檔 JSON 編解碼後仍保持一致。 */
class TableStatePersistenceTest {

    /** 內建規則配置的 persistence registry。 */
    private val ruleConfigRegistry = bundledPersistenceRegistries().ruleConfigs

    /** 內建牌河的 persistence registry。 */
    private val discardPileRegistry = bundledPersistenceRegistries().discardPiles

    /** 內建玩家規則狀態的 persistence registry。 */
    private val playerRuleStateRegistry = bundledPersistenceRegistries().playerRuleStates

    /** 內建動態牌桌狀態的 persistence registry。 */
    private val dynamicRuleStateRegistry = bundledPersistenceRegistries().dynamicRuleStates

    /** 內建擴充動作的 persistence registry。 */
    private val extensionGameActionRegistry = bundledPersistenceRegistries().extensionGameActions

    /** 內建流局原因的 persistence registry。 */
    private val exhaustiveDrawReasonRegistry = bundledPersistenceRegistries().exhaustiveDrawReasons

    /** 驗證一般摸打狀態保留完整牌山、隱藏手牌與實體牌牆配置。 */
    @Test
    fun `active turn round-trips with hidden authoritative state`() {
        val state = createTableState()
        val wallTiles = state.tileWall.getAllTiles()
        assertEncodedRoundTrip(
            state.copy(
                physicalWallLayout = TileWallPhysicalLayout(
                    wallTiles.mapIndexed { index, tile ->
                        tile.id to TileWallPlacement(
                            position = TileWallPosition(1, index, index % 2),
                            offset = TileWallPlacementOffset(alongWallStacks = 0.25),
                            orientation = TileWallPlacementOrientation.CLOCKWISE_90,
                        )
                    }.toMap(),
                ),
            ),
        )
    }

    /** 驗證等待捨牌反應時保留資格玩家與已提交回應。 */
    @Test
    fun `pending discard reaction round-trips in complete game state`() {
        val state = createTableState()
        val discarderId = state.players.first().id
        val responderId = state.players.last().id
        val tileId = state.players.first().discardPile.entries.last().tile.id

        assertEncodedRoundTrip(
            state.copy(
                pendingReaction = PendingReaction(
                    discarderId = discarderId,
                    tileId = tileId,
                    eligiblePlayerIds = setOf(responderId),
                    responses = mapOf(responderId to GameAction.Pon(tileId, emptyList())),
                ),
            ),
        )
    }

    /** 驗證等待搶槓反應時保留槓動作、被搶牌與已提交回應。 */
    @Test
    fun `pending chankan reaction round-trips in complete game state`() {
        val state = createTableState()
        val declarerId = state.players.first().id
        val responderId = state.players.last().id
        val robbedTile = identified(RiichiTileTypes.redFive(Tile.Suit.Dot))
        val kanAction = GameAction.Kan(
            type = GameAction.KanType.ADDED_KAN,
            tileId = robbedTile.id,
            withTiles = List(3) { Uuid.random() },
        )

        assertEncodedRoundTrip(
            state.copy(
                pendingRobbingReaction = PendingRobbingReaction(
                    declarerId = declarerId,
                    declaredAction = kanAction,
                    robbedTile = robbedTile,
                    eligiblePlayerIds = setOf(responderId),
                    responses = mapOf(responderId to GameAction.Pass),
                ),
            ),
        )
    }

    /** 驗證已擲骰開門的牌局保留開門位置與王牌初始快照。 */
    @Test
    fun `resolved wall opening and dead wall round-trip in complete game state`() {
        val state = createTableState()

        assertEncodedRoundTrip(
            state.copy(
                wallOpening = WallOpening(wallSideOffsetFromDealer = 2, stacksFromRight = 7),
                reservedWallTiles = listOf(identified(Tile.Honor.White), identified(Tile.Honor.Green)),
            ),
        )
    }

    /** 驗證本局已完成玩家集合保持不變，且不改變目前行動玩家。 */
    @Test
    fun `finished players round-trip in complete game state`() {
        val state = createTableState()
        val humanId = state.players.first().id

        assertEncodedRoundTrip(state.copy(finishedPlayerIds = setOf(humanId)))
    }

    /** 本局已公開、仍在手牌中的牌在存檔往返後保留。 */
    @Test
    fun `revealed hand tiles round-trip in complete game state`() {
        val state = createTableState()
        val handTileId = state.players.first().hand.standingTiles.first().id

        assertEncodedRoundTrip(state.copy(revealedHandTileIds = setOf(handTileId)))
    }

    /** 途中流局 resolver 的等待牌資料在存檔往返後保留。 */
    @Test
    fun `revealed hand settlements round-trip in complete game state`() {
        val state = createTableState()
        val playerId = state.players.first().id

        assertEncodedRoundTrip(
            state.copy(
                revealedHandSettlements = listOf(
                    RevealedHandSettlement(playerId, setOf(Tile.Honor.North)),
                ),
            ),
        )
    }

    /** 移除公開手牌欄位時拒絕載入，避免把缺欄舊資料誤當成空集合。 */
    @Test
    fun `missing revealed hand settlements field fails decoding`() {
        val encoded = persistenceJson.encodeToString(
            TableStatePersistenceDto.serializer(),
            createTableState().toPersistenceDto(),
        )
        val withoutField = encoded.replace(",\"revealedHandSettlements\":[]", "")

        assertEquals(-1, withoutField.indexOf("revealedHandSettlements"))
        assertFailsWith<SerializationException> {
            persistenceJson.decodeFromString(TableStatePersistenceDto.serializer(), withoutField)
        }
    }

    /** 存檔裡的 `finishedPlayerIds` 含不在座玩家時，還原成領域型別的當下就該被不變式擋下。 */
    @Test
    fun `restoring a table state whose finishedPlayerIds contains an outsider fails fast`() {
        val dto = createTableState().toPersistenceDto()
        val corrupted = dto.copy(finishedPlayerIds = setOf(Uuid.random().toString()))

        assertFailsWith<IllegalArgumentException> { corrupted.toDomain() }
    }

    /** 建立含兩位玩家、隱藏手牌、牌河與日麻動態規則狀態的測試桌況。 */
    private fun createTableState(): TableState {
        val humanDiscard = identified(Tile.Honor.East)
        val human = MahjongPlayer(
            id = Uuid.random(),
            initialSeatIndex = Wind.EAST.ordinal,
            seatWind = Wind.EAST,
            hand = Hand(
                tiles = listOf(identified(Tile.Numeric(Tile.Suit.Character, 1))),
                lastDrawn = identified(Tile.Numeric(Tile.Suit.Character, 2)),
            ),
            discardPile = RiichiDiscardPile().discard(RiichiDiscardEntry(humanDiscard, isRiichi = true)),
            playerRuleState = RiichiPlayerState(riichiTile = humanDiscard, isIppatsu = true),
            score = 24_000,
            passedTilesInRound = setOf(Tile.Honor.White),
            actionHistory = listOf(RIICHI_GAME_ACTION, GameAction.Discard(humanDiscard.id)),
        )
        val ai = MahjongPlayer(
            id = Uuid.random(),
            initialSeatIndex = Wind.SOUTH.ordinal,
            seatWind = Wind.SOUTH,
            hand = Hand(tiles = listOf(identified(Tile.Numeric(Tile.Suit.Bamboo, 9)))),
            discardPile = RiichiDiscardPile(),
            playerRuleState = RiichiPlayerState(),
            score = 26_000,
        )
        return TableState(
            id = Uuid.random(),
            players = listOf(human, ai),
            config = RiichiRuleConfig(),
            tileWall = TileWall(
                listOf(
                    identified(Tile.Honor.Red),
                    identified(RiichiTileTypes.redFive(Tile.Suit.Dot)),
                ),
            ),
            dealerPlayerId = human.id,
            prevalentWind = Wind.SOUTH,
            roundNumber = 5,
            comboCount = 2,
            currentPlayerIndex = 1,
            dynamicRuleState = RiichiDynamicState(
                riichiStickCount = 3,
                completedSupplementalDrawCount = 2,
                revealedKanDoraCount = 1,
                pendingKanDoraReveals = listOf(
                    RiichiPendingKanDoraReveal(human.id, GameAction.KanType.OPEN_KAN, 2),
                ),
            ),
        )
    }

    /**
     * 驗證完整桌況經 JSON 編解碼還原後仍產生相同的存檔 DTO。
     *
     * @param state 要進行存檔往返驗證的桌況。
     */
    private fun assertEncodedRoundTrip(state: TableState) {
        val expectedDto = state.toPersistenceDto()
        val encoded = persistenceJson.encodeToString(TableStatePersistenceDto.serializer(), expectedDto)
        val decodedDto = persistenceJson.decodeFromString(TableStatePersistenceDto.serializer(), encoded)
        val restored = decodedDto.toDomain()

        assertEquals(expectedDto, restored.toPersistenceDto())
        assertEquals(state.tileWall.getAllTiles(), restored.tileWall.getAllTiles())
        assertEquals(state.pendingReaction, restored.pendingReaction)
        assertEquals(state.pendingRobbingReaction, restored.pendingRobbingReaction)
    }

    /** 使用所有內建 registry 將 [TableState] 轉換成 persistence DTO。 */
    private fun TableState.toPersistenceDto(): TableStatePersistenceDto = toPersistenceDto(
        ruleConfigRegistry,
        discardPileRegistry,
        playerRuleStateRegistry,
        dynamicRuleStateRegistry,
        exhaustiveDrawReasonRegistry,
        extensionGameActionRegistry,
    )

    /** 使用所有內建 registry 將 persistence DTO 還原成 [TableState]。 */
    private fun TableStatePersistenceDto.toDomain(): TableState = toDomain(
        ruleConfigRegistry,
        discardPileRegistry,
        playerRuleStateRegistry,
        dynamicRuleStateRegistry,
        exhaustiveDrawReasonRegistry,
        extensionGameActionRegistry,
    )

    /**
     * 為指定牌種建立具有隨機識別碼的測試牌。
     *
     * @param tile 測試牌的牌種。
     * @return 帶有獨立 UUID 的測試牌。
     */
    private fun identified(tile: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), tile)
}
