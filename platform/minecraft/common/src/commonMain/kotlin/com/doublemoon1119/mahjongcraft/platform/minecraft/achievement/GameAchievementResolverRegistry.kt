package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameAchievementResolverRegistrar
import kotlin.uuid.Uuid

/** 供內建與第三方規則登記成果判定的凍結式 registry；擴充只透過 [GameAchievementResolverRegistrar] 登記。 */
interface GameAchievementResolverRegistry : GameAchievementResolverRegistrar {

    /**
     * 只以 [ruleModuleId] 登記的判定處理 [batch]；沒有登記的規則不產生規則專屬成果。
     *
     * @param ruleModuleId 這場對局的規則模組 ID。
     * @param batch 本次交易對單一桌子產生的公開事件批次。
     * @return 以玩家 UUID 索引的成果 ID。
     */
    fun resolve(ruleModuleId: String, batch: CommittedMatchEventBatch): Map<Uuid, Set<String>>

    /** 凍結判定集合，不影響已登記判定的使用。 */
    fun freeze()
}
