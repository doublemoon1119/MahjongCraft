package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/**
 * 一張打出過的牌是誰打的。
 *
 * @property discarderId 打出這張牌的玩家。
 * @property relation 打出者相對觀看者的位置；觀看者沒有入座時為 null。
 */
data class DiscardSource(
    val discarderId: Uuid,
    val relation: RelativeDirection?,
) {
    /** 從桌況查出打出者。 */
    companion object {
        /**
         * 查出 [tileId] 是誰打出的。
         *
         * 牌河記錄每一張打出的牌，被鳴走的也留著紀錄，所以還在牌河的牌與移到副露的被鳴的牌都查得到；
         * 從沒打出過的牌（例如加槓補上的牌、手牌、牌牆）查不到。
         *
         * @param snapshot 觀看者看到的桌況。
         * @param tileId 要查的牌。
         * @param viewerId 觀看者。
         * @return 打出者與相對位置；這張牌沒有被打出過時為 null。
         */
        fun of(
            snapshot: TableStateSnapshot,
            tileId: Uuid,
            viewerId: Uuid?,
        ): DiscardSource? {
            val discarderIndex = snapshot.players.indexOfFirst { player -> player.discardPile.entries.any { it.tile.id == tileId } }
            if (discarderIndex < 0) return null
            val viewerIndex = snapshot.players.indexOfFirst { it.id == viewerId }
            val relation = if (viewerIndex < 0) {
                null
            } else {
                when ((discarderIndex - viewerIndex).mod(snapshot.players.size)) {
                    0 -> RelativeDirection.Self
                    snapshot.players.size - 1 -> RelativeDirection.Left
                    1 -> RelativeDirection.Right
                    else -> RelativeDirection.Across
                }
            }
            return DiscardSource(snapshot.players[discarderIndex].id, relation)
        }
    }
}
