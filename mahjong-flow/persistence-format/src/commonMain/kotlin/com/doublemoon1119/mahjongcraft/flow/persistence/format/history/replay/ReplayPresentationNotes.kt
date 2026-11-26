package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.serialization.json.JsonObject

/**
 * 牌譜保存每份桌況時，由外層附上的呈現附註。
 *
 * 附註的內容由外層定義，例如桌上物件占用的範圍。牌譜原樣保存，讀取時原樣交回，不解讀內容，也不轉換其中的值。
 * 附註不可包含牌或玩家的 UUID。
 */
fun interface ReplayPresentationNotes {
    /**
     * 取得一份桌況的呈現附註。
     *
     * @param state 交易完成後的權威桌況。
     * @return 附註。沒有附註時為 null。
     */
    fun notesFor(state: TableState): JsonObject?

    /** 預設的附註來源。 */
    companion object {
        /** 不附上任何附註。 */
        val NONE: ReplayPresentationNotes = ReplayPresentationNotes { null }
    }
}
