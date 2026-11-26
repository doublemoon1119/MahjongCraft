package com.doublemoon1119.mahjongcraft.example.minecraft.presentation

import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleMinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.BuiltInRuleCatalogueCategoryIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.MinecraftRuleCatalogueScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogue
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueCategory
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueExample
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroupRole

/**
 * 建立簡易配對麻將的規則說明目錄來源：各規則共用的「基本玩法」分類，以及配對麻將自己的總覽分類。
 *
 * @return 只接受簡易配對規則設定的目錄來源。
 */
fun examplePairingCatalogue(): RuleCatalogueProvider = object : RuleCatalogueProvider {
    override val ruleModuleId: String = ExamplePairingIds.RULE_MODULE

    override fun defaultRuleConfig(): MahjongRuleConfig = ExamplePairingRuleConfig()

    override fun catalogue(config: MahjongRuleConfig): RuleCatalogue? = if (config is ExamplePairingRuleConfig) {
        RuleCatalogue(
            categories = listOf(
                RuleCatalogueCategory(id = BuiltInRuleCatalogueCategoryIds.BASICS, nameTranslationKey = MinecraftRuleCatalogueScreenKeys.CATEGORY_BASICS),
                RuleCatalogueCategory(id = ExampleMinecraftPresentationIds.RULE_CATEGORY, nameTranslationKey = ExampleMinecraftMessageKeys.CATALOGUE_CATEGORY),
            ),
            entries = examplePairingBasics(config) + RuleCatalogueEntry(
                id = ExampleMinecraftPresentationIds.CATALOGUE_ENTRY,
                categoryId = ExampleMinecraftPresentationIds.RULE_CATEGORY,
                nameTranslationKey = ExampleMinecraftMessageKeys.CATALOGUE_ENTRY,
                descriptionTranslationKey = ExampleMinecraftMessageKeys.CATALOGUE_ENTRY_DESCRIPTION,
            ),
        )
    } else {
        null
    }
}

/**
 * 配對麻將「基本玩法」的條目：牌與手牌、和牌條件、開局前交換牌、加分牌與付款、攤牌、流局與對局結束、公開牌面。
 *
 * 說明用通用說法，可調整的數值寫標準值並列出房間設定欄位。房間沒有開啟的選項保留條目並寫出原因。
 *
 * @param config 房間設定或一般說明使用的設定。
 * @return 依顯示順序排列的條目。
 */
private fun examplePairingBasics(config: ExamplePairingRuleConfig): List<RuleCatalogueEntry> = listOf(
    basic(
        slug = "tiles",
        name = ExampleMinecraftMessageKeys.BASICS_TILES,
        description = ExampleMinecraftMessageKeys.BASICS_TILES_DESCRIPTION,
        settings = listOf(ExampleMinecraftMessageKeys.CONFIG_WALL_TILE_VALUES),
    ),
    basic(
        slug = "winning",
        name = ExampleMinecraftMessageKeys.BASICS_WINNING,
        description = ExampleMinecraftMessageKeys.BASICS_WINNING_DESCRIPTION,
        examples = listOf(element = twoPairsExample()),
    ),
    basic(
        slug = "exchange",
        name = ExampleMinecraftMessageKeys.BASICS_EXCHANGE,
        description = ExampleMinecraftMessageKeys.BASICS_EXCHANGE_DESCRIPTION,
        settings = listOf(ExampleMinecraftMessageKeys.CONFIG_EXCHANGE),
        unavailable = ExampleMinecraftMessageKeys.BASICS_EXCHANGE_UNAVAILABLE.takeIf { !config.exchangeEnabled },
    ),
    basic(
        slug = "bonus",
        name = ExampleMinecraftMessageKeys.BASICS_BONUS,
        description = ExampleMinecraftMessageKeys.BASICS_BONUS_DESCRIPTION,
        settings = listOf(ExampleMinecraftMessageKeys.CONFIG_PAYMENT),
    ),
    basic(
        slug = "reveal",
        name = ExampleMinecraftMessageKeys.BASICS_REVEAL,
        description = ExampleMinecraftMessageKeys.BASICS_REVEAL_DESCRIPTION,
    ),
    basic(
        slug = "draw",
        name = ExampleMinecraftMessageKeys.BASICS_DRAW,
        description = ExampleMinecraftMessageKeys.BASICS_DRAW_DESCRIPTION,
        settings = listOf(ExampleMinecraftMessageKeys.CONFIG_ROUNDS),
    ),
    basic(
        slug = "public_faces",
        name = ExampleMinecraftMessageKeys.BASICS_PUBLIC_FACES,
        description = ExampleMinecraftMessageKeys.BASICS_PUBLIC_FACES_DESCRIPTION,
        settings = listOf(ExampleMinecraftMessageKeys.CONFIG_PUBLIC_FACES),
        unavailable = ExampleMinecraftMessageKeys.BASICS_PUBLIC_FACES_UNAVAILABLE.takeIf { !config.publicFacesEnabled },
    ),
)

/**
 * 建立一個「基本玩法」條目。
 *
 * @param slug 條目代號。
 * @param name 名稱翻譯鍵。
 * @param description 說明翻譯鍵。
 * @param settings 可在房間設定調整的欄位名稱翻譯鍵。
 * @param unavailable 不適用的原因翻譯鍵。適用時為 null。
 * @param examples 牌例。
 * @return 條目。
 */
private fun basic(
    slug: String,
    name: String,
    description: String,
    settings: List<String> = emptyList(),
    unavailable: String? = null,
    examples: List<RuleCatalogueExample> = emptyList(),
): RuleCatalogueEntry = RuleCatalogueEntry(
    id = "${ExamplePairingIds.RULE_MODULE}/basics/$slug",
    categoryId = BuiltInRuleCatalogueCategoryIds.BASICS,
    nameTranslationKey = name,
    descriptionTranslationKey = description,
    unavailableReasonTranslationKey = unavailable,
    examples = examples,
    adjustableSettingNameKeys = settings,
)

/**
 * 兩個對子的完整手牌範例：手上二萬一對與五萬，摸到五萬湊成第二對。
 *
 * @return 完整手牌範例。
 */
private fun twoPairsExample(): RuleCatalogueExample = RuleCatalogueExample(
    completeHand = true,
    groups = listOf(
        RuleCatalogueTileGroup(role = RuleCatalogueTileGroupRole.HAND, tiles = listOf(character(2), character(2), character(5))),
        RuleCatalogueTileGroup(role = RuleCatalogueTileGroupRole.WINNING_TILE, tiles = listOf(character(5))),
    ),
    descriptionTranslationKey = ExampleMinecraftMessageKeys.BASICS_EXAMPLE_TWO_PAIRS_NOTE,
    titleTranslationKey = ExampleMinecraftMessageKeys.BASICS_EXAMPLE_TWO_PAIRS,
)

/**
 * 範例用萬子。
 *
 * @param value 點數。
 * @return 牌面。
 */
private fun character(value: Int): TileFaceView = TileFaceView(Tile.Numeric(Tile.Suit.Character, value))
