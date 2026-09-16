package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.ShowcaseTimelineCurves.BOB_HEIGHT
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.ShowcaseTimelineCurves.BOB_SPEED
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.ShowcaseTimelineCurves.ELYTRA_APPEAR_TICK
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.ShowcaseTimelineCurves.ELYTRA_FADE_START_TICK
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.ShowcaseTimelineCurves.ELYTRA_OPEN_TICK
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.ShowcaseTimelineCurves.EQUIPMENT_FADE_END_TICK
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationShowcaseEntity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證役滿 showcase 的時間軸曲線。 */
class ShowcaseTimelineCurvesTest {
    /** 淡出開始前維持原尺寸，結束後完全消失，中間是一半。 */
    @Test
    fun `fades out over the fade out ticks`() {
        val fadeStart = 400.0
        val fadeTicks = WinCelebrationShowcaseEntity.FADE_OUT_TICKS.toDouble()

        assertEquals(1.0, ShowcaseTimelineCurves.fadeScale(elapsed = 100.0, fadeStart = fadeStart))
        assertEquals(1.0, ShowcaseTimelineCurves.fadeScale(elapsed = fadeStart, fadeStart = fadeStart))
        assertEquals(0.5, ShowcaseTimelineCurves.fadeScale(elapsed = fadeStart + fadeTicks / 2, fadeStart = fadeStart), TOLERANCE)
        assertEquals(0.0, ShowcaseTimelineCurves.fadeScale(elapsed = fadeStart + fadeTicks, fadeStart = fadeStart))
        assertEquals(0.0, ShowcaseTimelineCurves.fadeScale(elapsed = fadeStart + fadeTicks * 3, fadeStart = fadeStart))
    }

    /** 淡出過程中只會變小。 */
    @Test
    fun `never grows while fading out`() {
        val samples = (0..60).map { ShowcaseTimelineCurves.fadeScale(elapsed = 390.0 + it * 0.5, fadeStart = 400.0) }

        assertTrue(samples.zipWithNext().all { (earlier, later) -> later <= earlier })
    }

    /** 起飛裝備在抵達後的區間內淡出。 */
    @Test
    fun `hides the equipment after arrival`() {
        val middle = (ELYTRA_FADE_START_TICK + EQUIPMENT_FADE_END_TICK) / 2

        assertEquals(1.0, ShowcaseTimelineCurves.equipmentVisibility(ELYTRA_FADE_START_TICK - 1))
        assertEquals(1.0, ShowcaseTimelineCurves.equipmentVisibility(ELYTRA_FADE_START_TICK))
        assertEquals(0.5, ShowcaseTimelineCurves.equipmentVisibility(middle), TOLERANCE)
        assertEquals(0.0, ShowcaseTimelineCurves.equipmentVisibility(EQUIPMENT_FADE_END_TICK))
        assertEquals(0.0, ShowcaseTimelineCurves.equipmentVisibility(EQUIPMENT_FADE_END_TICK + 50))
    }

    /** 起飛裝備由 0 長到 1，前段長得快。 */
    @Test
    fun `grows the equipment in before the elytra opens`() {
        val middle = (ELYTRA_APPEAR_TICK + ELYTRA_OPEN_TICK) / 2

        assertEquals(0.0, ShowcaseTimelineCurves.equipmentAppearance(0.0))
        assertEquals(0.0, ShowcaseTimelineCurves.equipmentAppearance(ELYTRA_APPEAR_TICK))
        assertEquals(0.75, ShowcaseTimelineCurves.equipmentAppearance(middle), TOLERANCE)
        assertEquals(1.0, ShowcaseTimelineCurves.equipmentAppearance(ELYTRA_OPEN_TICK))
        assertEquals(1.0, ShowcaseTimelineCurves.equipmentAppearance(ELYTRA_OPEN_TICK + 100))
    }

