package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlinx.serialization.Serializable

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
internal data class AiCharacterDefinitionPersistenceDto(
    val id: String,
    val nameKey: String,
    val face: AiCharacterFacePersistenceDto?,
    val worldCapabilityId: String?,
    val preview: AiCharacterPreviewPersistenceDto?,
)

/**
 * 可保存的頭像來源。
 *
 * @property kind 來源種類。
 * @property resource 素材鍵或貼圖 ID。
 * @property region 貼圖區域；牌面來源為 null。
 */
@Serializable
internal data class AiCharacterFacePersistenceDto(
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
internal data class AiCharacterPreviewPersistenceDto(val capabilityId: String, val parameters: Map<String, String>)
