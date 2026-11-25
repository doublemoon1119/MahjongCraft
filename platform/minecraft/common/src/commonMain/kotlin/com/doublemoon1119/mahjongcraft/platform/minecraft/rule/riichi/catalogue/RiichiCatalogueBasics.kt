package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.BuiltInRuleCatalogueCategoryIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.MinecraftRuleCatalogueScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueExample
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroupRole
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.catalogueTiles
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiGameConfigTranslationKeys

/**
 * 日本麻將「基本玩法」分類的條目：牌與手牌、和牌條件、立直、振聽、鳴牌、點數、賽程、流局、對局結束、一炮多響、包牌與鷲巢麻將。
 * 三人日本麻將另有拔北，且牌組、鳴牌、賽程與點數的說明改用三人的版本。
 *
 * 說明一律用通用說法，可調整的數值寫標準值並列出房間設定欄位。只有條目是否適用依設定判斷。
 *
 * @param config 房間設定或一般說明使用的設定。
 * @return 依顯示順序排列的條目。
 */
internal fun riichiCatalogueBasicsEntries(config: RiichiFamilyRuleConfig): List<RuleCatalogueEntry> {
    val threePlayer = config.usesThreePlayerTiles
    return RiichiCatalogueBasic.entries
        .filter { basic -> basic != RiichiCatalogueBasic.PullNorth || threePlayer }
        .map { basic ->
            RuleCatalogueEntry(
                id = MahjongCraftMetadata.id("riichi/catalogue/basics/${basic.slug}"),
                categoryId = BuiltInRuleCatalogueCategoryIds.BASICS,
                nameTranslationKey = basic.nameKey,
                descriptionTranslationKey = basic.threePlayerDescriptionKey?.takeIf { threePlayer } ?: basic.descriptionKey,
                unavailableReasonTranslationKey = RiichiCatalogueBasicsKeys.WASHIZU_UNAVAILABLE.takeIf {
                    basic == RiichiCatalogueBasic.Washizu && !(config.transparentTiles && !config.buildsWall)
                },
                examples = riichiBasicExamples(basic, threePlayer),
                adjustableSettingNameKeys = riichiBasicSettings(basic, threePlayer),
            )
        }
}

/**
 * 日本麻將「基本玩法」的條目，排列順序即顯示順序。
 *
 * @property slug 條目代號。
 * @property nameKey 名稱翻譯鍵。
 * @property descriptionKey 說明翻譯鍵。
 * @property threePlayerDescriptionKey 三人日本麻將使用的說明翻譯鍵。與四人相同時為 null。
 */
internal enum class RiichiCatalogueBasic(
    val slug: String,
    val nameKey: String,
    val descriptionKey: String,
    val threePlayerDescriptionKey: String?,
) {
    /** 牌與手牌。 */
    Tiles(
        slug = "tiles",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.tiles",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.tiles.description",
        threePlayerDescriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.tiles.three_player",
    ),

    /** 和牌條件。 */
    Winning(
        slug = "winning",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.winning",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.winning.description",
        threePlayerDescriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.winning.three_player",
    ),

    /** 立直。 */
    Riichi(
        slug = "riichi",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.riichi",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.riichi.description",
        threePlayerDescriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.riichi.three_player",
    ),

    /** 振聽。 */
    Furiten(
        slug = "furiten",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.furiten",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.furiten.description",
        threePlayerDescriptionKey = null,
    ),

    /** 鳴牌與食替。 */
    Calls(
        slug = "calls",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.calls",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.calls.description",
        threePlayerDescriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.calls.three_player",
    ),

    /** 拔北，只有三人日本麻將。 */
    PullNorth(
        slug = "pull_north",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.pull_north",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.pull_north.description",
        threePlayerDescriptionKey = null,
    ),

    /** 點數怎麼算。 */
    Scoring(
        slug = "scoring",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.scoring",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.scoring.description",
        threePlayerDescriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.scoring.three_player",
    ),

    /** 賽程與連莊。 */
    Schedule(
        slug = "schedule",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.schedule",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.schedule.description",
        threePlayerDescriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.schedule.three_player",
    ),

    /** 流局與不聽罰符。 */
    Draw(
        slug = "draw",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.draw",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.draw.description",
        threePlayerDescriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.draw.three_player",
    ),

    /** 對局結束。 */
    MatchEnd(
        slug = "match_end",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.match_end",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.match_end.description",
        threePlayerDescriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.match_end.three_player",
    ),

    /** 一炮多響。 */
    MultipleRon(
        slug = "multiple_ron",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.multiple_ron",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.multiple_ron.description",
        threePlayerDescriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.multiple_ron.three_player",
    ),

    /** 包牌。 */
    Pao(
        slug = "pao",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.pao",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.pao.description",
        threePlayerDescriptionKey = null,
    ),

    /** 鷲巢麻將。 */
    Washizu(
        slug = "washizu",
        nameKey = "mahjongcraft.riichi.rule_catalogue.basics.washizu",
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.basics.washizu.description",
        threePlayerDescriptionKey = null,
    ),
}

