package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import org.joml.Matrix4f
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證雙面面板依觀看者所在的一側選出要畫的那一面。 */
class PanelFacingTest {
    /** 世界中的透視投影。 */
    private val perspective = Matrix4f().perspective(Math.toRadians(70.0).toFloat(), 16f / 9f, 0.05f, 1000f)

    /** 畫面使用的正交投影，原點在左上角，往下為正。 */
    private val screenOrthographic = Matrix4f().ortho(0f, 320f, 240f, 0f, 1000f, 3000f)

    /** 世界中面向鏡頭、文字正立的面板：與名牌相同，相對鏡頭翻轉上下與前後。 */
    private fun facingCamera(x: Float, y: Float, z: Float): Matrix4f = Matrix4f().translation(x, y, z).scale(1f, -1f, -1f)

    /** 面板在鏡頭前方、正面朝鏡頭時畫正面。 */
    @Test
    fun `panel facing the camera shows its front`() {
        assertTrue(PanelFacing.facesViewer(facingCamera(0f, 0f, -5f), perspective))
    }

    /** 同一個面板轉了半圈，鏡頭看到的是背面，改畫背面。 */
    @Test
    fun `panel turned away from the camera shows its back`() {
        val position = facingCamera(0f, 0f, -5f).rotateY(Math.PI.toFloat())

        assertFalse(PanelFacing.facesViewer(position, perspective))
    }

    /** 文字的鏡像縮放不會讓判斷相反：沒有翻轉前後的面板背對鏡頭。 */
    @Test
    fun `panel without the depth flip faces away`() {
        val position = Matrix4f().translation(0f, 0f, -5f).scale(-1f, -1f, 1f)

        assertFalse(PanelFacing.facesViewer(position, perspective))
    }

    /** 透視投影以面板到鏡頭的方向判斷：在鏡頭正右方、正面朝鏡頭的面板仍畫正面。 */
    @Test
    fun `perspective uses the direction from the panel to the camera`() {
        val beside = Matrix4f().translation(5f, 0f, 0f).rotateY((-Math.PI / 2).toFloat()).scale(1f, -1f, -1f)

        assertTrue(PanelFacing.facesViewer(beside, perspective))
        assertFalse(PanelFacing.facesViewer(Matrix4f(beside).rotateY(Math.PI.toFloat()), perspective))
    }

    /** 畫面座標的正交投影翻轉上下，介面文字不翻轉也看得到正面。 */
    @Test
    fun `screen orthographic projection shows unflipped text`() {
        val front = Matrix4f().translation(200f, 50f, -2000f)
        val back = Matrix4f().translation(200f, 50f, -2000f).rotateY(Math.PI.toFloat())

        assertTrue(PanelFacing.facesViewer(front, screenOrthographic))
        assertFalse(PanelFacing.facesViewer(back, screenOrthographic))
    }
}
