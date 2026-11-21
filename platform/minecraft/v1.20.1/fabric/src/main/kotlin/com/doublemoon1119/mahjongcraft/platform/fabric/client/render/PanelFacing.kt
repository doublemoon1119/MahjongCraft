package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * 雙面面板要畫哪一面：觀看者是否看得到面板正面的文字。
 *
 * 文字的每個字形依左上、左下、右下的順序排列頂點，背面會被剔除。兩面背對背同時畫時，兩面幾乎重疊，
 * 光影模組可能讓它們互相透出而閃爍，所以雙面面板只畫觀看者那一側的一面。
 */
internal object PanelFacing {
    /**
     * 觀看者是否看得到以目前轉換畫出的文字。
     *
     * 以文字頂點順序在視角座標中的朝向判斷：透視投影（世界中）以面板到鏡頭的方向比較，鏡頭在視角座標的原點；
     * 正交投影（畫面中的場景）以固定的視線方向比較。投影把畫面上下或左右翻轉時，看得到的一面也跟著相反。
     *
     * @param position 面板本地座標到視角座標的轉換。
     * @param projection 目前的投影矩陣。
     * @return 看得到正面，或剛好在面板平面上時為 true。
     */
    fun facesViewer(position: Matrix4f, projection: Matrix4f): Boolean {
        val topLeft = position.transformPosition(Vector3f(0f, 0f, 0f))
        val bottomLeft = position.transformPosition(Vector3f(0f, 1f, 0f))
        val bottomRight = position.transformPosition(Vector3f(1f, 1f, 0f))
        val front = Vector3f(bottomLeft).sub(topLeft).cross(Vector3f(bottomRight).sub(topLeft))
        val toViewer = if (isOrthographic(projection)) Vector3f(0f, 0f, 1f) else Vector3f(topLeft).negate()
        val facing = front.dot(toViewer)
        return if (mirrorsScreen(projection)) facing <= 0f else facing >= 0f
    }

    /**
     * 投影是否為正交投影；透視投影的齊次分量會隨深度改變。
     *
     * @param projection 投影矩陣。
     * @return 正交投影時為 true。
     */
    private fun isOrthographic(projection: Matrix4f): Boolean = projection.m23() == 0f

    /**
     * 投影是否把畫面上下或左右翻轉，例如畫面座標以左上為原點的正交投影。
     *
     * @param projection 投影矩陣。
     * @return 只翻轉其中一個方向時為 true。
     */
    private fun mirrorsScreen(projection: Matrix4f): Boolean = projection.m00() * projection.m11() < 0f
}
