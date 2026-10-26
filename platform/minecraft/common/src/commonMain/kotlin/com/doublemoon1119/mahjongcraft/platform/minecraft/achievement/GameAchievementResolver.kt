package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import kotlin.uuid.Uuid

/**
 * 規則專屬成果的判定；只讀取已提交的公開事件，不得重新計算胡牌或分數。
 *
 * 例如日麻可以從自己產生的胡牌詳情判定役滿。
 */
interface GameAchievementResolver {
    /** 對應的 namespaced 規則模組識別碼。 */
    val ruleModuleId: String

    /**
     * 判定本次交易中各玩家達成的規則專屬成果。
     *
     * @param batch 本次交易對單一桌子產生的公開事件批次。
     * @return 以玩家 UUID 索引的完整 namespaced 成果 ID；沒有成果的玩家不出現。
     */
    fun resolve(batch: CommittedMatchEventBatch): Map<Uuid, Set<String>>
}
