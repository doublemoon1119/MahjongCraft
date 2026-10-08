package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueExample
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroupRole

/** 日麻役種目錄使用的範例牌面與情境說明鍵。 */
internal object RiichiCatalogueExampleKeys {
    /** 範例說明翻譯鍵的共用前綴。 */
    private const val PREFIX: String = "mahjongcraft.rule_catalogue.riichi.example."

    /** 自風為西的情境。 */
    const val SEAT_WEST: String = PREFIX + "seat_west"

    /** 西場情境。 */
    const val ROUND_WEST: String = PREFIX + "round_west"

    /** 自摸情境。 */
    const val TSUMO: String = PREFIX + "tsumo"

    /** 榮和情境。 */
    const val RON: String = PREFIX + "ron"

    /** 立直情境。 */
    const val RIICHI: String = PREFIX + "riichi"

    /** 雙立直情境。 */
    const val DOUBLE_RIICHI: String = PREFIX + "double_riichi"

    /** 一發情境。 */
    const val IPPATSU: String = PREFIX + "ippatsu"

    /** 嶺上開花情境。 */
    const val RINSHAN: String = PREFIX + "rinshan"

    /** 海底摸月情境。 */
    const val HAITEI: String = PREFIX + "haitei"

    /** 河底撈魚情境。 */
    const val HOUTEI: String = PREFIX + "houtei"

    /** 搶槓情境。 */
    const val CHANKAN: String = PREFIX + "chankan"

    /** 天和情境。 */
    const val TENHOU: String = PREFIX + "tenhou"

    /** 地和情境。 */
    const val CHIIHOU: String = PREFIX + "chiihou"

    /** 牌面加碼示意。 */
    const val BONUS: String = PREFIX + "bonus"

    /** 拔出的北擺在副露旁示意。 */
    const val NUKI_DORA: String = PREFIX + "nuki_dora"

    /** 以其他玩家的立直宣言牌榮和。 */
    const val TSUBAME_GAESHI: String = PREFIX + "tsubame_gaeshi"

    /** 以其他玩家槓後打出的牌榮和。 */
    const val KANBURI: String = PREFIX + "kanburi"

    /** 海底自摸一筒。 */
    const val IIPIN_MOYUE: String = PREFIX + "iipin_moyue"

    /** 河底榮和九筒。 */
    const val CHUUPIN_RAOYUI: String = PREFIX + "chuupin_raoyui"

    /** 非莊家在自己第一次摸牌前榮和。 */
    const val RENHOU: String = PREFIX + "renhou"

    /** 雙立直後以海底自摸或河底榮和。 */
    const val ISHI_NO_UE_NI_MO_SANNEN: String = PREFIX + "ishi_no_ue_ni_mo_sannen"
}

/**
 * 建立指定日麻役種的靜態範例；範例只供說明，不參與權威規則判定。
 *
 * 四人與三人日麻共用同一套範例，因此範例不用二～八萬，也不用吃，三人日麻同樣成立。只有一定要用到二～八萬、
 * 三人日麻因此無法成立的役種例外。赤寶牌的張數兩者不同，依 [usesThreePlayerTiles] 顯示。
 *
 * @param type 要顯示範例的役種。
 * @param usesThreePlayerTiles 是否為三人日麻的牌組。
 * @return 對應役種的牌面或情境範例。
 */
