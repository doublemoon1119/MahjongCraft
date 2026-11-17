package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證房間卡片人物依卡片放大倍數變大，且不超出卡片。 */
class RoomFigureAreaTest {
    /** 放大倍數為 1 時，模型與頭像維持未放大卡片的大小與位置。 */
    @Test
    fun `keeps the unscaled figure at scale one`() {
        val area = RoomFigureArea(left = 0, top = 0, width = 126, feetY = 84, scale = 1f)

        assertEquals(36, area.modelScale)
        assertEquals(38, area.portraitSize)
        assertEquals(24, area.portraitTop)
        assertEquals(44, area.portraitLeft)
        assertEquals(42, area.lookY)
    }

    /** 卡片夠寬時，模型與頭像依放大倍數變大。 */
    @Test
    fun `grows the figure with the card scale`() {
        val area = RoomFigureArea(left = 0, top = 0, width = 126, feetY = 145, scale = 1.5f)

        assertEquals(54, area.modelScale)
        assertEquals(57, area.portraitSize)
        assertEquals(145 - 33 - 57, area.portraitTop)
        assertEquals(145 - 63, area.lookY)
    }

    /** 卡片太窄時調低放大後的大小，人物不超出卡片寬度，但不小於未放大的大小。 */
    @Test
    fun `lowers the scale when the figure would overflow the card width`() {
        val area = RoomFigureArea(left = 0, top = 0, width = 70, feetY = 145, scale = 1.5f)

        assertEquals(31, area.modelScale)
        assertTrue(area.modelScale * 1.8f <= 70 - 14)
        assertEquals(56, area.portraitSize)
        assertTrue(area.portraitLeft >= 7)
    }

    /** 短卡片的人物縮到可用空間，以免遮住名稱或越過卡片頂端。 */
    @Test
    fun `shrinks the figure to the space in a short card`() {
        val area = RoomFigureArea(left = 0, top = 58, width = 126, feetY = 114, scale = 0.8f)

        assertTrue(area.modelScale in 1 until 36)
        assertTrue(area.portraitSize in 1 until 38)
        assertTrue(area.feetY - area.modelScale * 1.8f >= area.top + 4)
        assertTrue(area.portraitTop >= area.top + 4)
    }

    /** 人物頂端不超出卡片頂端。 */
    @Test
    fun `keeps the figure below the card top`() {
        val area = RoomFigureArea(left = 0, top = 0, width = 126, feetY = 90, scale = 1.5f)

        assertTrue(area.feetY - area.modelScale * 1.8f >= 4)
        assertTrue(area.portraitTop >= 4)
    }
}
