package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterPreview
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** 角色保存資料的固定格式；不使用網路 JSON 設定，也不依賴目前登記的 provider。 */
class AiCharacterMetadataCodec {
    /** 嚴格保存所有欄位，包括可空欄位。 */
    private val format = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    /**
     * 將角色資料轉成可保存的文字。
     *
     * @param metadata 同一保存封套中的角色資料。
     * @return 完整保存內容。
     */
    fun encode(metadata: AiCharacterMetadata): String = format.encodeToString(
        MetadataDto(
            selections = metadata.selections.entries.sortedBy { it.key.toString() }.map { (table, players) ->
                SelectionDto(table.toString(), players.entries.sortedBy { it.key.toString() }.map { (player, character) -> ChoiceDto(player.toString(), character) })
            },
            matches = metadata.matches.entries.sortedBy { it.key.toString() }.map { (match, players) ->
                MatchDto(
                    match.toString(),
                    players.entries.sortedBy { it.key.toString() }.map { (player, snapshot) ->
                        SnapshotDto(player.toString(), snapshot.characterId, snapshot.definition?.toDto())
                    },
                )
            },
        ),
    )

    /**
     * 嚴格讀取保存資料，不以目前角色描述取代歷史內容。
     *
     * @param encoded 完整保存文字。
     * @return 已驗證的獨立角色資料。
     */
    fun decode(encoded: String): AiCharacterMetadata {
        val dto = format.decodeFromString<MetadataDto>(encoded)
        require(dto.selections.distinctBy { Uuid.parse(it.tableId) }.size == dto.selections.size) { "Duplicate character selection table" }
        require(dto.matches.distinctBy { Uuid.parse(it.matchId) }.size == dto.matches.size) { "Duplicate character snapshot match" }
        return AiCharacterMetadata(
            selections = dto.selections.associate { entry ->
                require(entry.players.distinctBy { Uuid.parse(it.playerId) }.size == entry.players.size) { "Duplicate character selection player" }
                Uuid.parse(entry.tableId) to entry.players.associate { Uuid.parse(it.playerId) to it.characterId }
            },
            matches = dto.matches.associate { entry ->
                require(entry.players.distinctBy { Uuid.parse(it.playerId) }.size == entry.players.size) { "Duplicate character snapshot player" }
                Uuid.parse(entry.matchId) to entry.players.associate { player ->
                    Uuid.parse(player.playerId) to AiCharacterSnapshot(player.characterId, player.definition?.toDomain())
                }
            },
        )
    }

    /**
     * 保留完整頭像與能力描述。
     *
     * @return 不含 callback 的保存 DTO。
     */
    private fun AiCharacterDefinition.toDto(): DefinitionDto = DefinitionDto(
        id = id,
        nameKey = nameKey,
        face = when (val source = face) {
            null -> null
            is AiCharacterFace.Tile -> FaceDto("tile", source.assetKey, null)
            is AiCharacterFace.TextureRegion -> FaceDto("texture", source.resourceId, listOf(source.u0, source.v0, source.u1, source.v1))
        },
        worldCapabilityId = worldCapabilityId,
        preview = preview?.let { PreviewDto(it.capabilityId, it.parameters) },
    )

    /**
     * 驗證並還原描述，不查詢登記表。
     *
     * @return 與保存內容相同的描述。
     */
    private fun DefinitionDto.toDomain(): AiCharacterDefinition = AiCharacterDefinition(
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

    /**
     * 保存區段根資料。
     *
     * @property selections 目前選擇。
     * @property matches 各場固定外觀。
     */
    @Serializable
    private data class MetadataDto(val selections: List<SelectionDto>, val matches: List<MatchDto>)

    /**
     * 一張桌子的角色選擇。
     *
     * @property tableId 桌子 UUID。
     * @property players 該桌各 AI 的選擇。
     */
    @Serializable
    private data class SelectionDto(val tableId: String, val players: List<ChoiceDto>)

    /**
     * 一名 AI 的目前選擇。
     *
     * @property playerId AI UUID。
     * @property characterId 角色 ID。
     */
    @Serializable
    private data class ChoiceDto(val playerId: String, val characterId: String)

    /**
     * 一場對局的固定角色。
     *
     * @property matchId 場次 UUID。
     * @property players 開局時各 AI 的描述。
     */
    @Serializable
    private data class MatchDto(val matchId: String, val players: List<SnapshotDto>)

    /**
     * 開局時一名 AI 的外觀。
     *
     * @property playerId AI UUID。
     * @property characterId 保留的角色 ID。
     * @property definition 當時可取得的描述。
     */
    @Serializable
    private data class SnapshotDto(
        val playerId: String,
        val characterId: String,
        val definition: DefinitionDto?,
    )

    /**
     * 角色的完整固定描述。
     *
     * @property id 角色 ID。
     * @property nameKey 名稱翻譯鍵。
     * @property face 頭像來源。
     * @property worldCapabilityId 世界角色能力。
     * @property preview 模型預覽描述。
     */
    @Serializable
    private data class DefinitionDto(
        val id: String,
        val nameKey: String,
        val face: FaceDto?,
        val worldCapabilityId: String?,
        val preview: PreviewDto?,
    )

    /**
     * 可保存的頭像來源。
     *
     * @property kind 來源種類。
     * @property resource 素材鍵或貼圖 ID。
     * @property region 貼圖區域；牌面來源為 null。
     */
    @Serializable
    private data class FaceDto(
        val kind: String,
        val resource: String,
        val region: List<Float>?,
    )

    /**
     * 模型預覽資料。
     *
     * @property capabilityId 預覽能力 ID。
     * @property parameters 工廠解讀的有界參數。
     */
    @Serializable
    private data class PreviewDto(val capabilityId: String, val parameters: Map<String, String>)
}
