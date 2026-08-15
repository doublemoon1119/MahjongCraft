package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

/** 註冊內建牌種（目前只有日麻赤五）的顯示名稱。 */
fun TileDisplayNameRegistry.registerBuiltInTileDisplayNames() {
    register(RiichiTileTypes.RED_FIVE_CHARACTER, MinecraftMessageKeys.TILE_RED_FIVE_CHARACTER)
    register(RiichiTileTypes.RED_FIVE_DOT, MinecraftMessageKeys.TILE_RED_FIVE_DOT)
    register(RiichiTileTypes.RED_FIVE_BAMBOO, MinecraftMessageKeys.TILE_RED_FIVE_BAMBOO)
}