internal fun riichiCatalogueExamples(type: YakuType, usesThreePlayerTiles: Boolean): List<RuleCatalogueExample> = when (type) {
    YakuType.Dora, YakuType.UraDora -> listOf(illustration(m9, p5, s5, description = RiichiCatalogueExampleKeys.BONUS))
    YakuType.AkaDora -> listOf(
        if (usesThreePlayerTiles) {
            illustration(p5Red, s5Red, description = RiichiCatalogueExampleKeys.BONUS)
        } else {
            illustration(m5Red, p5Red, s5Red, description = RiichiCatalogueExampleKeys.BONUS)
        },
    )
    YakuType.NukiDora -> listOf(illustration(north, north, description = RiichiCatalogueExampleKeys.NUKI_DORA))
    YakuType.Tanyao -> listOf(full(p2, p3, p4, p5, p6, p7, s3, s3, s3, s4, s5, s6, s8, winning = s8))
    YakuType.Pinfu -> listOf(full(p1, p2, p3, p5, p6, p7, s2, s3, s4, s6, s7, m9, m9, winning = s5))
    YakuType.Iipeikou -> listOf(full(p1, p2, p3, p1, p2, p3, s4, s4, s4, s7, s8, s9, m9, winning = m9))
    YakuType.Riichi -> listOf(full(p1, p2, p3, p4, p4, p4, s5, s6, s7, east, east, east, south, winning = south, description = RiichiCatalogueExampleKeys.RIICHI))
    YakuType.DoubleRiichi -> listOf(full(s1, s2, s3, s4, s4, s4, p5, p6, p7, white, white, white, green, winning = green, description = RiichiCatalogueExampleKeys.DOUBLE_RIICHI))
    YakuType.Ippatsu -> listOf(full(p1, p2, p3, p4, p4, p4, s5, s6, s7, east, east, east, south, winning = south, description = RiichiCatalogueExampleKeys.IPPATSU))
    YakuType.RinshanKaihou -> listOf(
        groupedFull(
            group(RuleCatalogueTileGroupRole.CLOSED_KAN, s8, s8, s8, s8),
            group(RuleCatalogueTileGroupRole.HAND, p2, p3, p4),
            group(RuleCatalogueTileGroupRole.HAND, p5, p5, p5),
            group(RuleCatalogueTileGroupRole.HAND, s3, s4, s5),
            group(RuleCatalogueTileGroupRole.HAND, s6),
            winning = s6,
            description = RiichiCatalogueExampleKeys.RINSHAN,
        ),
    )
    YakuType.Haitei -> listOf(full(p2, p2, p2, p4, p5, p6, s3, s3, s3, s9, s9, s9, east, winning = east, description = RiichiCatalogueExampleKeys.HAITEI))
    YakuType.Houtei -> listOf(full(p2, p2, p2, p4, p5, p6, s3, s3, s3, s9, s9, s9, east, winning = east, description = RiichiCatalogueExampleKeys.HOUTEI))
    YakuType.Chankan -> listOf(full(s1, s2, s3, p1, p1, p1, s4, s5, s6, east, east, east, west, winning = west, description = RiichiCatalogueExampleKeys.CHANKAN))
    YakuType.Menzentsumo -> listOf(full(m1, m1, m1, p2, p3, p4, s3, s3, s3, s7, s8, s9, south, winning = south, description = RiichiCatalogueExampleKeys.TSUMO))
    YakuType.Toitoi -> listOf(
        groupedFull(
            group(RuleCatalogueTileGroupRole.OPEN_MELD, m1, m1, m1),
            group(RuleCatalogueTileGroupRole.OPEN_MELD, p4, p4, p4),
            group(RuleCatalogueTileGroupRole.HAND, s3, s3, s3),
            group(RuleCatalogueTileGroupRole.HAND, east, east, east),
            group(RuleCatalogueTileGroupRole.HAND, south),
            winning = south,
        ),
    )
    YakuType.Sanankou -> listOf(full(m1, m1, m1, p1, p1, p1, s1, s1, s1, s3, s4, s5, east, winning = east))
    YakuType.Sankantsu -> listOf(
        groupedFull(
            group(RuleCatalogueTileGroupRole.OPEN_KAN, m1, m1, m1, m1),
            group(RuleCatalogueTileGroupRole.OPEN_KAN, p3, p3, p3, p3),
            group(RuleCatalogueTileGroupRole.CLOSED_KAN, s3, s3, s3, s3),
            group(RuleCatalogueTileGroupRole.HAND, p4, p5, p6),
            group(RuleCatalogueTileGroupRole.HAND, east),
            winning = east,
        ),
    )
    YakuType.SanshokuDokoku -> listOf(full(m1, m1, m1, p1, p1, p1, s1, s1, s1, s5, s6, s7, red, winning = red))
    YakuType.SanshokuDoujun -> listOf(full(m1, m2, m3, p1, p2, p3, s1, s2, s3, s6, s6, s6, west, winning = west))
    YakuType.Honchan -> listOf(full(p1, p2, p3, p7, p8, p9, s1, s2, s3, east, east, east, green, winning = green))
    YakuType.Junchan -> listOf(full(p1, p2, p3, p7, p8, p9, s1, s2, s3, m9, m9, m9, s9, winning = s9))
    YakuType.Honitsu -> listOf(full(p1, p1, p1, p2, p3, p4, p5, p6, p7, south, south, south, white, winning = white))
    YakuType.Ryanpeikou -> listOf(full(s1, s2, s3, s1, s2, s3, p4, p5, p6, p4, p5, p6, east, winning = east))
    YakuType.Ittuitsu -> listOf(full(p1, p2, p3, p4, p5, p6, p7, p8, p9, s1, s1, s1, east, winning = east))
    YakuType.Honroutou -> listOf(
        groupedFull(
            group(RuleCatalogueTileGroupRole.OPEN_MELD, m1, m1, m1),
            group(RuleCatalogueTileGroupRole.OPEN_MELD, m9, m9, m9),
            group(RuleCatalogueTileGroupRole.HAND, p1, p1, p1),
            group(RuleCatalogueTileGroupRole.HAND, s1, s1, s1),
            group(RuleCatalogueTileGroupRole.HAND, east),
            winning = east,
        ),
    )
    YakuType.Chinitsu -> listOf(full(p1, p2, p3, p2, p3, p4, p3, p4, p5, p6, p6, p6, p9, winning = p9))
    YakuType.Shousangen -> listOf(full(s2, s3, s4, p5, p6, p7, white, white, white, green, green, green, red, winning = red))
    YakuType.Chiitoitsu -> listOf(full(m1, m1, m9, m9, p3, p3, p4, p4, p6, p6, s7, s7, north, winning = north))
    YakuType.RoundWind -> listOf(full(p3, p3, p3, p4, p5, p6, s7, s8, s9, west, west, west, s1, winning = s1, description = RiichiCatalogueExampleKeys.ROUND_WEST))
    YakuType.SeatWind -> listOf(full(p3, p3, p3, p4, p5, p6, s7, s8, s9, west, west, west, s1, winning = s1, description = RiichiCatalogueExampleKeys.SEAT_WEST))
    YakuType.Dragon -> listOf(full(p3, p3, p3, p4, p5, p6, s7, s8, s9, red, red, red, s1, winning = s1))
    YakuType.KokushiMusou -> listOf(full(m1, m1, m9, p1, p9, s1, s9, east, south, west, north, white, green, winning = red))
    YakuType.ChurenPoto -> listOf(full(p1, p1, p1, p2, p3, p4, p5, p5, p7, p8, p9, p9, p9, winning = p6))
    YakuType.Tsuuiisou -> listOf(full(east, east, east, south, south, south, west, west, west, north, north, north, red, winning = red))
    YakuType.Ryuuuiisou -> listOf(full(s2, s3, s4, s2, s3, s4, s6, s6, s6, s8, s8, s8, green, winning = green))
    YakuType.Suuankou -> listOf(full(m1, m1, m1, p2, p2, p2, p5, p5, p5, s7, s7, north, north, winning = north, description = RiichiCatalogueExampleKeys.TSUMO))
    YakuType.Sukantsu -> listOf(
        groupedFull(
            group(RuleCatalogueTileGroupRole.OPEN_KAN, m1, m1, m1, m1),
            group(RuleCatalogueTileGroupRole.OPEN_KAN, p2, p2, p2, p2),
            group(RuleCatalogueTileGroupRole.OPEN_KAN, s3, s3, s3, s3),
            group(RuleCatalogueTileGroupRole.CLOSED_KAN, east, east, east, east),
            group(RuleCatalogueTileGroupRole.HAND, red),
            winning = red,
        ),
    )
    YakuType.Shousuushi -> listOf(full(east, east, east, south, south, south, west, west, west, north, north, p1, p2, winning = p3))
    YakuType.Daisangen -> listOf(full(red, red, red, green, green, green, white, white, white, s1, s2, s3, p1, winning = p1))
    YakuType.Chinroutou -> listOf(full(m1, m1, m1, m9, m9, m9, p1, p1, p1, p9, p9, s1, s1, winning = s1))
    YakuType.Tenhou -> listOf(full(p1, p2, p3, p7, p8, p9, s1, s2, s3, east, east, east, white, winning = white, description = RiichiCatalogueExampleKeys.TENHOU))
    YakuType.Chiihou -> listOf(full(p1, p2, p3, p7, p8, p9, s1, s2, s3, east, east, east, white, winning = white, description = RiichiCatalogueExampleKeys.CHIIHOU))
    YakuType.KokushiMusou13 -> listOf(full(m1, m9, p1, p9, s1, s9, east, south, west, north, white, green, red, winning = m1))
    YakuType.ChurenPoto9 -> listOf(full(p1, p1, p1, p2, p3, p4, p5, p6, p7, p8, p9, p9, p9, winning = p5))
    YakuType.SuuankouTanki -> listOf(full(m1, m1, m1, p2, p2, p2, p5, p5, p5, s7, s7, s7, north, winning = north))
    YakuType.Daisuushii -> listOf(full(east, east, east, south, south, south, west, west, west, north, north, north, p1, winning = p1))
    YakuType.TsubameGaeshi -> listOf(full(s1, s2, s3, p4, p5, p6, s7, s8, s9, m9, m9, p7, p8, winning = p9, description = RiichiCatalogueExampleKeys.TSUBAME_GAESHI))
    YakuType.Kanburi -> listOf(full(p2, p3, p4, s2, s3, s4, s6, s7, s8, east, east, p7, p8, winning = p6, description = RiichiCatalogueExampleKeys.KANBURI))
    YakuType.ShiiaruRaotai -> listOf(
        groupedFull(
            group(RuleCatalogueTileGroupRole.OPEN_MELD, m1, m1, m1),
            group(RuleCatalogueTileGroupRole.OPEN_MELD, p5, p5, p5),
            group(RuleCatalogueTileGroupRole.OPEN_MELD, s9, s9, s9),
            group(RuleCatalogueTileGroupRole.OPEN_MELD, east, east, east),
            group(RuleCatalogueTileGroupRole.HAND, white),
            winning = white,
        ),
    )
    YakuType.UumenChii -> listOf(full(m1, m1, m1, p4, p5, p6, s7, s8, s9, east, east, east, white, winning = white))
    YakuType.Sanrenkou -> listOf(
        groupedFull(
            group(RuleCatalogueTileGroupRole.OPEN_MELD, p3, p3, p3),
            group(RuleCatalogueTileGroupRole.OPEN_MELD, p4, p4, p4),
            group(RuleCatalogueTileGroupRole.HAND, p5, p5, p5),
            group(RuleCatalogueTileGroupRole.HAND, s2, s3),
            group(RuleCatalogueTileGroupRole.HAND, m9, m9),
            winning = s4,
        ),
    )
    YakuType.IsshokuSanjun -> listOf(full(s1, s2, s3, s1, s2, s3, s2, s3, p5, p6, p7, m9, m9, winning = s1))
    YakuType.IipinMoyue -> listOf(full(s2, s3, s4, s5, s6, s7, p4, p5, p6, east, east, p2, p3, winning = p1, description = RiichiCatalogueExampleKeys.IIPIN_MOYUE))
    YakuType.ChuupinRaoyui -> listOf(full(s2, s3, s4, s5, s6, s7, p4, p5, p6, east, east, p7, p8, winning = p9, description = RiichiCatalogueExampleKeys.CHUUPIN_RAOYUI))
    YakuType.Renhou -> listOf(full(s1, s2, s3, p4, p5, p6, s7, s8, s9, north, north, p7, p8, winning = p9, description = RiichiCatalogueExampleKeys.RENHOU))
    YakuType.Daisharin -> listOf(full(p2, p2, p3, p3, p4, p4, p5, p5, p6, p6, p7, p7, p8, winning = p8))
    YakuType.Daichikurin -> listOf(full(s2, s2, s3, s3, s4, s4, s5, s5, s6, s6, s7, s7, s8, winning = s8))
    YakuType.Daisuurin -> listOf(full(m2, m2, m3, m3, m4, m4, m5, m5, m6, m6, m7, m7, m8, winning = m8))
    YakuType.IshinoUeSannen -> listOf(
        full(s1, s2, s3, p4, p5, p6, s7, s8, s9, west, west, p7, p8, winning = p9, description = RiichiCatalogueExampleKeys.ISHI_NO_UE_NI_MO_SANNEN),
    )
    YakuType.Daichisei -> listOf(full(east, east, south, south, west, west, north, north, white, white, green, green, red, winning = red))
}

