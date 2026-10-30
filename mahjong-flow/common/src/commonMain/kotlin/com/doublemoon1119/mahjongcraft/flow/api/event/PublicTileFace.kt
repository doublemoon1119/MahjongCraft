package com.doublemoon1119.mahjongcraft.flow.api.event

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.jvm.JvmSynthetic

/**
 * 動作事件中可公開的一張牌面。
 *
 * @property typeId 牌面的完整 namespaced ID；擴充牌沿用牌種 ID，標準牌使用事件定義的牌面 ID，不包含實體牌 UUID。
 */
class PublicTileFace private constructor(
    val typeId: String,
) {
    init {
        NamespacedId.requireValid(typeId) { "Public tile type ID must be namespaced: $typeId" }
    }

    /** 事件系統內部建立資料的入口。 */
    internal companion object {
        /**
         * 建立公開牌面。
         *
         * @param typeId 牌種的完整 namespaced ID。
         * @return 建立的公開牌面。
         */
        @JvmSynthetic
        fun create(typeId: String): PublicTileFace = PublicTileFace(typeId)
    }
}
