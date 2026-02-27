package com.doublemoon1119.mahjongcraft.model.base

import java.util.*

/**
 * 管理玩家具有唯一身份標識的手牌集合。
 *
 * 負責處理摸牌、捨牌、整理手牌以及處理副露邏輯。
 * 內部將手牌區分為「立牌 (Standing Tiles)」與「副露 (Exposed Melds)」。
 *
 * @property tiles 已經整理在手中的立牌列表。
 * @property melds 玩家前方的副露列表。
 * @property lastDrawn 剛摸入但尚未放入手牌或尚未打出的牌。
 */
class Hand(
    private val tiles: MutableList<IdentifiedTile> = mutableListOf(),
    private val melds: MutableList<Meld> = mutableListOf(),
    var lastDrawn: IdentifiedTile? = null
) {
    /**
     * 將一張具備唯一標識的牌加入手牌列表。
     *
     * @param tile 欲加入手牌的 [IdentifiedTile] 實體。
     */
    fun addTile(tile: IdentifiedTile) {
        tiles.add(tile)
    }

    /**
     * 獲取所有立牌（包含最後一張摸牌）。
     *
     * @return 包含目前所有立牌的列表。
     */
    val standingTiles: List<IdentifiedTile>
        get() = tiles.toList() + listOfNotNull(lastDrawn)

    /**
     * 獲取所有已公開的副露。
     *
     * @return 包含所有副露實體的列表。
     */
    val exposedMelds: List<Meld>
        get() = melds.toList()

    /**
     * 獲取玩家持有的所有牌，包含立牌與副露中的牌。
     *
     * @return 該玩家目前擁有的所有 [IdentifiedTile]。
     */
    val allTiles: List<IdentifiedTile>
        get() = standingTiles + melds.flatMap { it.tiles }

    /**
     * 整理手牌。
     *
     * 將 [lastDrawn] 的牌放入 [tiles] 中，並根據提供的 [order] 進行排序。
     *
     * @param order 排序策略。
     */
    fun organize(order: TileOrder) {
        lastDrawn?.let {
            tiles.add(it)
            lastDrawn = null
        }
        tiles.sortWith(compareBy(order) { it.tile })
    }

    /**
     * 執行一般鳴牌動作（吃、碰、明槓、暗槓）。
     *
     * 此方法會將參與該副露且原本存在於手牌中的牌移除，並記錄鳴取來源。
     *
     * @param type 副露種類。
     * @param tiles 構成該副露的所有牌（包含來自他人的牌與自己手牌中的牌）。
     * @param source 鳴取來源的牌（別家打出的牌）。若為暗槓 [MeldType.CLOSED_KAN] 則傳入 null。
     * @param direction 鳴取來源的方位。
     */
    fun call(
        type: MeldType,
        tiles: List<IdentifiedTile>,
        source: IdentifiedTile? = null,
        direction: RelativeDirection? = null
    ) {
        // 遍歷組成副露的這些牌 (通常是 3 或 4 張)
        tiles.forEach { mTile ->
            // 如果這張牌「不是」從別家鳴取來的，代表它原本在我的手牌裡，必須從手牌移除
            if (source == null || mTile.id != source.id) {
                removeFromHand(mTile.id)
            }
        }
        // 將完整的副露組合（包含拿別人的那張）加入副露清單
        melds.add(Meld(type, tiles, source, direction))
    }

    /**
     * 執行加槓動作 (Added Kan)。
     *
     * 將手牌中的一張牌加入至既有的碰組 (PUNG) 副露中。
     *
     * @param tile 欲加槓的牌。
     * @param targetMeldIndex 欲升級的碰組在 [exposedMelds] 中的索引。
     * @throws IllegalArgumentException 當索引無效或指定的副露不是 [MeldType.PUNG] 時拋出。
     */
    fun upgradeToAddedKan(tile: IdentifiedTile, targetMeldIndex: Int) {
        val oldMeld = melds[targetMeldIndex]
        require(oldMeld.type == MeldType.PUNG) { "Only PUNG can be upgraded to ADDED_KAN" }

        removeFromHand(tile.id)

        val newTiles = oldMeld.tiles + tile
        melds[targetMeldIndex] = oldMeld.copy(type = MeldType.ADDED_KAN, tiles = newTiles)
    }

    /**
     * 根據唯一識別碼捨棄手牌。
     *
     * @param id 欲捨棄牌的 UUID。
     * @return 被捨棄的 [IdentifiedTile]，若找不到則返回 null。
     */
    fun discardById(id: UUID): IdentifiedTile? {
        if (lastDrawn?.id == id) {
            val t = lastDrawn
            lastDrawn = null
            return t
        }
        val index = tiles.indexOfFirst { it.id == id }
        return if (index != -1) tiles.removeAt(index) else null
    }

    /**
     * 內部輔助方法：從手牌（立牌或摸牌）中移除指定 ID 的牌。
     */
    private fun removeFromHand(id: UUID) {
        if (lastDrawn?.id == id) {
            lastDrawn = null
        } else {
            tiles.removeIf { it.id == id }
        }
    }
}