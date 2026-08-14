package com.doublemoon1119.mahjongcraft.flow.common.di

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeDefinition
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry

/**
 * 註冊 MahjongCraft 內建的規則特有牌種。
 *
 * 內建牌與第三方牌共用 [TileTypeRegistry.register]，不具有覆寫或繞過重複 ID 驗證的特權。
 */
fun TileTypeRegistry.registerBuiltInTileTypes() {
    RiichiTileTypes.ALL.forEach { id -> register(TileTypeDefinition(id)) }
}
