package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 依規則決定的桌面牌位配置；距離以 block 為單位，相對於通用的標準擺法。
 *
 * 牌的大小與桌子的大小所有規則共用，這裡只調整牌在桌上的位置。
 *
 * @property wallInset 牌牆整體往桌心移動的距離；`0` 為標準位置。
 * @property wallCornerOverhang 每面牌牆在標準錯開之外，再往「從桌外面向這面牌牆時的右端」移動的距離；
 * 四面同向移動，轉角處由一面牌牆超出、相鄰牌牆的端點頂在它的側邊，形成風車形。`0` 為標準錯開。
 */
data class TableTileLayoutProfile(
    val wallInset: Double = 0.0,
    val wallCornerOverhang: Double = 0.0,
) {
    init {
        require(wallInset.isFinite() && wallInset >= 0.0) { "Wall inset must be a non-negative finite distance: $wallInset" }
        require(wallCornerOverhang.isFinite() && wallCornerOverhang >= 0.0) {
            "Wall corner overhang must be a non-negative finite distance: $wallCornerOverhang"
        }
    }

    /** 標準配置。 */
    companion object {
        /** 所有欄位都是標準擺法的配置；沒有登記配置的規則使用它。 */
        val STANDARD: TableTileLayoutProfile = TableTileLayoutProfile()
    }
}
