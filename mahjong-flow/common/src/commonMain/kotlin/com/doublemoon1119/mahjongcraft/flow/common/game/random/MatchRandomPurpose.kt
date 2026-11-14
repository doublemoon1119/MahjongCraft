package com.doublemoon1119.mahjongcraft.flow.common.game.random

/**
 * 從對局種子衍生亂數時的用途；不同用途得到彼此獨立的亂數。
 *
 * @property label 衍生時寫入訊息的穩定名稱；改名會讓同一個種子產生不同的對局。
 */
internal enum class MatchRandomPurpose(val label: String) {
    /** 開場排座位。 */
    SEAT_ORDER("seat_order"),

    /** 建立牌山時產生牌張 ID 與洗牌。 */
    WALL("wall"),

    /** 擲骰。 */
    DICE("dice"),

    /** 建立規則組件初值。 */
    COMPONENTS("components"),
}
