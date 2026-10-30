package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType

/** 內建與第三方規則共用的標準組件定義。 */
object BuiltInTileComponents {
    /** 額外牌面授權；授權名單不直接進入觀察者資料。 */
    val FACE_ACCESS = ComponentType(
        target = BuiltInComponentOwners.TILE,
        id = "mahjongcraft:tile/face_access",
        valueClass = TileFaceAccess::class,
        copyValue = TileFaceAccess::detached,
        mergeValues = TileFaceAccess::union,
        mergePeerValues = TileFaceAccess::union,
    )

    /** 共同姿態；未設定時依目前區域與已成立效果決定。 */
    val POSE = ComponentType(
        target = BuiltInComponentOwners.TILE,
        id = "mahjongcraft:tile/pose",
        valueClass = TilePose::class,
        copyValue = { it },
        mergeValues = { _, higher -> higher },
    )

    /** 可公開的外觀樣式 ID；對應平台須另行登記素材。 */
    val APPEARANCE = ComponentType(
        target = BuiltInComponentOwners.TILE,
        id = "mahjongcraft:tile/appearance",
        valueClass = String::class,
        copyValue = { it },
        validateValue = { NamespacedId.requireValid(it) { "Tile appearance ID must be namespaced: $it" } },
    )
}
