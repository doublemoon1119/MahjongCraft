package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/**
 * showcase 編隊的尺寸參數，單位為方塊。
 *
 * @property cardSpacing 同一翼相鄰卡片的中心距。
 * @property cardWidth 展示卡片的寬度。
 * @property winningTileWidth 和牌張的寬度。
 * @property winningTileGap 和牌張與手牌之間的間隙。
 * @property minimumWingWidth 一翼的最小寬度，讓役名標題放得下。
 * @property wingGap 相鄰兩翼之間的間隙。
 * @property groupGap 同一翼中相鄰兩組（手牌與各組副露）之間額外的間隙。
 */
data class ShowcaseFormationMetrics(
    val cardSpacing: Double,
    val cardWidth: Double,
    val winningTileWidth: Double,
    val winningTileGap: Double,
    val minimumWingWidth: Double,
    val wingGap: Double,
    val groupGap: Double = 0.0,
)
