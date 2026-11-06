package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 內建規則共用的胡牌結算版面骨架，讓不同規則的面板保持同一套設計。
 *
 * 骨架固定面板尺寸與底色、標題、玩家身分列、左下摘要與右下總得分的位置與動畫；手牌列、規則專屬列與逐條揭示的
 * 清單由上往下排，缺少的列不佔位置，下面的列往上補。規則只提供自己的標題欄位、手牌下方的規則專屬列、清單欄位與
 * 摘要欄位。骨架不認得任何規則，規則專屬列與清單都可以省略。
 *
 * 想保持一致外觀的擴充可以呼叫 [root] 換上自己的內容；需要完全不同版面的擴充照常註冊自己的模板。
 */
object StandardWinSettlementLayout {
    /** 面板寬度。 */
    const val WIDTH: Float = 320f

    /** 面板高度。 */
    const val HEIGHT: Float = 156f

    /** 標題與強調文字的顏色。 */
    const val TITLE_ARGB: Int = 0xFFFFD45A.toInt()

    /** 小標題與關係箭頭的顏色。 */
    const val LABEL_ARGB: Int = 0xFFE5C16A.toInt()

    /** 一般摘要文字的顏色。 */
    const val SUMMARY_ARGB: Int = 0xFFE5E5E5.toInt()

    /** 手牌列與規則專屬列之間的間距。 */
    private const val ROW_GAP = 12f

    /** 規則專屬列與清單之間的間距。 */
    private const val ENTRIES_GAP = 13.5f

    /** 手牌列頂端的位置；沒有手牌時規則專屬列與清單依序往上補。 */
    private const val TILE_ROWS_TOP = 43.5f

    /** 面板底色，不留內容邊距。 */
    private val PANEL = PresentationContainerStyle(backgroundArgb = 0xC7000000.toInt(), padding = 0f)

    /**
     * 組出完整面板。
     *
     * @param title 標題欄位，例如這次胡牌動作的名稱。
     * @param ruleRow 手牌下方的規則專屬列；沒有時為 null。沒有手牌時這一列往上補到手牌的位置。
     * @param entries 逐條揭示的清單欄位；沒有時為 null。
     * @param summaries 清單揭示完成後出現在左下的摘要，例如用 [summary] 建立的合計。
     * @return 面板根節點。
     */
    fun root(
        title: PresentationFieldId,
        ruleRow: PresentationLayout?,
        entries: PresentationFieldId?,
        summaries: List<PresentationLayout>,
    ): PresentationLayout.Box = PresentationLayout.Box(
        width = WIDTH,
        height = HEIGHT,
        style = PANEL,
        children = listOfNotNull(
            centered(PresentationLayout.Text(title, scale = 1.35f, argb = TITLE_ARGB), 11f),
            centered(playerRelationship(), 29f),
            centered(
                PresentationLayout.Column(
                    children = listOfNotNull(
                        PresentationLayout.IfPresent(
                            fieldId = BuiltInWinSettlementFieldIds.COMPLETE_HAND_GROUPS,
                            child = PresentationLayout.Row(
                                children = listOf(
                                    PresentationLayout.TileGroups(BuiltInWinSettlementFieldIds.COMPLETE_HAND_GROUPS),
                                    PresentationLayout.Spacer(width = 5f),
                                    PresentationLayout.Tile(BuiltInWinSettlementFieldIds.WINNING_TILE),
                                ),
                                spacing = 0.6f,
                                arrangement = PresentationArrangement.CENTER,
                            ),
                        ),
                        ruleRow,
                        entries?.let { fieldId ->
                            PresentationLayout.IfPresent(
                                fieldId = fieldId,
                                child = PresentationLayout.Column(
                                    children = listOf(
                                        PresentationLayout.Spacer(height = ENTRIES_GAP - ROW_GAP),
                                        PresentationLayout.RepeatEntries(fieldId, entriesPerColumn = 4, width = 232f),
                                    ),
                                ),
                            )
                        },
                    ),
                    spacing = ROW_GAP,
                ),
                TILE_ROWS_TOP,
            ),
            PresentationLayout.Positioned(PresentationLayout.Row(summaries), x = 44f, y = 141f),
            PresentationLayout.Positioned(
                PresentationLayout.Animated(
                    PresentationLayout.Text(BuiltInWinSettlementFieldIds.TOTAL_SCORE, argb = TITLE_ARGB),
                    // 摘要那一行錨在逐條揭示之後，佔一條清單的間隔（8 tick）；分數接在它後面出現。
                    PresentationTimeline(PresentationTimelineAnchor.AFTER_ENTRIES, offsetTicks = 8, durationTicks = 18),
                    listOf(
                        PresentationAnimationEffect.Fade(),
                        PresentationAnimationEffect.ScaleKeyframes(listOf(ScaleKeyframe(0f, 1.35f), ScaleKeyframe(1f, 1f))),
                    ),
                    transformOriginX = PresentationAlignment.END,
                    transformOriginY = PresentationAlignment.START,
                ),
                x = 276f,
                y = 141f,
                horizontalAnchor = PresentationAlignment.END,
            ),
        ),
    )

