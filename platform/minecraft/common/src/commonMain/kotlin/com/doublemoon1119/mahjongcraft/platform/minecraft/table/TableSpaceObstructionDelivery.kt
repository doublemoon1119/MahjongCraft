package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/**
 * 要送給一位玩家的擺牌層標示。
 *
 * @property recipientId 收件玩家 UUID。
 * @property payload 要送出的標示；格子為空時代表清除。
 */
data class TableSpaceObstructionDelivery(
    val recipientId: Uuid,
    val payload: TableSpaceObstructionPayloadDto,
)
