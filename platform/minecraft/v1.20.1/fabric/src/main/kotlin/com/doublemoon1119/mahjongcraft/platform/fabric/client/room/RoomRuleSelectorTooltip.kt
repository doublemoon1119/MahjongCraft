package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 規則切換鈕的 tooltip，格式與其他設定欄位相同：目前規則的說明、目前值，以及所有已登記規則的清單。
 *
 * 清單中目前選中的規則標綠色，可以選的其他規則標白色，不能選的規則標紅色並附上原因。
 *
 * @param currentModuleId 設定頁目前顯示的規則模組。
 * @param candidates 依顯示順序排列的所有已登記規則。
 * @param ruleName 取得規則模組顯示名稱。
 */
internal fun roomRuleSelectorTooltip(
    currentModuleId: String,
    candidates: List<GameConfigPresentationDefinition>,
    ruleName: (String) -> Text,
): MutableText {
    val result = Text.empty()
    candidates.firstOrNull { it.ruleModuleId == currentModuleId }?.let { current ->
        result.append(Text.translatable(current.descriptionTranslationKey).formatted(Formatting.GRAY)).append("\n")
    }
    result.append("• ").append(
        Text.translatable(MinecraftRoomScreenKeys.CURRENT_VALUE, ruleName(currentModuleId)).formatted(Formatting.GREEN),
    )
    result.append("\n• ").append(Text.translatable(MinecraftRoomScreenKeys.AVAILABLE_OPTIONS).formatted(Formatting.GOLD))
    candidates.forEach { candidate ->
        val color = when {
            candidate.ruleModuleId == currentModuleId -> Formatting.GREEN
            candidate.selectable -> Formatting.WHITE
            else -> Formatting.RED
        }
        result.append("\n  • ").append(ruleName(candidate.ruleModuleId).copy().formatted(color))
        candidate.unavailableReasonTranslationKey?.let { reason ->
            result.append(" — ").append(Text.translatable(reason).formatted(Formatting.RED))
        }
    }
    return result
}
