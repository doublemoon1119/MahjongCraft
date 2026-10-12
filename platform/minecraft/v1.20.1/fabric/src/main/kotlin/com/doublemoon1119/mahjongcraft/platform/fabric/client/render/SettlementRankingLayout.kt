package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/**
 * 結算排名面板的水平版面：欄位由左至右依序排列，整體以 x = 0 置中。
 *
 * 欄寬在建立時就已決定，動畫途中分數變化不會改變面板尺寸。只處理數字；文字寬度由呼叫端量測後傳入。
 *
 * @property panelHalfWidth 面板半寬，含左右留白。
 * @property spans 各欄位 ID 對應的水平範圍。
 */
class SettlementRankingLayout private constructor(
    val panelHalfWidth: Float,
    private val spans: Map<SettlementRankingColumnId, SettlementRankingColumnSpan>,
) {
    /**
     * 取得指定欄位的範圍；欄位不在版面中時拋出例外。
     *
     * @param id 要查詢的欄位識別。
     * @return 欄位在版面中的水平範圍。
     * @throws NoSuchElementException 欄位不在此版面中。
     */
    operator fun get(id: SettlementRankingColumnId): SettlementRankingColumnSpan = spans.getValue(id)

    /**
     * 取得選用欄位的範圍；欄位不在版面中時為 `null`。
     *
     * @param id 要查詢的欄位識別。
     * @return 欄位範圍；未配置時為 `null`。
     */
    fun find(id: SettlementRankingColumnId): SettlementRankingColumnSpan? = spans[id]

    companion object {
        /** 名次欄寬度。 */
        const val RANK_COLUMN_WIDTH: Float = 12f

        /** 同一組欄位（名次、頭像、名稱）之間的間距。 */
        const val COLUMN_GAP: Float = 7f

        /** 不同組欄位之間的間距。 */
        const val SECTION_GAP: Float = 9f

        /** 玩家名稱的最大寬度，超出時由呼叫端截斷。 */
        const val NAME_MAX_WIDTH: Int = 96

        /** 分數欄的最小寬度。 */
        const val MIN_SCORE_COLUMN_WIDTH: Float = 48f

        /** 分數增減欄的最小寬度。 */
        const val MIN_DELTA_COLUMN_WIDTH: Float = 56f

        /** 數字欄內容兩側的留白。 */
        const val NUMERIC_COLUMN_PADDING: Float = 6f

        /**
         * 由左至右排列 [columns]，並在兩側加上 [panelPadding]。
         *
         * 同一個欄位出現多次時以最後一次為準。
         *
         * @param columns 依顯示順序排列的欄位及其寬度、前置間距。
         * @param panelPadding 面板左右內距。
         * @return 含欄位位置與面板半寬的版面。
         */
        fun arrange(
            columns: List<SettlementRankingColumn>,
            panelPadding: Float,
        ): SettlementRankingLayout {
            val totalWidth = panelPadding * 2 +
                columns.sumOf { it.width.toDouble() }.toFloat() +
                columns.drop(1).sumOf { it.gapBefore.toDouble() }.toFloat()
            var cursor = -totalWidth / 2f + panelPadding
            val spans = columns.mapIndexed { index, column ->
                if (index > 0) cursor += column.gapBefore
                val span = SettlementRankingColumnSpan(left = cursor, width = column.width)
                cursor += column.width
                column.id to span
            }.toMap()
            return SettlementRankingLayout(totalWidth / 2f, spans)
        }

        /**
         * 兩個結算面板共用的排名欄位：名次、頭像、名稱、（選用的）狀態、分數、增減、（選用的）付款原因。
         *
         * [statusWidth] 為 `null` 時不含狀態欄；[paymentReasonWidth] 為 `null` 時不含付款原因欄。
         *
         * @param faceSize 頭像欄寬。
         * @param scoreWidth 分數欄寬。
         * @param deltaWidth 分數增減欄寬。
         * @param statusWidth 選用狀態欄寬；`null` 時省略。
         * @param paymentReasonWidth 選用付款原因欄寬；`null` 時省略。
         * @return 按標準順序排列的欄位定義。
         */
        fun standardColumns(
            faceSize: Float,
            scoreWidth: Float,
            deltaWidth: Float,
            statusWidth: Float? = null,
            paymentReasonWidth: Float? = null,
        ): List<SettlementRankingColumn> = listOfNotNull(
            SettlementRankingColumn(SettlementRankingColumnId.RANK, RANK_COLUMN_WIDTH, gapBefore = 0f),
            SettlementRankingColumn(SettlementRankingColumnId.FACE, faceSize, COLUMN_GAP),
            SettlementRankingColumn(SettlementRankingColumnId.NAME, NAME_MAX_WIDTH.toFloat(), COLUMN_GAP),
            statusWidth?.let { SettlementRankingColumn(SettlementRankingColumnId.STATUS, it, SECTION_GAP) },
            SettlementRankingColumn(SettlementRankingColumnId.SCORE, scoreWidth, SECTION_GAP),
            SettlementRankingColumn(SettlementRankingColumnId.DELTA, deltaWidth, SECTION_GAP),
            paymentReasonWidth?.let { SettlementRankingColumn(SettlementRankingColumnId.PAYMENT_REASON, it, SECTION_GAP) },
        )

        /**
         * 依內容寬度決定欄寬：最寬內容加上兩側 [padding]，但不小於 [minWidth]。
         *
         * 沒有任何內容時為 [minWidth]。
         *
         * @param contentWidths 各內容項目的量測寬度。
         * @param padding 欄位內容兩側各自增加的留白。
         * @param minWidth 欄位最小寬度。
         * @return 足以容納最寬內容且不小於下限的欄寬。
         */
        fun contentColumnWidth(
            contentWidths: Iterable<Float>,
            padding: Float,
            minWidth: Float,
        ): Float = maxOf(minWidth, (contentWidths.maxOrNull() ?: 0f) + padding * 2)

        /**
         * 分數增減的顯示文字：正數帶 `+`，零顯示 `±0`。
         *
         * @param delta 分數變化量。
         * @return 符合版面格式的增減文字。
         */
        fun formatDelta(delta: Int): String = when {
            delta > 0 -> "+$delta"
            delta < 0 -> delta.toString()
            else -> "±0"
        }
    }
}
