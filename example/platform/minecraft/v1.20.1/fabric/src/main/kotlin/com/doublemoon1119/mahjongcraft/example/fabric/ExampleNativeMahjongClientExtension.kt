package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds
import com.doublemoon1119.mahjongcraft.example.fabric.character.client.ExampleActorPreviews
import com.doublemoon1119.mahjongcraft.example.fabric.table.ExampleHandRevealChipEntities
import com.doublemoon1119.mahjongcraft.example.fabric.table.ExampleHandRevealChipTablePropKind
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.table.ExampleHandRevealChipEntity
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleTablePropDefinitions
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.AiCharacterPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.AiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.TablePropPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.tableprop.TablePropPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.NativeMahjongClientExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent

/** 只由 client_extension 載入的模型工廠，不使專用伺服器掃描客戶端程式。 */
class ExampleNativeMahjongClientExtension : NativeMahjongClientExtension {
    /** 與共用及伺服器擴充相同的來源 ID。 */
    override val id: String = ExampleExtensionIds.EXTENSION

    /** 登記工廠，不在初始化時建立模型或查詢原生實體類型。 */
    override fun registerCharacterPreviews(registry: AiCharacterPreviewRegistrar) {
        registry.register(AiCharacterPreviewDefinition(ExampleCharacterIds.ACTOR, ExampleActorPreviews.PROVIDER))
    }

    /** 登記攤牌籌碼的預覽，讓牌譜回放等客戶端場景也畫得出伺服器生成的同一種物件。 */
    override fun registerTablePropPreviews(registry: TablePropPreviewRegistrar) {
        registry.register(
            definition = TablePropPreviewDefinition(ExampleTablePropDefinitions.HAND_REVEAL_CHIP) { world, target ->
                if (target.content != TablePropContent.CustomVariant(ExampleHandRevealChipTablePropKind.HAND_REVEALED_VARIANT)) {
                    null
                } else {
                    ExampleHandRevealChipEntity(ExampleHandRevealChipEntities.handRevealChip, world)
                }
            },
        )
    }
}
