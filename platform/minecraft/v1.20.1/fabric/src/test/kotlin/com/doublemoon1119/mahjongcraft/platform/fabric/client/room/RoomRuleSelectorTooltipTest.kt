package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import net.minecraft.util.Formatting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證規則切換鈕的 tooltip 與其他設定欄位採用相同格式。 */
class RoomRuleSelectorTooltipTest {
    private val candidates = listOf(
        definition(id = "mahjongcraft:first", description = "first.description", selectable = true),
        definition(id = "mahjongcraft:second", description = "second.description", selectable = true),
        definition(id = "mahjongcraft:closed", description = "closed.description", selectable = false),
    )

    /** 第一行是目前規則的說明，不是其他規則的說明。 */
    @Test
    fun `tooltip starts with the description of the current rule`() {
        val tooltip = roomRuleSelectorTooltip("mahjongcraft:second", candidates, ::ruleName)
        val description = tooltip.siblings.first()

        assertEquals("second.description", (description.content as TranslatableTextContent).key)
        assertEquals(Formatting.GRAY.colorValue, description.style.color?.rgb)
    }

    /** 切換規則後，清單中標綠色的是新的目前規則，其他可選規則改回白色。 */
    @Test
    fun `option list highlights the current rule after switching`() {
        val beforeSwitch = roomRuleSelectorTooltip("mahjongcraft:first", candidates, ::ruleName)
        val afterSwitch = roomRuleSelectorTooltip("mahjongcraft:second", candidates, ::ruleName)

        assertEquals(Formatting.GREEN.colorValue, beforeSwitch.optionColor("first"))
        assertEquals(Formatting.WHITE.colorValue, beforeSwitch.optionColor("second"))
        assertEquals(Formatting.WHITE.colorValue, afterSwitch.optionColor("first"))
        assertEquals(Formatting.GREEN.colorValue, afterSwitch.optionColor("second"))
    }

    /** 不能選的規則標紅色並附上原因。 */
    @Test
    fun `unselectable rules stay red with their reason`() {
        val tooltip = roomRuleSelectorTooltip("mahjongcraft:first", candidates, ::ruleName)

        assertEquals(Formatting.RED.colorValue, tooltip.optionColor("closed"))
        assertTrue(
            tooltip.siblings.any { sibling ->
                (sibling.content as? TranslatableTextContent)?.key == "closed.reason" &&
                    sibling.style.color?.rgb == Formatting.RED.colorValue
            },
        )
    }

    private fun ruleName(moduleId: String): Text = Text.literal(moduleId.substringAfter(':'))

    /** 清單中顯示名稱為 [name] 的那一項的顏色。 */
    private fun Text.optionColor(name: String): Int? = siblings.single { it.string == name }.style.color?.rgb

    private fun definition(
        id: String,
        description: String,
        selectable: Boolean,
    ): GameConfigPresentationDefinition = GameConfigPresentationDefinition(
        ruleModuleId = id,
        descriptionTranslationKey = description,
        selectable = selectable,
        unavailableReasonTranslationKey = if (selectable) null else "${id.substringAfter(':')}.reason",
        defaultRuleConfig = { RiichiRuleConfig() },
        categories = emptyList(),
        fields = emptyList(),
    )
}
