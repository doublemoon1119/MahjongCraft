package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/** [MatchSettlementPresentationTemplateRegistry] 的凍結與 fallback 基礎測試。 */
class MatchSettlementPresentationTemplateRegistryTest {
    /** 內建模板應採末位至第一名並保留五秒閱讀時間。 */
    @Test
    fun `built in template reveals last place first and reads for five seconds`() {
        val registry = MatchSettlementPresentationTemplateRegistryImpl()
        BuiltInMinecraftMahjongExtension.registerMatchSettlementPresentationTemplates(registry)

        val template = assertNotNull(registry.find(BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY))

        assertEquals(MatchSettlementRevealOrder.LAST_TO_FIRST, template.revealOrder)
        assertEquals(100, template.readingTicks)
    }

    /** 規則綁定的模板優先，沒有綁定或綁定的模板不存在時使用內建模板。 */
    @Test
    fun `templates are selected by rule module with built in fallback`() {
        val registry = MatchSettlementPresentationTemplateRegistryImpl()
        BuiltInMinecraftMahjongExtension.registerMatchSettlementPresentationTemplates(registry)
        registry.register(MatchSettlementPresentationTemplate("example:custom", "example.title"))
        registry.bindRuleTemplate("example:rule", "example:custom")
        registry.bindRuleTemplate("example:missing_template_rule", "example:missing")

        assertEquals("example:custom", registry.findForRule("example:rule")?.key)
        assertEquals(BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY, registry.findForRule("example:missing_template_rule")?.key)
        assertEquals(BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY, registry.findForRule("example:unbound")?.key)
    }

    /** registry 凍結後不得再接受第三方模板。 */
    @Test
    fun `frozen registry rejects later templates`() {
        val registry = MatchSettlementPresentationTemplateRegistryImpl()
        BuiltInMinecraftMahjongExtension.registerMatchSettlementPresentationTemplates(registry)
        registry.freeze()

        assertFailsWith<IllegalStateException> {
            registry.register(MatchSettlementPresentationTemplate("example:late", "example.title"))
        }
    }
}
