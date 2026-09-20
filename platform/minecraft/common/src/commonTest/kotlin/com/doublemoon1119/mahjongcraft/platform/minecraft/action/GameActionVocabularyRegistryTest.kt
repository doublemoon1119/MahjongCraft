package com.doublemoon1119.mahjongcraft.platform.minecraft.action

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerRiichiGameActionVocabulary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證動作用語的登記與「規則優先、退回中立預設」的查詢順序。 */
class GameActionVocabularyRegistryTest {
    /** 沒有登記自己用語的規則，取得中立預設。 */
    @Test
    fun `a rule without its own wording falls back to the default`() {
        val registry = GameActionVocabularyRegistryImpl().apply { registerBuiltInGameActionVocabulary() }

        val chi = registry.find(BuiltInRuleModuleIds.TAIWAN, BuiltInGameActionIds.CHI)

        assertEquals("mahjongcraft.hud.action.chi", chi?.labelKey)
        assertEquals("mahjongcraft.message.game_action_chi", chi?.messageKey)
        assertEquals(0, chi?.order)
    }

    /** 規則登記同一個動作時，覆寫中立預設，其他規則不受影響。 */
    @Test
    fun `a rule overrides the default wording of the same action`() {
        val registry = GameActionVocabularyRegistryImpl().apply {
            registerBuiltInGameActionVocabulary()
            register(BuiltInRuleModuleIds.TAIWAN, BuiltInGameActionIds.RON, GameActionVocabulary("example.hud.win", order = 9))
        }

        val taiwanRon = registry.find(BuiltInRuleModuleIds.TAIWAN, BuiltInGameActionIds.RON)

        assertEquals("example.hud.win", taiwanRon?.labelKey)
        assertEquals(9, taiwanRon?.order)
        assertEquals("mahjongcraft.hud.action.ron", registry.find(BuiltInRuleModuleIds.RIICHI, BuiltInGameActionIds.RON)?.labelKey)
    }

    /** 日麻登記自己的立直，排在核心動作之後；別的規則查不到它。 */
    @Test
    fun `riichi registers its own actions after the built-in ones`() {
        val registry = GameActionVocabularyRegistryImpl().apply {
            registerBuiltInGameActionVocabulary()
            registerRiichiGameActionVocabulary()
        }

        val riichi = registry.find(BuiltInRuleModuleIds.RIICHI, RiichiGameAction.Riichi.id)
        val tsumoOrder = registry.find(BuiltInRuleModuleIds.RIICHI, BuiltInGameActionIds.TSUMO)?.order

        assertEquals("mahjongcraft.message.game_action_riichi", riichi?.labelKey)
        assertEquals(riichi?.labelKey, riichi?.messageKey)
        assertTrue((riichi?.order ?: 0) > (tsumoOrder ?: 0), "Expected riichi to come after the built-in actions.")
        assertNull(registry.find(BuiltInRuleModuleIds.TAIWAN, RiichiGameAction.Riichi.id))
        assertNull(registry.find(BuiltInRuleModuleIds.RIICHI, "example:unregistered"))
    }

    /** 同一個中立預設或同一個規則的同一個動作重複登記會被拒絕。 */
    @Test
    fun `duplicate registration is rejected`() {
        val registry = GameActionVocabularyRegistryImpl().apply { registerBuiltInGameActionVocabulary() }

        assertFailsWith<IllegalArgumentException> {
            registry.registerDefault(BuiltInGameActionIds.CHI, GameActionVocabulary("example.hud.chi"))
        }
        registry.register(BuiltInRuleModuleIds.TAIWAN, BuiltInGameActionIds.CHI, GameActionVocabulary("example.hud.chi"))
        assertFailsWith<IllegalArgumentException> {
            registry.register(BuiltInRuleModuleIds.TAIWAN, BuiltInGameActionIds.CHI, GameActionVocabulary("example.hud.chi2"))
        }
    }

    /** 動作 ID 與規則模組 ID 都必須帶命名空間。 */
    @Test
    fun `ids must be namespaced`() {
        val registry = GameActionVocabularyRegistryImpl()

        assertFailsWith<IllegalArgumentException> { registry.registerDefault("chi", GameActionVocabulary("example.hud.chi")) }
        assertFailsWith<IllegalArgumentException> {
            registry.register("taiwan", BuiltInGameActionIds.CHI, GameActionVocabulary("example.hud.chi"))
        }
    }

    /** 凍結後禁止延遲登記。 */
    @Test
    fun `frozen registry rejects late registration`() {
        val registry = GameActionVocabularyRegistryImpl().apply { freeze() }

        assertFailsWith<IllegalStateException> { registry.registerDefault("example:late", GameActionVocabulary("example.hud.late")) }
        assertFailsWith<IllegalStateException> {
            registry.register(BuiltInRuleModuleIds.RIICHI, "example:late", GameActionVocabulary("example.hud.late"))
        }
    }
}
