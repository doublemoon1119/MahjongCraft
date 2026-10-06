package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證流局原因顯示名稱 registry 的完整 ID 與凍結契約。 */
class ExhaustiveDrawReasonDisplayNameRegistryTest {
    @Test
    fun `registers every built-in riichi reason with a stable translation key`() {
        val registry = ExhaustiveDrawReasonDisplayNameRegistryImpl()

        BundledRiichiMinecraftExtension.registerExhaustiveDrawReasonDisplayNames(registry)

        assertEquals(MinecraftMessageKeys.EXHAUSTIVE_DRAW_REASON_NORMAL, registry.find(RiichiExhaustiveDrawReason.Normal.id))
        assertEquals(MinecraftMessageKeys.GAME_ACTION_KYUUSHU_KYUUHAI, registry.find(RiichiExhaustiveDrawReason.KyuushuKyuuhai.id))
        assertEquals(MinecraftMessageKeys.GAME_ACTION_SUUFON_RENDA, registry.find(RiichiExhaustiveDrawReason.SuufonRenda.id))
        assertEquals(MinecraftMessageKeys.GAME_ACTION_SUUKAN_NAGARE, registry.find(RiichiExhaustiveDrawReason.SuukanNagare.id))
        assertEquals(MinecraftMessageKeys.GAME_ACTION_SUUCHA_RIICHI, registry.find(RiichiExhaustiveDrawReason.SuuchaRiichi.id))
        assertEquals(MinecraftMessageKeys.GAME_ACTION_SANCHA_HOU, registry.find(RiichiExhaustiveDrawReason.SanchaHou.id))
    }

    @Test
    fun `requires namespaced IDs and rejects registrations after freeze`() {
        val registry = ExhaustiveDrawReasonDisplayNameRegistryImpl()

        assertFailsWith<IllegalArgumentException> {
            registry.register("custom_reason", "example.reason")
        }
        registry.register("example:custom_reason", "example.reason")
        registry.freeze()

        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> {
            registry.register("example:another_reason", "example.another_reason")
        }
    }

    /** 流局原因可另外登記玩家結算身分用語；未登記時查無結果，重複登記會失敗。 */
    @Test
    fun `settlement status labels are registered per reason`() {
        val labels = ExhaustiveDrawSettlementStatusLabels(beneficiaryTranslationKey = "test.tenpai", othersTranslationKey = "test.noten")
        val registry = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply { registerSettlementStatusLabels("example:draw", labels) }

        assertEquals(labels, registry.findSettlementStatusLabels("example:draw"))
        assertNull(registry.findSettlementStatusLabels("example:other"))
        assertEquals(setOf("example:draw"), registry.registrationKeys)
        assertFailsWith<IllegalArgumentException> { registry.registerSettlementStatusLabels("example:draw", labels) }
    }
}
