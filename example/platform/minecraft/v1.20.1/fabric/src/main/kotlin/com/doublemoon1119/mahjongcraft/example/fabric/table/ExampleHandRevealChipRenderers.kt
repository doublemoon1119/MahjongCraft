package com.doublemoon1119.mahjongcraft.example.fabric.table

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry

/** 範例攤牌籌碼的 client renderer 註冊入口。 */
object ExampleHandRevealChipRenderers {
    /** 註冊範例攤牌籌碼 renderer。 */
    fun register() {
        EntityRendererRegistry.register(ExampleHandRevealChipEntities.handRevealChip, ::ExampleHandRevealChipRenderer)
    }
}
