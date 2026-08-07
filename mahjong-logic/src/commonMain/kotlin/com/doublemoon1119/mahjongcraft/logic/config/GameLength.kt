package com.doublemoon1119.mahjongcraft.logic.config

/**
 * 定義對局的預期長度。
 *
 * 不同的麻將規則會對應不同的局數計算方式（如：一局、東風戰、南風戰、半莊、一將）。
 */
interface GameLength {
    /**
     * 預計進行的總局數。
     * */
    val totalRounds: Int
}
