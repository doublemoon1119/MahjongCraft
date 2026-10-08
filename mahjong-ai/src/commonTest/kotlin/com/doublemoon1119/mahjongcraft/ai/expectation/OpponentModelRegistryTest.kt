package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.riichi.RiichiOpponentModel
import com.doublemoon1119.mahjongcraft.bundled.BundledRiichiExtension
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** 驗證對手模型依規則登記、沒有登記時退回規則中立的模型、讀牌深度傳給建立的模型，以及凍結後不能再登記。 */
class OpponentModelRegistryTest {
    private val riichi = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig())
    private val taiwan = TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, TaiwanRuleConfig())

    /** 登記過的規則使用自己的對手模型，其他規則使用 [NeutralOpponentModel]。 */
    @Test
    fun `registered rules get their own model and others get the neutral one`() {
        val registry = OpponentModelRegistry().apply { BundledRiichiExtension.registerOpponentModels(this) }

        assertIs<RiichiOpponentModel>(registry.create(riichi, ReadingDepth.BASIC))
        assertIs<NeutralOpponentModel>(registry.create(taiwan, ReadingDepth.BASIC))
        assertEquals(setOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.RIICHI_THREE_PLAYER), registry.registrationKeys)
    }

    /** 建立的模型使用決策者的讀牌深度，登記與未登記的規則皆然。 */
    @Test
    fun `created models read at the requested depth`() {
        val registry = OpponentModelRegistry().apply { BundledRiichiExtension.registerOpponentModels(this) }

        ReadingDepth.entries.forEach { depth ->
            assertEquals(depth, registry.create(riichi, depth).readingDepth)
            assertEquals(depth, registry.create(taiwan, depth).readingDepth)
        }
    }

    /** 基本深度直接使用規則自己的模型；進階深度一律在外面套用共用讀牌，登記與未登記的規則皆然。 */
    @Test
    fun `advanced depth layers the shared reading on every model`() {
        val registry = OpponentModelRegistry().apply { BundledRiichiExtension.registerOpponentModels(this) }

        assertIs<RiichiOpponentModel>(registry.create(riichi, ReadingDepth.BASIC))
        assertIs<ReadingOpponentModel>(registry.create(riichi, ReadingDepth.ADVANCED))
        assertIs<NeutralOpponentModel>(registry.create(taiwan, ReadingDepth.BASIC))
        assertIs<ReadingOpponentModel>(registry.create(taiwan, ReadingDepth.ADVANCED))
    }

    /** 同一個規則不能登記兩次。 */
    @Test
    fun `a rule cannot be registered twice`() {
        val registry = OpponentModelRegistry().apply { BundledRiichiExtension.registerOpponentModels(this) }

        assertFailsWith<IllegalArgumentException> { BundledRiichiExtension.registerOpponentModels(registry) }
    }

    /** 凍結後不能再登記。 */
    @Test
    fun `a frozen registry rejects registrations`() {
        val registry = OpponentModelRegistry().apply { freeze() }

        assertFailsWith<IllegalStateException> {
            registry.register(BuiltInRuleModuleIds.TAIWAN) { _, depth -> NeutralOpponentModel(depth) }
        }
    }
}
