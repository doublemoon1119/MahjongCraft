package com.doublemoon1119.mahjongcraft.logic.base

import kotlin.uuid.Uuid

/**
 * 代表玩家的副露（鳴牌組合）。
 *
 * 玩家透過鳴取他人的捨牌或宣告自己手中的牌而組成獨立牌組。
 * 組內可以有尚未公開的牌；是否門清及其計分影響由各規則判定。
 *
 * @property type 副露的種類，例如 [MeldType.CHI] 或 [MeldType.PON]。
 * @property tiles 組成該副露的所有 [IdentifiedTile]。
 * @property sourceTile 從其他玩家處鳴取而來的特定牌。若為暗槓 ([MeldType.CLOSED_KAN]) 則通常為 null。
 * @property sourceDirection 鳴取來源的相對方位；有 [sourceTile] 時不得為 [RelativeDirection.Self]。
 * @property publiclyRevealedTileIds 可讓一般觀察者知道牌面的牌張 ID；必須是 [tiles] 中牌張 ID 的子集合。
 * @property faceDownTileIds 桌面上需要朝下呈現的牌張 ID；必須是 [tiles] 中牌張 ID 的子集合，與牌面公開狀態分開保存。
 */
data class Meld(
    val type: MeldType,
    val tiles: List<IdentifiedTile>,
    val sourceTile: IdentifiedTile? = null,
    val sourceDirection: RelativeDirection,
    val publiclyRevealedTileIds: Set<Uuid>,
    val faceDownTileIds: Set<Uuid>,
) {
    init {
        val tileIds = tiles.map { it.id }.toSet()
        require(tileIds.size == tiles.size) { "Meld tiles must have unique IDs" }
        require(sourceTile == null || sourceTile in tiles) { "Meld source must belong to the meld" }
        require(sourceTile == null || sourceDirection != RelativeDirection.Self) { "Claimed source must come from another player" }
        require(sourceTile == null || sourceTile.id in publiclyRevealedTileIds) { "Claimed source tile must remain public" }
        require(publiclyRevealedTileIds.all(tileIds::contains)) {
            "Publicly revealed tile IDs must belong to the meld"
        }
        require(faceDownTileIds.all(tileIds::contains)) {
            "Face-down tile IDs must belong to the meld"
        }
    }
}

/**
 * 暗槓讓一般觀察者知道牌面的牌張：規則公開暗槓的牌時整組公開，否則都不公開。
 *
 * @param tiles 暗槓的四張牌。
 * @param revealsClosedKanTiles 規則是否公開暗槓的牌。
 * @return 公開牌面的牌張 ID。
 */
fun closedKanPubliclyRevealedTileIds(tiles: List<IdentifiedTile>, revealsClosedKanTiles: Boolean): Set<Uuid> = if (revealsClosedKanTiles) tiles.mapTo(LinkedHashSet()) { it.id } else emptySet()

/**
 * 暗槓擺出時朝下的牌張：兩端朝下、中間兩張朝上。規則不公開暗槓的牌時，桌面與快照另外整組蓋下。
 *
 * @param tiles 依展示順序排列的暗槓牌。
 * @return 兩端牌張的 ID。
 */
fun closedKanFaceDownTileIds(tiles: List<IdentifiedTile>): Set<Uuid> = setOf(tiles.first().id, tiles.last().id)
