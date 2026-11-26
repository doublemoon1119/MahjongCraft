package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 牌牆路徑規劃的強型別結果。 */
sealed interface TileWallMotionPathDecision {
    /**
     * 已成功建立路徑。
     *
     * @property segments 依播放順序排列的非空路徑段。
     */
    data class Completed(val segments: List<TileWallMotionSegment>) : TileWallMotionPathDecision {
        init {
            require(segments.isNotEmpty()) { "Completed wall motion path must contain at least one segment" }
        }
    }

    /**
     * 輸入無法用安全的單面或相鄰牆角路徑表達。
     *
     * @property reason 可供平台診斷的穩定原因。
     */
    data class Rejected(val reason: TileWallMotionPathRejection) : TileWallMotionPathDecision
}
