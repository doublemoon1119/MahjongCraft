package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes

/**
 * 註冊 MahjongCraft 內建擴充牌種對應的 asset key。
 *
 * 內建映射與第三方映射共用 [MinecraftTileAssetRegistry.register]，不具有覆寫或繞過重複 ID 驗證的
 * 特權。
 */
fun MinecraftTileAssetRegistry.registerBuiltInTileAssets() {
    registerRiichiTileAssets()
    registerTaiwanTileAssets()
}

/** 註冊日麻赤五的 asset key。 */
fun MinecraftTileAssetRegistry.registerRiichiTileAssets() {
    register(RiichiTileTypes.RED_FIVE_CHARACTER, "m5_red")
    register(RiichiTileTypes.RED_FIVE_DOT, "p5_red")
    register(RiichiTileTypes.RED_FIVE_BAMBOO, "s5_red")
}

/** 註冊台麻花牌的 asset key。 */
fun MinecraftTileAssetRegistry.registerTaiwanTileAssets() {
    register(TaiwanTileTypes.SPRING, "flower_spring")
    register(TaiwanTileTypes.SUMMER, "flower_summer")
    register(TaiwanTileTypes.AUTUMN, "flower_autumn")
    register(TaiwanTileTypes.WINTER, "flower_winter")
    register(TaiwanTileTypes.PLUM, "flower_plum")
    register(TaiwanTileTypes.ORCHID, "flower_orchid")
    register(TaiwanTileTypes.BAMBOO, "flower_bamboo")
    register(TaiwanTileTypes.CHRYSANTHEMUM, "flower_chrysanthemum")
}
