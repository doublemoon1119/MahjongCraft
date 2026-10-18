package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 一次牌張運動動畫的可調整參數；牌牆生成掉落、發牌、摸牌共用同一個 [TileMotionAnimation]，各自傳入
 * 不同的 spec——牌牆生成／發牌起訖姿態相同（[startPoseRotationDegrees] 等於 [endPoseRotationDegrees]，
 * 只有位置在動），摸牌才需要非零的姿態旋轉內插（牌背朝上→直立）。
 *
 * @property durationTicks 動畫總長度，以 server ticks 表示。
 * @property arcHeight 拋物線最高額外高度；牌牆生成可設 `0.0` 走純直落，不套用拋物線。
 * @property startPoseRotationDegrees 起始姿態的局部 X 軸旋轉角，取代靜態的 `tilePose.rotationDegrees`。
 * @property endPoseRotationDegrees 結束姿態的局部 X 軸旋轉角。
 * @property easeRotation 姿態旋轉角是否也套用跟位移相同的 ease-out 曲線。
 */
data class TileMotionAnimationSpec(
    val durationTicks: Int = DEFAULT_DURATION_TICKS,
    val arcHeight: Double = 0.0,
    val startPoseRotationDegrees: Float,
    val endPoseRotationDegrees: Float,
    val easeRotation: Boolean = false,
) {
    init {
        require(durationTicks > 0) { "durationTicks must be positive" }
        require(arcHeight >= 0.0) { "arcHeight must not be negative" }
    }

    companion object {
        /**
         * 預設動畫總長度，約為正常 TPS 下的 0.25 秒；牌牆生成掉落動畫經過遊戲內多次比對後持續調快，
         * 從原本 15（0.75 秒）先調到 8（0.4 秒）、再調到現在的值——擲骰子動畫改成等牌牆完全
         * 落地後才開始播放（見開局擲骰呈現），牌牆這段時間會
         * 直接疊加進開局的總等待時間，比過去兩者平行播放時更值得壓縮。
         */
        const val DEFAULT_DURATION_TICKS = 5
    }
}
