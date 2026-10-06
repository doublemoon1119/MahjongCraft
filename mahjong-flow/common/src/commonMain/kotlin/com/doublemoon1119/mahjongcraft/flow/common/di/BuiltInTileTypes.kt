package com.doublemoon1119.mahjongcraft.flow.common.di

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeDefinition
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry

/**
 * 註冊 MahjongCraft 內建的規則特有牌種。
 *
 * 內建牌與第三方牌共用 [TileTypeRegistry.register]，不具有覆寫或繞過重複 ID 驗證的特權。
 */
fun TileTypeRegistry.registerBuiltInTileTypes() {
    registerRiichiTileTypes()
    registerTaiwanTileTypes()
}

/** 註冊日麻特有的牌種（赤五）。 */
fun TileTypeRegistry.registerRiichiTileTypes() {
    RiichiTileTypes.ALL.forEach { id -> register(TileTypeDefinition(id)) }
}

/** 註冊台麻特有的牌種（花牌）。 */
fun TileTypeRegistry.registerTaiwanTileTypes() {
    TaiwanTileTypes.ALL.forEach { id -> register(TileTypeDefinition(id)) }
}
