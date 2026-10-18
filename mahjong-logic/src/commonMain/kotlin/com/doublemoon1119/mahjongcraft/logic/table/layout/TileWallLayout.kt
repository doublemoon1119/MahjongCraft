package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 規則提供的牌牆布局能力，將洗好的完整牌列依 [WallOpening] 排列成正式的摸牌順序與牌牆結構。
 *
 * 不同規則的牌數、面數、墩數與王牌張數皆可不同；共用初始化流程不得假設固定張數或固定每面墩數，
 * 一律透過此介面取得結果。
 */
interface TileWallLayout {
    /**
     * 取得本局各面牌牆的擺放位置，以及使用固定方向或莊家方向作為基準。
     *
     * 擺法由規則決定，不是玩家可調整的選項。本函式只描述位置；開門與摸牌順序由 [resolve] 處理。
     * 相同規則配置與莊家索引必須得到相同結果，讓恢復對局時能重建位置；
     * 不得使用亂數、修改狀態，或依賴可能改變的外部資料。
     *
     * @param dealerSeatIndex 莊家在完整玩家名單中的零基底座位索引。
     * @return 每面牌牆擺在哪一側，以及是否隨莊家方向旋轉。
     */
    fun sideArrangement(dealerSeatIndex: Int): TileWallSideArrangement

    /**
     * 依 [shuffledTiles] 與 [opening] 排列牌牆。
     *
     * @param shuffledTiles 已洗牌完成的完整牌組。
     * @param opening 本局的牌牆開門位置。
     * @return 排列結果，包含摸牌順序、王牌初始快照與結構座標。
     * @throws IllegalArgumentException 當 [shuffledTiles] 張數不符合此 layout 的需求，或 [opening]
     * 超出此 layout 的面數／墩數範圍時拋出。
     */
    fun resolve(shuffledTiles: List<IdentifiedTile>, opening: WallOpening): TileWallLayoutResult
}
