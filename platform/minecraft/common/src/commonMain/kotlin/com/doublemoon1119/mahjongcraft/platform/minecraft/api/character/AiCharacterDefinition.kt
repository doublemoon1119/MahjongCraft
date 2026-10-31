package com.doublemoon1119.mahjongcraft.platform.minecraft.api.character

/**
 * 一個角色的固定外觀描述；角色與打牌策略互不綁定。
 *
 * 所有能力都是登記的識別碼，不在建立描述時執行工廠。缺少能力時保留描述，由呈現端選擇備援。
 *
 * @property id 完整角色 ID。
 * @property nameKey 角色名稱的翻譯鍵。
 * @property face 可選的二維頭像；沒有時可以使用預覽或預設頭像。
 * @property worldCapabilityId 可選的世界角色能力 ID。
 * @property preview 可選的獨立客戶端預覽描述。
 */
class AiCharacterDefinition(
    val id: String,
    val nameKey: String,
    val face: AiCharacterFace?,
    val worldCapabilityId: String?,
    val preview: AiCharacterPreview?,
) {
    init {
        AiCharacterLimits.requireId(id)
        require(nameKey.isNotBlank() && nameKey.length <= AiCharacterLimits.MAX_ID_LENGTH) { "Invalid character name key" }
        worldCapabilityId?.let(AiCharacterLimits::requireId)
    }

    /** 所有描述欄位相同時相等。 */
    override fun equals(other: Any?): Boolean = other is AiCharacterDefinition && id == other.id && nameKey == other.nameKey && face == other.face && worldCapabilityId == other.worldCapabilityId && preview == other.preview

    /** 與相等規則一致的雜湊值。 */
    override fun hashCode(): Int = listOf(id, nameKey, face, worldCapabilityId, preview).hashCode()
}
