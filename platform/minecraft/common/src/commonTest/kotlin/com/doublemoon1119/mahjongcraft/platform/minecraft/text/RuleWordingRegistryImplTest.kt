package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiTranslationKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 驗證規則用語 registry 的中立用語、規則覆寫與登記限制。 */
class RuleWordingRegistryImplTest {
    /** 日麻覆寫自己的說法；台麻、第三方規則與不在對局中時使用中立用語。 */
    @Test
    fun `rules override the neutral wording only for their own tables`() {
        val registry = RuleWordingRegistryImpl().apply {
            BuiltInMinecraftMahjongExtension.registerRuleWordings(this)
            BundledRiichiMinecraftExtension.registerRuleWordings(this)
        }

        assertEquals(RiichiTranslationKeys.WALL_EXHAUSTED, registry.find(BuiltInRuleModuleIds.RIICHI, BuiltInRuleWordingIds.WALL_EXHAUSTED))
        assertEquals(RiichiTranslationKeys.HISTORY_ACTION_KAN, registry.find(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, BuiltInRuleWordingIds.HISTORY_KAN))
        assertEquals(MinecraftMessageKeys.WALL_EXHAUSTED, registry.find(BuiltInRuleModuleIds.TAIWAN, BuiltInRuleWordingIds.WALL_EXHAUSTED))
        assertEquals(MinecraftHistoryScreenKeys.ROUND_ACTION_KAN, registry.find("example:rule", BuiltInRuleWordingIds.HISTORY_KAN))
        assertEquals(MinecraftMessageKeys.WALL_EXHAUSTED, registry.find(null, BuiltInRuleWordingIds.WALL_EXHAUSTED))
        assertNull(registry.find(BuiltInRuleModuleIds.RIICHI, "example:unregistered"))
    }

    /** 同一段文字的中立用語或同一規則的說法只能登記一次；凍結後拒絕登記。 */
    @Test
    fun `duplicate and frozen registrations are rejected`() {
        val registry = RuleWordingRegistryImpl().apply {
            register(RuleWordingDefaultDefinition("example:wording", "example.default"))
            register(RuleWordingDefinition("example:rule", "example:wording", "example.rule"))
        }

        assertFailsWith<IllegalArgumentException> { registry.register(RuleWordingDefaultDefinition("example:wording", "example.other")) }
        assertFailsWith<IllegalArgumentException> { registry.register(RuleWordingDefinition("example:rule", "example:wording", "example.other")) }
        assertEquals(setOf("default/example:wording", "example:rule/example:wording"), registry.registrationKeys)
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.register(RuleWordingDefaultDefinition("example:another", "example.another")) }
    }
}
