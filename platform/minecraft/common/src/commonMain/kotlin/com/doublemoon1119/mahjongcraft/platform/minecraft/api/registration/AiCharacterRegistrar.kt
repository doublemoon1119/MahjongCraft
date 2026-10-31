package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition

/** 擴充登記角色時取得的介面；查詢與凍結由 MahjongCraft 管理。 */
interface AiCharacterRegistrar {
    /** 目前登記的角色 ID 快照。 */
    val registrationKeys: Set<String>

    /** 是否已停止接受登記。 */
    val isFrozen: Boolean

    /**
     * 登記角色描述，不建立世界角色或預覽。
     *
     * @param definition 包含身分與外觀能力的完整描述。
     */
    fun register(definition: AiCharacterDefinition)
}
