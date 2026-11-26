package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState

/** 本桌全部等待席位的檢查結果，不依名冊編號推測實體已離座。 */
enum class SeatedParticipantPresence {
    /** 已確認坐在本桌其中一個席位。 */
    PRESENT,

    /** 所有席位都確認沒有由本人乘坐。 */
    DEPARTED,

    /** 席位資料缺漏或提供者失敗，不能據此移除成員。 */
    UNKNOWN,
    ;

    /** 合併本桌的唯讀席位檢查。 */
    companion object {
        /**
         * 檢查完整席位集合，不把來源失敗當成離座。
         *
         * @param states 本桌全部標準等待席位的查詢結果。
         * @return 在席、離座或無法確認。
         */
        fun resolve(states: List<SeatState>): SeatedParticipantPresence = when {
            states.isEmpty() || SeatState.FAILED in states -> UNKNOWN
            SeatState.SEATED in states -> PRESENT
            else -> DEPARTED
        }
    }
}
