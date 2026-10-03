package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** 查詢解析可讀入的原始 Replay UTF-8 位元組上限。 */
const val DEFAULT_REPLAY_JSON_BYTES: Int = 8 * 1024 * 1024

/** 有界 Replay JSON 原文字串解析上限。
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

/** 以詞法預掃描與原生 JSON parser 解析有界 Replay 文件。
 * @param source 原始 JSON 文字。
 * @param limits 解析上限。
 * @param json 原生 JSON 設定。
 * @return 成功時為根物件，否則為穩定失敗。
 */
suspend fun parseBoundedReplayJson(source: String, limits: ReplayJsonParseLimits = ReplayJsonParseLimits(), json: Json = Json): ReplayReadResult<JsonObject> {
    currentCoroutineContext().ensureActive()
    if (source.length > limits.maximumUtf8Bytes) return ReplayReadResult.Failure(ReplayReadError.LIMIT_EXCEEDED)
    val rawBytes = source.encodeToByteArray()
    if (rawBytes.size > limits.maximumUtf8Bytes) return ReplayReadResult.Failure(ReplayReadError.LIMIT_EXCEEDED)
    return try {
        var depth = 0
        var tokens = 0
        var quoted = false
        var escaped = false
        source.forEachIndexed { index, character ->
            if (index % 256 == 0) currentCoroutineContext().ensureActive()
            if (quoted) {
                if (escaped) {
                    escaped = false
                } else if (character == '\\') {
                    escaped = true
                } else if (character == '"') {
                    quoted = false
                }
            } else {
                when (character) {
                    '"' -> {
                        quoted = true
                        tokens++
                    }
                    '{', '[' -> {
                        depth++
                        tokens++
                        if (depth > limits.maximumDepth) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
                    }
                    '}', ']' -> {
                        depth--
                        tokens++
                        if (depth < 0) throw ReplayReadException(ReplayReadError.INVALID_DOCUMENT)
                    }
                    ',', ':' -> tokens++
                }
            }
            if (tokens > limits.maximumTokens) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
        }
        if (quoted || escaped || depth != 0) return ReplayReadResult.Failure(ReplayReadError.INVALID_DOCUMENT)
        val parsed = json.parseToJsonElement(source)
        currentCoroutineContext().ensureActive()
        ReplayReadResult.Success(parsed.jsonObject, ReplayReadDiagnostics(0, 0, rawBytes.size.toLong(), 0))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: ReplayReadException) {
        ReplayReadResult.Failure(failure.error)
    } catch (_: IllegalArgumentException) {
        ReplayReadResult.Failure(ReplayReadError.INVALID_DOCUMENT)
    }
}
