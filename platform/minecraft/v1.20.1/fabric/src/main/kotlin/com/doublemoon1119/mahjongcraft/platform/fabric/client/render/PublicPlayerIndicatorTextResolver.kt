package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicatorValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import net.minecraft.text.Text
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

/** 將規則公開 indicator 統一解析成世界面板與 GUI 共用的本地化文字及顏色。 */
@Single
class PublicPlayerIndicatorTextResolver(
    @Provided private val displays: PublicPlayerIndicatorDisplayRegistry,
) {
    private val warnedIndicatorIds = mutableSetOf<String>()

    fun resolve(indicator: PublicPlayerIndicator): Pair<Text, Int> {
        val valueId = (indicator.indicatorValue as? PublicPlayerIndicatorValue.Option)?.optionId
        val displayId = valueId ?: indicator.id
        val display = displays.find(displayId)
        if (display == null && warnedIndicatorIds.add(displayId)) {
            LOGGER.warn("Unknown public player indicator display: {}", displayId)
        }
        val base = display?.let { Text.translatable(it.translationKey) } ?: Text.literal(displayId)
        val text = when (val indicatorValue = indicator.indicatorValue) {
            is PublicPlayerIndicatorValue.Count -> Text.literal("${base.string} ×${indicatorValue.value}")
            else -> base
        }
        return text to (0xFF000000.toInt() or (display?.colorRgb ?: DEFAULT_COLOR))
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger(PublicPlayerIndicatorTextResolver::class.java)
        const val DEFAULT_COLOR = 0xFFE08A
    }
}
