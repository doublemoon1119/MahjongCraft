package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import kotlin.uuid.Uuid

/**
 * [MahjongRuleModule.declareExhaustiveDraw] 計算一般流局結算後的結果。
 *
 * @property reason 本次流局的具體原因（由規則模組自行決定其具體型別，例如日麻的 `RiichiExhaustiveDrawReason.Normal`）。
 * @property tenpaiPlayerIds 聽牌的玩家 Uuid 集合，供規則與流局公開政策使用。
 * @property stickPotCollectorPlayerIds 本次流局中應收下場上供託（如立直棒）的玩家 Uuid 集合，
 *   通常為空集合。刻意不用規則專屬的具體概念命名（例如日麻的「流局滿貫」）——呼叫端
 *   只在意「這次流局供託該分給誰」。
 * @property scoreDeltas 本次流局造成的玩家分數異動（正負皆可，直接加總到玩家分數）。
 *   若無人需要交換分數則為空 map。
 */
data class ExhaustiveDrawSettlementResult(
    val reason: ExhaustiveDrawReason,
    val tenpaiPlayerIds: Set<Uuid>,
    val revealedHands: List<RevealedHandSettlement> = emptyList(),
    val stickPotCollectorPlayerIds: Set<Uuid>,
    val scoreDeltas: Map<Uuid, Int>,
)
