package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomAdmissionState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterPreview
import kotlin.uuid.Uuid

/**
 * 只轉換已授權的公開角色描述，不使用保存格式當作線路格式。
 *
 * @return 同一桌子與場次的線路快照。
 */
fun AiCharacterSyncSnapshot.toPayload(): AiCharacterSyncPayload = AiCharacterSyncPayload(
    sessionId = sessionId.toString(),
    revision = revision,
    tableId = tableId.toString(),
    matchId = matchId?.toString(),
    available = isAvailable,
    players = players.map { (player, snapshot) -> snapshot.toNetworkDto(player.toString()) },
    excludedPlayers = excludedPlayers.map { (player, snapshot) -> snapshot.toNetworkDto(player.toString()) },
    worldEntityIds = worldEntityIds.entries.associate { (player, entity) -> player.toString() to entity.toString() },
)

/**
 * 將一份已授權角色描述轉成線路資料，不包含其他成員。
 *
 * @param playerId 同一場的成員編號。
 * @return 已擷取的公開外觀。
 */
fun AiCharacterSnapshot.toNetworkDto(playerId: String): AiCharacterSnapshotNetworkDto = AiCharacterSnapshotNetworkDto(playerId, characterId, definition?.toNetworkDto(), displayName)

/**
 * 驗證一份角色線路描述。
 *
 * @return 與傳送內容相同的角色快照。
 */
fun AiCharacterSnapshotNetworkDto.toCharacterSnapshot(): AiCharacterSnapshot = AiCharacterSnapshot(characterId, definition?.toDomain(), displayName)

/**
 * 驗證有界描述後建立客戶端快照；不補上未傳送的登記預設。
 *
 * @return 已驗證的角色資料。
 */
fun AiCharacterSyncPayload.toSnapshot(): AiCharacterSyncSnapshot {
    require(players.size <= AiCharacterSyncPayload.MAX_PLAYERS) { "Too many character sync players" }
    val entries = players.map { Uuid.parse(it.playerId) to AiCharacterSnapshot(it.characterId, it.definition?.toDomain(), it.displayName) }
    require(entries.distinctBy { it.first }.size == entries.size) { "Duplicate character player" }
    require(excludedPlayers.size <= RoomAdmissionState.MAX_EXCLUDED_PARTICIPANTS) { "Too many excluded character players" }
    val excluded = excludedPlayers.map {
        val snapshot = it.toCharacterSnapshot()
        require(snapshot.definition?.preview == null && snapshot.definition?.worldCapabilityId == null) { "Excluded appearances cannot contain model capabilities" }
        Uuid.parse(it.playerId) to snapshot
    }
    require(excluded.distinctBy { it.first }.size == excluded.size) { "Duplicate excluded character player" }
    require(worldEntityIds.size <= AiCharacterSyncPayload.MAX_PLAYERS) { "Too many world entity references" }
    val references = worldEntityIds.entries.map { (player, entity) -> Uuid.parse(player) to Uuid.parse(entity) }
    require(references.map { it.first }.toSet().size == references.size) { "Duplicate world entity player" }
    return AiCharacterSyncSnapshot(
        sessionId = Uuid.parse(sessionId),
        revision = revision,
        tableId = Uuid.parse(tableId),
        isAvailable = available,
        catalogue = emptyList(),
        players = entries.toMap(),
        matchId = matchId?.let(Uuid::parse),
        excludedPlayers = excluded.toMap(),
        worldEntityIds = references.toMap(),
    )
}

/**
 * 擷取宣告式外觀，不帶原生物件。
 *
 * @return 有界線路描述。
 */
private fun AiCharacterDefinition.toNetworkDto(): AiCharacterDefinitionNetworkDto = AiCharacterDefinitionNetworkDto(
    id = id,
    nameKey = nameKey,
    faceKind = when (face) {
        null -> null
        is AiCharacterFace.Tile -> "tile"
        is AiCharacterFace.TextureRegion -> "texture"
    },
    faceResource = when (val value = face) {
        null -> null
        is AiCharacterFace.Tile -> value.assetKey
        is AiCharacterFace.TextureRegion -> value.resourceId
    },
    faceRegion = (face as? AiCharacterFace.TextureRegion)?.let { listOf(it.u0, it.v0, it.u1, it.v1) },
    worldCapabilityId = worldCapabilityId,
    previewCapabilityId = preview?.capabilityId,
    previewParameters = preview?.parameters,
)

/**
 * 驗證頭像種類與欄位對應，拒絕矛盾或超出規格的描述。
 *
 * @return 不帶回呼的角色描述。
 */
private fun AiCharacterDefinitionNetworkDto.toDomain(): AiCharacterDefinition {
    val face = when (faceKind) {
        null -> {
            require(faceResource == null && faceRegion == null)
            null
        }
        "tile" -> {
            require(faceRegion == null)
            AiCharacterFace.Tile(requireNotNull(faceResource))
        }
        "texture" -> {
            val region = requireNotNull(faceRegion)
            require(region.size == 4) { "Invalid character texture region" }
            AiCharacterFace.TextureRegion(requireNotNull(faceResource), region[0], region[1], region[2], region[3])
        }
        else -> throw IllegalArgumentException("Unknown character face kind")
    }
    val preview = if (previewCapabilityId == null) {
        require(previewParameters == null)
        null
    } else {
        AiCharacterPreview(previewCapabilityId, requireNotNull(previewParameters))
    }
    return AiCharacterDefinition(id, nameKey, face, worldCapabilityId, preview)
}
