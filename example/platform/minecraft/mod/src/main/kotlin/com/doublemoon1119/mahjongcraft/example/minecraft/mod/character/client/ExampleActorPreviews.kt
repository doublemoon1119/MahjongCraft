package com.doublemoon1119.mahjongcraft.example.minecraft.mod.character.client

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.character.ExampleActors
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.AiCharacterPreviewHandle
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.AiCharacterPreviewProvider
import net.minecraft.world.entity.Entity

/** 只從公開描述建立獨立人偶。不搜尋世界本人、不加入世界或執行 tick。 */
internal object ExampleActorPreviews {
    /** 本人不可用及歷史畫面共用的模型備援。 */
    val PROVIDER: AiCharacterPreviewProvider = AiCharacterPreviewProvider { world, _ ->
        val actor = ExampleActors.actorType.create(world) ?: return@AiCharacterPreviewProvider null
        object : AiCharacterPreviewHandle {
            /** 工廠自己的獨立模型，不是世界本人。 */
            override val entity: Entity = actor

            /** 釋放自己的獨立模型，不影響世界人偶。 */
            override fun close() {
                actor.discard()
            }
        }
    }
}
