package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds
import com.doublemoon1119.mahjongcraft.example.fabric.character.ExampleActors
import com.doublemoon1119.mahjongcraft.example.fabric.event.ExampleGameEventChat
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.fabric.registry.ExampleItemGroups
import com.doublemoon1119.mahjongcraft.example.fabric.table.ExampleHandRevealChipEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import net.fabricmc.api.ModInitializer

/** 範例 mod 的共用啟動入口，不建立第二份 MahjongCraft 依賴注入容器。 */
class ExampleMod : ModInitializer {
    /** 記錄範例 mod 的啟動狀態。 */
    private val logger = mahjongCraftLogger(ExampleMod::class)

    /** 先註冊家具、人偶與共用分頁，再訂閱遊戲事件。 */
    override fun onInitialize() {
        ExampleFurniture.register()
        ExampleActors.register()
        ExampleHandRevealChipEntities.register()
        ExampleItemGroups.register()
        ExampleGameEventChat.register()
        logger.info("Example mod loaded: {}", ExampleExtensionIds.MOD_ID)
    }
}
