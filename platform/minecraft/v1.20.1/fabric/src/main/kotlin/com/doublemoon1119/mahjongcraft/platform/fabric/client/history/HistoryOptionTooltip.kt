package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 建立以選項位置判斷目前選值的歷史畫面 tooltip，避免依翻譯後文字判斷身分。
 *
 * @param title 選項名稱。
 * @param current 目前選值。
 * @param selectedIndex 目前選項在 options 中的索引。
 * @param options 可選值。
 * @return 格式化後的 tooltip 文字。
 */
internal fun historyOptionTooltip(
    title: Text,
    current: Text,
    selectedIndex: Int,
    options: List<Text>,
): Text = Text.empty()
    .append(Text.translatable(MinecraftHistoryScreenKeys.TOOLTIP_DESCRIPTION, title).formatted(Formatting.GRAY))
    .append("\n")
    .append(Text.translatable(MinecraftHistoryScreenKeys.TOOLTIP_CURRENT, current).formatted(Formatting.GREEN))
    .append("\n")
    .append(Text.translatable(MinecraftHistoryScreenKeys.TOOLTIP_OPTIONS).formatted(Formatting.GOLD))
    .also { result ->
        options.forEachIndexed { index, option ->
            result.append("\n• ").append(option.copy().formatted(if (index == selectedIndex) Formatting.GREEN else Formatting.WHITE))
        }
    }
