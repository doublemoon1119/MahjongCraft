package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import kotlin.math.roundToInt

/**
 * 房間成員卡片上畫人物的範圍：卡片左右界、卡片頂端、腳底基線與卡片放大倍數。
 *
 * 模型與頭像都依同一個放大倍數變大，但不會超出卡片寬度或卡片頂端；放大倍數為 1 時與未放大的卡片相同。
 *
 * @property left 卡片左界。
 * @property top 卡片頂端。
 * @property width 卡片寬度。
 * @property feetY 人物腳底基線。
 * @property scale 卡片縮放倍數。
 */
internal data class RoomFigureArea(
    val left: Int,
    val top: Int,
    val width: Int,
    val feetY: Int,
    val scale: Float,
) {
    /** 卡片水平中心。 */
    val centerX: Int = left + width / 2

    /** 人物可用的最大寬度，扣掉卡片底色的內縮與左右邊距。 */
    private val maximumFigureWidth: Int = width - FIGURE_SIDE_MARGIN * 2

    /** 人物可用的最大高度，從卡片頂端邊距到腳底。 */
    private val maximumFigureHeight: Int = feetY - top - FIGURE_TOP_MARGIN

    /**
     * 未放大時的模型比例，依卡片寬度決定。
     */
    private val baseModelScale: Int = (width / 3).coerceIn(MINIMUM_MODEL_SCALE, MAXIMUM_MODEL_SCALE)

    /**
     * 模型的每單位繪製比例：未放大比例乘上放大倍數，再限制模型在卡片寬度與卡片頂端之內；不小於未放大比例。
     */
    val modelScale: Int = minOf((baseModelScale * scale).roundToInt(), (minOf(maximumFigureWidth, maximumFigureHeight) / MODEL_EXTENT).toInt())
        .coerceAtLeast(0)

    /** 未放大時的頭像邊長，依卡片寬度決定。 */
    private val basePortraitSize: Int = (width - PORTRAIT_WIDTH_INSET).coerceIn(MINIMUM_PORTRAIT_SIZE, MAXIMUM_PORTRAIT_SIZE)

    /** 頭像與腳底基線的距離。 */
    private val portraitGap: Int = (PORTRAIT_FEET_GAP * scale).roundToInt()

    /** 頭像邊長：未放大邊長乘上放大倍數，再限制在卡片寬度與卡片頂端之內；不小於未放大邊長。 */
    val portraitSize: Int = minOf((basePortraitSize * scale).roundToInt(), maximumFigureWidth, maximumFigureHeight - portraitGap)
        .coerceAtLeast(0)

    /** 頭像左界，在卡片內水平置中。 */
    val portraitLeft: Int = left + (width - portraitSize) / 2

    /** 頭像頂端，頭像底端與腳底基線保持固定距離。 */
    val portraitTop: Int = feetY - portraitGap - portraitSize

    /** 模型視線參考點，在腳底上方。 */
    val lookY: Int = feetY - (LOOK_HEIGHT * scale).roundToInt()

    internal companion object {
        /** 卡片底色內縮加上人物左右邊距。 */
        private const val FIGURE_SIDE_MARGIN = 7

        /** 人物頂端與卡片頂端至少保留的距離。 */
        private const val FIGURE_TOP_MARGIN = 4

        /** 模型在畫面上佔用的最大長度，以方塊為單位；非玩家模型的最長邊也縮放到這個長度以內。 */
        const val MODEL_EXTENT = 1.8f

        /** 未放大時的最小模型比例。 */
        private const val MINIMUM_MODEL_SCALE = 24

        /** 未放大時的最大模型比例。 */
        private const val MAXIMUM_MODEL_SCALE = 36

        /** 計算未放大頭像邊長時從卡片寬度扣掉的寬度。 */
        private const val PORTRAIT_WIDTH_INSET = 24

        /** 未放大時的最小頭像邊長。 */
        private const val MINIMUM_PORTRAIT_SIZE = 28

        /** 未放大時的最大頭像邊長。 */
        private const val MAXIMUM_PORTRAIT_SIZE = 38

        /** 未放大時頭像底端與腳底基線的距離。 */
        private const val PORTRAIT_FEET_GAP = 22

        /** 未放大時視線參考點在腳底上方的高度。 */
        private const val LOOK_HEIGHT = 42
    }
}
