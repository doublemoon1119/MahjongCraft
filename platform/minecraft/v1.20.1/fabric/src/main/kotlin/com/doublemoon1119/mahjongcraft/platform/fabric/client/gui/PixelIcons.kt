package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import net.minecraft.client.gui.DrawContext

/**
 * 用像素直接繪製的小圖示：選擇器箭頭與目前選項的打勾。
 *
 * 不使用文字符號，圖示的大小與粗細因此固定，不受目前語言或資源包使用的字型影響；筆畫粗細與遊戲文字相近，
 * 並和文字一樣在右下方帶一像素的陰影。
 */
internal object PixelIcons {
    /** 選擇器箭頭的寬度。 */
    const val ARROW_WIDTH: Int = 7

    /** 選擇器箭頭的高度。 */
    const val ARROW_HEIGHT: Int = 4

    /** 打勾的寬度。 */
    const val CHECK_WIDTH: Int = 7

    /** 打勾的高度。 */
    const val CHECK_HEIGHT: Int = 6

    /** 向下箭頭每一列的像素範圍：起點與長度，由上到下。 */
    private val ARROW_DOWN_ROWS: List<Pair<Int, Int>> = listOf(0 to 7, 1 to 5, 2 to 3, 3 to 1)

    /** 打勾每一列的像素範圍：起點與長度，由上到下；一列可以有多段。 */
    private val CHECK_ROWS: List<List<Pair<Int, Int>>> = listOf(
        listOf(6 to 1),
        listOf(5 to 2),
        listOf(0 to 1, 4 to 2),
        listOf(0 to 2, 3 to 2),
        listOf(1 to 3),
        listOf(2 to 1),
    )

    /**
     * 繪製選擇器的實心三角形箭頭。
     *
     * @param context 畫面繪製情境。
     * @param x 箭頭左界。
     * @param y 箭頭上界。
     * @param pointsUp 箭頭是否向上；否則向下。
     * @param color 不含透明度的 RGB 顏色。
     */
    fun arrow(context: DrawContext, x: Int, y: Int, pointsUp: Boolean, color: Int) {
        val rows = (if (pointsUp) ARROW_DOWN_ROWS.reversed() else ARROW_DOWN_ROWS).map(::listOf)
        draw(context, x, y, rows, color)
    }

    /**
     * 繪製打勾。
     *
     * @param context 畫面繪製情境。
     * @param x 打勾左界。
     * @param y 打勾上界。
     * @param color 不含透明度的 RGB 顏色。
     */
    fun check(context: DrawContext, x: Int, y: Int, color: Int) {
        draw(context, x, y, CHECK_ROWS, color)
    }

    /**
     * 先畫陰影再畫圖示本身。
     *
     * @param context 畫面繪製情境。
     * @param x 圖示左界。
     * @param y 圖示上界。
     * @param rows 每一列的像素範圍。
     * @param color 不含透明度的 RGB 顏色。
     */
    private fun draw(context: DrawContext, x: Int, y: Int, rows: List<List<Pair<Int, Int>>>, color: Int) {
        fillRows(context, x + 1, y + 1, rows, shadowOf(color))
        fillRows(context, x, y, rows, OPAQUE or color)
    }

    /**
     * 以單一顏色填滿每一列的像素範圍。
     *
     * @param context 畫面繪製情境。
     * @param x 圖示左界。
     * @param y 圖示上界。
     * @param rows 每一列的像素範圍。
     * @param argb 含透明度的顏色。
     */
    private fun fillRows(context: DrawContext, x: Int, y: Int, rows: List<List<Pair<Int, Int>>>, argb: Int) {
        rows.forEachIndexed { rowIndex, runs ->
            runs.forEach { (start, length) -> context.fill(x + start, y + rowIndex, x + start + length, y + rowIndex + 1, argb) }
        }
    }

    /**
     * 取得與遊戲文字陰影相同比例的陰影顏色：每個色版為原色的四分之一。
     *
     * @param color 不含透明度的 RGB 顏色。
     * @return 含透明度的陰影顏色。
     */
    private fun shadowOf(color: Int): Int = OPAQUE or ((color shr 2) and SHADOW_CHANNEL_MASK)

    /** 不透明的 alpha 色版。 */
    private const val OPAQUE: Int = 0xFF000000.toInt()

    /** 整個顏色右移兩位後，清除從相鄰色版移入的位元。 */
    private const val SHADOW_CHANNEL_MASK: Int = 0x3F3F3F
}
