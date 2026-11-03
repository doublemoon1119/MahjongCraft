package com.doublemoon1119.mahjongcraft.platform.fabric.api.registration

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricRoomParticipantDefinition

/** 初始化期間的既有實體參加能力登記入口；由框架提供，擴充不自行實作。 */
interface FabricRoomParticipantRegistrar {
    /** 已登記識別碼的獨立快照。 */
    val registrationKeys: Set<String>

    /** 是否已結束登記。 */
    val isFrozen: Boolean

    /**
     * 登記一份可辨識既有實體的能力，不在登記時呼叫提供者；目前最多接受 64 份，超出即拒絕初始化。
     *
     * @param definition 包含完整識別碼與實作的定義。
     */
    fun register(definition: FabricRoomParticipantDefinition)
}
