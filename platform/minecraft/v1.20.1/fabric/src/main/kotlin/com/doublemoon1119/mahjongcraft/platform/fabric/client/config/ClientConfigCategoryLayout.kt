package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

/** 小畫面分類按鈕排數與欄位起點；欄位從最後一排按鈕下方開始。 */
internal object ClientConfigCategoryLayout {
    /** 計算兩欄分類按鈕佔用的排數。 */
    fun compactRowCount(categoryCount: Int): Int = (categoryCount + 1) / 2

    /** 由面板頂端計算小畫面欄位起點。 */
    fun compactFieldsTop(panelTop: Int, categoryCount: Int): Int = panelTop + CATEGORY_TOP_OFFSET +
        compactRowCount(categoryCount) * CATEGORY_ROW_HEIGHT + FIELDS_GAP

    /** 分類按鈕相對面板上緣的起點。 */
    const val CATEGORY_TOP_OFFSET: Int = 42

    /** 分類按鈕每排的垂直間距。 */
    const val CATEGORY_ROW_HEIGHT: Int = 25

    /** 最後一排按鈕與欄位之間的額外間距。 */
    const val FIELDS_GAP: Int = 4
}
