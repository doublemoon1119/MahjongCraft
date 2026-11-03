package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 單一角色開局描述的保存格式；歷史封存不依賴目前世界或角色登記內容。 */
class AiCharacterSnapshotCodec {
    /** 與線路設定分離的嚴格保存格式。 */
    private val format = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    /**
     * 保存一名角色的完整描述。
     *
     * @param snapshot 開局時固定的描述。
     * @return 不帶回呼或原生身分的保存文字。
     */
    fun encode(snapshot: AiCharacterSnapshot): String = format.encodeToString(
        SnapshotDto(snapshot.characterId, snapshot.definition?.toPersistenceDefinitionDto(), snapshot.displayName),
    )

    /**
     * 依保存內容還原角色，不以目前登記預設覆寫。
     *
     * @param encoded 已保存的文字。
     * @return 當場描述；內容無效時拋出例外，由外觀消費端降級。
     */
    fun decode(encoded: String): AiCharacterSnapshot {
        require(encoded.length <= MAX_CHARACTERS) { "Saved character appearance is too large" }
        val dto = format.decodeFromString<SnapshotDto>(encoded)
        return AiCharacterSnapshot(dto.characterId, dto.definition?.toDefinition(), dto.displayName)
    }

    /**
     * 單一角色的保存封套。
     *
     * @property characterId 完整角色識別碼。
     * @property definition 當場可用描述，沒有時為 null。
     * @property displayName 當場普通名稱。
     */
    @Serializable
    private data class SnapshotDto(val characterId: String, val definition: AiCharacterDefinitionPersistenceDto?, val displayName: String?)

    /** 單一描述的保存容量限制。 */
    companion object {
        /** 足以容納全部有界參數的保存字元數。 */
        const val MAX_CHARACTERS: Int = 131072
    }
}
