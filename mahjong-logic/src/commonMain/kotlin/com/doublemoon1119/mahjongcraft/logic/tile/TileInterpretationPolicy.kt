package com.doublemoon1119.mahjongcraft.logic.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 將規則特有牌種解讀成共用麻將運算可使用的正規牌面。
 *
 * 例如日麻赤五保留自己的 [Tile.Extension] 身分以供寶牌、外觀與 persistence 使用，但在吃、碰、槓、
 * 向聽與牌型比較時必須分別等同普通五萬、五筒或五條。共用流程只能透過 [canonicalize] 比較牌面，
 * 不得直接判斷特定 extension ID。
 */
fun interface TileInterpretationPolicy {
    /** 將 [tile] 轉為目前規則用於一般牌面比較的正規表示。 */
    fun canonicalize(tile: Tile): Tile
}

/** 不具有規則特有牌面等價關係時使用的原樣解讀 policy。 */
object IdentityTileInterpretationPolicy : TileInterpretationPolicy {
    override fun canonicalize(tile: Tile): Tile = tile
}
