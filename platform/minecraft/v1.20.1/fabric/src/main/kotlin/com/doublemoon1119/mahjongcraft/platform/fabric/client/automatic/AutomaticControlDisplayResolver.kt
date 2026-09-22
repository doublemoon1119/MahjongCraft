package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistry
import net.minecraft.text.Text
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

/** Client 可直接呈現的一項自動操作顯示資料。 */
data class ResolvedAutomaticControlDisplay(
    val controlId: String,
    val label: Text,
    val description: Text?,
    val displayOrder: Int,
)

/** 將自動操作 ID 解析為本地化顯示資料，並安全保留未登記的第三方 ID。 */
@Single
class AutomaticControlDisplayResolver(
    @Provided private val registry: AutomaticControlDisplayRegistry,
) {
    internal val warnedUnknownControlIds = mutableSetOf<String>()

    /** 解析並依顯示順序、control ID 穩定排列指定項目。 */
    fun resolveAll(controlIds: Iterable<String>): List<ResolvedAutomaticControlDisplay> = controlIds
        .distinct()
        .map(::resolve)
        .sortedWith(compareBy(ResolvedAutomaticControlDisplay::displayOrder, ResolvedAutomaticControlDisplay::controlId))

    /** 解析單一項目；未知 ID 以原始 ID 顯示、沒有說明並排列於已登記項目之後。 */
    fun resolve(controlId: String): ResolvedAutomaticControlDisplay {
        val display = registry.find(controlId)
        if (display == null && warnedUnknownControlIds.add(controlId)) {
            LOGGER.warn("Unknown automatic control display: {}", controlId)
        }
        return ResolvedAutomaticControlDisplay(
            controlId = controlId,
            label = display?.let { Text.translatable(it.labelTranslationKey) } ?: Text.literal(controlId),
            description = display?.let { Text.translatable(it.descriptionTranslationKey) },
            displayOrder = display?.displayOrder ?: UNKNOWN_DISPLAY_ORDER,
        )
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger(AutomaticControlDisplayResolver::class.java)
        const val UNKNOWN_DISPLAY_ORDER: Int = Int.MAX_VALUE
    }
}
