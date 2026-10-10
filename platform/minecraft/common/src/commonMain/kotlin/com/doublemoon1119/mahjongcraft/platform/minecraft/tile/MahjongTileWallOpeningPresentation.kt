package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 在初次發牌與四家手牌翻起後，接續排入既有牌牆 entity 的開門呈現資料。
 *
 * @property wallPresentation 建立牌牆時保存的完整組裝與規則最終布局。
 * @property startGameTime 王牌整理可以開始的絕對 server game time。
 */
data class MahjongTileWallOpeningPresentation(
    val wallPresentation: MahjongTileWallPresentation,
    val startGameTime: Long,
)
