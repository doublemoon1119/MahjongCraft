package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.config.GameLength

/**
 * 簡易配對麻將的固定局數。
 *
 * @property totalRounds 整場對局的局數。
 */
data class ExamplePairingGameLength(
    override val totalRounds: Int = 4,
) : GameLength {
    init {
        require(totalRounds > 0) { "totalRounds must be positive" }
    }
}
