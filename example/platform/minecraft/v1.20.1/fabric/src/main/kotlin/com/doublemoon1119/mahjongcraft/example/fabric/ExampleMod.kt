package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds
import com.doublemoon1119.mahjongcraft.example.fabric.event.ExampleGameEventChat
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.fabric.registry.ExampleItemGroups
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import net.fabricmc.api.ModInitializer

/** 範例 mod 的共用啟動入口，不建立第二份 MahjongCraft 依賴注入容器。 */
class ExampleMod : ModInitializer {
    /** 記錄範例 mod 的啟動狀態。 */
    private val logger = mahjongCraftLogger(ExampleMod::class)

    /** 先完成原生家具與分頁註冊，再訂閱此程序的遊戲事件。 */
    override fun onInitialize() {
        ExampleFurniture.register()
        ExampleItemGroups.register()
        ExampleGameEventChat.register()
        logger.info("Example mod loaded: {}", ExampleExtensionIds.MOD_ID)
    }
}
