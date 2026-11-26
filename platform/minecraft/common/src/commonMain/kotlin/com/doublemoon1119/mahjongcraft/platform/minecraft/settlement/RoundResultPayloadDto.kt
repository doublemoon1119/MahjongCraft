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
