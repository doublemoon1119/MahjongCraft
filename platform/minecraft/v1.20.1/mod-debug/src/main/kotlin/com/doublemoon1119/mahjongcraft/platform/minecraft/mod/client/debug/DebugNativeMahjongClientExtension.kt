package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.AiCharacterPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.AiCharacterPreviewHandle
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.AiCharacterPreviewRegion
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.AiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.NativeMahjongClientExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.character.DebugSeatedParticipantAdapter
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand

/** 開發環境以獨立護甲座模型驗證沒有明確 FACE 的角色頭像。 */
internal object DebugNativeMahjongClientExtension : NativeMahjongClientExtension {
    override val id: String = "mahjongcraft:debug_client"

    override fun registerCharacterPreviews(registry: AiCharacterPreviewRegistrar) {
        registry.register(
            definition = AiCharacterPreviewDefinition(DebugSeatedParticipantAdapter.ID) { world, _ ->
                val model = ArmorStand(world, 0.0, 0.0, 0.0)
                object : AiCharacterPreviewHandle {
                    override val entity: Entity = model

                    override val portraitRegion: AiCharacterPreviewRegion = AiCharacterPreviewRegion(0.25f, 0f, 0.75f, 0.4f)

                    override fun close() {
                        model.discard()
                    }
                }
            },
        )
    }
}
