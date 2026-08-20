package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 麻將牌世界呈現共用尺寸常數。
 *
 * 沿用重構前 main 分支 `mahjong_tile_base.json`／`MahjongTileEntity`／`MahjongBoard` 已經過實際遊戲
 * 呈現驗證的初始比例（12×16×8 模型單位、`0.15` runtime 縮放），供 Fabric-only 的
 * `com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity` 與純 Kotlin common 座標
 * 計算（[MahjongTileTableLayout]）共用同一組數值，避免兩處各自定義後跑掉。
 */
object MahjongTileDimensions {
    /** 舊版驗證過的麻將牌世界縮放。 */
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
