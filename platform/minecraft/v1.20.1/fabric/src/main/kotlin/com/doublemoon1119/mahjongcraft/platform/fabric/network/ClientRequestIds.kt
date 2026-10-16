package com.doublemoon1119.mahjongcraft.platform.fabric.network

/**
 * 客戶端為配對回覆而附上的識別字串規則；伺服器會在回覆中原樣送回這些字串，因此一律限制長度。
 */
internal object ClientRequestIds {
    /** 識別字串的長度上限。 */
    const val MAX_LENGTH: Int = 64

    /**
     * 判斷識別字串可否接受。
     *
     * @param id 客戶端送來的識別字串。
     * @return 不是空白且不超過 [MAX_LENGTH] 時為 true。
     */
    fun isValid(id: String): Boolean = id.isNotBlank() && id.length <= MAX_LENGTH
}
