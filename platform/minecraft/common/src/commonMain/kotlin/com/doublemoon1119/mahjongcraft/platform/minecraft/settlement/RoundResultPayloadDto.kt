package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import kotlinx.serialization.Serializable

/**
 * 一次和牌或流局結算的結果，伺服器在結算當下送給入座的真人玩家。前後分數與名次由伺服器依結算前後的權威狀態決定，
 * client 原樣顯示。
 *
 * @property gameId 對局 UUID 字串。
 * @property ruleModuleId 對局規則模組 ID，用來查詢規則的動作用語。
 * @property outcomeId 結算結果 ID：和牌為結果 ID（例如自摸、榮和或規則的特殊結果），流局為流局原因 ID。
 * @property kind 結算是和牌還是流局。
 * @property roundContinues 本局是否在這次和牌之後仍然繼續。
 * @property players 依固定座位順序排列的玩家結算前後分數與名次。
 */
@Serializable
data class RoundResultPayloadDto(
    val gameId: String,
    val ruleModuleId: String,
    val outcomeId: String,
    val kind: RoundResultKindDto,
    val roundContinues: Boolean,
    val players: List<RoundResultPlayerDto>,
)

/** 結算的種類。 */
@Serializable
enum class RoundResultKindDto {
    /** 和牌，或規則視同和牌的特殊結果。 */
    WIN,

    /** 流局，包含途中流局。 */
    DRAW,
}

/**
 * 單一玩家結算前後的分數與名次。
 *
 * @property playerId 玩家 UUID 字串。
 * @property seatIndex 固定座位 index。
 * @property isAi 是否由 AI 操控。
 * @property previousScore 結算前總分。
 * @property currentScore 結算後總分。
 * @property previousRank 結算前名次，從 1 開始。
 * @property currentRank 結算後名次，從 1 開始。
 */
@Serializable
data class RoundResultPlayerDto(
    val playerId: String,
    val seatIndex: Int,
    val isAi: Boolean,
    val previousScore: Int,
    val currentScore: Int,
    val previousRank: Int,
    val currentRank: Int,
)
