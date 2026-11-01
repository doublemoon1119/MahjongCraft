package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterHandle
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongBotEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest

/**
 * 內建角色保持面向牌桌，只提供出牌回饋；注視沿用介面的無動作預設。
 *
 * @property entity 尚未生成或正在接管的牌體角色。
 */
internal class TileMascotCharacterHandle(
    override val entity: MahjongBotEntity,
) : FabricAiCharacterHandle {
    override val ownership: AiCharacterOwnership = AiCharacterOwnership.OWNED

    override fun prepare(): Boolean = !entity.isRemoved

    override fun release() = Unit

    override fun animate(actionId: String) {
        if (actionId == AiCharacterActionIds.DISCARD) entity.swingHand()
    }

    override fun seatAssigned(request: SeatRequest) {
        entity.seatYaw = request.seat.yaw
        entity.yaw = request.seat.yaw
        entity.pitch = 0f
    }
}
