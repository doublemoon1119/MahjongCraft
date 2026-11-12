package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileComponentViews
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora.riichiNextDora

/**
 * 判斷已知牌面是否為日麻加值牌：帶赤寶牌特徵，或是生效寶牌指示牌的下一張。
 *
 * 只把帶指示牌特徵的牌山牌當成指示牌；其他可見的牌山牌不影響結果。
 *
 * @param face 要判斷的已知牌面。
 * @param wallFaces 讀取者已知的牌山牌面。
 * @param usesThreePlayerTiles 是否使用三人麻將牌組，決定一萬與九萬的寶牌順序。
 * @return 這張牌目前是否為加值牌。
 */
internal fun riichiIsBonusTile(
    face: TileFaceView,
    wallFaces: List<TileFaceView>,
    usesThreePlayerTiles: Boolean,
): Boolean = face.views[RiichiTileComponentViews.RED_DORA] != null ||
    wallFaces
        .filter { it.views[RiichiTileComponentViews.DORA_INDICATOR] != null }
        .any { indicator -> face.tile == riichiNextDora(indicator.tile, usesThreePlayerTiles) }
