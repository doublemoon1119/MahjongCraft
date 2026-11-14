package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftSettingTooltipKeys
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 設定欄位提示框的共用格式，由上到下為說明、狀態區、提醒與操作提示，各段之間空一行，沒有內容的段落省略。
 *
 * - 說明：灰色。
 * - 狀態區：選項欄位列出全部選項，目前選項為綠色並以「▶」標示，其他可選的選項為白色，不可選的選項
 *   為紅色並附上原因；數值欄位顯示金色標題與範圍。目前值不另外重複一行。
 * - 提醒：紅色，例如欄位被其他設定停用。
 * - 操作提示：灰色，寫玩家要做的動作與結果；提到畫面上的按鈕時用方括號標出按鈕上的字。
 */
internal object SettingTooltip {
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
        title: Text = Text.translatable(MinecraftSettingTooltipKeys.OPTIONS),
        notice: Text? = null,
    ): Text = compose(
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
            .append(Text.translatable(MinecraftSettingTooltipKeys.RANGE).formatted(Formatting.GOLD))
            .append("  ")
            .append(range.copy().formatted(Formatting.WHITE))
        return compose(description = description, status = listOf(status), notice = notice, hints = hints)
    }

    /**
     * 建立只有說明的提示框，用於沒有對應值的動作按鈕。
     *
     * @param description 按鈕說明。
     * @return 灰色的說明文字。
     */
    fun description(description: Text): Text = compose(description = description, status = emptyList(), notice = null, hints = emptyList())

    /**
     * 建立數值範圍文字。
     *
     * @param minimum 最小值。
     * @param maximum 最大值。
     * @param unsetLabel 欄位可以不設定時，不設定狀態的名稱；必須設定數值時為 null。
     * @return 範圍文字，例如「1 – 13」或「不限制, 1000 – 100000」。
     */
    fun rangeText(minimum: Text, maximum: Text, unsetLabel: Text? = null): Text = if (unsetLabel == null) {
        Text.translatable(MinecraftSettingTooltipKeys.RANGE_VALUES, minimum, maximum)
    } else {
        Text.translatable(MinecraftSettingTooltipKeys.RANGE_VALUES_OPTIONAL, unsetLabel, minimum, maximum)
    }

    /** 開關的操作提示。 */
    fun toggleHints(): List<Text> = listOf(hint(Text.translatable(MinecraftSettingTooltipKeys.HINT_TOGGLE)))

    /** 點擊後開啟選項畫面的操作提示。 */
    fun chooseHints(): List<Text> = listOf(hint(Text.translatable(MinecraftSettingTooltipKeys.HINT_CHOOSE)))

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
        add(hint(Text.translatable(MinecraftSettingTooltipKeys.HINT_STEP, step)))
        add(hint(Text.translatable(MinecraftSettingTooltipKeys.HINT_SHIFT_STEP, shiftStep)))
        add(hint(Text.translatable(MinecraftSettingTooltipKeys.HINT_TYPE_NUMBER)))
        if (unsetLabel != null) add(hint(Text.translatable(MinecraftSettingTooltipKeys.HINT_CLEAR, minimum, unsetLabel)))
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
            Text.translatable(MinecraftSettingTooltipKeys.UNAVAILABLE_OPTION, option.label, reason)
        } ?: option.label.copy()
        val color = when {
            option.current -> Formatting.GREEN
            option.unavailableReason != null -> Formatting.RED
            else -> Formatting.WHITE
        }
        return Text.literal(if (option.current) CURRENT_MARKER else OPTION_INDENT).append(label).formatted(color)
    }

    /**
     * 將各段落組成提示文字，段落之間空一行。
     *
     * @param description 說明；沒有時為 null。
     * @param status 狀態區的每一行。
     * @param notice 提醒；沒有時為 null。
     * @param hints 操作提示的每一行。
     * @return 以換行分隔各行的提示文字。
     */
    private fun compose(description: Text?, status: List<Text>, notice: Text?, hints: List<Text>): Text {
        val sections = listOf(
            listOfNotNull(description?.copy()?.formatted(Formatting.GRAY)),
            status,
            listOfNotNull(notice?.copy()?.formatted(Formatting.RED)),
            hints,
        ).filter { it.isNotEmpty() }
        val result = Text.empty()
        sections.forEachIndexed { sectionIndex, lines ->
            if (sectionIndex > 0) result.append("\n\n")
            lines.forEachIndexed { lineIndex, line ->
                if (lineIndex > 0) result.append("\n")
                result.append(line)
            }
        }
        return result
    }
}
