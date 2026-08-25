package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證流局原因顯示名稱 registry 的完整 ID 與凍結契約。 */
class ExhaustiveDrawReasonDisplayNameRegistryTest {
    @Test
    fun `registers every built-in riichi reason with a stable translation key`() {
        val registry = ExhaustiveDrawReasonDisplayNameRegistryImpl()

        registry.registerBuiltInRiichiReasons()

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
}
