package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueExample
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroupRole

/**
 * 建立需要以牌面解釋的台灣麻將台型範例。
 *
 * 牌例只協助玩家辨認牌型，不參與權威計台。情境型台種由文字說明，不以一副無法表達先後順序的手牌代替。
 *
 * @param type 要顯示範例的台種。
 * @return 對應台種的完整手牌範例；不需要牌例時為空列表。
 */
internal fun taiwanCatalogueExamples(type: TaiwanTaiType): List<RuleCatalogueExample> = when (type) {
    TaiwanTaiType.ConcealedHand -> listOf(standardHand(TaiwanCatalogueKeys.CONCEALED_HAND_EXAMPLE))
    TaiwanTaiType.SelfDraw -> listOf(standardHand(TaiwanCatalogueKeys.SELF_DRAW_EXAMPLE))
    TaiwanTaiType.FullyConcealedHand -> listOf(standardHand(TaiwanCatalogueKeys.FULLY_CONCEALED_HAND_EXAMPLE))
    TaiwanTaiType.SeatWind -> listOf(
        full(m1, m2, m3, p4, p5, p6, s7, s8, s9, east, east, east, red, red, s1, s2, winning = s3, description = TaiwanCatalogueKeys.SEAT_WIND_EXAMPLE),
    )
    TaiwanTaiType.RoundWind -> listOf(
        full(m1, m2, m3, p4, p5, p6, s7, s8, s9, south, south, south, red, red, s1, s2, winning = s3, description = TaiwanCatalogueKeys.ROUND_WIND_EXAMPLE),
    )
    TaiwanTaiType.Dragon -> listOf(
        full(m1, m2, m3, p4, p5, p6, s7, s8, s9, red, red, red, east, east, s1, s2, winning = s3),
    )
    TaiwanTaiType.SeatFlower -> listOf(illustration(spring, plum, description = TaiwanCatalogueKeys.SEAT_FLOWER_EXAMPLE))
    TaiwanTaiType.FlowerSet -> listOf(illustration(spring, summer, autumn, winter))
    TaiwanTaiType.SingleWait -> listOf(
        full(m1, m2, m3, m4, m5, m6, p1, p2, p3, p4, p5, p6, s1, s2, s3, east, winning = east, description = TaiwanCatalogueKeys.SINGLE_WAIT_EXAMPLE),
    )
    TaiwanTaiType.RobbingKong -> listOf(standardHand(TaiwanCatalogueKeys.ROBBING_KONG_EXAMPLE))
    TaiwanTaiType.ReplacementWin -> listOf(standardHand(TaiwanCatalogueKeys.REPLACEMENT_WIN_EXAMPLE))
    TaiwanTaiType.LastDraw -> listOf(standardHand(TaiwanCatalogueKeys.LAST_DRAW_EXAMPLE))
    TaiwanTaiType.AllSequences -> listOf(
        full(m1, m2, m3, m4, m5, m6, p1, p2, p3, p4, p5, p6, s2, s3, s8, s8, winning = s1),
    )
    TaiwanTaiType.ThreeConcealedTriplets -> listOf(
        full(m1, m1, m1, p2, p2, p2, s3, s3, s3, p4, p5, p6, s7, s8, s9, east, winning = east),
    )
    TaiwanTaiType.FourConcealedTriplets -> listOf(
        full(m1, m1, m1, p2, p2, p2, s3, s3, s3, east, east, east, p4, p5, p6, red, winning = red),
    )
    TaiwanTaiType.FiveConcealedTriplets -> listOf(
        full(m1, m1, m1, p2, p2, p2, s3, s3, s3, east, east, east, red, red, red, white, winning = white),
    )
    TaiwanTaiType.AllExposed -> listOf(
        groupedFull(
            group(RuleCatalogueTileGroupRole.OPEN_MELD, m1, m2, m3),
            group(RuleCatalogueTileGroupRole.OPEN_MELD, p1, p2, p3),
            group(RuleCatalogueTileGroupRole.OPEN_MELD, s7, s8, s9),
            group(RuleCatalogueTileGroupRole.OPEN_MELD, red, red, red),
            group(RuleCatalogueTileGroupRole.OPEN_MELD, green, green, green),
            group(RuleCatalogueTileGroupRole.HAND, east),
            winning = east,
        ),
    )
    TaiwanTaiType.AllTriplets -> listOf(
        full(m1, m1, m1, p2, p2, p2, s3, s3, s3, east, east, east, red, red, white, white, winning = red),
    )
    TaiwanTaiType.LittleDragons -> listOf(
        full(m1, m2, m3, p4, p5, p6, white, white, white, green, green, green, red, red, s7, s8, winning = s9),
    )
    TaiwanTaiType.BigDragons -> listOf(
        full(m1, m2, m3, white, white, white, green, green, green, red, red, red, s7, s8, east, east, winning = s9),
    )
    TaiwanTaiType.HalfFlush -> listOf(
        full(p1, p2, p3, p4, p5, p6, p7, p8, p9, east, east, east, white, white, green, green, winning = white),
    )
    TaiwanTaiType.FullFlush -> listOf(
        full(p1, p2, p3, p2, p3, p4, p4, p5, p6, p7, p8, p9, p9, p9, p5, p5, winning = p5),
    )
    TaiwanTaiType.LittleWinds -> listOf(
        full(east, east, east, south, south, south, west, west, west, north, north, m1, m2, m3, p7, p8, winning = p9),
    )
    TaiwanTaiType.BigWinds -> listOf(
        full(east, east, east, south, south, south, west, west, west, north, north, north, m1, m2, red, red, winning = m3),
    )
    TaiwanTaiType.AllHonors -> listOf(
        full(east, east, east, south, south, south, west, west, west, north, north, north, white, white, white, red, winning = red),
    )
    TaiwanTaiType.DeclaredReady -> listOf(standardHand(TaiwanCatalogueKeys.DECLARED_READY_EXAMPLE))
    TaiwanTaiType.HeavenlyReady -> listOf(standardHand(TaiwanCatalogueKeys.HEAVENLY_READY_EXAMPLE))
    TaiwanTaiType.EarthlyReady -> listOf(standardHand(TaiwanCatalogueKeys.EARTHLY_READY_EXAMPLE))
    TaiwanTaiType.HumanWin -> listOf(standardHand(TaiwanCatalogueKeys.HUMAN_WIN_EXAMPLE))
    TaiwanTaiType.EarthlyWin -> listOf(standardHand(TaiwanCatalogueKeys.EARTHLY_WIN_EXAMPLE))
    TaiwanTaiType.HeavenlyWin -> listOf(standardHand(TaiwanCatalogueKeys.HEAVENLY_WIN_EXAMPLE))
    TaiwanTaiType.EightFlowers -> listOf(illustration(spring, summer, autumn, winter, plum, orchid, chrysanthemum, bamboo))
    TaiwanTaiType.SevenFlowers -> listOf(illustration(spring, summer, autumn, winter, plum, orchid, chrysanthemum))
}

