package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile

/**
 * 決定一局牌山洗好之後的排列。正式對局直接使用洗牌結果。
 *
 * - 替換排列只能重新排列同一副牌，不能增減或換掉任何一張。
 * - 開門、切出保留牌與牌牆位置仍照規則計算。
 */
fun interface TileWallArrangement {
    /**
     * 排列這一局的整副牌。
     *
     * @param shuffled 規則的牌山工廠洗好的整副牌。
     * @param open 把一個排列換成這一局實際的摸牌順序與保留牌，與正式開局使用同一套開門與布局。可用來推算要讓指定的牌出現在
     *   指定位置時，整副牌應該怎麼排。
     * @return 同一副牌的排列。
     */
    fun arrange(shuffled: List<IdentifiedTile>, open: (List<IdentifiedTile>) -> OpenedTileWallOrder): List<IdentifiedTile>

    /** 預設排列。 */
    companion object {
        /** 直接使用洗牌結果。 */
        val SHUFFLED: TileWallArrangement = TileWallArrangement { shuffled, _ -> shuffled }
    }
}
