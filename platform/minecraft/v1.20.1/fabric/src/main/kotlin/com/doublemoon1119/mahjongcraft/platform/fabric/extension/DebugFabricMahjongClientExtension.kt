package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterPreviewHandle
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterPreviewRegion
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.character.DebugSeatedParticipantAdapter
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.ArmorStandEntity

/** 開發環境以獨立護甲座模型驗證沒有明確 FACE 的角色頭像。 */
internal object DebugFabricMahjongClientExtension : FabricMahjongClientExtension {
    override val id: String = "mahjongcraft:debug_client"

    override fun registerCharacterPreviews(registry: FabricAiCharacterPreviewRegistrar) {
        registry.register(
            FabricAiCharacterPreviewDefinition(DebugSeatedParticipantAdapter.ID) { world, _ ->
                val model = ArmorStandEntity(world, 0.0, 0.0, 0.0)
                object : FabricAiCharacterPreviewHandle {
                    override val entity: Entity = model

                    override val portraitRegion: FabricAiCharacterPreviewRegion = FabricAiCharacterPreviewRegion(0.25f, 0f, 0.75f, 0.4f)

                    override fun close() {
                        model.discard()
                    }
                }
            },
        )
    }
}
