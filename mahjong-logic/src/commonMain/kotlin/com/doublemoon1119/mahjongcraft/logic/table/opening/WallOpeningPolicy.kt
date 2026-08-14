package com.doublemoon1119.mahjongcraft.logic.table.opening

/**
 * 將權威骰子結果轉換為特定麻將規則的牌牆開門位置。
 *
 * 骰子數量、牌牆方位與墩數公式皆屬規則能力；平台 adapter 與通用初始化器不得自行重複計算。
 */
interface WallOpeningPolicy {
    /** 此規則每次開門所需的骰子數量。 */
    val diceCount: Int

    /**
     * 解析本局的牌牆開門位置。
     *
     * @param diceRoll 已由伺服器決定的骰子結果。
     * @return 對應的牌牆方位與墩數。
     * @throws IllegalArgumentException 當 [diceRoll] 的骰子數量不符合 [diceCount] 時拋出。
     */
    fun resolve(diceRoll: DiceRollResult): WallOpening
}
