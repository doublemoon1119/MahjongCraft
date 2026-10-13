package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawHandDisclosure
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPlayerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證結算舞台輸入的組成。 */
class SettlementStageInputsTest {
    /** 手牌中的牌找得到。 */
    @Test
    fun `finds a tile held in a hand`() {
        val tile = tileOf(Tile.Numeric(Tile.Suit.Character, 3))
        val state = stateWith(handTiles = listOf(tile))

        assertEquals(tile, state.findTile(tile.id))
    }

    /** 活牌牆中的牌找得到。 */
    @Test
    fun `finds a tile still in the live wall`() {
        val tile = tileOf(Tile.Numeric(Tile.Suit.Bamboo, 7))
        val state = stateWith(wallTiles = listOf(tile))

        assertEquals(tile, state.findTile(tile.id))
    }

    /** 王牌區中的牌找得到。 */
    @Test
    fun `finds a tile in the dead wall`() {
        val tile = tileOf(Tile.Honor.White)
        val state = stateWith(deadWallTiles = listOf(tile))

        assertEquals(tile, state.findTile(tile.id))
    }

    /** 不在桌上的牌張 ID 沒有結果。 */
    @Test
    fun `finds no tile outside the table`() {
        assertNull(stateWith().findTile(Uuid.random()))
    }

    /** 牌張資產依 ID 對應，順序與重複都不影響結果。 */
    @Test
    fun `maps every requested tile to its asset`() {
        val first = tileOf(Tile.Numeric(Tile.Suit.Character, 1))
        val second = tileOf(Tile.Honor.East)
        val state = stateWith(handTiles = listOf(first, second))

        val assets = state.tileAssetKeysById(listOf(second.id, first.id, first.id), FakeTileAssetRegistry)

        assertEquals(mapOf(first.id to "m1", second.id to "east"), assets)
    }

    /** 找不到的牌張直接略過，不佔一個沒有資產的項目。 */
    @Test
    fun `skips a tile that is no longer on the table`() {
        val tile = tileOf(Tile.Numeric(Tile.Suit.Dot, 5))
        val state = stateWith(handTiles = listOf(tile))

        val assets = state.tileAssetKeysById(listOf(tile.id, Uuid.random()), FakeTileAssetRegistry)

        assertEquals(setOf(tile.id), assets.keys)
    }

    /** 聽牌資產依座位對應，並保留請求中的等待牌順序。 */
    @Test
    fun `keeps the waiting tile order of each seat`() {
        val request = requestOf(
            playerPresentation(seatIndex = 0, waitingTiles = listOf(Tile.Honor.East, Tile.Numeric(Tile.Suit.Dot, 9))),
            playerPresentation(seatIndex = 1, waitingTiles = listOf(Tile.Numeric(Tile.Suit.Bamboo, 2))),
        )

        val inputs = exhaustiveDrawSettlementStageInputs(request, stateWith(), emptyMap(), FakeTileOrder, FakeTileAssetRegistry)

        assertEquals(
            mapOf(0 to listOf("east", "p9"), 1 to listOf("s2")),
            inputs.waitingTileAssetsBySeat,
        )
    }

    /** 公開手牌的資產由權威桌況查出。 */
    @Test
    fun `resolves the revealed hand assets from the table`() {
        val revealed = tileOf(Tile.Numeric(Tile.Suit.Character, 5))
        val state = stateWith(handTiles = listOf(revealed))
        val request = requestOf(playerPresentation(seatIndex = 0, revealedHandTileIds = listOf(revealed.id)))

        val inputs = exhaustiveDrawSettlementStageInputs(request, state, emptyMap(), FakeTileOrder, FakeTileAssetRegistry)

        assertEquals(mapOf(revealed.id to "m5"), inputs.revealedTileAssetsById)
    }

    /** 桌況已不存在時仍算得出聽牌資產，另外兩份為空。 */
    @Test
    fun `still resolves the waiting assets without a table state`() {
        val request = requestOf(
            playerPresentation(seatIndex = 0, waitingTiles = listOf(Tile.Honor.South), revealedHandTileIds = listOf(Uuid.random())),
        )

        val inputs = exhaustiveDrawSettlementStageInputs(
            request = request,
            tableState = null,
            cornerWidthsBySeat = emptyMap(),
            tileOrder = FakeTileOrder,
            tileAssetRegistry = FakeTileAssetRegistry,
        )

        assertEquals(mapOf(0 to listOf("south")), inputs.waitingTileAssetsBySeat)
        assertEquals(emptyMap(), inputs.revealedTileAssetsById)
        assertEquals(emptyMap(), inputs.reservedCornerWidthsBySeat)
    }

    /** 每個座位都有一份桌角預留寬度。 */
    @Test
    fun `reserves a corner width for every seat`() {
        val inputs = exhaustiveDrawSettlementStageInputs(
            request = requestOf(playerPresentation(seatIndex = 0)),
            tableState = stateWith(),
            cornerWidthsBySeat = emptyMap(),
            tileOrder = FakeTileOrder,
            tileAssetRegistry = FakeTileAssetRegistry,
        )

        assertEquals(setOf(0, 1), inputs.reservedCornerWidthsBySeat.keys)
        assertTrue(inputs.reservedCornerWidthsBySeat.values.all { it >= 0.0 })
    }

