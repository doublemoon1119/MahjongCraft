package com.doublemoon1119.mahjongcraft.example.fabric.table

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry

/** 範例宣告籌碼的 client renderer 註冊入口。 */
object ExampleDeclarationChipRenderers {
    /** 註冊範例宣告籌碼 renderer。 */
    fun register() {
        EntityRendererRegistry.register(ExampleDeclarationChipEntities.declarationChip, ::ExampleDeclarationChipRenderer)
    }
}
