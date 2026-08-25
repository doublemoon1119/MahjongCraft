package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import kotlin.uuid.Uuid

/**
 * [MahjongRuleModule.declareExhaustiveDraw] 計算一般流局結算後的結果。
 *
 * @property reason 本次流局的具體原因（由規則模組自行決定其具體型別，如 [RiichiExhaustiveDrawReason.Normal]）。
 * @property tenpaiPlayerIds 聽牌的玩家 Uuid 集合。以自摸式點數結算的玩家（如日麻的流局滿貫成立者）
 *   視為聽牌，一併包含在內（見 [stickPotCollectorPlayerIds]），供呼叫端記錄連莊依據使用。
 * @property stickPotCollectorPlayerIds 本次流局中應收下場上供託（如立直棒）的玩家 Uuid 集合，
 *   通常為空集合。刻意不用規則專屬的具體概念命名（例如日麻的「流局滿貫」）——呼叫端
 *   （`:mahjong-flow`）只在意「這次流局供託該分給誰」，不需要、也不應該知道背後是哪個規則特有
 *   的機制造成的。
 * @property scoreDeltas 本次流局造成的玩家分數異動（正負皆可，直接加總到玩家分數）。
 *   以自摸式點數結算的玩家（如流局滿貫成立者）為每位成立者的自摸滿貫收支（整局不再進行不聽罰符
 *   收授）；否則為不聽罰符的收支；若無人聽牌、全員聽牌、或無人不聽，則為空 map。
 */
data class ExhaustiveDrawSettlementResult(
    val reason: ExhaustiveDrawReason,
    val tenpaiPlayerIds: Set<Uuid>,
    val revealedHands: List<RevealedHandSettlement> = emptyList(),
    val stickPotCollectorPlayerIds: Set<Uuid>,
    val scoreDeltas: Map<Uuid, Int>,
)

/**
 * 流局結算後由規則正式公開的一副手牌及其等待牌。
 *
 * [waitingTiles] 為空仍可要求公開手牌，例如九種九牌需要攤牌證明，但不是聽牌展示。
 *
 * @property playerId 手牌所屬玩家 Uuid。
 * @property waitingTiles 規則判定的等待牌；沒有等待牌資訊時為空集合。
 */
data class RevealedHandSettlement(
    val playerId: Uuid,
    val waitingTiles: Set<Tile>,
)