/**
 * 建立立牌加獨立和牌張的完整手牌範例。
 *
 * @param tiles 和牌前的立牌。
 * @param winning 和牌張。
 * @param description 必要情境說明鍵。
 * @return 明確標記為完整手牌的範例。
 */
private fun full(
    vararg tiles: Tile,
    winning: Tile,
    description: String? = null,
): RuleCatalogueExample = RuleCatalogueExample(
    completeHand = true,
    groups = listOf(RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.HAND, tiles.toList()), winningGroup(winning)),
    descriptionTranslationKey = description,
)

/**
 * 建立含有明示副露或槓子語意的完整範例。
 *
 * @param groups 和牌前已分組的牌面。
 * @param winning 獨立顯示的和牌張。
 * @param description 必要的場況或和牌方式說明鍵。
 * @return 帶有完整手牌標記的規則目錄範例。
 */
private fun groupedFull(
    vararg groups: RuleCatalogueTileGroup,
    winning: Tile,
    description: String? = null,
): RuleCatalogueExample = RuleCatalogueExample(
    completeHand = true,
    groups = groups.toList() + winningGroup(winning),
    descriptionTranslationKey = description,
)

/**
 * 建立指定語意的牌組。
 *
 * @param role 牌組在範例中的呈現語意。
 * @param tiles 牌組內依序顯示的牌。
 * @return 指定語意的牌組。
 */
