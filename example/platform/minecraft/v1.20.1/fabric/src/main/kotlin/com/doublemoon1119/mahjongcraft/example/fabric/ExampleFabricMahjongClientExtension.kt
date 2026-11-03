package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds
import com.doublemoon1119.mahjongcraft.example.fabric.character.client.ExampleActorPreviews
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongClientExtension

/** 只由 client_extension 載入的模型工廠，不使專用伺服器掃描客戶端程式。 */
class ExampleFabricMahjongClientExtension : FabricMahjongClientExtension {
    /** 與共用及伺服器擴充相同的來源 ID。 */
    override val id: String = ExampleExtensionIds.EXTENSION

    /** 登記工廠，不在初始化時建立模型或查詢原生實體類型。 */
    override fun registerCharacterPreviews(registry: FabricAiCharacterPreviewRegistrar) {
        registry.register(FabricAiCharacterPreviewDefinition(ExampleCharacterIds.ACTOR, ExampleActorPreviews.PROVIDER))
    }
}
