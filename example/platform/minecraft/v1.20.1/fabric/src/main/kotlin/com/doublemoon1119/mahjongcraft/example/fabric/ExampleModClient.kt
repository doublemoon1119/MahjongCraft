package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.client.ExampleFurnitureRenderers
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import net.fabricmc.api.ClientModInitializer

/** 只在客戶端載入的範例入口，與專用伺服器的啟動流程分開。 */
class ExampleModClient : ClientModInitializer {
    /** 記錄客戶端入口的載入狀態。 */
    private val logger = mahjongCraftLogger(ExampleModClient::class)

    /** 在客戶端註冊家具 renderer，不從伺服器入口載入繪圖類別。 */
    override fun onInitializeClient() {
        ExampleFurnitureRenderers.register()
        logger.info("Example client initialized")
    }
}
