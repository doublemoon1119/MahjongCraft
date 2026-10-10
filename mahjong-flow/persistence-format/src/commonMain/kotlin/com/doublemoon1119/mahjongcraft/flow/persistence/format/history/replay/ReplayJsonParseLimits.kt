package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

/**
 * 有界 Replay JSON 原文字串解析上限。
 *
 * @property maximumUtf8Bytes 原始 UTF-8 位元組上限。
 * @property maximumDepth JSON 巢狀深度上限。
 * @property maximumTokens JSON 詞元近似上限。
 */
data class ReplayJsonParseLimits(
    val maximumUtf8Bytes: Int = DEFAULT_REPLAY_JSON_BYTES,
    val maximumDepth: Int = 64,
    val maximumTokens: Int = 1_000_000,
) {
    init {
        require(maximumUtf8Bytes > 0) { "Replay JSON byte limit must be positive" }
        require(maximumDepth > 0) { "Replay JSON depth limit must be positive" }
        require(maximumTokens > 0) { "Replay JSON token limit must be positive" }
    }
}
