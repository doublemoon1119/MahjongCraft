package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

/** Replay 讀取作業可使用的資源上限。
 *
 * @property maxDepth JSON 樹允許的最大深度。
 * @property maxInputNodes 輸入文件允許的最大節點數。
 * @property maxProjectionNodes 投影樹同時存在的最大節點數。
 * @property maxExpandedStringBytes 展開字串允許的 UTF-8 位元組總數。
 * @property maxPlayers 單場允許的最大玩家數。
 * @property maxRounds 單場允許的最大局數。
 * @property maxTiles 單局已宣告的實體牌數上限，同時限制共用牌種字典大小。
 * @property maxTransactionsPerRound 每局允許的最大交易數。
 * @property maxFactsPerTransaction 每筆交易允許的最大事實數。
 * @property maxWorkUnits 讀取作業允許消耗的最大工作單位數。
 * @property cancellationInterval 工作單位之間檢查取消狀態的間隔。
 */
data class ReplayReadLimits(
    val maxDepth: Int = 64,
    val maxInputNodes: Long = 1_000_000L,
    val maxProjectionNodes: Long = 1_000_000L,
    val maxExpandedStringBytes: Long = 32L * 1024L * 1024L,
    val maxPlayers: Int = 64,
    val maxRounds: Int = 256,
    val maxTiles: Int = 4096,
    val maxTransactionsPerRound: Int = 8192,
    val maxFactsPerTransaction: Int = 128,
    val maxWorkUnits: Long = 8_000_000L,
    val cancellationInterval: Long = 256L,
) {
    init {
        require(maxDepth > 0)
        require(maxInputNodes > 0)
        require(maxProjectionNodes > 0)
        require(maxExpandedStringBytes > 0)
        require(maxPlayers > 0)
        require(maxRounds > 0)
        require(maxTiles > 0)
        require(maxTransactionsPerRound > 0)
        require(maxFactsPerTransaction > 0)
        require(maxWorkUnits > 0)
        require(cancellationInterval > 0)
    }
}
