package com.doublemoon1119.mahjongcraft.platform.fabric.api.registration

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterCapabilityDefinition

/** 初始化期間的原生角色能力登記入口；初始化完成後不再接受登記。 */
interface FabricAiCharacterCapabilityRegistrar {
    /** 已登記識別碼的快照。 */
    val registrationKeys: Set<String>

    /** 是否已結束初始化。 */
    val isFrozen: Boolean

    /**
     * 登記一個自行攜帶識別碼的角色能力。
     *
     * @param definition 完整能力定義；識別碼不可重複。
     * @throws IllegalArgumentException 識別碼重複時。
     * @throws IllegalStateException 初始化已完成時。
     */
    fun register(definition: FabricAiCharacterCapabilityDefinition)
}
