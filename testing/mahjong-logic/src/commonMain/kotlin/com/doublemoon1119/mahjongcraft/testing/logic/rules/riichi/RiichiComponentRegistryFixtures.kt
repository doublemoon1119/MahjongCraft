package com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.registerRiichiDiscardComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.registerRiichiPlayerComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.registerRiichiTableComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.registerRiichiTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents

/**
 * 建立已凍結的組件登記，含內建牌張組件與日麻的牌張、玩家、對局與捨牌組件。
 *
 * @return 日麻測試使用的完整組件登記。
 */
fun riichiComponentRegistryForTests(): ComponentRegistry = ComponentRegistryImpl().apply {
    BuiltInComponentOwners.ALL.forEach(::registerOwner)
    register(BuiltInTileComponents.FACE_ACCESS)
    register(BuiltInTileComponents.POSE)
    register(BuiltInTileComponents.APPEARANCE)
    registerRiichiTileComponents(this)
    registerRiichiPlayerComponents(this)
    registerRiichiTableComponents(this)
    registerRiichiDiscardComponents(this)
    freeze()
}
