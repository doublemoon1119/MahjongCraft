package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceDefinition

/** 擴充登記玩家頭像來源時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface PlayerPortraitSourceRegistrar {
    /** 目前已登記 provider ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: PlayerPortraitSourceDefinition)
}