/** 日本麻將「基本玩法」共用的翻譯鍵。 */
internal object RiichiCatalogueBasicsKeys {
    /** 不是鷲巢麻將時的不適用原因。 */
    const val WASHIZU_UNAVAILABLE: String = "mahjongcraft.riichi.rule_catalogue.basics.washizu.unavailable"

    /** 範例小標題：四組加一對。 */
    const val EXAMPLE_STANDARD: String = "mahjongcraft.riichi.rule_catalogue.basics.example.standard"

    /** 四組加一對範例的說明。 */
    const val EXAMPLE_STANDARD_NOTE: String = "mahjongcraft.riichi.rule_catalogue.basics.example.standard.note"

    /** 範例小標題：七對子。 */
    const val EXAMPLE_SEVEN_PAIRS: String = "mahjongcraft.riichi.rule_catalogue.basics.example.seven_pairs"

    /** 七對子範例的說明。 */
    const val EXAMPLE_SEVEN_PAIRS_NOTE: String = "mahjongcraft.riichi.rule_catalogue.basics.example.seven_pairs.note"

    /** 範例小標題：國士無雙。 */
    const val EXAMPLE_THIRTEEN_ORPHANS: String = "mahjongcraft.riichi.rule_catalogue.basics.example.thirteen_orphans"

    /** 國士無雙範例的說明。 */
    const val EXAMPLE_THIRTEEN_ORPHANS_NOTE: String = "mahjongcraft.riichi.rule_catalogue.basics.example.thirteen_orphans.note"

    /** 四人日本麻將振聽範例的說明。 */
    const val EXAMPLE_FURITEN_NOTE: String = "mahjongcraft.riichi.rule_catalogue.basics.example.furiten.note"

    /** 三人日本麻將振聽範例的說明。 */
    const val EXAMPLE_FURITEN_THREE_PLAYER_NOTE: String = "mahjongcraft.riichi.rule_catalogue.basics.example.furiten.three_player.note"

    /** 三人日本麻將碰的食替範例說明。 */
    const val EXAMPLE_PON_THREE_PLAYER_NOTE: String = "mahjongcraft.riichi.rule_catalogue.basics.example.pon.three_player.note"
}

/**
 * 條目的牌例。三人日本麻將沒有二到八萬，改用筒子與索子。
 *
 * @param basic 條目。
 * @param threePlayer 是否為三人日本麻將。
 * @return 牌例。沒有時為空集合。
 */
private fun riichiBasicExamples(basic: RiichiCatalogueBasic, threePlayer: Boolean): List<RuleCatalogueExample> = when (basic) {
    RiichiCatalogueBasic.Winning -> listOf(
        complete(
            title = RiichiCatalogueBasicsKeys.EXAMPLE_STANDARD,
            note = RiichiCatalogueBasicsKeys.EXAMPLE_STANDARD_NOTE,
            hand = if (threePlayer) "p1 p2 p3 s7 s8 s9 R R R p4 p5 p6 E" else "p1 p2 p3 s7 s8 s9 R R R m2 m3 m4 E",
            winning = "E",
        ),
        complete(
            title = RiichiCatalogueBasicsKeys.EXAMPLE_SEVEN_PAIRS,
            note = RiichiCatalogueBasicsKeys.EXAMPLE_SEVEN_PAIRS_NOTE,
            hand = if (threePlayer) "p1 p1 p4 p4 s2 s2 s7 s7 m1 m1 m9 m9 E" else "p1 p1 p4 p4 s2 s2 s7 s7 m3 m3 m9 m9 E",
            winning = "E",
        ),
        complete(
            title = RiichiCatalogueBasicsKeys.EXAMPLE_THIRTEEN_ORPHANS,
            note = RiichiCatalogueBasicsKeys.EXAMPLE_THIRTEEN_ORPHANS_NOTE,
            hand = "m1 m1 m9 p1 p9 s1 s9 E S W N Wh G",
            winning = "R",
        ),
    )
    RiichiCatalogueBasic.Furiten -> listOf(
        element = RuleCatalogueExample(
            completeHand = false,
            groups = listOf(
                group(RuleCatalogueTileGroupRole.HAND, if (threePlayer) "p1 p2 p3 s7 s8 s9 R R R p5 p6 E E" else "p1 p2 p3 s7 s8 s9 R R R m2 m3 E E"),
                group(RuleCatalogueTileGroupRole.ILLUSTRATION, if (threePlayer) "N p7 W" else "N m4 W", MinecraftRuleCatalogueScreenKeys.GROUP_DISCARDS),
            ),
            descriptionTranslationKey = if (threePlayer) RiichiCatalogueBasicsKeys.EXAMPLE_FURITEN_THREE_PLAYER_NOTE else RiichiCatalogueBasicsKeys.EXAMPLE_FURITEN_NOTE,
        ),
    )
    RiichiCatalogueBasic.Calls -> if (threePlayer) {
        listOf(ponExample(called = "p5", note = RiichiCatalogueBasicsKeys.EXAMPLE_PON_THREE_PLAYER_NOTE))
    } else {
        listOf(chiExample(), ponExample(called = "E", note = null))
    }
    else -> emptyList()
}

