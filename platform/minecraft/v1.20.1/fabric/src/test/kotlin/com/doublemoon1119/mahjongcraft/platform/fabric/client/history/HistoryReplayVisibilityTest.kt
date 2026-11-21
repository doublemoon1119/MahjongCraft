package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** 驗證回放場景的顯示設定與人物不透明度。 */
class HistoryReplayVisibilityTest {
    /** 預設人物自動、面板隱藏、桌上物件與胡牌特效顯示；改過後恢復預設值。 */
    @Test
    fun `defaults and reset`() {
        val visibility = HistoryReplayVisibility()
        assertTrue(visibility.isDefault)
        assertEquals(HistoryReplayVisibilityOption.AUTO, visibility.figure(2))
        assertFalse(visibility.playerInfo(2))
        assertFalse(visibility.roundInfo)
        assertTrue(visibility.tableProps)
        assertTrue(visibility.winEffect)

        visibility.setFigure(2, HistoryReplayVisibilityOption.HIDDEN)
        visibility.setPlayerInfo(1, true)
        visibility.roundInfo = true
        visibility.tableProps = false
        visibility.winEffect = false
        assertFalse(visibility.isDefault)
        assertEquals(HistoryReplayVisibilityOption.HIDDEN, visibility.figure(2))
        assertTrue(visibility.playerInfo(1))

        visibility.reset()
        assertTrue(visibility.isDefault)
        assertTrue(visibility.winEffect)
        assertEquals(HistoryReplayVisibilityOption.AUTO, visibility.figure(2))
    }

    /** 改回預設值的項目不算改過。 */
    @Test
    fun `setting a default value counts as default`() {
        val visibility = HistoryReplayVisibility()
        visibility.setFigure(0, HistoryReplayVisibilityOption.SHOWN)
        visibility.setFigure(0, HistoryReplayVisibilityOption.AUTO)
        visibility.setPlayerInfo(0, true)
        visibility.setPlayerInfo(0, false)
        assertTrue(visibility.isDefault)
    }

    /** 自動時只有鏡頭那一側的座位半透明；視角所在的那一家一律不畫。 */
    @Test
    fun `auto makes the camera side translucent`() {
        // 鏡頭角度是某一家的座位朝向轉半圈時，鏡頭在那一家後方。
        val seatYaw = 90f
        val behind = seatYaw - 180f
        val auto = HistoryReplayVisibilityOption.AUTO
        assertEquals(HistoryReplayFigureOpacity.TRANSLUCENT, HistoryReplayFigureOpacity.target(auto, viewed = false, seatYaw = seatYaw, cameraYaw = behind))
        assertEquals(HistoryReplayFigureOpacity.TRANSLUCENT, HistoryReplayFigureOpacity.target(auto, viewed = false, seatYaw = seatYaw, cameraYaw = behind + 45f))
        assertEquals(1f, HistoryReplayFigureOpacity.target(auto, viewed = false, seatYaw = seatYaw, cameraYaw = behind + 90f))
        assertEquals(1f, HistoryReplayFigureOpacity.target(auto, viewed = false, seatYaw = seatYaw, cameraYaw = seatYaw))
        assertEquals(0f, HistoryReplayFigureOpacity.target(HistoryReplayVisibilityOption.SHOWN, viewed = true, seatYaw = seatYaw, cameraYaw = behind))
    }

    /** 固定的顯示方式不看鏡頭方向。 */
    @Test
    fun `fixed choices ignore the camera`() {
        assertEquals(1f, HistoryReplayFigureOpacity.target(HistoryReplayVisibilityOption.SHOWN, viewed = false, seatYaw = 0f, cameraYaw = -180f))
        assertEquals(HistoryReplayFigureOpacity.TRANSLUCENT, HistoryReplayFigureOpacity.target(HistoryReplayVisibilityOption.TRANSLUCENT, viewed = false, seatYaw = 0f, cameraYaw = 0f))
        assertEquals(0f, HistoryReplayFigureOpacity.target(HistoryReplayVisibilityOption.HIDDEN, viewed = false, seatYaw = 0f, cameraYaw = 0f))
    }

    /** 淡入淡出以固定速度前進，不越過目標。 */
    @Test
    fun `fading moves at a fixed rate`() {
        val half = HistoryReplayFigureOpacity.FADE / 2
        assertEquals(0.5f, HistoryReplayFigureOpacity.approach(current = 1f, target = 0f, elapsed = half), 0.0001f)
        assertEquals(0.5f, HistoryReplayFigureOpacity.approach(current = 0f, target = 1f, elapsed = half), 0.0001f)
        assertEquals(0.3f, HistoryReplayFigureOpacity.approach(current = 0.4f, target = 0.3f, elapsed = 500.milliseconds))
    }
}
