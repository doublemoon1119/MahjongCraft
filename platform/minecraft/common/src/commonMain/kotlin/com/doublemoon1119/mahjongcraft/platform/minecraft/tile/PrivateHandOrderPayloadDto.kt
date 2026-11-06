package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlinx.serialization.Serializable

/**
 * 只送給本人的手牌排列時間軸項目，只含本人的牌張識別碼，不含牌面或其他玩家的資訊。
 *
 * @property tableId 桌子的 UUID 字串。
 * @property effectiveGameTime 開始生效的遊戲時間。
 * @property standingTileIds 立牌順序的 UUID 字串；null 是保留格位的空格。
 * @property drawnTileId 摸牌位的牌；沒有時為 null。
 * @property unsortedTileIds 還沒理過、其他人看到接在立牌最右邊的牌，依其他人看到的順序排列。
 */
@Serializable
data class PrivateHandOrderPayloadDto(
    val tableId: String,
    val effectiveGameTime: Long,
    val standingTileIds: List<String?>,
    val drawnTileId: String?,
    val unsortedTileIds: List<String>,
)
