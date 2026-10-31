package com.doublemoon1119.mahjongcraft.platform.minecraft.api.character

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 可由客戶端預覽工廠解讀的外觀資料，不包含世界物件或可執行內容。
 *
 * @property capabilityId 預覽能力的完整 namespaced ID。
 * @param parameters 以完整 namespaced ID 為鍵的有界字串參數；建立時複製，不保存任意 NBT。
 */
class AiCharacterPreview(
    val capabilityId: String,
    parameters: Map<String, String>,
) {
    /** 不與呼叫端共用的參數。 */
    private val capturedParameters = parameters.toMap()

    /** 參數的獨立快照。 */
    val parameters: Map<String, String> get() = capturedParameters.toMap()

    init {
        AiCharacterLimits.requireId(capabilityId)
        require(capturedParameters.size <= AiCharacterLimits.MAX_PREVIEW_PARAMETERS) { "Too many character preview parameters" }
        capturedParameters.forEach { (key, value) ->
            NamespacedId.requireValid(key) { "Invalid character preview parameter ID: $key" }
            require(key.length <= AiCharacterLimits.MAX_ID_LENGTH && value.length <= AiCharacterLimits.MAX_PARAMETER_LENGTH) { "Character preview parameter is too long" }
        }
    }

    /** 能力與所有外觀參數相同時相等。 */
    override fun equals(other: Any?): Boolean = other is AiCharacterPreview && capabilityId == other.capabilityId && capturedParameters == other.capturedParameters

    /** 與相等規則一致的雜湊值。 */
    override fun hashCode(): Int = 31 * capabilityId.hashCode() + capturedParameters.hashCode()
}
