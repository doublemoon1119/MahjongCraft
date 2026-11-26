package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 規則中立的線性牌堆值物件。
 *
 * - 只保存呼叫端交付的牌與其摸牌順序，不辨識活牌、王牌、嶺上牌或其他規則區域。
 * - 不會依 [MahjongRuleConfig.reservedTileCount] 自動保留或扣除任何牌。
 * - 內部存放 [IdentifiedTile]，每一張牌都可以追蹤。
 * - 完整牌組剛由規則牌山工廠建立時，可以暫時用本型別承載全部牌。
 * - 支援開門布局的對局經 [GameInitializer] 切分後，[TableState.tileWall] 只承載可正常摸取的牌。
 *   規則保留牌另存於 [TableState.reservedWallTiles]。
 * - 判斷一般摸牌序列是否耗盡時，直接使用 `tableState.tileWall.remainingCount == 0`，不得再扣除規則保留區張數。
 * - 本類別為不可變值物件。[draw] 與 [drawLast] 透過 [DrawResult] 回傳摸到的牌與新的 [TileWall] 實例。
 *
 * @property tiles 依摸牌順序排列、由此牌堆保存的牌張。
 */
data class TileWall(private val tiles: List<IdentifiedTile> = emptyList()) {

    /**
     * 摸牌動作的結果封裝。
     *
     * @property tile 摸到的 [IdentifiedTile]，若牌山已空則為 null。
     * @property wall 摸牌後的新 [TileWall] 實例。
     */
    data class DrawResult(val tile: IdentifiedTile?, val wall: TileWall)

    /** 目前這個容器實際保存的牌數。不額外包含或排除任何規則區域。 */
    val remainingCount: Int get() = tiles.size

    /**
     * 從牌山最前方摸取一張牌。
     *
     * @return 包含摸到的牌與新牌山狀態的 [DrawResult]。若牌山已空，[DrawResult.tile] 為 null 且牌山維持不變。
     */
    fun draw(): DrawResult {
        val tile = tiles.firstOrNull() ?: return DrawResult(null, this)
        return DrawResult(tile, TileWall(tiles.subList(1, tiles.size)))
    }

    /**
     * 從牌堆最後方取出一張牌。
     *
     * 本方法只提供規則中立的容器操作。呼叫規則負責定義最後方牌張的用途。
     *
     * @return 包含取出牌張與新牌堆的 [DrawResult]。牌堆為空時回傳 null 牌張及目前實例。
     */
    fun drawLast(): DrawResult {
        val tile = tiles.lastOrNull() ?: return DrawResult(null, this)
        return DrawResult(tile, TileWall(tiles.subList(0, tiles.lastIndex)))
    }

    /**
     * 獲取目前牌山的唯讀列表。
     *
     * @return 此容器保存的牌張，依摸牌順序排列。
     */
    fun getAllTiles(): List<IdentifiedTile> = tiles
}
