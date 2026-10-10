package com.doublemoon1119.mahjongcraft.logic.table

/**
 * 動作完成後由規則決定是否補摸額外牌張的純邏輯 policy。
 *
 * 呼叫端只負責建立候選桌況、驗證結果及原子套用，不得自行假設補牌來源、數量或牌牆變化；
 * 公開資訊另由 [WallRevealPolicy] 在明確 checkpoint 處理。
 */
fun interface SupplementalDrawPolicy {
    /** 根據 [context] 決定本次動作完成後的補牌與牌牆變化。 */
    fun resolve(context: SupplementalDrawContext): SupplementalDrawDecision
}
