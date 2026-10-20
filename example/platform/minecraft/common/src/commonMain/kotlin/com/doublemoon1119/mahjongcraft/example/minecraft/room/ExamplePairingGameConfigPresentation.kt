package com.doublemoon1119.mahjongcraft.example.minecraft.room

import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleMinecraftPresentationIds
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleMinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigCategoryDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigFieldDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue

/**
 * 建立簡易配對麻將的 Minecraft 設定畫面定義；共用流程欄位由本體 registry 自動附加。
 *
 * @return 簡易配對麻將的規則設定畫面定義。
 */
fun examplePairingGameConfigPresentation(): GameConfigPresentationDefinition = GameConfigPresentationDefinition(
    ruleModuleId = ExamplePairingIds.RULE_MODULE,
    descriptionTranslationKey = ExampleMinecraftMessageKeys.RULE_DESCRIPTION,
    selectable = true,
    defaultRuleConfig = ::ExamplePairingRuleConfig,
    categories = listOf(
        GameConfigCategoryDefinition(
            id = ExampleMinecraftPresentationIds.RULE_CATEGORY,
            nameTranslationKey = ExampleMinecraftMessageKeys.CONFIG_CATEGORY_RULE,
        ),
    ),
    fields = listOf(
        GameConfigFieldDefinition(
            id = ExampleMinecraftPresentationIds.EXCHANGE_FIELD,
            categoryId = ExampleMinecraftPresentationIds.RULE_CATEGORY,
            nameTranslationKey = ExampleMinecraftMessageKeys.CONFIG_EXCHANGE,
            descriptionTranslationKey = ExampleMinecraftMessageKeys.CONFIG_EXCHANGE_DESCRIPTION,
            editor = GameConfigEditorSpec.BooleanToggle,
            isEditable = true,
            read = { GameConfigPresentationValue.BooleanValue((it.ruleConfig as ExamplePairingRuleConfig).exchangeEnabled) },
            update = { config, value ->
                config.copy(ruleConfig = (config.ruleConfig as ExamplePairingRuleConfig).copy(exchangeEnabled = (value as GameConfigPresentationValue.BooleanValue).enabled))
            },
        ),
        GameConfigFieldDefinition(
            id = ExampleMinecraftPresentationIds.PAYMENT_FIELD,
            categoryId = ExampleMinecraftPresentationIds.RULE_CATEGORY,
            nameTranslationKey = ExampleMinecraftMessageKeys.CONFIG_PAYMENT,
            descriptionTranslationKey = ExampleMinecraftMessageKeys.CONFIG_PAYMENT_DESCRIPTION,
            editor = GameConfigEditorSpec.IntegerInput(1, 100),
            isEditable = true,
            read = { GameConfigPresentationValue.IntegerValue((it.ruleConfig as ExamplePairingRuleConfig).paymentPerPlayer) },
            update = { config, value ->
                config.copy(ruleConfig = (config.ruleConfig as ExamplePairingRuleConfig).copy(paymentPerPlayer = requireNotNull((value as GameConfigPresentationValue.IntegerValue).number)))
            },
        ),
        GameConfigFieldDefinition(
            id = ExampleMinecraftPresentationIds.ROUND_COUNT_FIELD,
            categoryId = ExampleMinecraftPresentationIds.RULE_CATEGORY,
            nameTranslationKey = ExampleMinecraftMessageKeys.CONFIG_ROUNDS,
            descriptionTranslationKey = ExampleMinecraftMessageKeys.CONFIG_ROUNDS_DESCRIPTION,
            editor = GameConfigEditorSpec.IntegerInput(1, 16),
            isEditable = true,
            read = { GameConfigPresentationValue.IntegerValue((it.ruleConfig as ExamplePairingRuleConfig).roundCount) },
            update = { config, value ->
                config.copy(ruleConfig = (config.ruleConfig as ExamplePairingRuleConfig).copy(roundCount = requireNotNull((value as GameConfigPresentationValue.IntegerValue).number)))
            },
        ),
    ),
)
