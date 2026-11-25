package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

/** 清單內容區底的上下界：標題下方到底部按鈕列上方，與表單畫面的版面一致。 */
internal object ContentBackground {
    /** 內容區底的上界，在標題下方。 */
    const val TOP: Int = FormScreenLayout.TITLE_BOTTOM - GAP

    /**
     * 內容區底的下界，在底部按鈕列上方。
     *
     * @param windowHeight 畫面高度。
     * @return 下界的 Y 座標。
     */
    fun bottom(windowHeight: Int): Int = windowHeight - FormScreenLayout.FOOTER_BOTTOM_MARGIN - FormScreenLayout.BUTTON_HEIGHT - GAP
}

/** 內容區底與標題、底部按鈕列之間的距離。 */
private const val GAP: Int = 4
