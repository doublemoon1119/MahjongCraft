package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicatorValue
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import net.minecraft.text.Text

/** 將規則公開 indicator 統一解析成世界面板與 GUI 共用的本地化文字及顏色。 */
class PublicPlayerIndicatorTextResolver(
    private val displays: PublicPlayerIndicatorDisplayRegistry,
) {
    private val logger = mahjongCraftLogger(PublicPlayerIndicatorTextResolver::class)

    private val warnedIndicatorIds = mutableSetOf<String>()

    /**
     * 將規則公開指示器解析為文字與不透明 ARGB 顏色；未知顯示設定使用 ID 字面值與預設色。
     *
     * @param indicator 要顯示的公開指示器。
     * @return 本地化或 fallback 文字及對應顏色。
     */
    fun resolve(indicator: PublicPlayerIndicator): Pair<Text, Int> {
        val valueId = (indicator.indicatorValue as? PublicPlayerIndicatorValue.Option)?.optionId
        val displayId = valueId ?: indicator.id
        val display = displays.find(displayId)
        if (display == null && warnedIndicatorIds.add(displayId)) {
            logger.warn("Unknown public player indicator display: {}", displayId)
        }
        // 帶數值的標記把數值交給翻譯文字排版；找不到顯示設定時以識別碼加數值呈現。
        val value = (indicator.indicatorValue as? PublicPlayerIndicatorValue.Count)?.value
        val text = when {
            display == null -> Text.literal(value?.let { "$displayId ×$it" } ?: displayId)
            value != null -> Text.translatable(display.translationKey, value)
            else -> Text.translatable(display.translationKey)
        }
        return text to (0xFF000000.toInt() or (display?.colorRgb ?: DEFAULT_COLOR))
    }

    private companion object {
        const val DEFAULT_COLOR = 0xFFE08A
    }
}
