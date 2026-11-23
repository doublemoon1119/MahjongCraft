package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.character.ExampleActors
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.event.ExampleGameEventChat
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleItemGroups
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.table.ExampleHandRevealChipEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry

/**
 * 範例 mod 的共用啟動入口，不建立第二份 MahjongCraft 依賴注入容器。
 *
 * 範例內容在共用程式中列出，這裡以 Fabric 的方式寫入登記表，並接上生物屬性與互動事件。
 */
class ExampleMod : ModInitializer {
    /** 範例 mod 載入狀態的記錄器。 */
    private val logger = mahjongCraftLogger(ExampleMod::class)

    /** 依原生物件、角色、牌桌道具、物品分頁、遊戲事件的順序登記範例內容。 */
    override fun onInitialize() {
        ExampleFurniture.register(FabricExampleRegistrar)
        ExampleActors.register(FabricExampleRegistrar)
        ExampleActors.defaultAttributes().forEach { (type, attributes) -> FabricDefaultAttributeRegistry.register(type, attributes) }
        UseEntityCallback.EVENT.register { player, world, hand, entity, _ -> ExampleActors.useOnCushion(player, world, hand, entity) }
        ExampleHandRevealChipEntities.register(FabricExampleRegistrar)
        ExampleItemGroups.register(FabricExampleRegistrar)
        ExampleGameEventChat.register()
        logger.info("Example mod loaded: {}", ExampleExtensionIds.MOD_ID)
    }
}