    /**
     * 清單揭示完成後才淡入的單一摘要；欄位不存在時不佔位置。
     *
     * @param fieldId 摘要欄位。
     * @param argb 文字顏色。
     * @return 摘要節點。
     */
    fun summary(fieldId: PresentationFieldId, argb: Int = SUMMARY_ARGB): PresentationLayout = PresentationLayout.IfPresent(
        fieldId,
        PresentationLayout.Animated(
            PresentationLayout.Text(fieldId, argb = argb),
            PresentationTimeline(PresentationTimelineAnchor.AFTER_ENTRIES, durationTicks = 6),
            listOf(PresentationAnimationEffect.Fade()),
        ),
    )

    /**
     * 小標題接一排小牌，例如規則專屬列中的指示牌或花牌。
     *
     * @param label 小標題欄位。
     * @param tiles 牌列節點，例如用 [smallTiles] 建立。
     * @return 置中的標題與牌列。
     */
    fun labeledTiles(label: PresentationFieldId, tiles: PresentationLayout): PresentationLayout = PresentationLayout.Row(
        children = listOf(
            PresentationLayout.Text(label, scale = 0.82f, argb = LABEL_ARGB),
            tiles,
        ),
        spacing = 4f,
        arrangement = PresentationArrangement.CENTER,
        fillMaxWidth = true,
    )

    /**
     * 規則專屬列使用的小牌列。
     *
     * @param fieldId 牌列欄位。
     * @return 小牌列節點。
     */
    fun smallTiles(fieldId: PresentationFieldId): PresentationLayout = PresentationLayout.TileList(fieldId, tileWidth = 8f, tileHeight = 11f, spacing = 2f)

    /** 胡牌者與可選責任玩家的身分列。 */
    private fun playerRelationship(): PresentationLayout = PresentationLayout.Row(
        children = listOf(
            PresentationLayout.PlayerIdentity(BuiltInWinSettlementFieldIds.WINNER_IDENTITY),
            PresentationLayout.IfPresent(
                BuiltInWinSettlementFieldIds.RESPONSIBLE_PLAYER_IDENTITY,
                PresentationLayout.Row(
                    listOf(
                        PresentationLayout.Text(BuiltInWinSettlementFieldIds.RELATION_ARROW, argb = LABEL_ARGB),
                        PresentationLayout.PlayerIdentity(BuiltInWinSettlementFieldIds.RESPONSIBLE_PLAYER_IDENTITY),
                    ),
                    spacing = 5f,
                ),
            ),
        ),
        spacing = 5f,
        arrangement = PresentationArrangement.CENTER,
        fillMaxWidth = true,
    )

    /**
     * 水平置中放在面板指定高度。
     *
     * @param child 要放置的節點。
     * @param y 節點頂端的位置。
     * @return 定位節點。
     */
    private fun centered(child: PresentationLayout, y: Float): PresentationLayout.Positioned = PresentationLayout.Positioned(
        child = child,
        x = WIDTH / 2f,
        y = y,
        horizontalAnchor = PresentationAlignment.CENTER,
    )
}
