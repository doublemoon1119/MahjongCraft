package com.doublemoon1119.mahjongcraft.testing.logic.base

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.closedKanFaceDownTileIds
import kotlin.uuid.Uuid

/**
 * 用於單元測試的副露工廠。
 *
 * 只讀一次傳入的牌組，公開牌面與朝下擺放的牌都取自同一組牌張識別碼。
 */
object FakeMeldFactory {
    /**
     * 建立一個測試用的副露；未指定時套用內建副露的設定：整組牌面公開，暗槓兩端朝下，其他種類全部朝上。
     *
     * @param type 副露種類。
     * @param tiles 組成副露的牌，依展示順序排列。
     * @param sourceTile 鳴取自他家的牌；沒有時為 null。
     * @param sourceDirection 鳴取來源的相對方位。
     * @param publiclyRevealedTileIds 可讓一般觀察者知道牌面的牌張；預設整組公開。
     * @param faceDownTileIds 朝下擺放的牌張；預設暗槓兩端朝下，其他種類全部朝上。
     * @return 測試用的副露。
     */
    fun create(
        type: MeldType,
        tiles: List<IdentifiedTile>,
        sourceTile: IdentifiedTile? = null,
        sourceDirection: RelativeDirection = RelativeDirection.Self,
        publiclyRevealedTileIds: Set<Uuid> = tiles.mapTo(LinkedHashSet()) { it.id },
        faceDownTileIds: Set<Uuid> = if (type == MeldType.CLOSED_KAN) closedKanFaceDownTileIds(tiles) else emptySet(),
    ): Meld = Meld(
        type = type,
        tiles = tiles,
        sourceTile = sourceTile,
        sourceDirection = sourceDirection,
        publiclyRevealedTileIds = publiclyRevealedTileIds,
        faceDownTileIds = faceDownTileIds,
    )
}
