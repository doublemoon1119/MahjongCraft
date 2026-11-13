package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.uuid.Uuid

/** 判斷本機排列的手牌何時交回共享呈現；倒牌與攤牌動畫由共享呈現播放。 */
object LocalHandRelease {
    /**
     * 手牌的主人已完成，或整副手牌已翻成正面或作為結算證據公開時，交回共享呈現。
     *
     * @param ownerId 手牌的主人。
     * @param handIds 立牌與摸牌位的牌。
     * @param finishedPlayerIds 已完成的玩家。
     * @param faceUpIds 已翻成正面的牌。
     * @param publicEvidenceTileIds 作為結算證據公開的牌。
     * @return 需要交回共享呈現時為 true。
     */
    fun isReleased(
        ownerId: Uuid,
        handIds: Set<Uuid>,
        finishedPlayerIds: Set<Uuid>,
        faceUpIds: Set<Uuid>,
        publicEvidenceTileIds: Set<Uuid>,
    ): Boolean = ownerId in finishedPlayerIds ||
        (handIds.isNotEmpty() && (faceUpIds.containsAll(handIds) || publicEvidenceTileIds.containsAll(handIds)))
}