/**
 * 建立十六張麻將的完整手牌範例。
 *
 * @param tiles 和牌前的十六張牌。
 * @param winning 獨立顯示的和牌張。
 * @return 明確標記為完整手牌的牌例。
 */
private fun full(
    vararg tiles: TileFaceView,
    winning: TileFaceView,
    description: String? = null,
): RuleCatalogueExample {
    require(tiles.size == 16) { "A Taiwan complete-hand example must contain 16 tiles before winning" }
    return RuleCatalogueExample(
        completeHand = true,
        groups = listOf(
            RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.HAND, tiles.toList()),
            RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.WINNING_TILE, listOf(winning)),
        ),
        descriptionTranslationKey = description,
    )
}

/** 建立常見完整手牌並附上指定情境說明。 */
private fun standardHand(description: String): RuleCatalogueExample = full(
    m1,
    m2,
    m3,
    m4,
    m5,
    m6,
    p1,
    p2,
    p3,
    p4,
    p5,
    p6,
    s1,
    s2,
    east,
    east,
    winning = s3,
    description = description,
)

/** 建立含有副露分組的完整手牌範例。 */
private fun groupedFull(
    vararg groups: RuleCatalogueTileGroup,
    winning: TileFaceView,
): RuleCatalogueExample = RuleCatalogueExample(
    completeHand = true,
    groups = groups.toList() + RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.WINNING_TILE, listOf(winning)),
)

