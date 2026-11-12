package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證台灣麻將的流局原因都有顯示名稱。 */
class BundledTaiwanMinecraftExtensionDrawReasonTest {
    /** 一般流局沿用共用名稱，其餘流局使用台麻自己的名稱。 */
    @Test
    fun `every draw reason has a display name`() {
        val registry = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply { BundledTaiwanMinecraftExtension.registerExhaustiveDrawReasonDisplayNames(this) }

        assertEquals(
            mapOf(
                TaiwanExhaustiveDrawReason.Normal.id to MinecraftMessageKeys.EXHAUSTIVE_DRAW_REASON_NORMAL,
                TaiwanExhaustiveDrawReason.NoSupplementalTile.id to TaiwanTranslationKeys.EXHAUSTIVE_DRAW_NO_SUPPLEMENTAL_TILE,
                TaiwanExhaustiveDrawReason.FourKans.id to TaiwanTranslationKeys.EXHAUSTIVE_DRAW_FOUR_KANS,
                TaiwanExhaustiveDrawReason.FourWinds.id to TaiwanTranslationKeys.EXHAUSTIVE_DRAW_FOUR_WINDS,
            ),
            registry.reasonIds.associateWith { checkNotNull(registry.find(it)) },
        )
    }

    /** 台麻流局沒有不聽罰符，不登記聽牌與未聽的身分用語。 */
    @Test
    fun `draw reasons have no settlement status labels`() {
        val registry = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply { BundledTaiwanMinecraftExtension.registerExhaustiveDrawReasonDisplayNames(this) }

        assertTrue(registry.settlementStatusReasonIds.isEmpty())
    }
}