private fun group(role: RuleCatalogueTileGroupRole, vararg tiles: Tile): RuleCatalogueTileGroup = RuleCatalogueTileGroup(role, tiles.toList())

/**
 * 建立不代表完整手牌的局部示意。
 *
 * @param tiles 示意牌面。
 * @param description 情境說明鍵。
 * @return 局部示意範例。
 */
private fun illustration(vararg tiles: Tile, description: String? = null): RuleCatalogueExample = RuleCatalogueExample(
    completeHand = false,
    groups = listOf(RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.ILLUSTRATION, tiles.toList())),
    descriptionTranslationKey = description,
)

/**
 * 建立獨立和牌張分組。
 *
 * @param tile 和牌張。
 * @return 明確標記為和牌張的分組。
 */
private fun winningGroup(tile: Tile): RuleCatalogueTileGroup = RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.WINNING_TILE, listOf(tile))

/**
 * 建立範例用數牌，不登記虛構牌種識別碼。
 *
 * @param suit 花色。
 * @param value 數值。
 * @return 數牌模型。
 */
private fun number(suit: Tile.Suit, value: Int): Tile = Tile.Numeric(suit, value)

/** 範例用萬1牌。 */
private val m1 = number(Tile.Suit.Character, 1)

/** 範例用萬2牌。 */
private val m2 = number(Tile.Suit.Character, 2)

