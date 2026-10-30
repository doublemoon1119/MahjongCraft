package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import kotlin.uuid.Uuid

/**
 * 透過與第三方相同的入口登記規則中立的標準組件保存轉換。
 *
 * @param registry 要登記的保存 registry。
 */
fun registerBuiltInComponentPersistence(registry: ComponentPersistenceRegistry) {
    registry.register(
        ComponentPersistenceDefinition(
            component = BuiltInTileComponents.FACE_ACCESS,
            serializer = TileFaceAccessPersistenceDto.serializer(),
            toDto = { access ->
                when (access) {
                    TileFaceAccess.None -> TileFaceAccessPersistenceDto("NONE", emptySet())
                    TileFaceAccess.Public -> TileFaceAccessPersistenceDto("PUBLIC", emptySet())
                    is TileFaceAccess.Players -> TileFaceAccessPersistenceDto("PLAYERS", access.playerIds.map(Uuid::toString).toSet())
                }
            },
            toDomain = { dto ->
                when (dto.kind) {
                    "NONE" -> {
                        require(dto.playerIds.isEmpty())
                        TileFaceAccess.None
                    }
                    "PUBLIC" -> {
                        require(dto.playerIds.isEmpty())
                        TileFaceAccess.Public
                    }
                    "PLAYERS" -> TileFaceAccess.Players(dto.playerIds.map(Uuid::parse).toSet())
                    else -> error("Unknown saved tile access kind: ${dto.kind}")
                }
            },
        ),
    )
    registry.register(
        ComponentPersistenceDefinition(
            component = BuiltInTileComponents.POSE,
            serializer = ComponentStringPersistenceDto.serializer(),
            toDto = { ComponentStringPersistenceDto(it.name) },
            toDomain = { TilePose.valueOf(it.value) },
        ),
    )
    registry.register(
        ComponentPersistenceDefinition(
            component = BuiltInTileComponents.APPEARANCE,
            serializer = ComponentStringPersistenceDto.serializer(),
            toDto = ::ComponentStringPersistenceDto,
            toDomain = { it.value },
        ),
    )
}
