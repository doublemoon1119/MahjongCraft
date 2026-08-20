package com.doublemoon1119.mahjongcraft.logic.base

import kotlin.uuid.Uuid

/**
 * 管理玩家具有唯一身份標識的手牌集合。
 *
 * 負責處理摸牌、捨牌、整理手牌以及處理副露邏輯。
 * 內部將手牌區分為「立牌 (Standing Tiles)」與「副露 (Exposed Melds)」。
 *
 * 本類別為不可變值物件：所有會改變手牌狀態的操作皆不會修改原實例，
 * 而是回傳一個反映變更後狀態的新 [Hand] 實例。
 *
 * @property tiles 已經整理在手中的立牌列表。
 * @property melds 玩家前方的副露列表。
 * @property lastDrawn 剛摸入但尚未放入手牌或尚未打出的牌。
 */
data class Hand(
    val tiles: List<IdentifiedTile> = emptyList(),
    val melds: List<Meld> = emptyList(),
    val lastDrawn: IdentifiedTile? = null,
) {
    /**
     * 捨牌動作的結果封裝。
     *
     * @property hand 執行捨牌後的新手牌狀態。
     * @property tile 被捨棄的那張牌。
     * @property isDiscardedFromDraw 是否為打出剛摸到的牌（摸切）。
     */
    data class DiscardResult(
        val hand: Hand,
        val tile: IdentifiedTile,
        val isDiscardedFromDraw: Boolean,
    )

    /**
     * 將一張具備唯一標識的牌加入手牌列表。
     *
     * @param tile 欲加入手牌的 [IdentifiedTile] 實體。
     * @return 加入該牌後的新 [Hand] 實例。
     */
    fun addTile(tile: IdentifiedTile): Hand = copy(tiles = tiles + tile)

    /**
     * 獲取所有立牌（包含最後一張摸牌）。
     *
     * @return 包含目前所有立牌的列表。
     */
    val standingTiles: List<IdentifiedTile>
        get() = tiles + listOfNotNull(lastDrawn)

    /**
     * 獲取所有已公開的副露。
     *
     * @return 包含所有副露實體的列表。
     */
    val exposedMelds: List<Meld>
        get() = melds

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
     * 將 [lastDrawn] 的牌併入立牌，並根據提供的 [order] 排序後回傳新的 [Hand] 實例。
     *
     * @param order 排序策略。
     * @return 整理後的新 [Hand] 實例。
     */
    fun organize(order: TileOrder): Hand {
        val mergedTiles = lastDrawn?.let { tiles + it } ?: tiles
        return copy(tiles = mergedTiles.sortedWith(compareBy(order) { it.tile }), lastDrawn = null)
    }

    /**
     * 執行一般鳴牌動作（吃、碰、明槓、暗槓）。
     *
     * 此方法會將參與該副露且原本存在於手牌中的牌移除，並記錄鳴取來源。
     *
     * @param type 副露種類。
     * @param tiles 構成該副露的所有牌（包含來自他人的牌與自己手牌中的牌）。
     * @param source 鳴取來源的牌（別家打出的牌）。若為暗槓 [MeldType.CLOSED_KAN]則傳入 null。
     * @param direction 鳴取來源的方位。對於暗槓 [MeldType.CLOSED_KAN]，必須傳入 [RelativeDirection.Self]。
     * @return 完成鳴牌後的新 [Hand] 實例。
     */
    fun call(
        type: MeldType,
        tiles: List<IdentifiedTile>,
        source: IdentifiedTile? = null,
        direction: RelativeDirection,
    ): Hand {
        // 遍歷組成副露的這些牌，將「不是從別家鳴取來的」（即原本就在自己手牌裡的）逐一移除
        var hand = this
        tiles.forEach { mTile ->
            if (source == null || mTile.id != source.id) {
                hand = hand.removeFromHand(mTile.id)
            }
        }
        // 將完整的副露組合（包含拿別人的那張）加入副露清單
        return hand.copy(melds = hand.melds + Meld(type, tiles, source, direction))
    }

    /**
     * 執行加槓動作 (Added Kan)。
     *
     * 將手牌中的一張牌加入至既有的碰組 (PON) 副露中。
     *
     * @param tile 欲加槓的牌。
     * @param targetMeldIndex 欲升級的碰組在 [exposedMelds] 中的索引。
     * @return 完成加槓後的新 [Hand] 實例。
     * @throws IllegalArgumentException 當索引無效或指定的副露不是 [MeldType.PON] 時拋出。
     */
    fun upgradeToAddedKan(tile: IdentifiedTile, targetMeldIndex: Int): Hand {
        val oldMeld = melds[targetMeldIndex]
        require(oldMeld.type == MeldType.PON) { "Only PON can be upgraded to ADDED_KAN" }

        val handAfterRemoval = removeFromHand(tile.id)
        val newMeld = oldMeld.copy(type = MeldType.ADDED_KAN, tiles = oldMeld.tiles + tile)
        val newMelds = handAfterRemoval.melds.toMutableList().apply { this[targetMeldIndex] = newMeld }

        return handAfterRemoval.copy(melds = newMelds)
    }

    /**
     * 根據唯一識別碼捨棄手牌。
     *
     * 如果捨棄的是剛摸到的牌（[lastDrawn]），則直接移除。
     * 如果捨棄的是手牌中的牌，則會將原本的 [lastDrawn] 加入手牌列表中，並移除目標牌。
     *
     * @param id 欲捨棄牌的 Uuid。
     * @return 包含新手牌狀態與捨牌結果的 [DiscardResult]，若找不到則返回 null。
     */
    fun discardById(id: Uuid): DiscardResult? {
        // 1. 檢查是否為摸切
        if (lastDrawn?.id == id) {
            return DiscardResult(copy(lastDrawn = null), lastDrawn, isDiscardedFromDraw = true)
        }

        // 2. 檢查是否在立牌中
        val index = tiles.indexOfFirst { it.id == id }
        if (index != -1) {
            val discardedTile = tiles[index]
            val remainingTiles = tiles.filterIndexed { i, _ -> i != index }
            // 若打出手牌而非摸切牌，則將摸到的牌併入立牌中，接在列表最後面——這裡的順序是加入的時間軸
            // （先加入的在前）。
            val newTiles = lastDrawn?.let { remainingTiles + it } ?: remainingTiles

            return DiscardResult(
                hand = copy(tiles = newTiles, lastDrawn = null),
                tile = discardedTile,
                isDiscardedFromDraw = false,
            )
        }

        return null
    }

    /**
     * 內部輔助方法：從手牌（立牌或摸牌）中移除指定 ID 的牌，回傳移除後的新 [Hand] 實例。
     */
    private fun removeFromHand(id: Uuid): Hand = if (lastDrawn?.id == id) {
        copy(lastDrawn = null)
    } else {
        copy(tiles = tiles.filterNot { it.id == id })
    }
}
