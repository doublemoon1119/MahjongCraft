package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import kotlin.uuid.Uuid

/**
 * 桌上一排牌：同一排的牌依序排列，排內換位置時才需要判斷會不會越過其他牌。
 *
 * @property region 區域種類。
 * @property ownerId 所屬玩家；牌牆與保留區沒有所屬玩家。
 * @property group 同一位玩家同一種區域中的分組，例如第幾組副露或哪一個移出區；沒有分組時為 null。
 */
data class TileRowKey(
    val region: TileRegion,
    val ownerId: Uuid?,
    val group: String?,
)
