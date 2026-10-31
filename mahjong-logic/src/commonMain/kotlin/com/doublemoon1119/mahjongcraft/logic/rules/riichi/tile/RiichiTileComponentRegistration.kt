package com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile

import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionDefinition
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig

/**
 * 登記日麻牌張的計分特性與牌面投影。
 *
 * 固定特性隨牌保留，指示牌用途由規則按當前公開進度解算。牌面讀取入口先驗證權限，才呼叫特性投影。
 *
 * @param registry 本次執行尚未凍結的組件登記。
 */
fun registerRiichiTileComponents(registry: ComponentRegistrar) {
    registry.register(RiichiTileComponents.DORA_INDICATOR)
    registry.registerView(RiichiTileComponentViews.DORA_INDICATOR)
    registry.registerProjection(
        ComponentProjectionDefinition(
            id = "mahjongcraft:riichi/projection/dora_indicator",
            ownerType = BuiltInComponentOwners.TILE,
            component = RiichiTileComponents.DORA_INDICATOR,
            viewType = RiichiTileComponentViews.DORA_INDICATOR,
            appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
            project = { _, _ -> Unit },
        ),
    )
    registry.register(RiichiTileComponents.RED_DORA)
    registry.registerView(RiichiTileComponentViews.RED_DORA)
    registry.registerProjection(
        ComponentProjectionDefinition(
            id = "mahjongcraft:riichi/projection/red_dora",
            ownerType = BuiltInComponentOwners.TILE,
            component = RiichiTileComponents.RED_DORA,
            viewType = RiichiTileComponentViews.RED_DORA,
            appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
            project = { _, _ -> Unit },
        ),
    )
}
