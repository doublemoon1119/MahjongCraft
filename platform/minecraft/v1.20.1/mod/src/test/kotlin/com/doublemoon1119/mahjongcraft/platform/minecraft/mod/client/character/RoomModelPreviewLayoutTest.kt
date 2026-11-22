package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character

import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 對照原有玩家呼叫的固定參數，避免模型顯示統一時改掉原版面。 */
class RoomModelPreviewLayoutTest {
    /** 卡片中心、腳底基線及游標參考點都與原有玩家呼叫相同。 */
    @Test
    fun `player placement matches the original room preview`() {
        assertEquals(
            expected = RoomModelPreviewLayout(155, 124, 36, 35f, 12f),
            actual = RoomModelPreviewLayout.forCard(area(left = 100, top = 40, width = 110), 120, 70),
        )
    }

    /** 不同視窗寬度仍使用原有玩家縮放上限與下限。 */
    @Test
    fun `player scale preserves the original limits`() {
        assertEquals(24, RoomModelPreviewLayout.forCard(area(left = 0, top = 0, width = 60), 0, 0).scale)
        assertEquals(30, RoomModelPreviewLayout.forCard(area(left = 0, top = 0, width = 90), 0, 0).scale)
        assertEquals(36, RoomModelPreviewLayout.forCard(area(left = 0, top = 0, width = 180), 0, 0).scale)
    }

    /** 非生物也使用原版預覽的 Z 軸翻轉，不能把模型背面當成正面。 */
    @Test
    fun `generic entity preview preserves vanilla coordinate handedness`() {
        val view = RoomModelPreviewLayout.forCard(area(left = 0, top = 0, width = 108), 54, 42)
        val transform = view.scaleMatrix(view.scale).rotate(view.modelRotation())
        val right = transform.transformDirection(Vector3f(1f, 0f, 0f))
        val up = transform.transformDirection(Vector3f(0f, 1f, 0f))
        val front = transform.transformDirection(Vector3f(0f, 0f, 1f))
        assertTrue(right.x < 0f, "Preview must match vanilla horizontal orientation")
        assertTrue(up.y < 0f, "Model up must point toward the top of the screen")
        assertTrue(front.z < 0f, "Preview must preserve vanilla front-to-back orientation")
        assertEquals(180f, view.bodyYaw())
    }

    /**
     * 建立未放大的卡片人物範圍，腳底在卡片頂端下方 84。
     *
     * @param left 卡片左界。
     * @param top 卡片頂端。
     * @param width 卡片寬度。
     * @return 放大倍數為 1 的人物範圍。
     */
    private fun area(left: Int, top: Int, width: Int) = RoomFigureArea(left = left, top = top, width = width, feetY = top + 84, scale = 1f)
}
