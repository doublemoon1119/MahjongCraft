package com.doublemoon1119.mahjongcraft.flow.api.event

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.jvm.JvmSynthetic

/**
 * 公開事件所帶的本局基本位置資訊。
 *
 * @property roundNumber 這一局在場次中的局數，從 1 開始。
 * @property prevalentWindId 本局場風的完整 namespaced ID；規則不提供時為 `null`。
 */
class EventRoundContext private constructor(
    val roundNumber: Int,
    val prevalentWindId: String?,
) {
    init {
        require(roundNumber > 0) { "Round number must be positive: $roundNumber" }
        prevalentWindId?.let { windId ->
            NamespacedId.requireValid(windId) { "Prevalent wind ID must be namespaced: $windId" }
        }
    }

    /** 事件系統內部建立資料的入口。 */
    internal companion object {
        /**
         * 建立本局資訊。
         *
         * @param roundNumber 這一局在場次中的局數。
         * @param prevalentWindId 本局場風的完整 namespaced ID；沒有時為 `null`。
         * @return 建立的本局資訊。
         */
        @JvmSynthetic
        fun create(roundNumber: Int, prevalentWindId: String?): EventRoundContext = EventRoundContext(roundNumber, prevalentWindId)
    }
}
