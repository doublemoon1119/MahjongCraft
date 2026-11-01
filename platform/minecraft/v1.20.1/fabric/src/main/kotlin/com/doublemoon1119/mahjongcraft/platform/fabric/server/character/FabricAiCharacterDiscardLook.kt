package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import net.minecraft.util.math.Vec3d
import kotlin.uuid.Uuid

/**
 * 一次已成立捨牌的公開注視目標，不保存手牌中的動畫起點。
 *
 * @property tileId 已打出的公開牌張 UUID。
 * @property position 牌河配置的公開位置。
 * @property expiresAt 停止短暫注視的主執行緒 tick。
 */
internal class FabricAiCharacterDiscardLook(
    val tileId: Uuid,
    val position: Vec3d,
    val expiresAt: Long,
) {
    /**
     * 檢查目標是否仍是本局牌河中未被取走的牌。
     *
     * @param tick 目前主執行緒 tick。
     * @param discardIds 目前公開牌河中仍存在的牌張 UUID。
     * @return 目標仍存在且未逾期時為 true。
     */
    fun isCurrent(tick: Long, discardIds: Set<Uuid>): Boolean = tick <= expiresAt && tileId in discardIds
}
