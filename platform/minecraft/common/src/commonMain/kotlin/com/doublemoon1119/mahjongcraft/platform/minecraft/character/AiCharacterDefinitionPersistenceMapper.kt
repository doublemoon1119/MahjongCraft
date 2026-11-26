package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterPreview

/**
 * 保留完整頭像與能力描述。
 *
 * @return 不含 callback 的保存 DTO。
 */
internal fun AiCharacterDefinition.toPersistenceDefinitionDto(): AiCharacterDefinitionPersistenceDto = AiCharacterDefinitionPersistenceDto(
    id = id,
    nameKey = nameKey,
    face = when (val source = face) {
        null -> null
        is AiCharacterFace.Tile -> AiCharacterFacePersistenceDto("tile", source.assetKey, null)
        is AiCharacterFace.TextureRegion -> AiCharacterFacePersistenceDto("texture", source.resourceId, listOf(source.u0, source.v0, source.u1, source.v1))
    },
    worldCapabilityId = worldCapabilityId,
    preview = preview?.let { AiCharacterPreviewPersistenceDto(it.capabilityId, it.parameters) },
)

/**
 * 驗證並還原描述，不查詢登記表。
 *
 * @return 與保存內容相同的描述。
 */
internal fun AiCharacterDefinitionPersistenceDto.toDefinition(): AiCharacterDefinition = AiCharacterDefinition(
    id = id,
    nameKey = nameKey,
    face = face?.let { source ->
        when (source.kind) {
            "tile" -> {
                require(source.region == null) { "Tile character face cannot have texture coordinates" }
                AiCharacterFace.Tile(source.resource)
            }
            "texture" -> {
                val region = requireNotNull(source.region) { "Missing character face texture coordinates" }
                require(region.size == 4) { "Invalid character face coordinate count" }
                AiCharacterFace.TextureRegion(source.resource, region[0], region[1], region[2], region[3])
            }
            else -> error("Unknown character face kind: ${source.kind}")
        }
    },
    worldCapabilityId = worldCapabilityId,
    preview = preview?.let { AiCharacterPreview(it.capabilityId, it.parameters) },
)
