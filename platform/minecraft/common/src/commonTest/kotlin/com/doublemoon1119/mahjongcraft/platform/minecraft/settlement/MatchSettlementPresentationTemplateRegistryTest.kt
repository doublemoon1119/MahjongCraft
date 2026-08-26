package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY
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
        registry.registerBuiltInMatchSettlementTemplate()

        val template = assertNotNull(registry.find(BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY))

        assertEquals(MatchSettlementRevealOrder.LAST_TO_FIRST, template.revealOrder)
        assertEquals(100, template.readingTicks)
    }

    /** registry 凍結後不得再接受第三方模板。 */
    @Test
    fun `frozen registry rejects later templates`() {
        val registry = MatchSettlementPresentationTemplateRegistryImpl()
        registry.registerBuiltInMatchSettlementTemplate()
        registry.freeze()

        assertFailsWith<IllegalStateException> {
            registry.register(MatchSettlementPresentationTemplate("example:late", "example.title"))
        }
    }
}
