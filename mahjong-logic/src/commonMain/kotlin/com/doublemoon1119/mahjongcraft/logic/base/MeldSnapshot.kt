package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import kotlin.uuid.Uuid

/**
 * [Meld] 對指定觀察者的可見快照。
 *
 * 每張牌是否可見由該牌是否屬於 [Meld.publiclyRevealedTileIds]、觀察者權限及規則設定共同決定。
 *
 * @property componentViews 已按讀取者裁切的副露資料，不包含原始組件。
 * @property id 副露的穩定識別碼。
 * @property type 副露種類。
 * @property tiles 依保存順序排列的牌張，未知牌仍保留實體識別碼。
 * @property sourceTile 鳴取的來源牌；沒有來源時為 null。
 * @property sourceDirection 來源玩家相對於本人的方位。
 * @property faceDownTileIds 實際朝下擺放的牌張，不代表該觀察者是否知道牌面。
 */
data class MeldSnapshot(
    val id: Uuid,
    val type: MeldType,
    val tiles: List<IdentifiedTileSnapshot>,
    val sourceTile: IdentifiedTileSnapshot?,
    val sourceDirection: RelativeDirection,
    val faceDownTileIds: Set<Uuid>,
    val componentViews: ComponentViewSet = ComponentViewSet.EMPTY,
)

/**
 * 產生一個 [Meld] 的快照。
 *
 * 觀察者本人或被授權者可以看見整組牌；其他觀察者只能看見公開集合中的牌面，其餘牌保留 ID。
 *
 * @param handIsVisible 觀察者是否為手牌本人或獲授權查看者。
 * @param revealsClosedKanTiles 規則是否公開暗槓牌面。
 * @return 依規則與觀察者可見性建立的副露快照。
 */
fun Meld.toSnapshot(handIsVisible: Boolean, revealsClosedKanTiles: Boolean): MeldSnapshot {
    val canSeeAll = handIsVisible || (type == MeldType.CLOSED_KAN && revealsClosedKanTiles)
    fun isVisible(tile: IdentifiedTile): Boolean = canSeeAll || (type != MeldType.CLOSED_KAN && tile.id in publiclyRevealedTileIds)
    return MeldSnapshot(
        id = id,
        type = type,
        tiles = tiles.map { tile ->
            tile.toSnapshot(isVisible = isVisible(tile))
        },
        sourceTile = sourceTile?.let { tile ->
            tile.toSnapshot(isVisible = isVisible(tile))
        },
        sourceDirection = sourceDirection,
        faceDownTileIds = if (type == MeldType.CLOSED_KAN && !revealsClosedKanTiles) {
            tiles.mapTo(mutableSetOf()) { it.id }
        } else {
            faceDownTileIds.toSet()
        },
    )
}
