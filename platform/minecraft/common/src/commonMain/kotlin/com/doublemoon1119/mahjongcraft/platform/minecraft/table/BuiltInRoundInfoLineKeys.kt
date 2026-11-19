package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension

/** 不屬於任何規則的局況行，由 [BuiltInMinecraftMahjongExtension] 登記，任何規則都可以使用。 */
object BuiltInRoundInfoLineKeys {
    /** 目前是第幾局，不帶場風或圈風；參數為局數。 */
    const val ROUND_NUMBER: String = "mahjongcraft:round_number"

    /** 牌牆剩餘張數；參數為張數。 */
    const val WALL_REMAINING: String = "mahjongcraft:wall_remaining"
}