/** 範例用萬3牌。 */
private val m3 = number(Tile.Suit.Character, 3)

/** 範例用萬4牌。 */
private val m4 = number(Tile.Suit.Character, 4)

/** 範例用萬5牌。 */
private val m5 = number(Tile.Suit.Character, 5)

/** 範例用萬6牌。 */
private val m6 = number(Tile.Suit.Character, 6)

/** 範例用萬7牌。 */
private val m7 = number(Tile.Suit.Character, 7)

/** 範例用萬8牌。 */
private val m8 = number(Tile.Suit.Character, 8)

/** 範例用萬9牌。 */
private val m9 = number(Tile.Suit.Character, 9)

/** 範例用筒1牌。 */
private val p1 = number(Tile.Suit.Dot, 1)

/** 範例用筒2牌。 */
private val p2 = number(Tile.Suit.Dot, 2)

/** 範例用筒3牌。 */
private val p3 = number(Tile.Suit.Dot, 3)

/** 範例用筒4牌。 */
private val p4 = number(Tile.Suit.Dot, 4)

/** 範例用筒5牌。 */
private val p5 = number(Tile.Suit.Dot, 5)

/** 範例用筒6牌。 */
private val p6 = number(Tile.Suit.Dot, 6)

/** 範例用筒7牌。 */
private val p7 = number(Tile.Suit.Dot, 7)

