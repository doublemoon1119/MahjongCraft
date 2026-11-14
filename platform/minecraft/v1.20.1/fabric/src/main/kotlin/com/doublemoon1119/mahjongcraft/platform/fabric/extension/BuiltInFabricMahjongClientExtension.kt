package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterPreviewHandle
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongBotEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import net.minecraft.entity.Entity

/** 內建角色也透過客戶端公開登記入口建立預覽。 */
internal object BuiltInFabricMahjongClientExtension : FabricMahjongClientExtension {
    override val id: String = "mahjongcraft:built_in_client"

    override fun registerCharacterPreviews(registry: FabricAiCharacterPreviewRegistrar) {
        registry.register(
            definition = FabricAiCharacterPreviewDefinition(BuiltInAiCharacters.TILE_MASCOT_CAPABILITY_ID) { world, character ->
                val model = MahjongBotEntity(world = world)
                model.faceAssetKey = (character.face as? AiCharacterFace.Tile)?.assetKey ?: model.faceAssetKey
                object : FabricAiCharacterPreviewHandle {
                    override val entity: Entity = model

                    override fun close() {
                        model.discard()
                    }
                }
            },
        )
    }
}
