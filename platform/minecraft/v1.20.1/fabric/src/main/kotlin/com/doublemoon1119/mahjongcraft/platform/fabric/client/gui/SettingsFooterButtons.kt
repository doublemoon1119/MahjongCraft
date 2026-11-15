package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

/** 設定頁底部操作列的按鈕順序：重設為預設、復原、套用、完成，由左至右排列。 */
internal object SettingsFooterButtons {
    /** 按鈕數量。 */
    const val COUNT: Int = 4

    /** 重設為預設按鈕的順序。 */
    const val RESET: Int = 0

    /** 復原按鈕的順序。 */
    const val UNDO: Int = 1

    /** 套用按鈕的順序。 */
    const val APPLY: Int = 2

    /** 完成或返回按鈕的順序。 */
    const val DONE: Int = 3
}
