package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import kotlin.uuid.Uuid

/**
 * 依目前房間內 AI 的固定順序產生一致顯示名稱；權威身分仍是 UUID，等待階段成員改變時可自然重新編號。
 *
 * @param playerId 要顯示名稱的 AI 玩家 UUID。
 * @param orderedAiPlayerIds 依座位順序排列的 AI 玩家 UUID。
 * @return 房間內序號名稱；找不到該玩家時以 UUID 前六字元作為 fallback。
 */
fun aiPlayerDisplayName(playerId: Uuid, orderedAiPlayerIds: List<Uuid>): String {
    val ordinal = orderedAiPlayerIds.indexOf(playerId)
    return if (ordinal >= 0) "AI ${ordinal + 1}" else "AI-${playerId.toString().take(6)}"
}
