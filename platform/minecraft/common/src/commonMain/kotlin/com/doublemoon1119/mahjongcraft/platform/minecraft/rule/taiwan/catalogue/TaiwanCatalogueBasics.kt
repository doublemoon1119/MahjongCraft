package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.catalogue

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.BuiltInRuleCatalogueCategoryIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.MinecraftRuleCatalogueScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueExample
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroupRole
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.catalogueTiles
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.TaiwanGameConfigTranslationKeys

/**
 * 台灣麻將「基本玩法」分類的條目：牌與手牌、和牌條件、花牌與補花、鳴牌與食替、過水、聽牌宣告、台數與付款、賽程與連莊、
 * 流局與一炮多響。包牌沿用付款規則分類的條目，同時列在這個分類的最後。
 *
 * 說明一律用通用說法，可調整的數值寫標準值並列出房間設定欄位；只有條目是否適用依設定判斷。
 *
 * @param config 房間設定或一般說明使用的設定。
 * @return 依顯示順序排列的條目。
 */
internal fun taiwanCatalogueBasicsEntries(config: TaiwanRuleConfig): List<RuleCatalogueEntry> = TaiwanCatalogueBasic.entries.map { basic ->
    RuleCatalogueEntry(
        id = MahjongCraftMetadata.id("taiwan/catalogue/basics/${basic.slug}"),
        categoryId = BuiltInRuleCatalogueCategoryIds.BASICS,
        nameTranslationKey = basic.nameKey,
        descriptionTranslationKey = basic.descriptionKey,
        unavailableReasonTranslationKey = TaiwanCatalogueKeys.FLOWERS_UNAVAILABLE.takeIf { basic == TaiwanCatalogueBasic.Flowers && !config.useFlowerTiles },
        examples = taiwanBasicExamples(basic),
        adjustableSettingNameKeys = basic.settingNameKeys,
    )
}

/**
 * 台灣麻將「基本玩法」的條目，排列順序即顯示順序。
 *
 * @property slug 條目代號。
 * @property nameKey 名稱翻譯鍵。
 * @property descriptionKey 說明翻譯鍵。
 * @property settingNameKeys 會改變這條說明的房間設定欄位名稱翻譯鍵。
 */
internal enum class TaiwanCatalogueBasic(
    val slug: String,
    val nameKey: String,
    val descriptionKey: String,
    val settingNameKeys: List<String>,
) {
    /** 牌與手牌。 */
    Tiles(
        slug = "tiles",
        nameKey = "mahjongcraft.taiwan.rule_catalogue.basics.tiles",
        descriptionKey = "mahjongcraft.taiwan.rule_catalogue.basics.tiles.description",
        settingNameKeys = listOf(TaiwanGameConfigTranslationKeys.USE_FLOWER_TILES),
    ),

    /** 和牌條件。 */
    Winning(
        slug = "winning",
        nameKey = "mahjongcraft.taiwan.rule_catalogue.basics.winning",
        descriptionKey = "mahjongcraft.taiwan.rule_catalogue.basics.winning.description",
        settingNameKeys = listOf(TaiwanGameConfigTranslationKeys.MINIMUM_TAI),
    ),

    /** 花牌與補花。 */
    Flowers(
        slug = "flowers",
        nameKey = "mahjongcraft.taiwan.rule_catalogue.basics.flowers",
        descriptionKey = "mahjongcraft.taiwan.rule_catalogue.basics.flowers.description",
        settingNameKeys = listOf(TaiwanGameConfigTranslationKeys.USE_FLOWER_TILES),
    ),

    /** 鳴牌與食替。 */
    Calls(
        slug = "calls",
        nameKey = "mahjongcraft.taiwan.rule_catalogue.basics.calls",
        descriptionKey = "mahjongcraft.taiwan.rule_catalogue.basics.calls.description",
        settingNameKeys = emptyList(),
    ),

    /** 過水。 */
    PassedWin(
        slug = "passed_win",
        nameKey = "mahjongcraft.taiwan.rule_catalogue.basics.passed_win",
        descriptionKey = "mahjongcraft.taiwan.rule_catalogue.basics.passed_win.description",
        settingNameKeys = listOf(TaiwanGameConfigTranslationKeys.KAN_CLEARS_PASSED_WIN),
    ),

    /** 聽牌宣告。 */
    ReadyDeclaration(
        slug = "ready_declaration",
        nameKey = "mahjongcraft.taiwan.rule_catalogue.basics.ready_declaration",
        descriptionKey = "mahjongcraft.taiwan.rule_catalogue.basics.ready_declaration.description",
        settingNameKeys = listOf(TaiwanGameConfigTranslationKeys.GENERAL_READY_DECLARATION),
    ),

    /** 台數與付款。 */
    Payment(
        slug = "payment",
        nameKey = "mahjongcraft.taiwan.rule_catalogue.basics.payment",
        descriptionKey = "mahjongcraft.taiwan.rule_catalogue.basics.payment.description",
        settingNameKeys = listOf(TaiwanGameConfigTranslationKeys.BASE_SCORE, TaiwanGameConfigTranslationKeys.POINT_PER_TAI),
    ),

    /** 賽程與連莊。 */
    Schedule(
        slug = "schedule",
        nameKey = "mahjongcraft.taiwan.rule_catalogue.basics.schedule",
        descriptionKey = "mahjongcraft.taiwan.rule_catalogue.basics.schedule.description",
        settingNameKeys = listOf(TaiwanGameConfigTranslationKeys.GAME_LENGTH),
    ),

    /** 流局。 */
    Draw(
        slug = "draw",
        nameKey = "mahjongcraft.taiwan.rule_catalogue.basics.draw",
        descriptionKey = "mahjongcraft.taiwan.rule_catalogue.basics.draw.description",
        settingNameKeys = listOf(TaiwanGameConfigTranslationKeys.FOUR_WINDS_ABORTIVE_DRAW),
    ),

    /** 一炮多響。 */
    MultipleWin(
        slug = "multiple_win",
        nameKey = "mahjongcraft.taiwan.rule_catalogue.basics.multiple_win",
        descriptionKey = "mahjongcraft.taiwan.rule_catalogue.basics.multiple_win.description",
        settingNameKeys = listOf(TaiwanGameConfigTranslationKeys.DOUBLE_WIN_RESOLUTION, TaiwanGameConfigTranslationKeys.TRIPLE_WIN_RESOLUTION),
    ),
}

