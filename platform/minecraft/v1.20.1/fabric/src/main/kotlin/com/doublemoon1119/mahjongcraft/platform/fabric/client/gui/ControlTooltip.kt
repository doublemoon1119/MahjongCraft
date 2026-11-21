package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftControlTooltipKeys
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 控制項提示框的共用格式，由上到下為說明、狀態區、提醒與操作提示，各段之間空一行，沒有內容的段落省略。
 *
 * - 說明：灰色。
 * - 狀態區：選項欄位列出全部選項，目前選項為綠色並以「▶」標示，其他可選的選項為白色，不可選的選項
 *   為紅色並附上原因；數值欄位顯示金色標題與範圍；開啟其他畫面編輯的按鈕顯示金色標題與目前內容。
 *   目前值不另外重複一行。
 * - 提醒：紅色，例如欄位被其他設定停用。
 * - 操作提示：灰色，寫玩家要做的動作與結果；提到畫面上的按鈕時用方括號標出按鈕上的字。
 */
internal object ControlTooltip {
    /** 目前選項行首的標示。 */
    private const val CURRENT_MARKER = "▶ "

    /** 其他選項行首的縮排，寬度與 [CURRENT_MARKER] 相近。 */
    private const val OPTION_INDENT = "   "

    /**
     * 提示框選項清單中的一個選項。
     *
     * @property label 選項名稱。
     * @property current 是否為目前選項；多選欄位可以有多個。
     * @property unavailableReason 不可選的原因；可選時為 null。
     */
    data class Option(
        val label: Text,
        val current: Boolean,
        val unavailableReason: Text? = null,
    )

    /**
     * 建立選項、開關與多選欄位的提示框。
     *
     * @param description 欄位說明；沒有說明時為 null。
     * @param options 依顯示順序排列的全部選項。
     * @param hints 操作提示；不需要提示時為空。
     * @param title 選項清單的標題。
     * @param notice 提醒文字；沒有提醒時為 null。
     * @return 以換行分隔各行的提示文字。
     */
    fun options(
        description: Text?,
        options: List<Option>,
        hints: List<Text> = emptyList(),
        title: Text = Text.translatable(MinecraftControlTooltipKeys.OPTIONS),
        notice: Text? = null,
    ): Text = joinLines(optionLines(description = description, options = options, hints = hints, title = title, notice = notice))

    /**
     * 建立只需要標出目前是第幾個選項的提示框，用於全部選項都可選的循環按鈕。
     *
     * @param description 控制項說明；沒有說明時為 null。
     * @param labels 依顯示順序排列的全部選項名稱。
     * @param currentIndex 目前選項的索引；沒有目前選項時為負值。
     * @param hints 操作提示；不需要提示時為空。
     * @return 以換行分隔各行的提示文字。
     */
    fun options(
        description: Text?,
        labels: List<Text>,
        currentIndex: Int,
        hints: List<Text> = emptyList(),
    ): Text = options(
        description = description,
        options = labels.mapIndexed { index, label -> Option(label = label, current = index == currentIndex) },
        hints = hints,
    )

    /**
     * 建立選項提示框的每一行，供逐行繪製提示框的畫面使用；內容與 [options] 相同。
     *
     * @param description 控制項說明；沒有說明時為 null。
     * @param options 依顯示順序排列的全部選項。
     * @param hints 操作提示；不需要提示時為空。
     * @param title 選項清單的標題。
     * @param notice 提醒文字；沒有提醒時為 null。
     * @return 提示框的每一行；段落之間以空行分隔。
     */
    fun optionLines(
        description: Text?,
        options: List<Option>,
        hints: List<Text> = emptyList(),
        title: Text = Text.translatable(MinecraftControlTooltipKeys.OPTIONS),
        notice: Text? = null,
    ): List<Text> = composeLines(
        description = description,
        status = listOf<Text>(title.copy().formatted(Formatting.GOLD)) + options.map(::optionLine),
        notice = notice,
        hints = hints,
    )

    /**
     * 建立數值欄位的提示框。
     *
     * @param description 欄位說明；沒有說明時為 null。
     * @param range 以 [rangeText] 建立的範圍文字。
     * @param hints 操作提示；不需要提示時為空。
     * @param notice 提醒文字；沒有提醒時為 null。
     * @return 以換行分隔各行的提示文字。
     */
    fun range(
        description: Text?,
        range: Text,
        hints: List<Text> = emptyList(),
        notice: Text? = null,
    ): Text {
        val status = Text.empty()
            .append(Text.translatable(MinecraftControlTooltipKeys.RANGE).formatted(Formatting.GOLD))
            .append("  ")
            .append(range.copy().formatted(Formatting.WHITE))
        return joinLines(composeLines(description = description, status = listOf(status), notice = notice, hints = hints))
    }

    /**
     * 建立列出目前內容的提示框，用於開啟其他畫面編輯多項內容的按鈕，例如列出已套用的篩選條件。
     *
     * @param description 按鈕說明；沒有說明時為 null。
     * @param title 內容清單的標題。
     * @param lines 內容清單的每一行，以白色縮排列出。
     * @param hints 操作提示；不需要提示時為空。
     * @return 以換行分隔各行的提示文字。
     */
    fun list(
        description: Text?,
        title: Text,
        lines: List<Text>,
        hints: List<Text> = emptyList(),
    ): Text {
        val status = listOf<Text>(title.copy().formatted(Formatting.GOLD)) +
            lines.map { Text.literal(OPTION_INDENT).append(it).formatted(Formatting.WHITE) }
        return joinLines(composeLines(description = description, status = status, notice = null, hints = hints))
    }