/** 建立指定語意的範例牌組。 */
private fun group(role: RuleCatalogueTileGroupRole, vararg tiles: TileFaceView): RuleCatalogueTileGroup = RuleCatalogueTileGroup(role, tiles.toList())

/** 建立不代表完整手牌的局部牌面示意。 */
private fun illustration(
    vararg tiles: TileFaceView,
    description: String? = null,
): RuleCatalogueExample = RuleCatalogueExample(
    completeHand = false,
    groups = listOf(RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.ILLUSTRATION, tiles.toList())),
    descriptionTranslationKey = description,
)

/** 建立範例用數牌。 */
private fun number(suit: Tile.Suit, value: Int): TileFaceView = TileFaceView(Tile.Numeric(suit, value))

/** 範例用一萬。 */
private val m1 = number(Tile.Suit.Character, 1)

/** 範例用二萬。 */
private val m2 = number(Tile.Suit.Character, 2)

/** 範例用三萬。 */
private val m3 = number(Tile.Suit.Character, 3)

/** 範例用四萬。 */
private val m4 = number(Tile.Suit.Character, 4)

/** 範例用五萬。 */
private val m5 = number(Tile.Suit.Character, 5)

/** 範例用六萬。 */
private val m6 = number(Tile.Suit.Character, 6)

/** 範例用一筒。 */
private val p1 = number(Tile.Suit.Dot, 1)

/** 範例用二筒。 */
private val p2 = number(Tile.Suit.Dot, 2)

/** 範例用三筒。 */
private val p3 = number(Tile.Suit.Dot, 3)

/** 範例用四筒。 */
private val p4 = number(Tile.Suit.Dot, 4)

/** 範例用五筒。 */
private val p5 = number(Tile.Suit.Dot, 5)

/** 範例用六筒。 */
private val p6 = number(Tile.Suit.Dot, 6)

/** 範例用七筒。 */
private val p7 = number(Tile.Suit.Dot, 7)

/** 範例用八筒。 */
private val p8 = number(Tile.Suit.Dot, 8)

/** 範例用九筒。 */
private val p9 = number(Tile.Suit.Dot, 9)

/** 範例用一條。 */
private val s1 = number(Tile.Suit.Bamboo, 1)

/** 範例用二條。 */
private val s2 = number(Tile.Suit.Bamboo, 2)

/** 範例用三條。 */
private val s3 = number(Tile.Suit.Bamboo, 3)

/** 範例用七條。 */
private val s7 = number(Tile.Suit.Bamboo, 7)

/** 範例用八條。 */
private val s8 = number(Tile.Suit.Bamboo, 8)

/** 範例用九條。 */
private val s9 = number(Tile.Suit.Bamboo, 9)

/** 範例用東風。 */
private val east = TileFaceView(Tile.Honor.East)

/** 範例用南風。 */
private val south = TileFaceView(Tile.Honor.South)

/** 範例用西風。 */
private val west = TileFaceView(Tile.Honor.West)

/** 範例用北風。 */
private val north = TileFaceView(Tile.Honor.North)

/** 範例用紅中。 */
private val red = TileFaceView(Tile.Honor.Red)

/** 範例用青發。 */
private val green = TileFaceView(Tile.Honor.Green)

/** 範例用白板。 */
private val white = TileFaceView(Tile.Honor.White)

/** 範例用春。 */
private val spring = TileFaceView(Tile.Extension(TaiwanTileTypes.SPRING))

/** 範例用夏。 */
private val summer = TileFaceView(Tile.Extension(TaiwanTileTypes.SUMMER))

/** 範例用秋。 */
private val autumn = TileFaceView(Tile.Extension(TaiwanTileTypes.AUTUMN))

/** 範例用冬。 */
private val winter = TileFaceView(Tile.Extension(TaiwanTileTypes.WINTER))

/** 範例用梅。 */
private val plum = TileFaceView(Tile.Extension(TaiwanTileTypes.PLUM))

/** 範例用蘭。 */
private val orchid = TileFaceView(Tile.Extension(TaiwanTileTypes.ORCHID))

/** 範例用菊。 */
private val chrysanthemum = TileFaceView(Tile.Extension(TaiwanTileTypes.CHRYSANTHEMUM))

/** 範例用竹。 */
private val bamboo = TileFaceView(Tile.Extension(TaiwanTileTypes.BAMBOO))
