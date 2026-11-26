package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 客戶端初始化時登記的獨立模型預覽能力。
 *
 * @property id 與角色 preview.capabilityId 對應的完整識別碼。
 * @property provider 不搜尋世界實體的工廠。
 */
class AiCharacterPreviewDefinition(
    val id: String,
    val provider: AiCharacterPreviewProvider,
) {
    init {
        NamespacedId.requireValid(id) { "Invalid character preview capability ID" }
    }
}
