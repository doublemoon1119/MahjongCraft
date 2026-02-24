package com.doublemoon1119.mahjongcraft.model.config

/**
 * 定義對局的預期長度與結束條件。
 *
 * 不同的麻將規則會對應不同的局數計算方式（如：東風戰、半莊、一將）。
 */
interface GameLength {
    /** 該模式預計進行的總局數。 */
    val totalRounds: Int

    /** 該長度模式的內部識別名稱。 */
    val name: String
}