package com.doublemoon1119.mahjongcraft.flow.api.event

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.jvm.JvmSynthetic

/**
 * 一組有名稱的公開和牌詳情。
 *
 * @property id 詳情欄位的完整 namespaced ID。
 * @property entries 這個欄位中的公開標記項目。
 * @property quantities 這個欄位中的帶單位數值，保留原本的分組關係。
 */
class WinDetailField private constructor(
    val id: String,
    val entries: List<PublicEventMarker>,
    val quantities: List<EventQuantity>,
) {
    init {
        NamespacedId.requireValid(id) { "Win detail field ID must be namespaced: $id" }
    }

    /** 事件系統內部建立資料的入口。 */
    internal companion object {
        /**
         * 建立和牌詳情欄位，並複製兩組清單。
         *
         * @param id 詳情欄位的完整 namespaced ID。
         * @param entries 欄位中的公開標記。
         * @param quantities 欄位中的帶單位數值。
         * @return 建立的詳情欄位。
         */
        @JvmSynthetic
        fun create(
            id: String,
            entries: List<PublicEventMarker>,
            quantities: List<EventQuantity>,
        ): WinDetailField = WinDetailField(
            id = id,
            entries = ReadOnlyListSnapshot(entries),
            quantities = ReadOnlyListSnapshot(quantities),
        )
    }
}
