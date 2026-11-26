package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import kotlinx.serialization.Serializable

/**
 * [RiichiExhaustiveDrawReason] 的 persistence DTO。
 *
 * @property value 流局原因的穩定 persistence 值。
 */
@Serializable
data class RiichiExhaustiveDrawReasonPersistenceDto(val value: RiichiExhaustiveDrawReasonPersistenceValue)
