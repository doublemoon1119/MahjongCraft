package com.doublemoon1119.mahjongcraft.platform.fabric.api.registration

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterPreviewDefinition

/** 只在客戶端初始化使用的預覽能力登記入口，不在伺服器載入工廠。 */
interface FabricAiCharacterPreviewRegistrar {
    /** 已登記完整識別碼的快照。 */
    val registrationKeys: Set<String>

    /** 是否已完成初始化。 */
    val isFrozen: Boolean

    /**
     * 登記一份獨立模型預覽能力，不立即建立模型。
     *
     * @param definition 自帶完整識別碼的定義。
     */
    fun register(definition: FabricAiCharacterPreviewDefinition)
}
