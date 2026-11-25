package com.doublemoon1119.mahjongcraft.example.minecraft.mod.client

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.character.ExampleActors
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.client.ExampleFurnitureRenderers
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleRendererRegistrar
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.table.ExampleHandRevealChipEntities
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.table.ExampleHandRevealChipRenderer
import net.minecraft.client.renderer.entity.ArmorStandRenderer

/** 範例所有實體的 client renderer；各載入器的用戶端入口以自己的方式登記。 */
object ExampleRenderers {
    /**
     * 登記範例家具、人偶與攤牌籌碼的 renderer；人偶沿用原版盔甲座的繪製方式。
     *
     * @param registrar 載入器的繪製器登記方式。
     */
    fun register(registrar: ExampleRendererRegistrar) {
        ExampleFurnitureRenderers.register(registrar)
        registrar.register(ExampleActors.actorType, ::ArmorStandRenderer)
        registrar.register(ExampleActors.proxyType, ::ArmorStandRenderer)
        registrar.register(ExampleHandRevealChipEntities.handRevealChip, ::ExampleHandRevealChipRenderer)
    }
}
