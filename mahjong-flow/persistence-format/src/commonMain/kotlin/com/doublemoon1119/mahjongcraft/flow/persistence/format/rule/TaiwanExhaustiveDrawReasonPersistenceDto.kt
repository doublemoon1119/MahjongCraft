package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import kotlinx.serialization.Serializable

/**
 * [TaiwanExhaustiveDrawReason] 的 persistence DTO。
 *
 * @property value 流局原因的穩定 persistence 值。
 */
@Serializable
data class TaiwanExhaustiveDrawReasonPersistenceDto(val value: TaiwanExhaustiveDrawReasonPersistenceValue)
