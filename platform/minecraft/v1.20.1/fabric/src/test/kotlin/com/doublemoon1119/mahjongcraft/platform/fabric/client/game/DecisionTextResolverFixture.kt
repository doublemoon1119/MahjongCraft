package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl

/**
 * 建立以內建登記為準的文字解析器，供 HUD 測試使用。
 *
 * @param preparationOptions 額外登記的開局準備選項名稱。
 * @return 以內建詞彙與提供選項建立的文字解析器。
 */
internal fun testDecisionTextResolver(
    preparationOptions: Map<String, String> = emptyMap(),
): DecisionTextResolver = DecisionTextResolver(
    actionVocabulary = GameActionVocabularyRegistryImpl().apply {
        BuiltInMinecraftMahjongExtension.registerGameActionVocabulary(this)
        BundledRiichiMinecraftExtension.registerGameActionVocabulary(this)
    },
    decisionStatusDisplayNames = DecisionStatusDisplayNameRegistryImpl().apply {
        BuiltInMinecraftMahjongExtension.registerDecisionStatusDisplayNames(this)
        BundledRiichiMinecraftExtension.registerDecisionStatusDisplayNames(this)
    },
    exhaustiveDrawReasonDisplayNames = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply { BundledRiichiMinecraftExtension.registerExhaustiveDrawReasonDisplayNames(this) },
    roundPreparationDisplayNames = RoundPreparationDisplayNameRegistryImpl().apply {
        preparationOptions.forEach { (id, translationKey) -> register(id, translationKey) }
    },
)
