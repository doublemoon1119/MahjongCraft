package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 從面板的版面設計讀出「役種逐條揭示完之後」各段內容的出現時間。
 *
 * 面板的播放順序是：整面淡入 → 役種一條一條出現 → 之後的內容（例如日麻的翻符那一行、分數）。之後的內容各自在
 * 版面裡寫明「錨在逐條揭示之後、延遲多少 tick 出現」，因此程式不需要認得任何規則的欄位，直接讀版面就知道：
 * 分數何時出現、面板要留多長、每一行出現時要不要響一聲。
 */
object WinSettlementRevealTimeline {
    /**
     * 分數在逐條揭示結束後多少 tick 出現。
     *
     * 版面若把分數錨在逐條揭示之後，就取它的延遲；分數沒有這樣排時為 0，代表逐條揭示一結束就出現。
     *
     * @param layout 面板的版面設計。
     */
    fun scoreRevealDelayTicks(layout: PresentationLayout): Int = animatedNodes(layout, presentFieldIds = null)
        .firstOrNull { node ->
            node.timeline.anchor == PresentationTimelineAnchor.AFTER_ENTRIES &&
                containsField(node.child, BuiltInWinSettlementFieldIds.TOTAL_SCORE)
        }
        ?.timeline
        ?.offsetTicks
        ?.coerceAtLeast(0)
        ?: 0

    /**
     * 分數以外、排在逐條揭示之後的每一行，在逐條揭示結束後多少 tick 出現，由早到晚排列。
     *
     * 版面裡「有資料才顯示」的部分依 [presentFieldIds] 判斷，這位贏家沒有的那一行不會列入。
     *
     * @param layout 面板的版面設計。
     * @param presentFieldIds 這位贏家實際有內容的欄位。
     */
    fun lineRevealDelaysTicks(layout: PresentationLayout, presentFieldIds: Set<PresentationFieldId>): List<Int> = animatedNodes(layout, presentFieldIds)
        .filter { node ->
            node.timeline.anchor == PresentationTimelineAnchor.AFTER_ENTRIES &&
                !containsField(node.child, BuiltInWinSettlementFieldIds.TOTAL_SCORE)
        }
        .map { node -> node.timeline.offsetTicks.coerceAtLeast(0) }
        .sorted()

    /**
     * 收集版面裡會實際出現的動畫節點。
     *
     * [presentFieldIds] 為 `null` 時不看「有資料才顯示」的條件，所有節點都算。
     */
    private fun animatedNodes(
        layout: PresentationLayout,
        presentFieldIds: Set<PresentationFieldId>?,
    ): List<PresentationLayout.Animated> = when (layout) {
        is PresentationLayout.Animated -> listOf(layout) + animatedNodes(layout.child, presentFieldIds)
        is PresentationLayout.IfPresent ->
            if (presentFieldIds == null || layout.fieldId in presentFieldIds) animatedNodes(layout.child, presentFieldIds) else emptyList()
        else -> childrenOf(layout).flatMap { child -> animatedNodes(child, presentFieldIds) }
    }

    /** 這段版面裡有沒有顯示指定欄位。 */
    private fun containsField(layout: PresentationLayout, fieldId: PresentationFieldId): Boolean = when (layout) {
        is PresentationLayout.Text -> layout.fieldId == fieldId
        is PresentationLayout.PlayerIdentity -> layout.fieldId == fieldId
        is PresentationLayout.Tile -> layout.fieldId == fieldId
        is PresentationLayout.TileList -> layout.fieldId == fieldId
        is PresentationLayout.TileGroups -> layout.fieldId == fieldId
        is PresentationLayout.RepeatEntries -> layout.fieldId == fieldId
        else -> childrenOf(layout).any { child -> containsField(child, fieldId) }
    }

    /** 容器類節點的直接子節點；沒有子節點的類型回傳空清單。 */
    private fun childrenOf(layout: PresentationLayout): List<PresentationLayout> = when (layout) {
        is PresentationLayout.Row -> layout.children
        is PresentationLayout.Column -> layout.children
        is PresentationLayout.Grid -> layout.children
        is PresentationLayout.Box -> layout.children
        is PresentationLayout.Positioned -> listOf(layout.child)
        is PresentationLayout.Weighted -> listOf(layout.child)
        is PresentationLayout.SizeConstraint -> listOf(layout.child)
        is PresentationLayout.IfPresent -> listOf(layout.child)
        is PresentationLayout.Animated -> listOf(layout.child)
        is PresentationLayout.Text,
        is PresentationLayout.PlayerIdentity,
        is PresentationLayout.Tile,
        is PresentationLayout.TileList,
        is PresentationLayout.TileGroups,
        is PresentationLayout.RepeatEntries,
        is PresentationLayout.Spacer,
        -> emptyList()
    }
}
