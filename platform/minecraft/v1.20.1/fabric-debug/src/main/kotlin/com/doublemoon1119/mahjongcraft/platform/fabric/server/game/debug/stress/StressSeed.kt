package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.flow.common.game.random.MatchSeed
import java.security.MessageDigest

/**
 * 壓力測試的基準種子：各桌的對局種子由基準種子與桌的建立順序衍生，以同一個基準種子與相同設定重跑時，各桌的座位、牌山與骰子相同。
 *
 * 壓力測試的對局只在獨立的測試環境中由 AI 進行，公開基準種子不影響正式對局。
 *
 * @property base 基準種子。
 */
data class StressSeed(val base: Long) {
    /**
     * 第 [tableOrder] 張建立的桌使用的對局種子。
     *
     * @param tableOrder 桌的建立順序，從 0 起算。
     * @return 由基準種子與建立順序衍生的對局種子。
     */
    fun matchSeed(tableOrder: Long): MatchSeed {
        require(tableOrder >= 0) { "Table order must not be negative" }
        val digest = MessageDigest.getInstance(DIGEST_ALGORITHM).digest("$LABEL/$base/$tableOrder".encodeToByteArray())
        return MatchSeed.fromByteArray(digest)
    }

    /** 衍生使用的固定參數。 */
    private companion object {
        /** 輸出長度與 [MatchSeed.SIZE_BYTES] 相同的雜湊演算法。 */
        const val DIGEST_ALGORITHM = "SHA-256"

        /** 衍生標籤；改變它會讓同一個基準種子產生不同的對局。 */
        const val LABEL = "mahjongcraft:stress_seed"
    }
}
