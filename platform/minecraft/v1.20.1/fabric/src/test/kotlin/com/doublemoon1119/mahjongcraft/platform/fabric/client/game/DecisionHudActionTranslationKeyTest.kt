package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.registerBuiltInGameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.vocabularyActionId
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.BuiltInDecisionStatusIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.registerBuiltInDecisionStatusDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerRiichiDecisionStatusDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerRiichiGameActionVocabulary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 驗證登記的用語與狀態名稱，最終的翻譯鍵在四種語言都存在。
 *
 * 登記與語系檔各自獨立維護，靜態掃描與既有語系檔測試都無法驗證兩者組合後的結果。
 */
class DecisionHudActionTranslationKeyTest {
    /** 支援的 Minecraft 語系代碼。 */
    private val locales = listOf("en_us", "ja_jp", "zh_cn", "zh_tw")

    private val vocabulary = GameActionVocabularyRegistryImpl().apply {
        registerBuiltInGameActionVocabulary()
        registerRiichiGameActionVocabulary()
    }

    private val statusDisplayNames = DecisionStatusDisplayNameRegistryImpl().apply {
        registerBuiltInDecisionStatusDisplayNames()
        registerRiichiDecisionStatusDisplayNames()
    }

    /** 每一個內建與日麻動作的短名稱、訊息文字都必須在所有語系檔中有對應翻譯。 */
    @Test
    fun `every registered action resolves translated labels in every language`() {
        val translationsByLocale = locales.associateWith(::loadTranslations)

        builtInActions().forEach { action ->
            val actionId = action.vocabularyActionId()
            val entry = assertNotNull(vocabulary.find(BuiltInRuleModuleIds.RIICHI, actionId), "no vocabulary registered for $action")
            listOf(entry.labelKey, entry.messageKey).forEach { key ->
                translationsByLocale.forEach { (locale, translations) ->
                    assertTrue(key in translations, "$locale is missing $key for $action (actionId=$actionId)")
                }
            }
        }
    }

    /** 每一個內建與日麻的捨牌分析狀態名稱都必須在所有語系檔中有對應翻譯。 */
    @Test
    fun `every registered decision status resolves a translated name in every language`() {
        val translationsByLocale = locales.associateWith(::loadTranslations)
        val statusIds = listOf(
            BuiltInDecisionStatusIds.WIN_AVAILABLE,
            RiichiDiscardReadinessAnalyzer.StatusIds.DISCARD_FURITEN,
            RiichiDiscardReadinessAnalyzer.StatusIds.TEMPORARY_FURITEN,
            RiichiDiscardReadinessAnalyzer.StatusIds.PERMANENT_FURITEN,
            RiichiDiscardReadinessAnalyzer.StatusIds.WIN_TSUMO_ONLY,
            RiichiDiscardReadinessAnalyzer.StatusIds.WIN_NO_YAKU,
            RiichiDiscardReadinessAnalyzer.StatusIds.WIN_BELOW_MINIMUM,
        )

        statusIds.forEach { statusId ->
            val key = assertNotNull(statusDisplayNames.find(BuiltInRuleModuleIds.RIICHI, statusId), "no display name registered for $statusId")
            translationsByLocale.forEach { (locale, translations) ->
                assertTrue(key in translations, "$locale is missing $key for $statusId")
            }
        }
    }

    /** 涵蓋玩家在決策介面上會看到的每一種內建動作。 */
    private fun builtInActions(): List<GameAction> = listOf(
        GameAction.Chi(Uuid.random(), emptyList()),
        GameAction.Pon(Uuid.random(), emptyList()),
        GameAction.Kan(type = GameAction.KanType.OPEN_KAN, tileId = Uuid.random(), withTiles = emptyList()),
        GameAction.Kan(type = GameAction.KanType.CLOSED_KAN, tileId = Uuid.random(), withTiles = emptyList()),
        GameAction.Kan(type = GameAction.KanType.ADDED_KAN, tileId = Uuid.random(), withTiles = emptyList()),
        GameAction.Ron(Uuid.random()),
        GameAction.Tsumo,
        GameAction.Pass,
        GameAction.Discard(Uuid.random()),
        GameAction.Extension(RiichiGameAction.Riichi),
        GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.KyuushuKyuuhai),
    )

    /** 讀取指定語系檔的全部 translation key。 */
    private fun loadTranslations(locale: String): Set<String> {
        val resource = checkNotNull(javaClass.classLoader.getResourceAsStream("assets/mahjongcraft/lang/$locale.json")) {
            "missing language file: $locale"
        }
        val content = resource.bufferedReader().use { it.readText() }
        return (Json.parseToJsonElement(content) as JsonObject).keys
    }
}
