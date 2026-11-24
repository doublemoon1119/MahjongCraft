package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes

/**
 * 測試牌譜指定的一張牌。
 *
 * @property tile 牌面。
 * @property red 是否是赤牌；只有數牌的五可以是赤牌。
 */
data class HistoryFixtureTile(val tile: Tile, val red: Boolean = false) {
    init {
        require(!red || (tile is Tile.Numeric && tile.value == RED_VALUE)) { "Only a numeric five can be red: $tile" }
    }

    /**
     * 實際的牌是否就是這張指定的牌，赤牌與一般的五分開比對。
     *
     * @param candidate 牌山中的一張牌。
     * @return 牌面與是否為赤牌都相同時為 true。
     */
    fun matches(candidate: IdentifiedTile): Boolean = candidate.tile == tile && isRed(candidate) == red

    /** 解析測試牌譜使用的牌面寫法。 */
    companion object {
        /** 赤牌的數字。 */
        private const val RED_VALUE = 5

        /** 字牌依 1～7 的順序：東、南、西、北、白、發、中。 */
        private val HONORS = listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North, Tile.Honor.White, Tile.Honor.Green, Tile.Honor.Red)

        /**
         * 解析牌面寫法，例如 `123m 0p 789s 1157z 18f`：數字後面接花色，`m` 萬、`p` 筒、`s` 索、`z` 字牌、`f` 花牌；`0` 是赤五。
         * 花牌 1～8 依序是春、夏、秋、冬、梅、蘭、菊、竹。空白只用來分隔，不影響順序。
         *
         * @param notation 牌面寫法。
         * @return 依寫法順序排列的牌。
         * @throws IllegalArgumentException 寫法不正確時拋出。
         */
        fun parse(notation: String): List<HistoryFixtureTile> {
            val result = mutableListOf<HistoryFixtureTile>()
            val pending = mutableListOf<Int>()
            notation.filterNot(Char::isWhitespace).forEach { char ->
                when {
                    char.isDigit() -> pending += char.digitToInt()
                    else -> {
                        require(pending.isNotEmpty()) { "A suit letter needs digits before it: $notation" }
                        pending.forEach { digit -> result += tile(digit, char, notation) }
                        pending.clear()
                    }
                }
            }
            require(pending.isEmpty()) { "Digits must end with a suit letter: $notation" }
            return result
        }

        /**
         * 單張牌。
         *
         * @param digit 數字。
         * @param suit 花色字母。
         * @param notation 錯誤訊息使用的原始寫法。
         * @return 指定的牌。
         */
        private fun tile(digit: Int, suit: Char, notation: String): HistoryFixtureTile {
            if (suit == 'z') {
                require(digit in 1..HONORS.size) { "Honor tiles are 1z to 7z: $notation" }
                return HistoryFixtureTile(HONORS[digit - 1])
            }
            if (suit == 'f') {
                require(digit in 1..TaiwanTileTypes.ALL.size) { "Flower tiles are 1f to ${TaiwanTileTypes.ALL.size}f: $notation" }
                return HistoryFixtureTile(Tile.Extension(TaiwanTileTypes.ALL[digit - 1]))
            }
            val numericSuit = when (suit) {
                'm' -> Tile.Suit.Character
                'p' -> Tile.Suit.Dot
                's' -> Tile.Suit.Bamboo
                else -> throw IllegalArgumentException("Unknown suit '$suit': $notation")
            }
            return if (digit == 0) HistoryFixtureTile(Tile.Numeric(numericSuit, RED_VALUE), red = true) else HistoryFixtureTile(Tile.Numeric(numericSuit, digit))
        }

        /**
         * 牌山中的一張牌是否是赤牌。
         *
         * @param candidate 牌山中的牌。
         * @return 是赤五時為 true。
         */
        private fun isRed(candidate: IdentifiedTile): Boolean {
            val tile = candidate.tile as? Tile.Numeric ?: return false
            return tile.value == RED_VALUE && candidate.components == RiichiTileValues.redFive(tile.suit).components
        }
    }
}
