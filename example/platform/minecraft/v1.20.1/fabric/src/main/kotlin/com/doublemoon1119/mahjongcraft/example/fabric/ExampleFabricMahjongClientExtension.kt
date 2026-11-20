package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds
import com.doublemoon1119.mahjongcraft.example.fabric.character.client.ExampleActorPreviews
import com.doublemoon1119.mahjongcraft.example.fabric.table.ExampleHandRevealChipEntities
import com.doublemoon1119.mahjongcraft.example.fabric.table.ExampleHandRevealChipEntity
import com.doublemoon1119.mahjongcraft.example.fabric.table.ExampleHandRevealChipTablePropKind
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleTablePropDefinitions
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricTablePropPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop.FabricTablePropPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongClientExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent

/** 只由 client_extension 載入的模型工廠，不使專用伺服器掃描客戶端程式。 */
class ExampleFabricMahjongClientExtension : FabricMahjongClientExtension {
    /** 與共用及伺服器擴充相同的來源 ID。 */
    override val id: String = ExampleExtensionIds.EXTENSION

    /** 登記工廠，不在初始化時建立模型或查詢原生實體類型。 */
    override fun registerCharacterPreviews(registry: FabricAiCharacterPreviewRegistrar) {
        registry.register(FabricAiCharacterPreviewDefinition(ExampleCharacterIds.ACTOR, ExampleActorPreviews.PROVIDER))
    }

    /** 登記攤牌籌碼的預覽，讓牌譜回放等客戶端場景也畫得出伺服器生成的同一種物件。 */
    override fun registerTablePropPreviews(registry: FabricTablePropPreviewRegistrar) {
        registry.register(
            definition = FabricTablePropPreviewDefinition(ExampleTablePropDefinitions.HAND_REVEAL_CHIP) { world, target ->
                if (target.content != TablePropContent.CustomVariant(ExampleHandRevealChipTablePropKind.HAND_REVEALED_VARIANT)) {
                    null
                } else {
                    ExampleHandRevealChipEntity(ExampleHandRevealChipEntities.handRevealChip, world)
                }
            },
        )
    }
}
