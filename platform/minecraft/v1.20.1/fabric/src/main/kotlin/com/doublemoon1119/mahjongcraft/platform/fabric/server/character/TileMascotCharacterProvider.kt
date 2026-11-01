package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterContext
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterHandle
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongBotEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import net.minecraft.text.Text

/** 由與第三方相同的原生能力入口建立內建牌體角色，不在工廠生成世界實體。 */
internal object TileMascotCharacterProvider : FabricAiCharacterProvider {
    override fun acquire(context: FabricAiCharacterContext): FabricAiCharacterHandle {
        val entity = MahjongBotEntity(world = context.world).apply {
            faceAssetKey = (context.character.face as? AiCharacterFace.Tile)?.assetKey ?: UNKNOWN_TILE_ASSET_KEY
            customName = Text.translatable(context.character.nameKey)
            seatYaw = context.seat.seat.yaw
        }
        return TileMascotCharacterHandle(entity)
    }
}
