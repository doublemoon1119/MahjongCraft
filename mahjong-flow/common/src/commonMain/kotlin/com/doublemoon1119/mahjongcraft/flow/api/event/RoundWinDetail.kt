package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 一名玩家在一組和牌結果中的公開詳情。
 *
 * @property playerId 和牌玩家 UUID。
 * @property method 和牌方式。
 * @property detailFields 依規則定義分組的公開和牌詳情。
 * @property publicMarkers 和牌當下的公開狀態標記。
 */
class RoundWinDetail private constructor(
    val playerId: Uuid,
    val method: WinMethod,
    val detailFields: List<WinDetailField>,
    val publicMarkers: List<PublicEventMarker>,
) {
    /** 事件系統內部建立資料的入口。 */
    internal companion object {
        /**
         * 建立玩家和牌詳情，並複製清單。
         *
         * @param playerId 和牌玩家 UUID。
         * @param method 和牌方式。
         * @param detailFields 公開和牌詳情欄位。
         * @param publicMarkers 和牌當下的公開狀態標記。
         * @return 建立的玩家和牌詳情。
         */
        @JvmSynthetic
        fun create(
            playerId: Uuid,
            method: WinMethod,
            detailFields: List<WinDetailField>,
            publicMarkers: List<PublicEventMarker>,
        ): RoundWinDetail = RoundWinDetail(
            playerId = playerId,
            method = method,
            detailFields = ReadOnlyListSnapshot(detailFields),
            publicMarkers = ReadOnlyListSnapshot(publicMarkers),
        )
    }
}
