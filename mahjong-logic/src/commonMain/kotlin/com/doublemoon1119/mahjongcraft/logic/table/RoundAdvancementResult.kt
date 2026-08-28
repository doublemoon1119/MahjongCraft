package com.doublemoon1119.mahjongcraft.logic.table

/**
 * 一局結束後，連莊/過莊判定與局數/場風推進的計算結果。
 *
 * @property players 延續至下一局的固定座位玩家列表。
 * @property dealerPlayerId 下一局的權威莊家 Uuid。
 * @property roundNumber 套用後的局數。
 * @property comboCount 套用後的本場數。
 * @property prevalentWind 套用後的場風。
 * @property isMatchOver 整場對局是否已依規則的 `GameLength` 結束。
 */
data class RoundAdvancementResult(
    val players: List<MahjongPlayer>,
    val dealerPlayerId: kotlin.uuid.Uuid,
    val roundNumber: Int,
    val comboCount: Int,
    val prevalentWind: Wind,
    val isMatchOver: Boolean,
)