    /**
     * 建立只有說明的提示框，用於沒有對應值的動作按鈕。
     *
     * @param description 按鈕說明。
     * @param notice 紅字提醒，例如按鈕停用的原因；沒有時為 null。
     * @return 灰色的說明文字，以及紅字提醒。
     */
    fun description(description: Text, notice: Text? = null): Text {
        val lines = composeLines(description = description, status = emptyList(), notice = notice, hints = emptyList())
        return joinLines(lines)
    }

    /**
     * 建立數值範圍文字。
     *
     * @param minimum 最小值。
     * @param maximum 最大值。
     * @param unsetLabel 欄位可以不設定時，不設定狀態的名稱；必須設定數值時為 null。
     * @return 範圍文字，例如「1 – 13」或「不限制, 1000 – 100000」。
     */
    fun rangeText(minimum: Text, maximum: Text, unsetLabel: Text? = null): Text = if (unsetLabel == null) {
        Text.translatable(MinecraftControlTooltipKeys.RANGE_VALUES, minimum, maximum)
    } else {
        Text.translatable(MinecraftControlTooltipKeys.RANGE_VALUES_OPTIONAL, unsetLabel, minimum, maximum)
    }

    /** 開關的操作提示。 */
    fun toggleHints(): List<Text> = listOf(hint(Text.translatable(MinecraftControlTooltipKeys.HINT_TOGGLE)))

    /** 點擊後開啟選項畫面的操作提示。 */
    fun chooseHints(): List<Text> = listOf(hint(Text.translatable(MinecraftControlTooltipKeys.HINT_CHOOSE)))

    /**
     * 建立數值欄位的操作提示。
     *
     * @param step 每次點擊的調整量。
     * @param shiftStep 按住 Shift 時每次點擊的調整量。
     * @param minimum 最小值；只在欄位可以不設定時使用。
     * @param unsetLabel 欄位可以不設定時，不設定狀態的名稱；必須設定數值時為 null。
     * @return 加減按鈕、按住 Shift、直接輸入的提示；欄位可以不設定時再加一行改為不設定的方法。
     */
    fun integerHints(step: Text, shiftStep: Text, minimum: Text, unsetLabel: Text? = null): List<Text> = buildList {
        add(hint(Text.translatable(MinecraftControlTooltipKeys.HINT_STEP, step)))
        add(hint(Text.translatable(MinecraftControlTooltipKeys.HINT_SHIFT_STEP, shiftStep)))
        add(hint(Text.translatable(MinecraftControlTooltipKeys.HINT_TYPE_NUMBER)))
        if (unsetLabel != null) add(hint(Text.translatable(MinecraftControlTooltipKeys.HINT_CLEAR, minimum, unsetLabel)))
    }

    /**
     * 將文字設為操作提示的顏色。
     *
     * @param text 提示內容。
     * @return 灰色的提示。
     */
    private fun hint(text: MutableText): Text = text.formatted(Formatting.GRAY)

    /**
     * 建立選項清單中的一行。
     *
     * @param option 要呈現的選項。
     * @return 依目前、可選、不可選上色並加上行首標示的文字。
     */
    private fun optionLine(option: Option): Text {
        val label = option.unavailableReason?.let { reason ->
            Text.translatable(MinecraftControlTooltipKeys.UNAVAILABLE_OPTION, option.label, reason)
        } ?: option.label.copy()
        val color = when {
            option.current -> Formatting.GREEN
            option.unavailableReason != null -> Formatting.RED
            else -> Formatting.WHITE
        }
        return Text.literal(if (option.current) CURRENT_MARKER else OPTION_INDENT).append(label).formatted(color)
    }

    /**
     * 將各段落排成提示框的每一行，段落之間空一行，沒有內容的段落省略。
     *
     * @param description 說明；沒有時為 null。
     * @param status 狀態區的每一行。
     * @param notice 提醒；沒有時為 null。
     * @param hints 操作提示的每一行。
     * @return 提示框的每一行。
     */
    private fun composeLines(description: Text?, status: List<Text>, notice: Text?, hints: List<Text>): List<Text> {
        val sections = listOf(
            listOfNotNull(description?.copy()?.formatted(Formatting.GRAY)),
            status,
            listOfNotNull(notice?.copy()?.formatted(Formatting.RED)),
            hints,
        ).filter { it.isNotEmpty() }
        return buildList {
            sections.forEachIndexed { index, lines ->
                if (index > 0) add(Text.empty())
                addAll(lines)
            }
        }
    }

    /**
     * 將每一行以換行串成一段提示文字。
     *
     * @param lines 提示框的每一行。
     * @return 以換行分隔各行的提示文字。
     */
    private fun joinLines(lines: List<Text>): Text {
        val result = Text.empty()
        lines.forEachIndexed { index, line ->
            if (index > 0) result.append("\n")
            result.append(line)
        }
        return result
    }
}
