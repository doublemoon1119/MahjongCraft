package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

class MahjongCraftMod : ModInitializer {

    private val logger = LoggerFactory.getLogger("mahjongcraft")

    override fun onInitialize() {
        ModItems.register()
        logger.info("MahjongCraft (Fabric, Minecraft 1.20.1) initialized.")
    }
}
