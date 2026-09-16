package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementHandPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPlayerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
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

        val inputs = exhaustiveDrawSettlementStageInputs(request, stateWith(), FakeTileAssetRegistry)

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

        val inputs = exhaustiveDrawSettlementStageInputs(request, state, FakeTileAssetRegistry)

        assertEquals(mapOf(revealed.id to "m5"), inputs.revealedTileAssetsById)
    }

    /** 桌況已不存在時仍算得出聽牌資產，另外兩份為空。 */
    @Test
    fun `still resolves the waiting assets without a table state`() {
        val request = requestOf(
            playerPresentation(seatIndex = 0, waitingTiles = listOf(Tile.Honor.South), revealedHandTileIds = listOf(Uuid.random())),
        )

        val inputs = exhaustiveDrawSettlementStageInputs(request, tableState = null, tileAssetRegistry = FakeTileAssetRegistry)

        assertEquals(mapOf(0 to listOf("south")), inputs.waitingTileAssetsBySeat)
        assertEquals(emptyMap(), inputs.revealedTileAssetsById)
        assertEquals(emptyMap(), inputs.reservedCornerWidthsBySeat)
    }

    /** 每個座位都有一份桌角預留寬度。 */
    @Test
    fun `reserves a corner width for every seat`() {
        val inputs = exhaustiveDrawSettlementStageInputs(requestOf(playerPresentation(seatIndex = 0)), stateWith(), FakeTileAssetRegistry)

        assertEquals(setOf(0, 1), inputs.reservedCornerWidthsBySeat.keys)
        assertTrue(inputs.reservedCornerWidthsBySeat.values.all { it >= 0.0 })
    }

    /** 連莊棒只佔莊家的桌角。 */
    @Test
    fun `charges the combo sticks to the dealer only`() {
        val withoutCombo = cornerWidths(comboCount = 0)
        val withCombo = cornerWidths(comboCount = 3)

        assertTrue(withCombo.getValue(0) > withoutCombo.getValue(0), "Expected the dealer's corner to grow with the combo sticks.")
        assertEquals(withoutCombo.getValue(1), withCombo.getValue(1), "Expected a non dealer's corner to ignore the combo sticks.")
    }

    /** 取得各座位的桌角預留寬度。 */
    private fun cornerWidths(comboCount: Int) = exhaustiveDrawSettlementStageInputs(
        requestOf(playerPresentation(seatIndex = 0)),
        stateWith(comboCount = comboCount),
        FakeTileAssetRegistry,
    ).reservedCornerWidthsBySeat

    /** 建立測試用的牌張。 */
    private fun tileOf(tile: Tile): IdentifiedTile = FakeIdentifiedTileFactory.create(tile)

    /** 建立測試用的桌況。 */
    private fun stateWith(
        handTiles: List<IdentifiedTile> = emptyList(),
        wallTiles: List<IdentifiedTile> = emptyList(),
        deadWallTiles: List<IdentifiedTile> = emptyList(),
        comboCount: Int = 0,
    ): TableState {
        val dealer = FakeMahjongPlayerFactory.create(Wind.EAST, hand = Hand(tiles = handTiles))
        val opponent = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        return FakeTableStateFactory.create(
            players = listOf(dealer, opponent),
            dealerPlayerId = dealer.id,
            tileWall = TileWall(wallTiles),
            comboCount = comboCount,
            initialDeadWall = deadWallTiles,
        )
    }

    /** 建立測試用的結算請求。 */
    private fun requestOf(vararg players: ExhaustiveDrawSettlementPlayerPresentation) = ExhaustiveDrawSettlementPresentationRequest(reasonId = "mahjongcraft:exhaustive_draw", players = players.toList())

    /** 建立測試用的玩家結算資料。 */
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
        handPresentation = ExhaustiveDrawSettlementHandPresentation.REVEAL_TENPAI,
        revealedHandTileIds = revealedHandTileIds,
        waitingTiles = waitingTiles,
        statusId = null,
    )

    /**
     * 只在解析擴充牌種時才會被查詢的 registry 測試替身。
     *
     * 內建牌種的資產名稱不經過 registry（見 `Tile.toAssetKey`），本測試也只使用內建牌種。
     */
    private object FakeTileAssetRegistry : MinecraftTileAssetRegistry {
        override val registrationKeys: Set<String> get() = emptySet()

        override val registeredAssetKeys: Set<String> get() = emptySet()

        override val isFrozen: Boolean get() = false

        override fun register(typeId: TileTypeId, assetKey: String) = error("Unexpected registration")

        override fun find(typeId: TileTypeId): String? = null

        override fun isRegisteredAssetKey(assetKey: String): Boolean = false

        override fun freeze() = error("Unexpected freeze")
    }
}
