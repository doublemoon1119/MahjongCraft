package com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene

import org.joml.Matrix4f
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證場景先畫不透明牌，半透明牌之後由遠到近畫。 */
class GuideSceneTileDrawOrderTest {
    /** 場景牌；以牌面 key 標示是否半透明。 */
    private fun tile(assetKey: String, z: Double): GuideSceneTile = GuideSceneTile(assetKey = assetKey, x = 0.0, y = 0.0, z = z, yaw = 0f)

    /** 測試用的半透明判斷。 */
    private val isTranslucent: (GuideSceneTile) -> Boolean = { it.assetKey?.startsWith("glass") == true }

    /** 不透明牌維持原順序，半透明牌另外分開。 */
    @Test
    fun `opaque tiles keep their order and translucent tiles are separated`() {
        val tiles = listOf(tile("m1", 0.0), tile("glass1", 0.0), tile("m2", 0.0), tile("m3", 0.0))

        val split = GuideSceneTileDrawOrder.split(tiles, Matrix4f(), isTranslucent)

        assertEquals(listOf("m1", "m2", "m3"), split.opaque.map { it.assetKey })
        assertEquals(listOf("glass1"), split.translucentFarToNear.map { it.assetKey })
    }

    /** 畫面座標 Z 越大越靠近觀看者，半透明牌從 Z 最小的畫起。 */
    @Test
    fun `translucent tiles are drawn from far to near`() {
        val tiles = listOf(tile("glass-near", 3.0), tile("glass-far", -3.0), tile("glass-middle", 0.0))

        val split = GuideSceneTileDrawOrder.split(tiles, Matrix4f(), isTranslucent)

        assertEquals(listOf("glass-far", "glass-middle", "glass-near"), split.translucentFarToNear.map { it.assetKey })
    }

    /** 遠近依換算到畫面後的位置判斷：場景轉了半圈時，原本最近的牌變成最遠。 */
    @Test
    fun `distance follows the scene rotation`() {
        val tiles = listOf(tile("glass-a", 3.0), tile("glass-b", -3.0))
        val turned = Matrix4f().rotateY(Math.PI.toFloat())

        val split = GuideSceneTileDrawOrder.split(tiles, turned, isTranslucent)

        assertEquals(listOf("glass-a", "glass-b"), split.translucentFarToNear.map { it.assetKey })
    }
}
