package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameDefaultDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry

/** 擴充登記決策狀態名稱時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface DecisionStatusDisplayNameRegistrar {
    /** 目前已登記組合的穩定 key 快照，格式為 `規則模組 ID/狀態 ID`，中立預設以 [DecisionStatusDisplayNameRegistry.DEFAULT_KEY] 表示。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記所有規則共用的中立預設。
     *
     * @param definition 中立狀態名稱的完整登記描述。
     */
    fun register(definition: DecisionStatusDisplayNameDefaultDefinition)

    /**
     * 登記指定規則模組專屬的顯示名稱，優先於同一狀態的中立預設。
     *
     * @param definition 規則專屬狀態名稱的完整登記描述。
     */
    fun register(definition: DecisionStatusDisplayNameDefinition)
}
