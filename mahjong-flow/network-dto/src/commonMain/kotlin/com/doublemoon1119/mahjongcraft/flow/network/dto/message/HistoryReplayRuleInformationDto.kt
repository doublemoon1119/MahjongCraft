package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * Replay 規則公開資訊 DTO。
 *
 * @property typeKey 穩定資訊種類識別碼。
 * @property summary 公開摘要；不可公開時為 null。
 */
@Serializable
data class HistoryReplayRuleInformationDto(val typeKey: String, val summary: String?)