    /** 角落物件寬度只加在指定的座位上，沒有列出的座位不佔用。 */
    @Test
    fun `adds the corner prop width to the listed seat only`() {
        val without = cornerWidths(emptyMap())
        val with = cornerWidths(mapOf(0 to 0.5))

        assertEquals(without.getValue(0) + 0.5, with.getValue(0), "Expected the listed seat to reserve the corner prop width.")
        assertEquals(without.getValue(1), with.getValue(1), "Expected an unlisted seat to reserve no corner prop width.")
    }

    /**
     * 取得各座位的桌角預留寬度。
     *
     * @param cornerWidthsBySeat 指定座位的物件寬度。
     * @return 計入指定物件後，各座位的預留寬度。
     */
    private fun cornerWidths(cornerWidthsBySeat: Map<Int, Double>) = exhaustiveDrawSettlementStageInputs(
        request = requestOf(playerPresentation(seatIndex = 0)),
        tableState = stateWith(),
        cornerWidthsBySeat = cornerWidthsBySeat,
        tileOrder = FakeTileOrder,
        tileAssetRegistry = FakeTileAssetRegistry,
    ).reservedCornerWidthsBySeat

    /**
     * 建立測試用的牌張。
     *
     * @param tile 牌面。
     * @return 帶有測試識別碼的牌張。
     */
    private fun tileOf(tile: Tile): IdentifiedTile = FakeIdentifiedTileFactory.create(tile)

    /**
     * 建立測試用的桌況。
     *
     * @param handTiles 莊家手牌。
     * @param wallTiles 活牌牆牌序。
     * @param deadWallTiles 王牌區牌序。
     * @return 含東、南兩位玩家及指定牌區的桌況。
     */
    private fun stateWith(
        handTiles: List<IdentifiedTile> = emptyList(),
        wallTiles: List<IdentifiedTile> = emptyList(),
        deadWallTiles: List<IdentifiedTile> = emptyList(),
    ): TableState {
        val dealer = FakeMahjongPlayerFactory.create(Wind.EAST, hand = Hand(tiles = handTiles))
        val opponent = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        return FakeTableStateFactory.create(
            players = listOf(dealer, opponent),
            dealerPlayerId = dealer.id,
            tileWall = TileWall(wallTiles),
            reservedWallTiles = deadWallTiles,
        )
    }

    /**
     * 建立測試用的結算請求。
     *
     * @param players 要放入請求的玩家呈現資料。
     * @return 一般荒牌流局原因的結算請求。
     */
    private fun requestOf(vararg players: ExhaustiveDrawSettlementPlayerPresentation) = ExhaustiveDrawSettlementPresentationRequest(reasonId = "mahjongcraft:exhaustive_draw", players = players.toList())

    /**
     * 建立測試用的玩家結算資料。
     *
     * @param seatIndex 座位索引，也用來選擇測試自風。
     * @param waitingTiles 等待牌面。
     * @param revealedHandTileIds 要公開的手牌 ID。
     * @return 排名、聽牌與公開手牌欄位均已填入的玩家呈現資料。
     */
    private fun playerPresentation(
        seatIndex: Int,
        waitingTiles: List<Tile> = emptyList(),
        revealedHandTileIds: List<Uuid> = emptyList(),
    ) = ExhaustiveDrawSettlementPlayerPresentation(
        ranking = ScoreRankingPlayer(
            playerId = Uuid.random(),
            seatIndex = seatIndex,
            isAi = false,
            previousScore = 25000,
            currentScore = 25000,
            previousRank = seatIndex + 1,
            currentRank = seatIndex + 1,
        ),
        seatWind = Wind.entries[seatIndex],
        handTileIds = emptyList(),
        handDisclosure = ExhaustiveDrawHandDisclosure.TENPAI,
        revealedHandTileIds = revealedHandTileIds,
        waitingTiles = waitingTiles,
        statusId = null,
    )

    /** 副露寬度與牌序無關，測試只需要一個穩定的比較器。 */
    private object FakeTileOrder : TileOrder {
        override fun compare(first: Tile, second: Tile): Int = 0
    }

    /** 只接受 lookup 的測試替身；任何註冊或凍結呼叫都表示測試走錯路徑。 */
    private object FakeTileAssetRegistry : MinecraftTileAssetRegistry {
        override val registrationKeys: Set<String> get() = emptySet()

        override val registeredAssetKeys: Set<String> get() = emptySet()

        override val isFrozen: Boolean get() = false

        override fun register(definition: MinecraftTileAssetDefinition) = error("Unexpected registration")

        override fun find(typeId: TileTypeId): String? = null

        override fun isRegisteredAssetKey(assetKey: String): Boolean = false

        override fun freeze() = error("Unexpected freeze")
    }
}
