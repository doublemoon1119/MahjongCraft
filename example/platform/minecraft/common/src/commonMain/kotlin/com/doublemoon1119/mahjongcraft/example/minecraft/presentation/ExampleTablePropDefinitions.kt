package com.doublemoon1119.mahjongcraft.example.minecraft.presentation

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropKindDefinition

/** 範例各版本 adapter 共用的規則物件定義。 */
object ExampleTablePropDefinitions {
    /** 攤牌籌碼的實際模型尺寸與唯一接受的公開內容。 */
    val HAND_REVEAL_CHIP = TablePropKindDefinition(
        id = ExampleMinecraftPresentationIds.HAND_REVEAL_CHIP_KIND,
        dimensions = TablePropDimensions(width = 0.06, height = 0.012, depth = 0.06),
        acceptsContent = { it == TablePropContent.CustomVariant(ExampleMinecraftPresentationIds.HAND_REVEAL_CHIP_VARIANT) },
    )
}
