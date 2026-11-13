package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證散放牌河的位置表與挑位置：不重疊、不碰牌牆與擲骰區、內建台麻不會疊牌，先打出的牌不會移動。 */
class ScatteredDiscardLayoutTest {
    /**
     * 每一張位置表的位置都在牌河範圍內、兩兩不重疊，位置數多於台麻牌河上同時可能存在的牌數；有花牌與沒有花牌的牌桌都檢查。
     */
    @Test
    fun `every pattern fits the most discards a taiwan river can hold`() {
        TAIWAN_TABLES.forEach { (wallStacks, mostRiverTiles) ->
            val reach = MahjongTileTableLayout.scatterReach(taiwanTileLayout(), wallStacks)
            ScatteredDiscardLayout.PATTERN_SEEDS.forEach { seed ->
                val slots = ScatteredDiscardLayout.pattern(seed, reach)

                assertTrue(slots.size >= mostRiverTiles, "Seed $seed has ${slots.size} slots for $wallStacks stacks, needs $mostRiverTiles")
                slots.forEach { slot -> assertTrue(ScatteredDiscardLayout.fits(slot, reach), "Seed $seed slot leaves the river: $slot") }
                slots.forEachIndexed { index, first ->
                    slots.drop(index + 1).forEach { second -> assertFalse(ScatteredDiscardLayout.overlaps(first, second), "Seed $seed: $first overlaps $second") }
                }
            }
        }
    }

    /** 位置表的每個位置四個角都在牌牆內側，整張牌都在擲骰區之外；角度涵蓋四個方向。 */
    @Test
    fun `patterns keep clear of the walls and the dice area and turn every way`() {
        val wallInnerFace = TAIWAN_WALL_STACKS / 2.0 * (MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING) -
            taiwanTileLayout().wallInset - MahjongTileDimensions.TILE_HEIGHT / 2.0
        val slots = ScatteredDiscardLayout.pattern(ScatteredDiscardLayout.PATTERN_SEEDS.first(), reach())

        slots.forEach { slot ->
            assertTrue(ScatteredDiscardLayout.corners(slot).all { (x, z) -> abs(x) < wallInnerFace && abs(z) < wallInnerFace }, "Slot reaches a wall: $slot")
            assertTrue(ScatteredDiscardLayout.distanceFromCentre(slot) >= MahjongTileTableLayout.SCATTER_DICE_CLEARANCE, "Slot enters the dice area: $slot")
        }
        assertTrue((0 until QUADRANTS).all { quadrant -> slots.any { (it.angleDegrees / QUARTER_TURN_DEGREES).toInt() == quadrant } })
    }

    /** 同一張桌子相鄰兩局用不同的位置表。 */
    @Test
    fun `consecutive rounds use different patterns`() {
        val random = Random(SEED)
        repeat(TABLES) {
            val tableId = Uuid.fromLongs(random.nextLong(), random.nextLong())
            (0 until ROUNDS).forEach { roundSerial ->
                assertNotEquals(
                    ScatteredDiscardLayout.roundPattern(tableId, roundSerial, reach()),
                    ScatteredDiscardLayout.roundPattern(tableId, roundSerial + 1, reach()),
                    "Round $roundSerial and the next share a pattern",
                )
            }
        }
    }

    /**
     * 一局打到牌河最多的牌數，中間穿插被鳴走的牌：牌河上的牌都在桌面上、互不重疊；有空位時牌落在打牌者那一側。
     */
    @Test
    fun `a full taiwan round never stacks discards`() {
        val slots = ScatteredDiscardLayout.pattern(ScatteredDiscardLayout.PATTERN_SEEDS.first(), reach())
        val requests = fullRound(Random(SEED))
        val placed = ScatteredDiscardLayout.assign(slots, requests)
        val inRiver = requests.filterNot { it.isTaken }.map { placed.getValue(it.tileId) }

        assertTrue(inRiver.all { it.y == 0.0 }, "A discard was stacked")
        inRiver.forEachIndexed { index, first -> inRiver.drop(index + 1).forEach { second -> assertFalse(ScatteredDiscardLayout.overlaps(first, second)) } }
        requests.take(EARLY_DISCARDS).forEach { request ->
            val discard = placed.getValue(request.tileId)
            assertTrue(discard.x * request.towardX + discard.z * request.towardZ > 0.0, "Early discard $discard is not on its discarder's side")
        }
    }

    /** 打出更多牌或鳴走剛打出的牌，都不會讓先打出的牌移動。 */
    @Test
    fun `discards keep their place as the round goes on`() {
        val slots = ScatteredDiscardLayout.pattern(ScatteredDiscardLayout.PATTERN_SEEDS.first(), reach())
        val requests = fullRound(Random(SEED))
        val all = ScatteredDiscardLayout.assign(slots, requests)

        (1 until requests.size).forEach { count ->
            val prefix = ScatteredDiscardLayout.assign(slots, requests.take(count))
            requests.take(count - 1).forEach { request -> assertEquals(all.getValue(request.tileId), prefix.getValue(request.tileId)) }
        }
    }

