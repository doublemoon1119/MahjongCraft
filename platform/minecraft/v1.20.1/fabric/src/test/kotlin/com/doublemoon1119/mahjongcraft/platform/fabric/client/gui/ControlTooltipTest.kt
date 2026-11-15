package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftControlTooltipKeys
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.text.TextColor
import net.minecraft.util.Formatting
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證設定欄位提示框的段落順序、內容與顏色。 */
class ControlTooltipTest {
    /** 選項欄位：說明、空行、標題與全部選項、空行、操作提示；目前選項以「▶」標示，不另外重複目前值。 */
    @Test
    fun `option tooltip lists every option and marks the current one`() {
        val tooltip = ControlTooltip.options(
            description = Text.literal("Length of the match."),
            options = listOf(
                ControlTooltip.Option(label = Text.literal("One Game"), current = true),
                ControlTooltip.Option(label = Text.literal("East Game"), current = false),
            ),
            hints = listOf(Text.literal("Left-click: next option")),
        )

        assertEquals(
            expected = listOf(
                "Length of the match.",
                "",
                MinecraftControlTooltipKeys.OPTIONS,
                "▶ One Game",
                "   East Game",
                "",
                "Left-click: next option",
            ),
            actual = lines(tooltip),
        )
    }

    /** 說明為灰色、標題為金色、目前選項為綠色、其他可選選項為白色、不可選選項為紅色。 */
    @Test
    fun `option tooltip colours each part`() {
        val tooltip = ControlTooltip.options(
            description = Text.literal("Which rule this room plays."),
            options = listOf(
                ControlTooltip.Option(label = Text.literal("Riichi"), current = true),
                ControlTooltip.Option(label = Text.literal("Sanma"), current = false, unavailableReason = Text.literal("needs 3 players")),
                ControlTooltip.Option(label = Text.literal("Taiwan"), current = false),
            ),
        )

        assertEquals(Formatting.GRAY, colorOf(tooltip, "Which rule this room plays."))
        assertEquals(Formatting.GOLD, colorOf(tooltip, MinecraftControlTooltipKeys.OPTIONS))
        assertEquals(Formatting.GREEN, colorOf(tooltip, "▶ "))
        assertEquals(Formatting.RED, colorOf(tooltip, "   ", occurrence = 0))
        assertEquals(Formatting.WHITE, colorOf(tooltip, "   ", occurrence = 1))
    }

    /** 不可選的選項留在原本的位置，並附上原因。 */
    @Test
    fun `unavailable option keeps its position and shows the reason`() {
        val tooltip = ControlTooltip.options(
            description = null,
            options = listOf(
                ControlTooltip.Option(label = Text.literal("Riichi"), current = true),
                ControlTooltip.Option(label = Text.literal("Sanma"), current = false, unavailableReason = Text.literal("needs 3 players")),
                ControlTooltip.Option(label = Text.literal("Taiwan"), current = false),
            ),
        )

        assertEquals(
            expected = listOf(
                MinecraftControlTooltipKeys.OPTIONS,
                "▶ Riichi",
                "   ${MinecraftControlTooltipKeys.UNAVAILABLE_OPTION}",
                "   Taiwan",
            ),
            actual = lines(tooltip),
        )
    }

    /** 多選欄位可以有多個目前選項，並使用呼叫端提供的標題。 */
    @Test
    fun `multi choice marks every selected option`() {
        val tooltip = ControlTooltip.options(
            description = Text.literal("Tiles in play."),
            options = listOf(
                ControlTooltip.Option(label = Text.literal("Bamboo"), current = true),
                ControlTooltip.Option(label = Text.literal("Dots"), current = false),
                ControlTooltip.Option(label = Text.literal("Honours"), current = true),
            ),
            hints = ControlTooltip.chooseHints(),
            title = Text.literal("Options (2 selected, 1–3)"),
        )

        assertEquals(
            expected = listOf(
                "Tiles in play.",
                "",
                "Options (2 selected, 1–3)",
                "▶ Bamboo",
                "   Dots",
                "▶ Honours",
                "",
                MinecraftControlTooltipKeys.HINT_CHOOSE,
            ),
            actual = lines(tooltip),
        )
    }

    /** 數值欄位顯示標題與範圍，接著加減、按住 Shift 與直接輸入三行操作提示。 */
    @Test
    fun `range tooltip shows the range and the three hints`() {
        val tooltip = ControlTooltip.range(
            description = Text.literal("Lowest han a hand needs."),
            range = Text.literal("1 – 13"),
            hints = ControlTooltip.integerHints(step = Text.literal("1"), shiftStep = Text.literal("10"), minimum = Text.literal("1")),
        )

        assertEquals(
            expected = listOf(
                "Lowest han a hand needs.",
                "",
                "${MinecraftControlTooltipKeys.RANGE}  1 – 13",
                "",
                MinecraftControlTooltipKeys.HINT_STEP,
                MinecraftControlTooltipKeys.HINT_SHIFT_STEP,
                MinecraftControlTooltipKeys.HINT_TYPE_NUMBER,
            ),
            actual = lines(tooltip),
        )
        assertEquals(Formatting.GOLD, colorOf(tooltip, MinecraftControlTooltipKeys.RANGE))
    }