/** 範例用筒8牌。 */
private val p8 = number(Tile.Suit.Dot, 8)

/** 範例用筒9牌。 */
private val p9 = number(Tile.Suit.Dot, 9)

/** 範例用條1牌。 */
private val s1 = number(Tile.Suit.Bamboo, 1)

/** 範例用條2牌。 */
private val s2 = number(Tile.Suit.Bamboo, 2)

/** 範例用條3牌。 */
private val s3 = number(Tile.Suit.Bamboo, 3)

/** 範例用條4牌。 */
private val s4 = number(Tile.Suit.Bamboo, 4)

/** 範例用條5牌。 */
private val s5 = number(Tile.Suit.Bamboo, 5)

/** 範例用條6牌。 */
private val s6 = number(Tile.Suit.Bamboo, 6)

/** 範例用條7牌。 */
private val s7 = number(Tile.Suit.Bamboo, 7)

/** 範例用條8牌。 */
private val s8 = number(Tile.Suit.Bamboo, 8)

/** 範例用條9牌。 */
private val s9 = number(Tile.Suit.Bamboo, 9)

/** 範例用東牌。 */
private val east: Tile = Tile.Honor.East

/** 範例用南牌。 */
private val south: Tile = Tile.Honor.South

/** 範例用西牌。 */
private val west: Tile = Tile.Honor.West

/** 範例用北牌。 */
private val north: Tile = Tile.Honor.North

/** 範例用中牌。 */
private val red: Tile = Tile.Honor.Red

/** 範例用發牌。 */
private val green: Tile = Tile.Honor.Green

/** 範例用白牌。 */
private val white: Tile = Tile.Honor.White

/** 範例用赤五萬牌。 */
private val m5Red: Tile = Tile.Extension(RiichiTileTypes.RED_FIVE_CHARACTER)

/** 範例用赤五筒牌。 */
private val p5Red: Tile = Tile.Extension(RiichiTileTypes.RED_FIVE_DOT)

/** 範例用赤五條牌。 */
private val s5Red: Tile = Tile.Extension(RiichiTileTypes.RED_FIVE_BAMBOO)
