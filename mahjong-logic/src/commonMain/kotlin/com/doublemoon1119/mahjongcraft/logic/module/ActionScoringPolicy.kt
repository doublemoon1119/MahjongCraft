package com.doublemoon1119.mahjongcraft.logic.module

/**
 * 決定動作成立當下是否收付點數的純 policy，例如槓牌時由放槓者或其他玩家付點數。
 *
 * Flow 在吃、碰、明槓與其他鳴取捨牌得標後，以及暗槓、加槓、移出手牌確定成立後各呼叫一次，補牌之前套用；
 * 被搶和或判定流局而沒有成立的動作不呼叫。同一輸入必須得到同一結果；policy 不得修改狀態或發布副作用。
 */
fun interface ActionScoringPolicy {
    /**
     * 計算這次動作的收付。
     *
     * @param context 動作與其前後桌況。
     * @return 這次動作的收付；不收付時為 null。
     */
    fun resolve(context: ActionScoringContext): ActionScoring?
}
