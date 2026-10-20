package com.doublemoon1119.mahjongcraft.example.minecraft.presentation

import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleMinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogue
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueCategory
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueProvider

/**
 * 建立簡易配對麻將的規則說明目錄來源。
 *
 * @return 只接受簡易配對規則設定的目錄來源。
 */
fun examplePairingCatalogue(): RuleCatalogueProvider = object : RuleCatalogueProvider {
    override val ruleModuleId: String = ExamplePairingIds.RULE_MODULE

    override fun defaultRuleConfig(): MahjongRuleConfig = ExamplePairingRuleConfig()

    override fun catalogue(config: MahjongRuleConfig): RuleCatalogue? = if (config is ExamplePairingRuleConfig) {
        RuleCatalogue(
            categories = listOf(
                RuleCatalogueCategory(
                    id = ExampleMinecraftPresentationIds.RULE_CATEGORY,
                    nameTranslationKey = ExampleMinecraftMessageKeys.CATALOGUE_CATEGORY,
                ),
            ),
            entries = listOf(
                RuleCatalogueEntry(
                    id = ExampleMinecraftPresentationIds.CATALOGUE_ENTRY,
                    categoryId = ExampleMinecraftPresentationIds.RULE_CATEGORY,
                    nameTranslationKey = ExampleMinecraftMessageKeys.CATALOGUE_ENTRY,
                    descriptionTranslationKey = ExampleMinecraftMessageKeys.CATALOGUE_ENTRY_DESCRIPTION,
                ),
            ),
        )
    } else {
        null
    }
}
