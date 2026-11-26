package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** 規則可安全公開給所有玩家與旁觀者的座位狀態。 */
sealed interface PublicPlayerIndicatorValue {
    /** 只有存在與否的公開標記。 */
    data object Marker : PublicPlayerIndicatorValue

    /**
     * 非負整數次數。
     *
     * @property value 公開狀態的非負次數。
     */
    data class Count(val value: Int) : PublicPlayerIndicatorValue {
        init {
            require(value >= 0) { "Public player indicator count must not be negative" }
        }
    }

    /**
     * 由規則定義、具完整 namespaced ID 的公開選項。
     *
     * @property optionId 規則定義的公開選項識別碼。
     */
    data class Option(val optionId: String) : PublicPlayerIndicatorValue {
        init {
            NamespacedId.requireValid(optionId) { "Public player indicator option ID must be namespaced: $optionId" }
        }
    }
}