    /** 晃動幅度隨淡出係數縮小。 */
    @Test
    fun `scales the bob with the fade`() {
        val peak = PI / 2 / BOB_SPEED

        assertEquals(BOB_HEIGHT, ShowcaseTimelineCurves.winningTileBobOffset(peak, fade = 1.0), TOLERANCE)
        assertEquals(BOB_HEIGHT / 2, ShowcaseTimelineCurves.winningTileBobOffset(peak, fade = 0.5), TOLERANCE)
        assertEquals(0.0, ShowcaseTimelineCurves.winningTileBobOffset(peak, fade = 0.0))
    }

    /** 晃動最低點時距離最低點為 0，前後分別為負值與正值。 */
    @Test
    fun `measures the ticks from the bottom of the bob`() {
        val bottom = PI * 1.5 / BOB_SPEED

        assertEquals(-BOB_HEIGHT, ShowcaseTimelineCurves.winningTileBobOffset(bottom, fade = 1.0), TOLERANCE)
        assertEquals(0.0, ShowcaseTimelineCurves.winningTileTicksFromBottom(bottom), TOLERANCE)
        assertEquals(-2.0, ShowcaseTimelineCurves.winningTileTicksFromBottom(bottom - 2.0), TOLERANCE)
        assertEquals(3.0, ShowcaseTimelineCurves.winningTileTicksFromBottom(bottom + 3.0), TOLERANCE)
    }

    /** 每個晃動週期都回到同一個最低點，數值不會超過半個週期。 */
    @Test
    fun `wraps the ticks from the bottom every period`() {
        val period = PI * 2 / BOB_SPEED
        val bottom = PI * 1.5 / BOB_SPEED

        assertEquals(0.0, ShowcaseTimelineCurves.winningTileTicksFromBottom(bottom + period * 7), 1e-6)
        assertEquals(1.0, ShowcaseTimelineCurves.winningTileTicksFromBottom(bottom + period * 3 + 1.0), 1e-6)
        assertTrue((0..500).all { abs(ShowcaseTimelineCurves.winningTileTicksFromBottom(it * 0.37)) <= period / 2 + TOLERANCE })
    }

    /** 引信剛點燃時亮一下，隨即暗下。 */
    @Test
    fun `flashes once as the fuse is lit`() {
        assertTrue(ShowcaseTimelineCurves.tntFlashVisible(0.0))
        assertFalse(ShowcaseTimelineCurves.tntFlashVisible(0.1))
    }

    /** 越接近爆炸，閃白的時間占比越高。 */
    @Test
    fun `flashes more often near the explosion`() {
        val early = flashRatio(0.0..0.3)
        val late = flashRatio(0.7..1.0)

        assertTrue(late > early * 2, "Expected the late flash ratio $late to be well above the early ratio $early.")
    }

    /** 爆炸前的最後階段，每個週期大部分時間都亮著，而不只是閃得更頻繁。 */
    @Test
    fun `stays lit for most of each cycle before the explosion`() {
        val late = flashRatio(0.7..1.0)

        assertTrue(late > 0.6, "Expected the flash to stay lit most of the time, got $late.")
    }

    /** 平滑曲線與緩出曲線的端點與中點。 */
    @Test
    fun `keeps the easing endpoints`() {
        assertEquals(0.0, ShowcaseTimelineCurves.smoothStep(0.0))
        assertEquals(0.5, ShowcaseTimelineCurves.smoothStep(0.5))
        assertEquals(1.0, ShowcaseTimelineCurves.smoothStep(1.0))
        assertEquals(0.0, ShowcaseTimelineCurves.easeOut(0.0))
        assertEquals(0.75, ShowcaseTimelineCurves.easeOut(0.5))
        assertEquals(1.0, ShowcaseTimelineCurves.easeOut(1.0))
        assertEquals(2.5, ShowcaseTimelineCurves.lerp(start = 2.0, end = 4.0, progress = 0.25))
    }

    /** 引信進度在 [range] 內均勻取樣時，閃白的樣本比例。 */
    private fun flashRatio(range: ClosedFloatingPointRange<Double>): Double {
        val samples = (0..1000).map { range.start + (range.endInclusive - range.start) * it / 1000 }
        return samples.count(ShowcaseTimelineCurves::tntFlashVisible).toDouble() / samples.size
    }

    private companion object {
        /** 浮點比較的容許誤差。 */
        const val TOLERANCE = 1e-9
    }
}
