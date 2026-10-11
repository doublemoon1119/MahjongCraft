package com.doublemoon1119.mahjongcraft.logic.base

/**
 * 副露的種類定義。
 * 採用國際通用英文命名，並在註釋中提供日文與中文對照。
 */
sealed interface MeldType {
    /**
     * 吃 (Chow)。
     * 日文：チー (Chi)。
     * 說明：順子組合。
     */
    data object CHI : MeldType

    /**
     * 碰 (Pung)。
     * 日文：ポン (Pon)。
     * 說明：刻子組合。
     */
    data object PON : MeldType

    /**
     * 明槓 (Exposed Kan)。
     * 日文：大明槓 (Daiminkan)。
     * 說明：鳴取他人的捨牌構成的槓子。
     */
    @Suppress("ktlint:standard:class-naming")
    data object OPEN_KAN : MeldType

    /**
     * 暗槓 (Closed Kan)。
     * 日文：暗槓 (Ankan)。
     * 說明：由自己手中的四張相同牌構成的槓子。
     */
    @Suppress("ktlint:standard:class-naming")
    data object CLOSED_KAN : MeldType

    /**
     * 加槓 (Added Kan)。
     * 日文：加槓 (Kakan) / 小明槓。
     * 說明：在已有的碰 (PON) 基礎上，增加第四張相同的牌構成的槓子。
     */
    @Suppress("ktlint:standard:class-naming")
    data object ADDED_KAN : MeldType

    /**
     * 第三方規則提供的副露種類。
     *
     * @property typeId 規則定義的副露種類識別碼。
     */
    data class Extension(val typeId: MeldTypeId) : MeldType
}