/**
 * 條目會用到的房間設定欄位名稱。
 *
 * @param basic 條目。
 * @param threePlayer 是否為三人日本麻將。三人沒有三響。
 * @return 欄位名稱翻譯鍵。沒有時為空集合。
 */
private fun riichiBasicSettings(basic: RiichiCatalogueBasic, threePlayer: Boolean): List<String> = when (basic) {
    RiichiCatalogueBasic.Winning -> listOf(RiichiGameConfigTranslationKeys.MINIMUM_WIN_CONSTRAINT)
    RiichiCatalogueBasic.Calls -> listOf(RiichiGameConfigTranslationKeys.ALLOW_OPEN_TANYAO)
    RiichiCatalogueBasic.Schedule -> listOf(RiichiGameConfigTranslationKeys.GAME_LENGTH)
    RiichiCatalogueBasic.Draw -> listOf(RiichiGameConfigTranslationKeys.NOTEN_PENALTY_UNIT)
    RiichiCatalogueBasic.MatchEnd -> listOfNotNull(
        RiichiGameConfigTranslationKeys.INITIAL_SCORE.takeIf { threePlayer },
        RiichiGameConfigTranslationKeys.BUST_THRESHOLD,
        RiichiGameConfigTranslationKeys.MIN_POINTS_TO_WIN,
    )
    RiichiCatalogueBasic.MultipleRon -> listOfNotNull(
        RiichiGameConfigTranslationKeys.DOUBLE_RON_RESOLUTION,
        RiichiGameConfigTranslationKeys.TRIPLE_RON_RESOLUTION.takeUnless { threePlayer },
    )
    else -> emptyList()
}

/**
 * 吃的食替牌例：上家打出三萬，用四萬、五萬吃，吃完不能打三萬和六萬。
 *
 * @return 局部示意。
 */
private fun chiExample(): RuleCatalogueExample = RuleCatalogueExample(
    completeHand = false,
    groups = listOf(
        group(RuleCatalogueTileGroupRole.ILLUSTRATION, "m3", MinecraftRuleCatalogueScreenKeys.GROUP_LEFT_DISCARD),
        group(RuleCatalogueTileGroupRole.HAND, "m4 m5", MinecraftRuleCatalogueScreenKeys.GROUP_IN_HAND),
        group(RuleCatalogueTileGroupRole.ILLUSTRATION, "m3 m6", MinecraftRuleCatalogueScreenKeys.GROUP_NO_DISCARD_AFTER_CHI),
    ),
    titleTranslationKey = MinecraftRuleCatalogueScreenKeys.EXAMPLE_CHI,
)

/**
 * 碰的食替牌例：別人打出一張，手上已有兩張同樣的牌，碰完不能打同一種牌。
 *
 * @param called 被碰的牌的簡寫。
 * @param note 牌例下方的說明。沒有時為 null。
 * @return 局部示意。
 */
private fun ponExample(called: String, note: String?): RuleCatalogueExample = RuleCatalogueExample(
    completeHand = false,
    groups = listOf(
        group(RuleCatalogueTileGroupRole.ILLUSTRATION, called, MinecraftRuleCatalogueScreenKeys.GROUP_OTHER_DISCARD),
        group(RuleCatalogueTileGroupRole.HAND, "$called $called", MinecraftRuleCatalogueScreenKeys.GROUP_IN_HAND),
        group(RuleCatalogueTileGroupRole.ILLUSTRATION, called, MinecraftRuleCatalogueScreenKeys.GROUP_NO_DISCARD_AFTER_PON),
    ),
    descriptionTranslationKey = note,
    titleTranslationKey = MinecraftRuleCatalogueScreenKeys.EXAMPLE_PON,
)

/**
 * 立牌加和牌張的完整手牌範例。
 *
 * @param title 範例小標題翻譯鍵。
 * @param note 範例說明翻譯鍵。
 * @param hand 立牌簡寫。
 * @param winning 和牌張簡寫。
 * @return 完整手牌範例。
 */
private fun complete(
    title: String,
    note: String,
    hand: String,
    winning: String,
): RuleCatalogueExample = RuleCatalogueExample(
    completeHand = true,
    groups = listOf(group(RuleCatalogueTileGroupRole.HAND, hand), group(RuleCatalogueTileGroupRole.WINNING_TILE, winning)),
    descriptionTranslationKey = note,
    titleTranslationKey = title,
)

/**
 * 建立一組牌例牌組。
 *
 * @param role 牌組語意。
 * @param notation 牌的簡寫。
 * @param label 牌組上方標籤的翻譯鍵。null 時使用語意的標籤。
 * @return 牌組。
 */
private fun group(role: RuleCatalogueTileGroupRole, notation: String, label: String? = null): RuleCatalogueTileGroup = RuleCatalogueTileGroup(role = role, tiles = catalogueTiles(notation), labelTranslationKey = label)