    /** 牌桌上被鳴走的牌只離開，其他牌的位置不變。 */
    @Test
    fun `taking a discard leaves the other discards where they are`() {
        val tiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, it + 1)) }
        val pile = tiles.fold(FakeDiscardPile()) { acc, tile -> acc.discardTile(tile) }
        val tableId = Uuid.random()
        fun arrange(discardPile: FakeDiscardPile) = TableTileArrangement.arrange(
            TableTileArrangementInput(
                controllerX = 0,
                controllerY = 64,
                controllerZ = 0,
                tableFacing = MahjongTableFacing.NORTH,
                seatLayout = MahjongTableSeatLayout.forPlayerCount(2),
                tableState = FakeTableStateFactory.create(
                    id = tableId,
                    players = listOf(FakeMahjongPlayerFactory.create(Wind.EAST, discardPile = discardPile), FakeMahjongPlayerFactory.create(Wind.SOUTH)),
                ),
                tileLayout = taiwanTileLayout(),
                cornerWidthBySeat = emptyMap(),
                handPoseStates = emptyList(),
                wallProjection = null,
                faceUpWallTileIds = emptySet(),
            ),
        )
        val before = arrange(pile)
        val after = arrange(pile.takeLast())

        assertEquals(before.getValue(tiles[0].id).placement, after.getValue(tiles[0].id).placement)
        assertEquals(before.getValue(tiles[1].id).placement, after.getValue(tiles[1].id).placement)
        assertTrue(tiles[2].id !in after)
    }

    /**
     * 模擬一局：四家輪流打牌，偶爾有人鳴走剛打出的牌（被鳴走的牌不留在牌河），牌河上的牌數達到 [MOST_RIVER_TILES]。
     *
     * @param random 模擬用的隨機序列。
     * @return 依打出先後排列的牌。
     */
    private fun fullRound(random: Random): List<ScatterRequest> {
        val towards = listOf(0.0 to 1.0, 1.0 to 0.0, 0.0 to -1.0, -1.0 to 0.0)
        val requests = mutableListOf<ScatterRequest>()
        var seat = 0
        while (requests.count { !it.isTaken } < MOST_RIVER_TILES) {
            val (x, z) = towards[seat]
            val taken = random.nextInt(CLAIM_ONE_IN) == 0
            requests += ScatterRequest(Uuid.fromLongs(random.nextLong(), random.nextLong()), x, z, taken)
            seat = (seat + if (taken) 2 else 1) % towards.size
        }
        return requests
    }

    /** @return 有花牌的台麻牌桌的牌河範圍外緣。 */
    private fun reach(): Double = MahjongTileTableLayout.scatterReach(taiwanTileLayout(), TAIWAN_WALL_STACKS)

    /** @return 內建台麻登記的桌面牌位配置。 */
    private fun taiwanTileLayout(): TableTileLayoutProfile = TableTileLayoutProfileRegistryImpl().apply {
        BundledMinecraftMahjongExtensions.all.forEach { it.registerTableTileLayoutProfiles(this) }
    }.profileFor(BuiltInRuleModuleIds.TAIWAN)

    private companion object {
        /** 台麻有花牌時每面牌牆的墩數。 */
        const val TAIWAN_WALL_STACKS: Int = 18

        /**
         * 有花牌與沒有花牌的台麻牌桌，以及牌河上同時可能存在的最多牌數：活牌張數（總張數減王牌 16 張與發出的 65 張）加莊家第一打。
         * 被鳴走的牌離開牌河，鳴牌的人那一輪沒有摸牌，所以牌河上的牌不會超過這個數。
         */
        val TAIWAN_TABLES: List<Pair<Int, Int>> = listOf(18 to 144 - 16 - 65 + 1, 17 to 136 - 16 - 65 + 1)

        /** 模擬一局時牌河上的牌數，取有花牌時的最多牌數。 */
        const val MOST_RIVER_TILES: Int = 144 - 16 - 65 + 1

        /** 模擬時每幾張牌有一張被鳴走。 */
        const val CLAIM_ONE_IN: Int = 6

        /** 一局初期必定落在打牌者那一側的牌數。 */
        const val EARLY_DISCARDS: Int = 12

        /** 隨機種子。 */
        const val SEED: Int = 7

        /** 檢查的桌子數。 */
        const val TABLES: Int = 20

        /** 每張桌子檢查的局數。 */
        const val ROUNDS: Int = 16

        /** 四分之一圈的個數。 */
        const val QUADRANTS: Int = 4

        /** 四分之一圈的角度。 */
        const val QUARTER_TURN_DEGREES: Float = 90.0f
    }
}
