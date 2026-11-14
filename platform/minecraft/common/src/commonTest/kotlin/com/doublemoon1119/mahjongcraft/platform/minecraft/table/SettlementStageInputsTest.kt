package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawHandDisclosure
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPlayerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileAreas
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftRuleTileAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileResources
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileStyleAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfile
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

    /** 規則保留區中的牌找得到。 */
    @Test
    fun `finds a tile in the reserved wall`() {
        val tile = tileOf(Tile.Honor.White)
        val state = stateWith(reservedWallTiles = listOf(tile))

        assertEquals(tile, state.findTile(tile.id))
    }

    /** 不在桌上的牌張 ID 沒有結果。 */
    @Test
    fun `finds no tile outside the table`() {
        assertNull(stateWith().findTile(Uuid.random()))
    }

    /** 已裁切的牌面依 ID 轉成資產。 */
    @Test
    fun `maps every face to its asset`() {
        val first = tileOf(Tile.Numeric(Tile.Suit.Character, 1))
        val second = tileOf(Tile.Honor.East)

        val assets = tileAssetKeysById(mapOf(second.id to TileFaceView(second.tile), first.id to TileFaceView(first.tile)), FakeTileAssetRegistry)

        assertEquals(mapOf(first.id to "m1", second.id to "east"), assets)
    }

    /** 聽牌資產依座位對應，並保留請求中的等待牌順序。 */
    @Test
    fun `keeps the waiting tile order of each seat`() {
        val request = requestOf(
            playerPresentation(seatIndex = 0, waitingTiles = listOf(Tile.Honor.East, Tile.Numeric(Tile.Suit.Dot, 9))),
            playerPresentation(seatIndex = 1, waitingTiles = listOf(Tile.Numeric(Tile.Suit.Bamboo, 2))),
        )

        val inputs = exhaustiveDrawSettlementStageInputs(request, stateWith(), emptyMap(), TableTileLayoutProfile.STANDARD, null, emptyMap(), FakeTileAssetRegistry)

        assertEquals(
            mapOf(0 to listOf("east", "p9"), 1 to listOf("s2")),
            inputs.waitingTileAssetsBySeat,
        )
    }

    /** 公開手牌的資產由已裁切的牌面取得。 */
    @Test
    fun `resolves the revealed hand assets from the table`() {
        val revealed = tileOf(Tile.Numeric(Tile.Suit.Character, 5))
        val state = stateWith(handTiles = listOf(revealed))
        val request = requestOf(playerPresentation(seatIndex = 0, revealedHandTileIds = listOf(revealed.id)))

        val inputs = exhaustiveDrawSettlementStageInputs(request, state, emptyMap(), TableTileLayoutProfile.STANDARD, null, mapOf(revealed.id to TileFaceView(revealed.tile)), FakeTileAssetRegistry)

        assertEquals(mapOf(revealed.id to "m5"), inputs.revealedTileAssetsById)
    }

    /** 桌況已不存在時仍算得出聽牌資產，沒有公開牌面與手牌平移。 */
    @Test
    fun `still resolves the waiting assets without a table state`() {
        val request = requestOf(
            playerPresentation(seatIndex = 0, waitingTiles = listOf(Tile.Honor.South), revealedHandTileIds = listOf(Uuid.random())),
        )

        val inputs = exhaustiveDrawSettlementStageInputs(
            request = request,
            tableState = null,
            cornerWidthsBySeat = emptyMap(),
            tileLayout = TableTileLayoutProfile.STANDARD,
            wallClearance = null,
            revealedTileFaces = emptyMap(),
            tileAssetRegistry = FakeTileAssetRegistry,
        )

        assertEquals(mapOf(0 to listOf("south")), inputs.waitingTileAssetsBySeat)
        assertEquals(emptyMap(), inputs.revealedTileAssetsById)
        assertEquals(emptyMap(), inputs.handShiftsBySeat)
    }

    /** 移出手牌的牌（例如拔出的北）接在副露之後，手牌平移與桌面擺放使用同一份計算。 */
    @Test
    fun `hand shifts count set aside tiles like the table layout`() {
        val north = tileOf(Tile.Honor.North)
        val initial = stateWith()
        val state = initial.copy(
            players = initial.players.mapIndexed { index, player ->
                if (index == 0) player.copy(tileAreas = PlayerTileAreas.EMPTY.withArea("test:aside", listOf(north))) else player
            },
        )

        val inputs = exhaustiveDrawSettlementStageInputs(
            request = requestOf(playerPresentation(seatIndex = 0)),
            tableState = state,
            cornerWidthsBySeat = emptyMap(),
            tileLayout = TableTileLayoutProfile.STANDARD,
            wallClearance = null,
            revealedTileFaces = emptyMap(),
            tileAssetRegistry = FakeTileAssetRegistry,
        )

        state.players.forEachIndexed { seat, player ->
            assertEquals(
                MahjongPlayerAreaLayout.handShift(
                    tileLayout = TableTileLayoutProfile.STANDARD,
                    wallClearance = null,
                    cornerWidth = 0.0,
                    melds = emptyList(),
                    setAsideTileCount = player.setAsideTiles.size,
                    standingTileCount = player.hand.tiles.size,
                    playerCount = state.players.size,
                ),
                inputs.handShiftsBySeat.getValue(seat),
            )
        }
    }

    /** 每個座位都有一份手牌平移。 */
    @Test
    fun `gives every seat a hand shift`() {
        val inputs = exhaustiveDrawSettlementStageInputs(
            request = requestOf(playerPresentation(seatIndex = 0)),
            tableState = stateWith(),
            cornerWidthsBySeat = emptyMap(),
            tileLayout = TableTileLayoutProfile.STANDARD,
            wallClearance = null,
            revealedTileFaces = emptyMap(),
            tileAssetRegistry = FakeTileAssetRegistry,
        )

        assertEquals(setOf(0, 1), inputs.handShiftsBySeat.keys)
        assertTrue(inputs.handShiftsBySeat.values.all { it.isFinite() })
    }

    /** 桌角物件寬度只影響指定的座位。 */
    @Test
    fun `applies the corner prop width to the listed seat only`() {
        val state = stateWith()
        val without = handShifts(emptyMap())
        val with = handShifts(mapOf(0 to 2.0))
        val expected = MahjongPlayerAreaLayout.handShift(
            tileLayout = TableTileLayoutProfile.STANDARD,
            wallClearance = null,
            cornerWidth = 2.0,
            melds = emptyList(),
            setAsideTileCount = 0,
            standingTileCount = state.players[0].hand.tiles.size,
            playerCount = state.players.size,
        )

        assertEquals(expected, with.getValue(0), "Expected the listed seat to use its corner prop width.")
        assertEquals(without.getValue(1), with.getValue(1), "Expected an unlisted seat to keep its hand position.")
    }

    /**
     * 取得各座位的手牌平移。
     *
     * @param cornerWidthsBySeat 指定座位的物件寬度。
     * @return 計入指定物件後，各座位的手牌平移。
     */
    private fun handShifts(cornerWidthsBySeat: Map<Int, Double>) = exhaustiveDrawSettlementStageInputs(
        request = requestOf(playerPresentation(seatIndex = 0)),
        tableState = stateWith(),
        cornerWidthsBySeat = cornerWidthsBySeat,
        tileLayout = TableTileLayoutProfile.STANDARD,
        wallClearance = null,
        revealedTileFaces = emptyMap(),
        tileAssetRegistry = FakeTileAssetRegistry,
    ).handShiftsBySeat

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
     * @param reservedWallTiles 規則保留區牌序。
     * @return 含東、南兩位玩家及指定牌區的桌況。
     */
    private fun stateWith(
        handTiles: List<IdentifiedTile> = emptyList(),
        wallTiles: List<IdentifiedTile> = emptyList(),
        reservedWallTiles: List<IdentifiedTile> = emptyList(),
    ): TableState {
        val dealer = FakeMahjongPlayerFactory.create(Wind.EAST, hand = Hand(tiles = handTiles))
        val opponent = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        return FakeTableStateFactory.create(
            players = listOf(dealer, opponent),
            dealerPlayerId = dealer.id,
            tileWall = TileWall(wallTiles),
            reservedWallTiles = reservedWallTiles,
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
        drawnTileId = null,
        handDisclosure = ExhaustiveDrawHandDisclosure.TENPAI,
        revealedHandTileIds = revealedHandTileIds,
        waitingTiles = waitingTiles,
        statusId = null,
    )

    /** 只接受 lookup 的測試替身；任何註冊或凍結呼叫都表示測試走錯路徑。 */
    private object FakeTileAssetRegistry : MinecraftTileAssetRegistry {
        override val registrationKeys: Set<String> get() = emptySet()

        override val appearanceRegistrationKeys: Set<String> get() = emptySet()

        override val ruleAppearanceBindingKeys: Set<String> get() = emptySet()

        override val registeredAppearances: List<MinecraftTileSetAppearanceDefinition> get() = emptyList()

        override val registeredAssetKeys: Set<String> get() = emptySet()

        override val isFrozen: Boolean get() = false

        override fun register(definition: MinecraftTileAssetDefinition) = error("Unexpected registration")

        override fun register(definition: MinecraftTileStyleAssetDefinition) = error("Unexpected registration")

        override fun register(definition: MinecraftTileSetAppearanceDefinition) = error("Unexpected registration")

        override fun register(definition: MinecraftRuleTileAppearanceDefinition) = error("Unexpected registration")

        override fun find(typeId: TileTypeId): String? = null

        override fun findStyle(styleId: String, tile: Tile): String? = null

        override fun findResources(assetKey: String): MinecraftTileResources? = null

        override fun baseAssetKey(assetKey: String): String = assetKey

        override fun findBodyAppearance(assetKey: String): MinecraftTileSetAppearanceDefinition? = null

        override fun findAppearance(appearanceId: String): MinecraftTileSetAppearanceDefinition? = null

        override fun findAppearanceForRule(ruleModuleId: String): MinecraftTileSetAppearanceDefinition? = null

        override fun isRegisteredAssetKey(assetKey: String): Boolean = false

        override fun freeze() = error("Unexpected freeze")
    }
}
