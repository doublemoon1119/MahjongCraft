package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration.ComponentViewPersistenceRegistrar
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponentViews

/**
 * 登記規則中立內建組件對外值的保存格式。
 *
 * @param registry 尚未凍結的對外值保存登記。
 */
fun registerBuiltInComponentViewPersistence(registry: ComponentViewPersistenceRegistrar) {
    registry.register(
        definition = ComponentViewPersistenceDefinition(
            viewType = BuiltInTileComponentViews.TRANSPARENT,
            serializer = ComponentUnitPersistenceDto.serializer(),
            toDto = { ComponentUnitPersistenceDto },
            toDomain = {},
        ),
    )
}
