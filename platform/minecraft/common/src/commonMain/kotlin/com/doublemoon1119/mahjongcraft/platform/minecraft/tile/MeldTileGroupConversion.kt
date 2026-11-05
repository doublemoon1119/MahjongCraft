package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation

/**
 * 把副露的呈現資料轉成牌區擺放使用的副露牌組。
 *
 * @return 同樣牌序與朝向的副露牌組。
 */
fun MeldPresentation.toTileGroup(): MahjongMeldTileGroup = MahjongMeldTileGroup(
    type = type,
    tileIds = tileIds,
    calledTileId = calledTileId,
    sourceDirection = sourceDirection,
    allTilesFaceDown = allTilesFaceDown,
    faceDownTileIds = faceDownTileIds,
)