    /** 可以不設定的數值欄位多一行改為不設定的操作提示，範圍文字使用含不設定名稱的格式。 */
    @Test
    fun `optional range adds the hint for clearing the value`() {
        val hints = ControlTooltip.integerHints(
            step = Text.literal("1000"),
            shiftStep = Text.literal("10000"),
            minimum = Text.literal("1000"),
            unsetLabel = Text.literal("No limit"),
        )

        assertEquals(
            expected = listOf(
                MinecraftControlTooltipKeys.HINT_STEP,
                MinecraftControlTooltipKeys.HINT_SHIFT_STEP,
                MinecraftControlTooltipKeys.HINT_TYPE_NUMBER,
                MinecraftControlTooltipKeys.HINT_CLEAR,
            ),
            actual = hints.map { it.string },
        )
        assertEquals(
            expected = MinecraftControlTooltipKeys.RANGE_VALUES_OPTIONAL,
            actual = ControlTooltip.rangeText(Text.literal("1000"), Text.literal("100000"), Text.literal("No limit")).string,
        )
        assertEquals(MinecraftControlTooltipKeys.RANGE_VALUES, ControlTooltip.rangeText(Text.literal("1"), Text.literal("13")).string)
    }

    /** 提醒為紅色，位於狀態區與操作提示之間；沒有操作提示時不留多餘的空行。 */
    @Test
    fun `notice sits between the status and the hints`() {
        val disabled = ControlTooltip.options(
            description = Text.literal("Description"),
            options = listOf(ControlTooltip.Option(label = Text.literal("On"), current = true)),
            notice = Text.literal("Disabled by another setting"),
        )

        assertEquals(
            expected = listOf("Description", "", MinecraftControlTooltipKeys.OPTIONS, "▶ On", "", "Disabled by another setting"),
            actual = lines(disabled),
        )
        assertEquals(Formatting.RED, colorOf(disabled, "Disabled by another setting"))
        assertEquals(listOf(MinecraftControlTooltipKeys.HINT_TOGGLE), ControlTooltip.toggleHints().map { it.string })
    }

    /** 動作按鈕的提示只有灰色說明，沒有狀態區與操作提示。 */
    @Test
    fun `action tooltip only has the description`() {
        val tooltip = ControlTooltip.description(Text.literal("Move the HUD elements."))

        assertEquals(listOf("Move the HUD elements."), lines(tooltip))
        assertEquals(Formatting.GRAY, colorOf(tooltip, "Move the HUD elements."))
    }

    /** 只給選項名稱與目前索引時，標出該選項；索引不在範圍內時沒有任何選項被標示。 */
    @Test
    fun `indexed options mark the option at the current index`() {
        val labels = listOf(Text.literal("Any"), Text.literal("Won"), Text.literal("Lost"))

        assertEquals(
            expected = listOf("Outcome", "", MinecraftControlTooltipKeys.OPTIONS, "   Any", "▶ Won", "   Lost"),
            actual = lines(ControlTooltip.options(description = Text.literal("Outcome"), labels = labels, currentIndex = 1)),
        )
        assertEquals(
            expected = listOf(MinecraftControlTooltipKeys.OPTIONS, "   Any", "   Won", "   Lost"),
            actual = lines(ControlTooltip.options(description = null, labels = labels, currentIndex = -1)),
        )
    }

    /** 逐行版本與單一文字版本內容相同，段落之間以空行分隔，每一行都不含換行字元。 */
    @Test
    fun `option lines match the joined tooltip`() {
        val options = listOf(
            ControlTooltip.Option(label = Text.literal("Decision Panel"), current = true),
            ControlTooltip.Option(label = Text.literal("Hand Analysis"), current = false),
        )
        val hints = listOf(Text.literal("Left-click: next option"))

        val optionLines = ControlTooltip.optionLines(description = null, options = options, hints = hints)

        assertEquals(
            expected = listOf(MinecraftControlTooltipKeys.OPTIONS, "▶ Decision Panel", "   Hand Analysis", "", "Left-click: next option"),
            actual = optionLines.map { it.string },
        )
        assertEquals(lines(ControlTooltip.options(description = null, options = options, hints = hints)), optionLines.map { it.string })
    }

    /**
     * 將提示文字拆成每一行的內容。
     *
     * @param tooltip 以換行分隔各行的提示文字。
     * @return 每一行的純文字。
     */
    private fun lines(tooltip: Text): List<String> = tooltip.string.split("\n")

    /**
     * 找出提示文字中某段內容的顏色。
     *
     * @param tooltip 要檢查的提示文字。
     * @param content 要尋找的片段內容。
     * @param occurrence 內容相同的片段中要取第幾個，從 0 開始。
     * @return 該片段的顏色。
     */
    private fun colorOf(tooltip: Text, content: String, occurrence: Int = 0): Formatting {
        val colors = mutableListOf<TextColor?>()
        tooltip.visit(
            { style, text ->
                if (text == content) colors += style.color
                Optional.empty<Unit>()
            },
            Style.EMPTY,
        )
        val color = colors[occurrence]
        return Formatting.entries.first { TextColor.fromFormatting(it) == color }
    }
}
