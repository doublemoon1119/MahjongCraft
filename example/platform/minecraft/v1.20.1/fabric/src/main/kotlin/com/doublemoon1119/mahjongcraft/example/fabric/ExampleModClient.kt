package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import net.fabricmc.api.ClientModInitializer

/** 只在客戶端載入的範例入口，與專用伺服器的啟動流程分開。 */
class ExampleModClient : ClientModInitializer {
    /** 記錄客戶端入口的載入狀態。 */
    private val logger = mahjongCraftLogger(ExampleModClient::class)

    override fun onInitializeClient() {
        logger.info("Example client initialized")
    }
}
