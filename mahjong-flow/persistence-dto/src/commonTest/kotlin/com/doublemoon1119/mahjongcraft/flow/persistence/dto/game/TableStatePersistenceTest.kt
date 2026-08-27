package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildDiscardPilePersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildDynamicRuleStatePersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildExhaustiveDrawReasonPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildExtensionGameActionPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildPlayerRuleStatePersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule.buildRuleConfigPersistenceRegistry
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingKanReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證完整 Game 權威狀態的 encoded persistence round-trip。 */
class TableStatePersistenceTest {
    /** persistence 測試使用的 JSON 編解碼器。 */
    private val json = Json

    /** 內建規則配置的 persistence registry。 */
    private val ruleConfigRegistry = buildRuleConfigPersistenceRegistry()

    /** 內建牌河的 persistence registry。 */
    private val discardPileRegistry = buildDiscardPilePersistenceRegistry()

    /** 內建玩家規則狀態的 persistence registry。 */
    private val playerRuleStateRegistry = buildPlayerRuleStatePersistenceRegistry()

    /** 內建動態牌桌狀態的 persistence registry。 */
    private val dynamicRuleStateRegistry = buildDynamicRuleStatePersistenceRegistry()

    /** 內建擴充動作的 persistence registry。 */
    private val extensionGameActionRegistry = buildExtensionGameActionPersistenceRegistry()

    /** 內建流局原因的 persistence registry。 */
    private val exhaustiveDrawReasonRegistry = buildExhaustiveDrawReasonPersistenceRegistry()

    /** 驗證一般摸打狀態保留完整牌山、隱藏手牌與 AI 玩家資訊。 */
    @Test
    fun `active turn round-trips with hidden authoritative state`() {
        assertEncodedRoundTrip(createTableState())
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
                    responses = mapOf(responderId to GameAction.Pon(tileId)),
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
                pendingKanReaction = PendingKanReaction(
                    declarerId = declarerId,
                    kanAction = kanAction,
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
                initialDeadWall = listOf(identified(Tile.Honor.White), identified(Tile.Honor.Green)),
            ),
        )
    }

    /**
     * 驗證已標記為 finished 的玩家集合正確保留（不影響 `currentPlayerIndex` 所指向的 active 玩家，
     * 標記 human 而非 currentPlayerIndex 指向的 ai）。
     */
    @Test
    fun `finished players round-trip in complete game state`() {
        val state = createTableState()
        val humanId = state.players.first().id

        assertEncodedRoundTrip(state.copy(finishedPlayerIds = setOf(humanId)))
    }

    /** 驗證舊存檔缺少 `finishedPlayerIds` 欄位時，解碼後預設為空集合。 */
    @Test
    fun `decoding a persistence dto without finishedPlayerIds defaults to empty set`() {
        val state = createTableState()
        val json = this.json.encodeToJsonElement(TableStatePersistenceDto.serializer(), state.toPersistenceDto())
        val withoutFinishedPlayerIds = JsonObject(json.jsonObject.filterKeys { it != "finishedPlayerIds" })

        val decoded = this.json.decodeFromJsonElement(TableStatePersistenceDto.serializer(), withoutFinishedPlayerIds)

        assertEquals(emptySet(), decoded.finishedPlayerIds)
    }

    /** 建立同時包含人類、AI、隱藏手牌、牌河與動態牌桌狀態的測試牌局。 */
    private fun createTableState(): TableState {
        val humanDiscard = identified(Tile.Honor.East)
        val human = MahjongPlayer(
            id = Uuid.random(),
            initialSeat = Wind.EAST,
            hand = Hand(
                tiles = listOf(identified(Tile.Numeric(Tile.Suit.Character, 1))),
                lastDrawn = identified(Tile.Numeric(Tile.Suit.Character, 2)),
            ),
            discardPile = RiichiDiscardPile().discard(RiichiDiscardEntry(humanDiscard, isRiichi = true)),
            playerRuleState = RiichiPlayerState(riichiTile = humanDiscard, isIppatsu = true),
            score = 24_000,
            passedTilesInRound = setOf(Tile.Honor.White),
            actionHistory = listOf(com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION, GameAction.Discard(humanDiscard.id)),
        )
        val ai = MahjongPlayer(
            id = Uuid.random(),
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(identified(Tile.Numeric(Tile.Suit.Bamboo, 9)))),
            discardPile = RiichiDiscardPile(),
            playerRuleState = RiichiPlayerState(),
            score = 26_000,
            aiStrategyKey = "random",
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
            prevalentWind = Wind.SOUTH,
            roundNumber = 5,
            comboCount = 2,
            currentPlayerIndex = 1,
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 3),
        )
    }

    /** 驗證完整狀態經 JSON 編解碼與 domain 還原後仍產生相同 persistence DTO。 */
    private fun assertEncodedRoundTrip(state: TableState) {
        val expectedDto = state.toPersistenceDto()
        val encoded = json.encodeToString(TableStatePersistenceDto.serializer(), expectedDto)
        val decodedDto = json.decodeFromString(TableStatePersistenceDto.serializer(), encoded)
        val restored = decodedDto.toDomain()

        assertEquals(expectedDto, restored.toPersistenceDto())
        assertEquals(state.tileWall.getAllTiles(), restored.tileWall.getAllTiles())
        assertEquals(state.pendingReaction, restored.pendingReaction)
        assertEquals(state.pendingKanReaction, restored.pendingKanReaction)
    }

    /** 使用所有內建 registry 將 [TableState] 轉換成 persistence DTO。 */
    private fun TableState.toPersistenceDto(): TableStatePersistenceDto = toPersistenceDto(
        ruleConfigRegistry,
        discardPileRegistry,
        playerRuleStateRegistry,
        dynamicRuleStateRegistry,
        exhaustiveDrawReasonRegistry,
        extensionGameActionRegistry,
        json,
    )

    /** 使用所有內建 registry 將 persistence DTO 還原成 [TableState]。 */
    private fun TableStatePersistenceDto.toDomain(): TableState = toDomain(
        ruleConfigRegistry,
        discardPileRegistry,
        playerRuleStateRegistry,
        dynamicRuleStateRegistry,
        exhaustiveDrawReasonRegistry,
        extensionGameActionRegistry,
        json,
    )

    /** 建立具有隨機穩定識別碼的測試牌。 */
    private fun identified(tile: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), tile)
}
