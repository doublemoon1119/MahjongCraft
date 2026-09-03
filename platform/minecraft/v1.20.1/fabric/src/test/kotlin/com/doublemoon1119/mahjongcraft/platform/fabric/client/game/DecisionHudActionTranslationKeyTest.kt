package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.presentationId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 驗證每一個內建 [GameAction] 經伺服器 `presentationId()` 與 client 端 [translationKey] 轉換後，最終的
 * 翻譯鍵在四種語言都存在。
 *
 * 兩段轉換各自獨立維護，靜態掃描與既有語系檔測試都無法驗證兩者組合後的結果。
 */
class DecisionHudActionTranslationKeyTest {
    /** 支援的 Minecraft 語系代碼。 */
    private val locales = listOf("en_us", "ja_jp", "zh_cn", "zh_tw")

    /** 每一個內建、非第三方擴充的 [GameAction] 都必須在所有語系檔中有對應的決策 HUD 翻譯。 */
    @Test
    fun `every built-in game action resolves a translated hud action label in every language`() {
        val translationsByLocale = locales.associateWith(::loadTranslations)

        builtInActions().forEach { action ->
            val key = action.presentationId().translationKey()
            translationsByLocale.forEach { (locale, translations) ->
                assertTrue(
                    key in translations,
                    "$locale is missing $key for $action (presentationId=${action.presentationId()})",
                )
            }
        }
    }

    /** 驗證槓的三種類型各自解析出彼此不同的翻譯鍵，確保不是三個類型意外都落到同一個 key。 */
    @Test
    fun `each kan type resolves a distinct translation key`() {
        val keys = GameAction.KanType.entries.map { type ->
            GameAction.Kan(type = type, tileId = Uuid.random(), withTiles = emptyList()).presentationId().translationKey()
        }

        assertTrue(keys.distinct().size == keys.size, "kan types must not collapse onto the same translation key: $keys")
    }

    /** 建立每一種內建（非 [GameAction.Extension]）動作的最小合法實例。 */
    private fun builtInActions(): List<GameAction> = buildList {
        add(GameAction.Tsumo)
        add(GameAction.Ron(tileId = Uuid.random()))
        add(GameAction.Chi(tileId = Uuid.random(), withTiles = listOf(Uuid.random(), Uuid.random())))
        add(GameAction.Pon(tileId = Uuid.random()))
        GameAction.KanType.entries.forEach { type ->
            add(GameAction.Kan(type = type, tileId = Uuid.random(), withTiles = emptyList()))
        }
        add(GameAction.Pass)
        add(GameAction.ExhaustiveDraw(reason = RiichiExhaustiveDrawReason.KyuushuKyuuhai))
        // Discard 代表 presentationId() 通用 fallback（"mahjongcraft:action"）涵蓋的其餘內建動作。
        add(GameAction.Discard(tileId = Uuid.random()))
    }

    /** 從測試 classpath 載入並解析指定 Minecraft 語系資源，比照 `MinecraftLanguageFilesTest`。 */
    private fun loadTranslations(locale: String): JsonObject {
        val resourcePath = "/assets/mahjongcraft/lang/$locale.json"
        val content = checkNotNull(this::class.java.getResourceAsStream(resourcePath)) {
            "Language resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }

        return Json.parseToJsonElement(content) as JsonObject
    }
}
