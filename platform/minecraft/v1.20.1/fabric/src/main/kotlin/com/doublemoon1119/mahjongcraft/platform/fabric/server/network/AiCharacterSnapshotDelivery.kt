package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import net.minecraft.server.network.ServerPlayerEntity
import kotlin.uuid.Uuid

/** 已授權桌況旁的公開角色交付邊界，不提供查詢或授權。 */
fun interface AiCharacterSnapshotDelivery {
    /**
     * 在既有交付來源檢查後同步角色。
     *
     * @param player 已驗證收件者。
     * @param tableId 已授權桌子。
     * @param expectedMatchId 準備同一快照時的場次，等待室為 null。
     * @param playerIds 同一快照的完整名冊。
     */
    fun sendAuthorized(player: ServerPlayerEntity, tableId: Uuid, expectedMatchId: Uuid?, playerIds: Set<Uuid>)
}
