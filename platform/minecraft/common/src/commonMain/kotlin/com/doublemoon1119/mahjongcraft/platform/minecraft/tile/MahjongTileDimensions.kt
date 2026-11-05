package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 麻將牌世界呈現共用尺寸常數。
 *
 * 牌的模型是 12×16×8 模型單位、runtime 縮放 `0.15`。Fabric 的 `MahjongTileEntity` 與 common 的座標計算
 * （[MahjongTileTableLayout]）共用同一組數值，避免兩處各自定義而不一致。
 */
object MahjongTileDimensions {
    /** 麻將牌世界縮放係數。 */
    private const val TILE_SCALE: Double = 0.15

    /** 麻將牌世界寬度。 */
    const val TILE_WIDTH: Double = 12.0 / 16.0 * TILE_SCALE

    /** 麻將牌世界高度。 */
    const val TILE_HEIGHT: Double = 16.0 / 16.0 * TILE_SCALE

    /** 麻將牌世界深度（厚度）。 */
    const val TILE_DEPTH: Double = 8.0 / 16.0 * TILE_SCALE

    /** 牌與牌之間的固定小間距，避免相鄰牌面重疊產生 Z-fighting。 */
    const val TILE_SMALL_PADDING: Double = 0.0025
}
