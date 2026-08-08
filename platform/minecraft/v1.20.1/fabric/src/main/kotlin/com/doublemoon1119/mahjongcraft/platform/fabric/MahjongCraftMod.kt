package com.doublemoon1119.mahjongcraft.platform.fabric

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

class MahjongCraftMod : ModInitializer {

    private val logger = LoggerFactory.getLogger("mahjongcraft")

    override fun onInitialize() {
        logger.info("MahjongCraft (Fabric, Minecraft 1.20.1) initialized.")
    }
}
