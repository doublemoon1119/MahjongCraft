package com.doublemoon1119.mahjongcraft.example.pairing

/** 簡易配對麻將依玩家人數與牌面種類數計算各面牌牆的墩數。 */
internal object ExamplePairingWallGeometry {
    /**
     * 計算每面牌牆的墩數；四面牌牆的多餘墩數依固定順序分到偶數面。
     *
     * @param playerCount 本局完整玩家人數。
     * @param wallTileValueCount 牌山使用的牌面種類數。
     * @return 各面牌牆的墩數。
     */
    fun stacksPerSide(playerCount: Int, wallTileValueCount: Int): List<Int> {
        val sideCount = if (playerCount == 2) 2 else 4
        val totalStacks = wallTileValueCount * 2
        val baseStacks = totalStacks / sideCount
        val extraStacks = totalStacks % sideCount
        return if (sideCount == 2) {
            listOf(baseStacks, baseStacks)
        } else {
            (0 until sideCount).map { side ->
                baseStacks + if (side % 2 == 0 && side / 2 < extraStacks) 1 else 0
            }
        }
    }
}
