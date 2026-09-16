package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/** 結算排名列可出現的欄位。 */
enum class SettlementRankingColumnId {
    /** 名次，靠右對齊。 */
    RANK,

    /** 玩家頭像。 */
    FACE,

    /** 玩家名稱，靠左對齊。 */
    NAME,

    /** 結算狀態文字，置中對齊。 */
    STATUS,

    /** 分數，靠右對齊。 */
    SCORE,

    /** 分數增減，靠右對齊。 */
    DELTA,

    /** 付款原因文字，靠左對齊。 */
    PAYMENT_REASON,
}

/**
 * 排名列中的一欄。
 *
 * @property id 欄位識別。
 * @property width 欄位寬度。
 * @property gapBefore 與前一欄之間的間距；第一欄的值不使用。
 */
data class SettlementRankingColumn(
    val id: SettlementRankingColumnId,
    val width: Float,
    val gapBefore: Float,
)

/**
 * 一欄在面板座標中的水平範圍；面板以 x = 0 為中心。
 *
 * @property left 左緣。
 * @property width 寬度。
 */
data class SettlementRankingColumnSpan(
    val left: Float,
    val width: Float,
) {
    /** 右緣。 */
    val right: Float get() = left + width

    /** 水平中心。 */
    val centerX: Float get() = left + width / 2f
}

/**
 * 結算排名面板的水平版面：欄位由左至右依序排列，整體以 x = 0 置中。
 *
 * 欄寬在建立時就已決定，動畫途中分數變化不會改變面板尺寸。只處理數字；文字寬度由呼叫端量測後傳入。
 *
 * @property panelHalfWidth 面板半寬，含左右留白。
 */
class SettlementRankingLayout private constructor(
    val panelHalfWidth: Float,
    private val spans: Map<SettlementRankingColumnId, SettlementRankingColumnSpan>,
) {
    /** 取得指定欄位的範圍；欄位不在版面中時拋出例外。 */
    operator fun get(id: SettlementRankingColumnId): SettlementRankingColumnSpan = spans.getValue(id)

    /** 取得選用欄位的範圍；欄位不在版面中時為 `null`。 */
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
         */
        fun contentColumnWidth(
            contentWidths: Iterable<Float>,
            padding: Float,
            minWidth: Float,
        ): Float = maxOf(minWidth, (contentWidths.maxOrNull() ?: 0f) + padding * 2)

        /** 分數增減的顯示文字：正數帶 `+`，零顯示 `±0`。 */
        fun formatDelta(delta: Int): String = when {
            delta > 0 -> "+$delta"
            delta < 0 -> delta.toString()
            else -> "±0"
        }
    }
}
