package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import kotlin.uuid.Uuid

/**
 * 規則專屬成果的判定；只讀取已提交的事實與交易前後的對局，不得重新計算胡牌或分數。
 *
 * 例如日麻可以從自己產生的胡牌詳情判定役滿。
 */
interface GameAchievementResolver {
    /** 對應的 namespaced 規則模組識別碼。 */
    val ruleModuleId: String

    /**
     * 判定本次交易中各玩家達成的規則專屬成果。
     *
     * @param facts 本次交易對單一桌子提交的事實。
     * @return 以玩家 UUID 索引的完整 namespaced 成果 ID；沒有成果的玩家不出現。
     */
    fun resolve(facts: CommittedGameFacts): Map<Uuid, Set<String>>
}
