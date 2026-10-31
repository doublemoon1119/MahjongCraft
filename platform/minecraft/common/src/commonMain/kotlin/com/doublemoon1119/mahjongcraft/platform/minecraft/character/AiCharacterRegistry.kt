package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.AiCharacterRegistrar

/** 角色登記的內部查詢與凍結能力。 */
interface AiCharacterRegistry : AiCharacterRegistrar {
    /**
     * 查詢完整角色描述。
     *
     * @param id 完整角色 ID。
     * @return 已登記的描述；缺少時為 null。
     */
    fun find(id: String): AiCharacterDefinition?

    /** 依 ID 排序的角色目錄快照。 */
    fun definitions(): List<AiCharacterDefinition>

    /** 停止接受新的角色登記。 */
    fun freeze()
}
