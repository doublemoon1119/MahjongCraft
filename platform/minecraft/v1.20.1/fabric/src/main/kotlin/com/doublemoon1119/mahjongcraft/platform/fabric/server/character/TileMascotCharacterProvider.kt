package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.AiCharacterContext
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.AiCharacterHandle
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.AiCharacterProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongBotEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import net.minecraft.network.chat.Component

/** 由與第三方相同的原生能力入口建立內建牌體角色，不在工廠生成世界實體。 */
internal object TileMascotCharacterProvider : AiCharacterProvider {
    override fun acquire(context: AiCharacterContext): AiCharacterHandle {
        val entity = MahjongBotEntity(world = context.world).apply {
            faceAssetKey = (context.character.face as? AiCharacterFace.Tile)?.assetKey ?: UNKNOWN_TILE_ASSET_KEY
            customName = Component.translatable(context.character.nameKey)
            seatYaw = context.seat.seat.yaw
        }
        return TileMascotCharacterHandle(entity)
    }
}
