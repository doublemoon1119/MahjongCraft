package com.doublemoon1119.mahjongcraft.platform.forge

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import net.minecraftforge.fml.common.Mod

/** Forge mod 的初始化入口；載入器接點尚未實作，目前只記錄本 mod 已載入。 */
@Mod(MinecraftModMetadata.MOD_ID)
object MahjongCraftForge {
    /** Forge 啟動的記錄器。 */
    private val logger = mahjongCraftLogger(MahjongCraftForge::class)

    init {
        logger.info("MahjongCraft loaded on Forge")
    }
}
