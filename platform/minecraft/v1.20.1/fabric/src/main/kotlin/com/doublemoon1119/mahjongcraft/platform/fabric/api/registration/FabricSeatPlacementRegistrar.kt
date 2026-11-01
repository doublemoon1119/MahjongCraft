package com.doublemoon1119.mahjongcraft.platform.fabric.api.registration

import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementDefinition

/** 初始化期間登記座位物品的放置能力，與既有座位解析分開。 */
interface FabricSeatPlacementRegistrar {
    /** 已登記識別碼的獨立快照。 */
    val registrationKeys: Set<String>

    /** 初始化是否已完成。 */
    val isFrozen: Boolean

    /**
     * 登記一項放置能力。
     *
     * @param definition 自行攜帶完整識別碼的能力。
     * @throws IllegalArgumentException 識別碼重複時。
     * @throws IllegalStateException 初始化完成後登記時。
     */
    fun register(definition: FabricSeatPlacementDefinition)
}