/** 台灣麻將「基本玩法」共用的翻譯鍵。 */
internal object TaiwanCatalogueBasicsKeys {
    /** 範例小標題：五組加一對。 */
    const val EXAMPLE_STANDARD: String = "mahjongcraft.taiwan.rule_catalogue.basics.example.standard"

    /** 五組加一對範例的說明。 */
    const val EXAMPLE_STANDARD_NOTE: String = "mahjongcraft.taiwan.rule_catalogue.basics.example.standard.note"
}

/**
 * 條目的牌例。
 *
 * @param basic 條目。
 * @return 牌例；沒有時為空集合。
 */
private fun taiwanBasicExamples(basic: TaiwanCatalogueBasic): List<RuleCatalogueExample> = when (basic) {
    TaiwanCatalogueBasic.Winning -> listOf(
        element = RuleCatalogueExample(
            completeHand = true,
            groups = listOf(
                group(RuleCatalogueTileGroupRole.HAND, "p1 p2 p3 s7 s8 s9 R R R m2 m3 m4 W W W E"),
                group(RuleCatalogueTileGroupRole.WINNING_TILE, "E"),
            ),
            descriptionTranslationKey = TaiwanCatalogueBasicsKeys.EXAMPLE_STANDARD_NOTE,
            titleTranslationKey = TaiwanCatalogueBasicsKeys.EXAMPLE_STANDARD,
        ),
    )
    TaiwanCatalogueBasic.Calls -> listOf(
        RuleCatalogueExample(
            completeHand = false,
            groups = listOf(
                group(RuleCatalogueTileGroupRole.ILLUSTRATION, "m3", MinecraftRuleCatalogueScreenKeys.GROUP_LEFT_DISCARD),
                group(RuleCatalogueTileGroupRole.HAND, "m4 m5", MinecraftRuleCatalogueScreenKeys.GROUP_IN_HAND),
                group(RuleCatalogueTileGroupRole.ILLUSTRATION, "m3 m6", MinecraftRuleCatalogueScreenKeys.GROUP_NO_DISCARD_AFTER_CHI),
            ),
            titleTranslationKey = MinecraftRuleCatalogueScreenKeys.EXAMPLE_CHI,
        ),
        RuleCatalogueExample(
            completeHand = false,
            groups = listOf(
                group(RuleCatalogueTileGroupRole.ILLUSTRATION, "E", MinecraftRuleCatalogueScreenKeys.GROUP_OTHER_DISCARD),
                group(RuleCatalogueTileGroupRole.HAND, "E E", MinecraftRuleCatalogueScreenKeys.GROUP_IN_HAND),
                group(RuleCatalogueTileGroupRole.ILLUSTRATION, "E", MinecraftRuleCatalogueScreenKeys.GROUP_NO_DISCARD_AFTER_PON),
            ),
            titleTranslationKey = MinecraftRuleCatalogueScreenKeys.EXAMPLE_PON,
        ),
    )
    else -> emptyList()
}

/**
 * 建立一組牌例牌組。
 *
 * @param role 牌組語意。
 * @param notation 牌的簡寫。
 * @param label 牌組上方標籤的翻譯鍵；null 時使用語意的標籤。
 * @return 牌組。
 */
private fun group(role: RuleCatalogueTileGroupRole, notation: String, label: String? = null): RuleCatalogueTileGroup = RuleCatalogueTileGroup(role = role, tiles = catalogueTiles(notation), labelTranslationKey = label)
