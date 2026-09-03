package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import kotlin.math.roundToInt

/** 建立 Client Config Screen 與 HUD editor 共用的本地化差異清單。 */
fun clientConfigDifferenceText(from: MahjongClientConfigState, to: MahjongClientConfigState): Text {
    val result = Text.translatable(MinecraftClientConfigScreenKeys.CONFIG_CHANGES).formatted(Formatting.GOLD)
    if (from.autoSortHandEnabled != to.autoSortHandEnabled) {
        result.appendValueChange(
            MinecraftClientConfigScreenKeys.AUTO_SORT_HAND,
            booleanText(from.autoSortHandEnabled),
            booleanText(to.autoSortHandEnabled),
        )
    }
    if (from.tileLabelsEnabled != to.tileLabelsEnabled) {
        result.appendValueChange(
            MinecraftClientConfigScreenKeys.TILE_LABELS,
            booleanText(from.tileLabelsEnabled),
            booleanText(to.tileLabelsEnabled),
        )
    }
    result.appendHudLayoutChanges(from.hudLayout, to.hudLayout)
    return result
}

/** 附加 HUD 百分比配置差異。 */
private fun MutableText.appendHudLayoutChanges(from: MahjongHudLayoutConfig, to: MahjongHudLayoutConfig) {
    if (from.decisionPanelY != to.decisionPanelY) {
        append("\n").append(
            Text.translatable(
                MinecraftClientConfigScreenKeys.HUD_LAYOUT_Y_CHANGE,
                Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_DECISION_PANEL),
                percent(from.decisionPanelY),
                percent(to.decisionPanelY),
            ).formatted(Formatting.GRAY),
        )
    }
    if (from.compactPromptX != to.compactPromptX || from.compactPromptY != to.compactPromptY) {
        append("\n").append(
            Text.translatable(
                MinecraftClientConfigScreenKeys.HUD_LAYOUT_XY_CHANGE,
                Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_COMPACT_PROMPT),
                percent(from.compactPromptX),
                percent(from.compactPromptY),
                percent(to.compactPromptX),
                percent(to.compactPromptY),
            ).formatted(Formatting.GRAY),
        )
    }
    if (from.discardAnalysisY != to.discardAnalysisY) {
        append("\n").append(
            Text.translatable(
                MinecraftClientConfigScreenKeys.HUD_LAYOUT_Y_CHANGE,
                Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_DISCARD_ANALYSIS),
                percent(from.discardAnalysisY),
                percent(to.discardAnalysisY),
            ).formatted(Formatting.GRAY),
        )
    }
}

/** 附加一般設定值差異。 */
private fun MutableText.appendValueChange(nameKey: String, from: Text, to: Text) {
    append("\n").append(
        Text.translatable(
            MinecraftClientConfigScreenKeys.CONFIG_VALUE_CHANGE,
            Text.translatable(nameKey),
            from,
            to,
        ).formatted(Formatting.GRAY),
    )
}

/** 將 Boolean 轉換為 Client Config Screen 共用文字。 */
private fun booleanText(enabled: Boolean): Text = Text.translatable(
    if (enabled) MinecraftClientConfigScreenKeys.ENABLED else MinecraftClientConfigScreenKeys.DISABLED,
)

/** 將比例轉換為整數百分比。 */
private fun percent(value: Double): Int = (value * 100).roundToInt()
