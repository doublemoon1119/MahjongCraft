package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType

/** 讀取者已獲准知道牌面後，可取得的規則中立牌張特性。 */
object BuiltInTileComponentViews {
    /** 這張已知牌面是透明牌，平台以透明牌身呈現。 */
    val TRANSPARENT: ComponentViewType<Unit> = ComponentViewType(
        id = "mahjongcraft:tile_view/transparent",
        valueClass = Unit::class,
        copyValue = {},
    )
}
