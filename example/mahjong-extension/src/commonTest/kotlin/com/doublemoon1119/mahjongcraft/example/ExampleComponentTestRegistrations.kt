package com.doublemoon1119.mahjongcraft.example

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents

/**
 * 建立只含基礎組件及範例自身登記的測試環境，不載入內建規則。
 *
 * @return 已凍結且可供正式建立與投影流程使用的組件 registry。
 */
internal fun exampleComponentRegistry(): ComponentRegistry = ComponentRegistryImpl().apply {
    BuiltInComponentOwners.ALL.forEach(::registerOwner)
    register(BuiltInTileComponents.FACE_ACCESS)
    register(BuiltInTileComponents.POSE)
    register(BuiltInTileComponents.APPEARANCE)
    ExampleMahjongExtension().registerComponents(this)
    freeze()
}
