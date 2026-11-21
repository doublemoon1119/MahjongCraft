package com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene

import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * 場景中牌的繪製順序。
 *
 * 場景畫在畫面上，半透明牌一畫上去就與當下畫面上的顏色混合，並擋住之後畫在它後方的東西。所以半透明牌要等不透明的東西
 * 全部畫進畫面後才畫，彼此之間由遠到近，前面的牌才透得出後面的牌。
 */
internal object GuideSceneTileDrawOrder {
    /**
     * 把牌分成先畫的不透明牌與後畫的半透明牌。
     *
     * @param tiles 場景中的牌。
     * @param sceneToScreen 場景座標換算成畫面座標的矩陣；畫面座標的 Z 越大越靠近觀看者。
     * @param isTranslucent 判斷一張牌是否以半透明方式繪製。
     * @return 不透明牌維持原順序；半透明牌依牌底中心由遠到近排列。
     */
    fun split(
        tiles: List<GuideSceneTile>,
        sceneToScreen: Matrix4f,
        isTranslucent: (GuideSceneTile) -> Boolean,
    ): Split {
        val (translucent, opaque) = tiles.partition(isTranslucent)
        return Split(
            opaque = opaque,
            translucentFarToNear = translucent.sortedBy { tile ->
                sceneToScreen.transformPosition(Vector3f(tile.x.toFloat(), tile.y.toFloat(), tile.z.toFloat())).z
            },
        )
    }

    /**
     * 分好的兩組牌。
     *
     * @property opaque 先畫的不透明牌。
     * @property translucentFarToNear 不透明的東西都畫完後，由遠到近畫的半透明牌。
     */
    data class Split(
        val opaque: List<GuideSceneTile>,
        val translucentFarToNear: List<GuideSceneTile>,
    )
}
