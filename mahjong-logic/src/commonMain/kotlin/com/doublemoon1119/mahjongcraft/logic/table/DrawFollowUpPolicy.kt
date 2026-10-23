package com.doublemoon1119.mahjongcraft.logic.table

/**
 * 決定玩家摸牌後是否自動處理的純 policy，例如摸到花牌時自動移出並補牌。
 *
 * 一般摸牌與動作後的補牌都會詢問，補到的牌會再詢問一次，直到回傳 [DrawFollowUpDecision.None] 或
 * [DrawFollowUpDecision.EndRound]。一次摸到或補到多張時，依摸取順序逐張詢問，每次詢問前先把那張牌換到摸牌位。
 * 同一輸入必須得到同一結果；policy 不得修改狀態或發布副作用。
 * 回傳 [DrawFollowUpDecision.SetAside] 時規則必須確定還有牌可以補，補不到牌時應回傳
 * [DrawFollowUpDecision.EndRound]；否則視為規則錯誤，本局以中止結束。
 */
fun interface DrawFollowUpPolicy {
    /**
     * 決定這次摸牌後的處理。
     *
     * @param context 剛完成的摸牌與目前桌況。
     * @return 這次摸牌後的處理決策。
     */
    fun resolve(context: DrawFollowUpContext): DrawFollowUpDecision
}
