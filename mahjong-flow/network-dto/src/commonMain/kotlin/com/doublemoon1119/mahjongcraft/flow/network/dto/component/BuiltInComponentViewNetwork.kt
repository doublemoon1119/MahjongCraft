package com.doublemoon1119.mahjongcraft.flow.network.dto.component

import com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration.ComponentViewNetworkRegistrar
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponentViews

/**
 * 登記規則中立內建組件對外值的網路格式。
 *
 * @param registry 尚未凍結的對外值網路登記。
 */
fun registerBuiltInComponentViewNetworkDtos(registry: ComponentViewNetworkRegistrar) {
    registry.register(
        definition = ComponentViewNetworkDefinition(
            viewType = BuiltInTileComponentViews.TRANSPARENT,
            dtoClass = TransparentTileViewDto::class,
            serializer = TransparentTileViewDto.serializer(),
            toDto = { TransparentTileViewDto },
            toValue = {},
        ),
    )
}
