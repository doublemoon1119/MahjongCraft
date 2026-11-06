package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.uuid.Uuid

/**
 * 本人某一時刻的手牌排列。
 *
 * @property standing 立牌順序；null 是空格，保留格位但不對應任何牌，例如剛離開立牌、其餘的牌還沒讓位時。
 * @property drawnId 摸牌位的牌；沒有時為 null。
 * @property unsortedIds 還沒理過、其他人看到接在立牌最右邊的牌，依其他人看到的順序排列。
 */
data class PrivateHandOrder(
    val standing: List<Uuid?>,
    val drawnId: Uuid?,
    val unsortedIds: List<Uuid>,
)
