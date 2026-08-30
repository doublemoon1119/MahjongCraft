package com.doublemoon1119.mahjongcraft.logic.module

/** 規則可安全公開給所有玩家與旁觀者的座位狀態。 */
sealed interface PublicPlayerIndicatorValue {
    /** 只有存在與否的公開標記。 */
    data object Marker : PublicPlayerIndicatorValue

    /** 非負整數次數。 */
    data class Count(val value: Int) : PublicPlayerIndicatorValue {
        init {
            require(value >= 0) { "Public player indicator count must not be negative" }
        }
    }

    /** 由規則定義、具完整 namespaced ID 的公開選項。 */
    data class Option(val optionId: String) : PublicPlayerIndicatorValue {
        init {
            require(':' in optionId) { "Public player indicator option ID must be namespaced: $optionId" }
        }
    }
}

/** 單一規則公開狀態；不得承載振聽、手牌內容等私人資訊。 */
data class PublicPlayerIndicator(
    val id: String,
    val indicatorValue: PublicPlayerIndicatorValue = PublicPlayerIndicatorValue.Marker,
) {
    init {
        require(':' in id) { "Public player indicator ID must be namespaced: $id" }
    }
}
