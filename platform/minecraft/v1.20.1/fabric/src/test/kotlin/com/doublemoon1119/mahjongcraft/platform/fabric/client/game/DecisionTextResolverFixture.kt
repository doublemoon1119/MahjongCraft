package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.registerBuiltInGameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.registerBuiltInDecisionStatusDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiReasons
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerRiichiDecisionStatusDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerRiichiGameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl

/**
 * 建立以內建登記為準的文字解析器，供 HUD 測試使用。
 *
 * @param preparationOptions 額外登記的開局準備選項名稱。
 */
internal fun testDecisionTextResolver(
    preparationOptions: Map<String, String> = emptyMap(),
): DecisionTextResolver = DecisionTextResolver(
    actionVocabulary = GameActionVocabularyRegistryImpl().apply {
        registerBuiltInGameActionVocabulary()
        registerRiichiGameActionVocabulary()
    },
    decisionStatusDisplayNames = DecisionStatusDisplayNameRegistryImpl().apply {
        registerBuiltInDecisionStatusDisplayNames()
        registerRiichiDecisionStatusDisplayNames()
    },
    exhaustiveDrawReasonDisplayNames = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply { registerBuiltInRiichiReasons() },
    roundPreparationDisplayNames = RoundPreparationDisplayNameRegistryImpl().apply {
        preparationOptions.forEach { (id, translationKey) -> register(id, translationKey) }
    },
)
