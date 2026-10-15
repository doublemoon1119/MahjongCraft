package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistry
import net.minecraft.text.Text

/**
 * 將自動操作 ID 解析為本地化顯示資料，並安全保留未登記的第三方 ID。
 *
 * @property registry 已登記的顯示資料來源。
 */
class AutomaticControlDisplayResolver(
    private val registry: AutomaticControlDisplayRegistry,
) {
    /** 記錄本 session 已警告過的未知識別碼，避免重複輸出相同訊息。 */
    private val logger = mahjongCraftLogger(AutomaticControlDisplayResolver::class)

    /** 已輸出過警告的未知控制項 ID。 */
    internal val warnedUnknownControlIds = mutableSetOf<String>()

    /**
     * 解析並依顯示順序、control ID 穩定排列指定項目。
     *
     * @param controlIds 要顯示的自動操作 ID。
     * @return 去重後的顯示資料清單。
     */
    fun resolveAll(controlIds: Iterable<String>): List<ResolvedAutomaticControlDisplay> = controlIds
        .distinct()
        .map(::resolve)
        .sortedWith(compareBy(ResolvedAutomaticControlDisplay::displayOrder, ResolvedAutomaticControlDisplay::controlId))

    /**
     * 解析單一項目；未知 ID 以原始 ID 顯示、沒有說明並排列於已登記項目之後。
     *
     * @param controlId 自動操作 ID。
     * @return 對應的本地化顯示資料或未知 ID 的回退顯示。
     */
    fun resolve(controlId: String): ResolvedAutomaticControlDisplay {
        val display = registry.find(controlId)
        if (display == null && warnedUnknownControlIds.add(controlId)) {
            logger.warn("Unknown automatic control display: {}", controlId)
        }
        return ResolvedAutomaticControlDisplay(
            controlId = controlId,
            label = display?.let { Text.translatable(it.labelTranslationKey) } ?: Text.literal(controlId),
            description = display?.let { Text.translatable(it.descriptionTranslationKey) },
            displayOrder = display?.displayOrder ?: UNKNOWN_DISPLAY_ORDER,
        )
    }

    private companion object {
        /** 未登記項目使用的最後顯示順序。 */
        const val UNKNOWN_DISPLAY_ORDER: Int = Int.MAX_VALUE
    }
}
