package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlinx.serialization.Serializable

/**
 * 不帶執行回呼的角色外觀線路描述。
 *
 * @property id 完整角色識別碼。
 * @property nameKey 名稱翻譯鍵。
 * @property faceKind 頭像來源種類，沒有時為 null。
 * @property faceResource 牌面素材鍵或貼圖識別碼。
 * @property faceRegion 正規化 UV 區域，只有貼圖來源使用。
 * @property worldCapabilityId 世界角色能力識別碼。
 * @property previewCapabilityId 獨立模型預覽能力識別碼。
 * @property previewParameters 預覽工廠解讀的有界參數。
 */
@Serializable
data class AiCharacterDefinitionNetworkDto(
    val id: String,
    val nameKey: String,
    val faceKind: String?,
    val faceResource: String?,
    val faceRegion: List<Float>?,
    val worldCapabilityId: String?,
    val previewCapabilityId: String?,
    val previewParameters: Map<String, String>?,
)
