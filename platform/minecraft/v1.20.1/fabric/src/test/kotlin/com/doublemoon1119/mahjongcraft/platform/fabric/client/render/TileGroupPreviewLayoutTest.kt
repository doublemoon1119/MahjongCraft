package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTileOrientationDto
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** 驗證橫向牌組排列中，疊放牌不會跟橫置錨點牌重疊。 */
class TileGroupPreviewLayoutTest {
    /**
     * 加槓補上的第 4 張牌與副露來源的橫置錨點牌都是橫置方向，旋轉 90 度後螢幕高度等於原本的牌寬，
     * 疊放距離必須依此換算，不能沿用直立牌高度算出的固定間距，否則兩張牌會互相重疊（曾經發生過的
     * 問題）。
     */
    @Test
    fun `stacked sideways tile does not overlap a sideways anchor`() {
        val entries = listOf(
            TileGroupPreviewEntry("m1", DecisionTileOrientationDto.UPRIGHT, stacked = false),
            TileGroupPreviewEntry("m2", DecisionTileOrientationDto.ROTATED_RIGHT, stacked = false),
            TileGroupPreviewEntry("m3", DecisionTileOrientationDto.UPRIGHT, stacked = false),
            TileGroupPreviewEntry("m2", DecisionTileOrientationDto.ROTATED_RIGHT, stacked = true),
        )

        val layout = TileGroupPreviewLayoutCalculator.calculate(entries, tileWidth = 20f, tileHeight = 28f, gap = 2f)

        val anchor = layout.placements[1]
        val stacked = layout.placements.last()
        val minimumSeparation = 20f / 2f + 20f / 2f
        assertTrue(
            anchor.centerY - stacked.centerY >= minimumSeparation,
            "Stacked tile must clear the sideways anchor's rotated on-screen height, " +
                "was ${anchor.centerY - stacked.centerY}, needed >= $minimumSeparation",
        )
    }

    /**
     * 呼叫端（[MahjongTileEntityRenderer]）把半透明背景畫在「內容垂直置中於 0」的假設下；加槓疊放牌
     * 只往其中一側延伸，實際內容的垂直範圍天生不會對稱分佈在 0 兩側，[calculate] 必須自行平移每張牌
     * 的 `centerY`，否則背景會跟牌面對不齊（曾經發生過的問題）。
     */
    @Test
    fun `content is vertically centered on zero even with an asymmetric stack`() {
        val entries = listOf(
            TileGroupPreviewEntry("m1", DecisionTileOrientationDto.UPRIGHT, stacked = false),
            TileGroupPreviewEntry("m2", DecisionTileOrientationDto.ROTATED_RIGHT, stacked = false),
            TileGroupPreviewEntry("m3", DecisionTileOrientationDto.UPRIGHT, stacked = false),
            TileGroupPreviewEntry("m2", DecisionTileOrientationDto.ROTATED_RIGHT, stacked = true),
        )

        val layout = TileGroupPreviewLayoutCalculator.calculate(entries, tileWidth = 20f, tileHeight = 28f, gap = 2f)

        fun onScreenHalfHeight(orientation: DecisionTileOrientationDto) = if (orientation == DecisionTileOrientationDto.UPRIGHT) 28f / 2f else 20f / 2f
        val minimumY = layout.placements.minOf { it.centerY - onScreenHalfHeight(it.orientation) }
        val maximumY = layout.placements.maxOf { it.centerY + onScreenHalfHeight(it.orientation) }
        assertTrue(abs(minimumY + maximumY) < 0.001f, "Content must be vertically centered on zero, was minimumY=$minimumY maximumY=$maximumY")
    }
}
