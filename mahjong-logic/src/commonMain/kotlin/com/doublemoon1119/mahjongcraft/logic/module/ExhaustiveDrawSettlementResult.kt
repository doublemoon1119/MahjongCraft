package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import kotlin.uuid.Uuid

/**
 * [MahjongRuleModule.declareExhaustiveDraw] 計算一般流局結算後的結果。
 *
 * @property reason 本次流局的具體原因（由規則模組自行決定其具體型別，如
 *   [com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason.Normal]）。
 * @property tenpaiPlayerIds 聽牌的玩家 Uuid 集合。流局滿貫成立者視為聽牌，一併包含在內
 *   （見 [nagashiManganPlayerIds]），供呼叫端記錄連莊依據使用。
 * @property nagashiManganPlayerIds 成立流局滿貫的玩家 Uuid 集合，通常為空集合。
 * @property scoreDeltas 本次流局造成的玩家分數異動（正負皆可，直接加總到玩家分數）。
 *   流局滿貫成立時為每位成立者的自摸滿貫收支（整局不再進行不聽罰符收授）；
 *   否則為不聽罰符的收支；若無人聽牌、全員聽牌、或無人不聽，則為空 map。
 */
data class ExhaustiveDrawSettlementResult(
    val reason: ExhaustiveDrawReason,
    val tenpaiPlayerIds: Set<Uuid>,
    val nagashiManganPlayerIds: Set<Uuid>,
    val scoreDeltas: Map<Uuid, Int>,
)
