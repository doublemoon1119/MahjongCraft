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
        element = GameConfigCategoryDefinition(
            id = ExampleMinecraftPresentationIds.RULE_CATEGORY,
            nameTranslationKey = ExampleMinecraftMessageKeys.CONFIG_CATEGORY_RULE,
        ),
    ),
    fields = listOf(
        GameConfigFieldDefinition(
            id = ExampleMinecraftPresentationIds.WALL_TILE_VALUES_FIELD,
            categoryId = ExampleMinecraftPresentationIds.RULE_CATEGORY,
            nameTranslationKey = ExampleMinecraftMessageKeys.CONFIG_WALL_TILE_VALUES,
            descriptionTranslationKey = ExampleMinecraftMessageKeys.CONFIG_WALL_TILE_VALUES_DESCRIPTION,
            editor = GameConfigEditorSpec.MultiChoice(
                optionIds = ExamplePairingIds.WALL_TILE_VALUE_OPTIONS.map { it.second },
                minimumCount = 2,
                optionTranslationKeys = ExamplePairingIds.WALL_TILE_VALUE_OPTIONS.associate { (value, id) ->
                    id to ExampleMinecraftMessageKeys.WALL_TILE_VALUE_OPTIONS.getValue(value)
                },
                optionDescriptionTranslationKeys = ExamplePairingIds.WALL_TILE_VALUE_OPTIONS.associate { (value, id) ->
                    id to if (value == 1) {
                        ExampleMinecraftMessageKeys.CONFIG_WALL_ONE_DESCRIPTION
                    } else {
                        ExampleMinecraftMessageKeys.CONFIG_WALL_TILE_OPTION_DESCRIPTION
                    }
                },
            ),
            isEditable = true,
            read = { config ->
                val values = (config.ruleConfig as ExamplePairingRuleConfig).wallTileValues
                GameConfigPresentationValue.MultiChoiceValue(
                    optionIds = ExamplePairingIds.WALL_TILE_VALUE_OPTIONS.filter { it.first in values }.map { it.second }.toSet(),
                )
            },
            update = { config, value ->
                val selected = (value as GameConfigPresentationValue.MultiChoiceValue).optionIds
                require(selected.all { id -> ExamplePairingIds.WALL_TILE_VALUE_OPTIONS.any { it.second == id } }) {
                    "Pairing wall selection contains an unknown option"
                }
                config.copy(
                    ruleConfig = (config.ruleConfig as ExamplePairingRuleConfig).copy(
                        wallTileValues = ExamplePairingIds.WALL_TILE_VALUE_OPTIONS.filter { it.second in selected }.map { it.first }.toSet(),
                    ),
                )
            },
        ),
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
            id = ExampleMinecraftPresentationIds.PUBLIC_FACES_FIELD,
            categoryId = ExampleMinecraftPresentationIds.RULE_CATEGORY,
            nameTranslationKey = ExampleMinecraftMessageKeys.CONFIG_PUBLIC_FACES,
            descriptionTranslationKey = ExampleMinecraftMessageKeys.CONFIG_PUBLIC_FACES_DESCRIPTION,
            editor = GameConfigEditorSpec.BooleanToggle,
            isEditable = true,
            read = { GameConfigPresentationValue.BooleanValue((it.ruleConfig as ExamplePairingRuleConfig).publicFacesEnabled) },
            update = { config, value ->
                config.copy(ruleConfig = (config.ruleConfig as ExamplePairingRuleConfig).copy(publicFacesEnabled = (value as GameConfigPresentationValue.BooleanValue).enabled))
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
