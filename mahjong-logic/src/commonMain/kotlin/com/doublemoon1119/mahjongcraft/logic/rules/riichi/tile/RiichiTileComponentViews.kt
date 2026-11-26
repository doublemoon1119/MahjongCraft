package com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile

import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType

/** 讀取者已獲准知道牌面後，可取得的日麻牌張特性。 */
object RiichiTileComponentViews {
    /** 這張已知牌面具有赤寶牌特性，不代表它本身是起和役。 */
    val RED_DORA: ComponentViewType<Unit> = ComponentViewType(
        id = "mahjongcraft:riichi/tile_view/red_dora",
        valueClass = Unit::class,
        copyValue = {},
    )

    /** 這張公開牌是目前生效的表寶牌指示牌，不能從一般摸牌取得。 */
    val DORA_INDICATOR = ComponentViewType(
        id = "mahjongcraft:riichi/tile_view/dora_indicator",
        valueClass = Unit::class,
        copyValue = {},
    )
}
