package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 依規則決定的桌面牌位配置；距離以 block 為單位，相對於通用的標準擺法。
 *
 * 牌的大小與桌子的大小所有規則共用，這裡只調整牌在桌上的位置。
 *
 * @property wallInset 牌牆整體往桌心移動的距離；`0` 為標準位置。
 * @property wallCornerOverhang 每面牌牆在標準錯開之外，再往「從桌外面向這面牌牆時的右端」移動的距離；
 * 四面同向移動，轉角處由一面牌牆超出、相鄰牌牆的端點頂在它的側邊，形成風車形。`0` 為標準錯開。
 * @property meldArea 副露擺放的區域；移出手牌的牌（例如花牌）接在副露後面。
 * @property claimedTileSideways 副露中被鳴的牌是否依來源方向橫放；`false` 時全部直立。
 * @property discardArea 牌河的擺法。
 * @property highlightsLatestDiscard 是否讓最後打出、仍留在牌河的那張牌外框持續發光，方便在牌河裡找到它；玩家可以在
 * 自己的顯示設定關掉，但規則沒開時無法打開。
 * @property addedKan 加槓補上的牌的擺法；[TableAddedKanPlacement.BESIDE_CLAIMED_TILE] 需要 [claimedTileSideways]，
 * 且副露放在桌角。
 */
data class TableTileLayoutProfile(
    val wallInset: Double = 0.0,
    val wallCornerOverhang: Double = 0.0,
    val meldArea: TableMeldArea = TableMeldArea.CORNER,
    val claimedTileSideways: Boolean = true,
    val addedKan: TableAddedKanPlacement = TableAddedKanPlacement.BESIDE_CLAIMED_TILE,
    val discardArea: TableDiscardArea = TableDiscardArea.ROWS,
    val highlightsLatestDiscard: Boolean = false,
) {
    init {
        require(wallInset.isFinite() && wallInset >= 0.0) { "Wall inset must be a non-negative finite distance: $wallInset" }
        require(wallCornerOverhang.isFinite() && wallCornerOverhang >= 0.0) {
            "Wall corner overhang must be a non-negative finite distance: $wallCornerOverhang"
        }
        require(addedKan != TableAddedKanPlacement.BESIDE_CLAIMED_TILE || claimedTileSideways) {
            "An added kan can sit beside the claimed tile only when claimed tiles lie sideways"
        }
        require(meldArea == TableMeldArea.CORNER || addedKan == TableAddedKanPlacement.IN_ROW) {
            "Melds between the wall and the hand lay an added kan in the row"
        }
    }

    /** 標準配置。 */
    companion object {
        /** 所有欄位都是標準擺法的配置；沒有登記配置的規則使用它。 */
        val STANDARD: TableTileLayoutProfile = TableTileLayoutProfile()
    }
}
