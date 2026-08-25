package com.doublemoon1119.mahjongcraft.platform.minecraft.metadata

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** Minecraft 各 loader 共用的 MahjongCraft metadata。 */
object MinecraftModMetadata {
    /** Minecraft mod identifier。 */
    const val MOD_ID: String = MahjongCraftMetadata.PROJECT_ID

    /** Minecraft mod 清單中顯示的名稱。 */
    const val MOD_NAME: String = MahjongCraftMetadata.PROJECT_DISPLAY_NAME
}
