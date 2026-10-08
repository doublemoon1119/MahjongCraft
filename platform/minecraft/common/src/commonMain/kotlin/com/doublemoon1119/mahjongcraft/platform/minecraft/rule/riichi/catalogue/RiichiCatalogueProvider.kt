package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogue
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueCategory
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiYakuTranslationKeys
import kotlin.reflect.KClass

/**
 * 日麻（四人或三人）說明目錄來源，只組成說明與範例，不執行和牌或計分判定。
 *
 * 三人日麻（[RiichiFamilyRuleConfig.usesThreePlayerTiles]）多列拔北寶牌，並省略只有四人才會發生的四風連打與四家立直。
 *
 * @property ruleModuleId 目錄所屬的規則模組識別碼。
 * @property configClass 這份目錄接受的規則配置型別。
 * @property defaultConfig 一般說明用的規則配置。
 */
class RiichiCatalogueProvider(
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI,
    private val configClass: KClass<out RiichiFamilyRuleConfig> = RiichiRuleConfig::class,
    private val defaultConfig: () -> RiichiFamilyRuleConfig = ::RiichiRuleConfig,
) : RuleCatalogueProvider {
    /** 建立一般說明用配置，不表示任一場對局的實際設定。 */
    override fun defaultRuleConfig(): MahjongRuleConfig = defaultConfig()

    /**
     * 依日麻配置調整說明條件；不支援其他規則配置。
     *
     * @param config 已知配置或一般說明配置。
     * @return 日麻目錄，或配置型別不匹配時的 null。
     */
    override fun catalogue(config: MahjongRuleConfig): RuleCatalogue? {
        if (!configClass.isInstance(config)) return null
        config as RiichiFamilyRuleConfig
        return RuleCatalogue(
            categories = RiichiCatalogueCategory.entries.map { RuleCatalogueCategory(it.id, it.nameKey) },
            entries = RiichiCatalogueYaku.entries
                .filter { definition -> definition.type != YakuType.NukiDora || config.usesThreePlayerTiles }
                .map { definition ->
                    RuleCatalogueEntry(
                        id = definition.id,
                        categoryId = definition.category.id,
                        nameTranslationKey = if (definition == RiichiCatalogueYaku.Dragon) RiichiCatalogueKeys.DRAGON_NAME else RiichiYakuTranslationKeys.keyFor(definition.type),
                        descriptionTranslationKey = definition.descriptionKey,
                        labels = buildList {
                            add(definition.valueKey)
                            definition.conditionKey?.let(::add)
                            if (definition.type == YakuType.Tanyao && !config.allowOpenTanyao) add(RiichiCatalogueKeys.CLOSED_ONLY)
                            if (definition.type.isLocal) add(RiichiCatalogueKeys.LOCAL_YAKU)
                        }.map(::riichiCatalogueLabel),
                        unavailableReasonTranslationKey = when {
                            definition.type in MIDDLE_CHARACTER_YAKU && config.usesThreePlayerTiles -> RiichiCatalogueKeys.THREE_PLAYER_TILES_UNAVAILABLE
                            definition.type.isLocal && !config.useLocalYaku -> RiichiCatalogueKeys.LOCAL_YAKU_UNAVAILABLE
                            definition.type == YakuType.AkaDora && config.redDoraCount == 0 -> RiichiCatalogueKeys.RED_DORA_UNAVAILABLE
                            else -> null
                        },
                        examples = riichiCatalogueExamples(definition.type, config.usesThreePlayerTiles),
                        additionalCategoryIds = if (definition.type.isLocal) listOf(RiichiCatalogueCategory.LOCAL.id) else emptyList(),
                    )
                } + riichiCatalogueSpecialEntries(config),
        )
    }

    private companion object {
        /** 一定要用到二～八萬才能成立的役種；三人日麻的牌組沒有這些牌。 */
        val MIDDLE_CHARACTER_YAKU: Set<YakuType> = setOf(YakuType.SanshokuDoujun, YakuType.Daisuurin)
    }
}

/** 建立不經一般手牌役種判定的特殊結算與途中流局說明；三人日麻沒有四風連打與四家立直。 */
private fun riichiCatalogueSpecialEntries(config: RiichiFamilyRuleConfig): List<RuleCatalogueEntry> = RiichiCatalogueSpecial.entries
    .filter { definition ->
        !config.usesThreePlayerTiles || (definition != RiichiCatalogueSpecial.SUUFON_RENDA && definition != RiichiCatalogueSpecial.SUUCHA_RIICHI)
    }
    .map { definition ->
        val isMangan = definition == RiichiCatalogueSpecial.NAGASHI_MANGAN
        RuleCatalogueEntry(
            id = definition.id,
            categoryId = if (isMangan) RiichiCatalogueCategory.MANGAN.id else RiichiCatalogueCategory.ABORTIVE_DRAW.id,
            nameTranslationKey = definition.nameKey,
            descriptionTranslationKey = definition.descriptionKey,
            labels = if (isMangan) listOf(riichiCatalogueLabel(RiichiCatalogueKeys.MANGAN)) else emptyList(),
        )
    }

/**
 * 建立日麻標籤，需要解釋的標籤附上說明。
 *
 * @param key 標籤翻譯鍵。
 * @return 目錄標籤。
 */
private fun riichiCatalogueLabel(key: String): RuleCatalogueLabel = RuleCatalogueLabel(
    nameTranslationKey = key,
    descriptionTranslationKey = when (key) {
        RiichiCatalogueKeys.BONUS_ONLY -> RiichiCatalogueKeys.BONUS_ONLY_DESCRIPTION
        RiichiCatalogueKeys.CLOSED_ONLY -> RiichiCatalogueKeys.CLOSED_ONLY_DESCRIPTION
        RiichiCatalogueKeys.LOCAL_YAKU -> RiichiCatalogueKeys.LOCAL_YAKU_DESCRIPTION
        RiichiCatalogueKeys.OPEN_HAN_1, RiichiCatalogueKeys.OPEN_HAN_2, RiichiCatalogueKeys.OPEN_HAN_5 -> RiichiCatalogueKeys.OPEN_HAN_DESCRIPTION
        else -> null
    },
)
