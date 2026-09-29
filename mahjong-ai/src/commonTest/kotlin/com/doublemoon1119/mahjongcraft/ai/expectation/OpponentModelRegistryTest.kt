package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.riichi.RiichiOpponentModel
import com.doublemoon1119.mahjongcraft.ai.riichi.registerRiichiOpponentModel
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

/** 驗證對手模型依規則登記、沒有登記時退回規則中立的模型，以及凍結後不能再登記。 */
class OpponentModelRegistryTest {
    private val riichi = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig())
    private val taiwan = TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, TaiwanRuleConfig())

    /** 登記過的規則使用自己的對手模型，其他規則使用 [NeutralOpponentModel]。 */
    @Test
    fun `registered rules get their own model and others get the neutral one`() {
        val registry = OpponentModelRegistry().apply { registerRiichiOpponentModel() }

        assertIs<RiichiOpponentModel>(registry.create(riichi))
        assertSame(NeutralOpponentModel, registry.create(taiwan))
        assertEquals(setOf(BuiltInRuleModuleIds.RIICHI), registry.registrationKeys)
    }

    /** 同一個規則不能登記兩次。 */
    @Test
    fun `a rule cannot be registered twice`() {
        val registry = OpponentModelRegistry().apply { registerRiichiOpponentModel() }

        assertFailsWith<IllegalArgumentException> { registry.registerRiichiOpponentModel() }
    }

    /** 凍結後不能再登記。 */
    @Test
    fun `a frozen registry rejects registrations`() {
        val registry = OpponentModelRegistry().apply { freeze() }

        assertFailsWith<IllegalStateException> { registry.register(BuiltInRuleModuleIds.TAIWAN) { NeutralOpponentModel } }
    }
}
