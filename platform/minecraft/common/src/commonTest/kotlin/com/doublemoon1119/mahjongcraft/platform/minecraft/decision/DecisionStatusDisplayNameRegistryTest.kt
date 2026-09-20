package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerRiichiDecisionStatusDisplayNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 驗證捨牌分析狀態名稱的登記與「規則優先、退回中立預設」的查詢順序。 */
class DecisionStatusDisplayNameRegistryTest {
    private fun registry() = DecisionStatusDisplayNameRegistryImpl().apply {
        registerBuiltInDecisionStatusDisplayNames()
        registerRiichiDecisionStatusDisplayNames()
    }

    /** 日麻的振聽與和牌資格由日麻自己登記，別的規則查不到。 */
    @Test
    fun `riichi statuses resolve only for riichi`() {
        val registry = registry()

        assertEquals(
            "mahjongcraft.hud.furiten.discard",
            registry.find(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.DISCARD_FURITEN),
        )
        assertEquals(
            "mahjongcraft.hud.win_availability.no_yaku",
            registry.find(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.WIN_NO_YAKU),
        )
        assertNull(registry.find(BuiltInRuleModuleIds.TAIWAN, RiichiDiscardReadinessAnalyzer.StatusIds.WIN_NO_YAKU))
    }

    /** 中立預設「可和牌」對所有規則都查得到，也能被規則覆寫。 */
    @Test
    fun `the neutral default applies to every rule until one overrides it`() {
        val registry = registry()

        assertEquals("mahjongcraft.hud.win_availability.available", registry.find(BuiltInRuleModuleIds.RIICHI, BuiltInDecisionStatusIds.WIN_AVAILABLE))
        assertEquals("mahjongcraft.hud.win_availability.available", registry.find(BuiltInRuleModuleIds.TAIWAN, BuiltInDecisionStatusIds.WIN_AVAILABLE))

        registry.register(BuiltInRuleModuleIds.TAIWAN, BuiltInDecisionStatusIds.WIN_AVAILABLE, "example.hud.can_win")

        assertEquals("example.hud.can_win", registry.find(BuiltInRuleModuleIds.TAIWAN, BuiltInDecisionStatusIds.WIN_AVAILABLE))
        assertEquals("mahjongcraft.hud.win_availability.available", registry.find(BuiltInRuleModuleIds.RIICHI, BuiltInDecisionStatusIds.WIN_AVAILABLE))
    }

    /** 未登記的狀態查不到，由呼叫端顯示原始 ID。 */
    @Test
    fun `an unregistered status resolves to nothing`() {
        assertNull(registry().find(BuiltInRuleModuleIds.RIICHI, "example:unregistered"))
    }

    /** 重複登記與凍結後登記皆拒絕。 */
    @Test
    fun `duplicate and late registrations are rejected`() {
        val registry = registry()

        assertFailsWith<IllegalArgumentException> {
            registry.register(BuiltInRuleModuleIds.RIICHI, RiichiDiscardReadinessAnalyzer.StatusIds.DISCARD_FURITEN, "example.hud.duplicate")
        }
        assertFailsWith<IllegalArgumentException> {
            registry.registerDefault(BuiltInDecisionStatusIds.WIN_AVAILABLE, "example.hud.duplicate")
        }

        registry.freeze()

        assertFailsWith<IllegalStateException> { registry.register(BuiltInRuleModuleIds.TAIWAN, "example:late", "example.hud.late") }
        assertFailsWith<IllegalStateException> { registry.registerDefault("example:late", "example.hud.late") }
    }
}
