package com.doublemoon1119.mahjongcraft.platform.minecraft.character

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
            participants = metadata.participants.entries.sortedBy { it.key.toString() }.map { (player, snapshot) -> SnapshotDto(player.toString(), snapshot.characterId, snapshot.definition?.toPersistenceDefinitionDto(), snapshot.displayName) },
            selections = metadata.selections.entries.sortedBy { it.key.toString() }.map { (table, players) ->
                SelectionDto(table.toString(), players.entries.sortedBy { it.key.toString() }.map { (player, character) -> ChoiceDto(player.toString(), character) })
            },
            matches = metadata.matches.entries.sortedBy { it.key.toString() }.map { (match, players) ->
                MatchDto(
                    match.toString(),
                    players.entries.sortedBy { it.key.toString() }.map { (player, snapshot) ->
                        SnapshotDto(player.toString(), snapshot.characterId, snapshot.definition?.toPersistenceDefinitionDto(), snapshot.displayName)
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
        require(dto.participants.distinctBy { Uuid.parse(it.playerId) }.size == dto.participants.size) { "Duplicate participant appearance" }
        return AiCharacterMetadata(
            participants = dto.participants.associate { Uuid.parse(it.playerId) to AiCharacterSnapshot(it.characterId, it.definition?.toDefinition(), it.displayName) },
            selections = dto.selections.associate { entry ->
                require(entry.players.distinctBy { Uuid.parse(it.playerId) }.size == entry.players.size) { "Duplicate character selection player" }
                Uuid.parse(entry.tableId) to entry.players.associate { Uuid.parse(it.playerId) to it.characterId }
            },
            matches = dto.matches.associate { entry ->
                require(entry.players.distinctBy { Uuid.parse(it.playerId) }.size == entry.players.size) { "Duplicate character snapshot player" }
                Uuid.parse(entry.matchId) to entry.players.associate { player ->
                    Uuid.parse(player.playerId) to AiCharacterSnapshot(player.characterId, player.definition?.toDefinition(), player.displayName)
                }
            },
        )
    }

    /**
     * 保存區段根資料。
     *
     * @property selections 目前選擇。
     * @property matches 各場固定外觀。
     * @property participants 關聯成員及暫時排除者的描述，隨房間生命週期清理。
     */
    @Serializable
    private data class MetadataDto(
        val selections: List<SelectionDto>,
        val matches: List<MatchDto>,
        val participants: List<SnapshotDto>,
    )

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
     * @property displayName 當場普通名稱。
     */
    @Serializable
    private data class SnapshotDto(
        val playerId: String,
        val characterId: String,
        val definition: AiCharacterDefinitionPersistenceDto?,
        val displayName: String?,
    )
}
