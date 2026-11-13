package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 判斷碰或明槓一張字牌時，是否讓整套三元牌或風牌都成為副露。
 *
 * 套內其餘每一種牌都必須已經是副露（碰、明槓、加槓或暗槓）；手中的暗刻不算。例如日麻與台麻以此判斷大三元、
 * 大四喜的包牌責任。
 */
object HonorSetCompletionDetector {
    /**
     * 檢查碰或明槓 [calledTile] 是否補齊一整套字牌副露。
     *
     * @param hand 碰或明槓之前的手牌。
     * @param calledTile 這次鳴取的牌。
     * @return 被補齊的整套字牌；不是字牌或其餘牌尚未全部副露時為 null。
     */
    fun completedBy(hand: Hand, calledTile: Tile): HonorSet? {
        val honorSet = HonorSet.entries.firstOrNull { calledTile in it.tiles } ?: return null
        val exposedTiles = hand.exposedMelds.mapNotNull { it.tiles.firstOrNull()?.tile }.toSet()
        return honorSet.takeIf { (it.tiles - calledTile).all { tile -> tile in exposedTiles } }
    }
}
