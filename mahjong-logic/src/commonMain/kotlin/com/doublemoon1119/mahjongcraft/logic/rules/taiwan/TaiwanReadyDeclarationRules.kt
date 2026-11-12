package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType

/**
 * 宣告聽牌的時機判斷與報聽開關。
 *
 * 天聽與地聽一律可以宣告；其他時機的一般宣告（報聽）只有在 [TaiwanRuleConfig.generalReadyDeclaration] 開啟時才能宣告與計台。
 */
internal object TaiwanReadyDeclarationRules {
    /**
     * 依打出宣告牌之前的桌況判斷宣告種類。
     *
     * 莊家第一打為天聽；全桌沒有副露時，閒家第一打或莊家第二打為地聽；其他時候為一般宣告。
     *
     * @param tableHasMelds 全桌是否已有任何副露。
     * @param isDealer 宣告者是否為莊家。
     * @param previousDiscards 宣告者在這張宣告牌之前已打出的張數。
     * @return 宣告種類。
     */
    fun kind(
        tableHasMelds: Boolean,
        isDealer: Boolean,
        previousDiscards: Int,
    ): TaiwanReadyKind = when {
        !tableHasMelds && isDealer && previousDiscards == 0 -> TaiwanReadyKind.HEAVENLY
        !tableHasMelds && previousDiscards == (if (isDealer) 1 else 0) -> TaiwanReadyKind.EARTHLY
        else -> TaiwanReadyKind.GENERAL
    }

    /**
     * 判斷這個時機的宣告是否可以進行。
     *
     * @param config 規則設定。
     * @param kind 這個時機的宣告種類。
     * @return 天聽、地聽，或已開啟報聽時為 true。
     */
    fun isDeclarable(config: TaiwanRuleConfig, kind: TaiwanReadyKind): Boolean = kind != TaiwanReadyKind.GENERAL || config.generalReadyDeclaration

    /**
     * 宣告後胡牌時成立的計台項目。
     *
     * 宣告天聽或地聽後放過胡牌會改為一般宣告；未開啟報聽時，一般宣告仍鎖住手牌但不計台。
     *
     * @param config 規則設定。
     * @param kind 目前的宣告種類；沒有宣告時為 null。
     * @return 計台項目；不計台時為 null。
     */
    fun taiType(config: TaiwanRuleConfig, kind: TaiwanReadyKind?): TaiwanTaiType? = kind?.takeIf { isDeclarable(config, it) }?.taiType
}
