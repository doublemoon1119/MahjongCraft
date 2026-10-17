package com.doublemoon1119.mahjongcraft.logic.base

import kotlin.uuid.Uuid

/** 公開蓋牌位置的穩定順序；只依牌張身分排列，不讀取牌面或私人理牌偏好。 */
object PublicHandOrder {
    /**
     * 將同一組牌張排成與牌面無關的固定順序。
     *
     * @param tileIds 要擺放的立牌識別碼，不含獨立摸牌位。
     * @return 依識別碼排列的新列表。
     */
    fun arrange(tileIds: List<Uuid>): List<Uuid> = tileIds.sortedBy(Uuid::toString)
}
