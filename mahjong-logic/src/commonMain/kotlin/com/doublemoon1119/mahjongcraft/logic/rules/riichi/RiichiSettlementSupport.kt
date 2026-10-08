package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInPaymentReasonIds
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.util.isHonor
import com.doublemoon1119.mahjongcraft.logic.util.isTerminal
import kotlin.uuid.Uuid

/**
 * 依自摸的計算結果換算成各玩家應付金額：莊家自摸時其他每位玩家支付相同點數，閒家自摸時莊家與其他閒家分別支付，
 * 包牌時由責任者支付包牌部分。
 *
 * @param tableState 目前桌況。
 * @param winnerId 自摸的玩家。
 * @param result 手牌價值計算結果。
 * @param isTsumoLoss 贏家收到的點數是否為所有付款的總和；三人麻將少一家，缺少的那一份不支付（自摸損）。
 *   為 false 時以計算結果的總點數為準。
 * @return 和牌結算；計算結果並非自摸應有的點數結算形狀（理論上不會發生，僅作防呆）時為 null。
 */
internal fun riichiTsumoResolution(
    tableState: TableState,
    winnerId: Uuid,
    result: RiichiHandValueResult,
    isTsumoLoss: Boolean,
): WinResolutionResult? {
    var paymentReasons: Map<Uuid, String> = emptyMap()
    val payments: Map<Uuid, Int> = when (val pointResult = result.pointResult) {
        is RiichiPointResult.DealerTsumo, is RiichiPointResult.NonDealerTsumo -> riichiTsumoPayments(tableState, winnerId, pointResult)

        is RiichiPointResult.PaoTsumo -> {
            // 理論上不會發生：RiichiHandValueCalculator 只在 paoLiability 非 null 時才會回傳 PaoTsumo。
            val paoLiability = result.paoLiability ?: return null
            val paoPlayerId = tableState.riichiPaoPlayerId(winnerId, paoLiability)
            paymentReasons = mapOf(paoPlayerId to BuiltInPaymentReasonIds.PAO)
            val remainder = pointResult.remainder?.let { riichiTsumoPayments(tableState, winnerId, it) }.orEmpty()
            mergePayments(mapOf(paoPlayerId to pointResult.paoPayment), remainder)
        }

        // Ron / PaoRon 理論上不會出現在自摸的計算結果中。若真的發生，視為此規則無法對這次自摸完成結算，
        // 回傳 null 讓呼叫端以 IllegalAction 處理，而非產生錯誤的結算。
        is RiichiPointResult.Ron, is RiichiPointResult.PaoRon -> return null
    }
    return WinResolutionResult(
        settlement = WinSettlementResult(
            totalGained = if (isTsumoLoss) payments.values.sum() else result.totalPoint,
            paymentsByPlayerId = payments,
            paymentReasonIdsByPlayerId = paymentReasons,
        ),
        handValueResult = result,
    )
}

/**
 * 依榮和的計算結果換算成各玩家應付金額：一般由放銃者支付全額；包牌時由放銃者與責任者平分包牌部分。
 *
 * @param discarderId 放銃者；拔北被榮和時為拔北的玩家。
 * @return 和牌結算；計算結果並非榮和應有的點數結算形狀（理論上不會發生，僅作防呆）時為 null。
 */
internal fun riichiRonResolution(
    tableState: TableState,
    winnerId: Uuid,
    discarderId: Uuid,
    result: RiichiHandValueResult,
): WinResolutionResult? {
    var paymentReasons: Map<Uuid, String> = emptyMap()
    val payments: Map<Uuid, Int> = when (val pointResult = result.pointResult) {
        is RiichiPointResult.Ron -> mapOf(discarderId to pointResult.total)

        is RiichiPointResult.PaoRon -> {
            // 理論上不會發生：RiichiHandValueCalculator 只在 paoLiability 非 null 時才會回傳 PaoRon。
            val paoLiability = result.paoLiability ?: return null
            val paoPlayerId = tableState.riichiPaoPlayerId(winnerId, paoLiability)
            // 包牌責任者剛好就是放銃者本人時，兩份「一半」其實是同一個人要付，直接歸戶成一筆全額。
            paymentReasons = mapOf(paoPlayerId to BuiltInPaymentReasonIds.PAO)
            val paoPayments = if (paoPlayerId == discarderId) {
                mapOf(discarderId to pointResult.paymentEach * 2)
            } else {
                mapOf(discarderId to pointResult.paymentEach, paoPlayerId to pointResult.paymentEach)
            }
            // remainder 在榮和情境下只會是 Ron，由放銃者全額支付。
            val remainder = (pointResult.remainder as? RiichiPointResult.Ron)?.let { mapOf(discarderId to it.total) }.orEmpty()
            mergePayments(paoPayments, remainder)
        }

        is RiichiPointResult.DealerTsumo, is RiichiPointResult.NonDealerTsumo, is RiichiPointResult.PaoTsumo -> return null
    }
    return WinResolutionResult(
        settlement = WinSettlementResult(
            totalGained = result.totalPoint,
            paymentsByPlayerId = payments,
            paymentReasonIdsByPlayerId = paymentReasons,
        ),
        handValueResult = result,
    )
}

/** 一般自摸（不含包牌）時其他每位玩家的付款；包牌與榮和形狀回傳空 map。 */
private fun riichiTsumoPayments(
    tableState: TableState,
    winnerId: Uuid,
    pointResult: RiichiPointResult,
): Map<Uuid, Int> = when (pointResult) {
    is RiichiPointResult.DealerTsumo -> tableState.players.filter { it.id != winnerId }.associate { it.id to pointResult.paymentPerNonDealer }
    is RiichiPointResult.NonDealerTsumo -> {
        val dealerId = tableState.dealerPlayerId
        tableState.players.filter { it.id != winnerId }
            .associate { it.id to if (it.id == dealerId) pointResult.dealerPayment else pointResult.otherNonDealerPayment }
    }
    is RiichiPointResult.Ron, is RiichiPointResult.PaoTsumo, is RiichiPointResult.PaoRon -> emptyMap()
}

/** 合併兩份付款；同一位玩家的金額相加，不互相覆蓋。 */
private fun mergePayments(base: Map<Uuid, Int>, extra: Map<Uuid, Int>): Map<Uuid, Int> = (base.keys + extra.keys).associateWith { id -> (base[id] ?: 0) + (extra[id] ?: 0) }

/**
 * 一般流局（牌山摸盡）的點數結算：聽牌判定（非 [ShantenResult.NotTenpai] 皆視為聽牌），並依
 * [riichiNotenPenaltyDeltas] 計算不聽罰符。只計入 [TableState.activePlayers]。
 */
internal fun riichiExhaustiveDrawSettlement(
    tableState: TableState,
    shantenCalculator: RiichiShantenCalculator,
    notenPenaltyUnit: Int,
): ExhaustiveDrawSettlementResult {
    val shantenByPlayer = tableState.activePlayers.associateWith { shantenCalculator.calculate(it.hand) }
    val tenpaiIds = shantenByPlayer.filterValues { it !is ShantenResult.NotTenpai }.keys.map { it.id }.toSet()
    return ExhaustiveDrawSettlementResult(
        reason = RiichiExhaustiveDrawReason.Normal,
        tenpaiPlayerIds = tenpaiIds,
        revealedHands = shantenByPlayer.mapNotNull { (player, result) ->
            val waits = (result as? ShantenResult.Tenpai)?.winningTiles ?: return@mapNotNull null
            RevealedHandSettlement(player.id, waits.toSet())
        },
        stickPotCollectorPlayerIds = emptySet(),
        scoreDeltas = riichiNotenPenaltyDeltas(tableState, tenpaiIds, notenPenaltyUnit),
    )
}

/**
 * 不聽罰符：總額為「[notenPenaltyUnit] × (仍在局中的人數 − 1)」（四人 3000、三人 2000），由聽牌者均分收取、
 * 不聽者均分支付。無人聽牌或全員聽牌時回傳空 map。
 */
private fun riichiNotenPenaltyDeltas(tableState: TableState, tenpaiIds: Set<Uuid>, notenPenaltyUnit: Int): Map<Uuid, Int> {
    val activeCount = tableState.activePlayers.size
    val notenIds = tableState.activePlayers.map { it.id }.toSet() - tenpaiIds
    if (tenpaiIds.isEmpty() || notenIds.isEmpty()) return emptyMap()

    val total = notenPenaltyUnit * (activeCount - 1)
    val gainPerTenpai = total / tenpaiIds.size
    val lossPerNoten = total / notenIds.size
    return tenpaiIds.associateWith { gainPerTenpai } + notenIds.associateWith { -lossPerNoten }
}

/**
 * 判定流局滿貫並計算自摸滿貫式收支；沒有任何人成立時回傳 `null`。
 *
 * 成立條件為牌河非空、全部是么九牌且從未被鳴走。只計入 [TableState.activePlayers]；付款方式與自摸相同，
 * 少於四人時缺少的那一份不支付。
 */
internal fun riichiNagashiMangan(tableState: TableState): NagashiManganResolution? {
    val achieverIds = tableState.activePlayers
        .filter { player ->
            player.discardPile.entries.isNotEmpty() &&
                player.discardPile.entries.all { entry -> !entry.isTaken && (entry.tile.tile.isTerminal || entry.tile.tile.isHonor) }
        }
        .mapTo(linkedSetOf()) { it.id }
    if (achieverIds.isEmpty()) return null

    val deltas = tableState.players.associate { it.id to 0 }.toMutableMap()
    achieverIds.forEach { achieverId ->
        val pointResult = PointCalculator.calculateNonYakumanPoint(han = 5, fu = 0, isDealer = tableState.isDealer(achieverId), isTsumo = true)
        val payments: Map<Uuid, Int> = when (pointResult) {
            is RiichiPointResult.DealerTsumo ->
                tableState.activePlayers.filter { it.id != achieverId }.associate { it.id to pointResult.paymentPerNonDealer }
            is RiichiPointResult.NonDealerTsumo -> {
                val dealerId = tableState.dealerPlayerId
                tableState.activePlayers.filter { it.id != achieverId }
                    .associate { it.id to if (it.id == dealerId) pointResult.dealerPayment else pointResult.otherNonDealerPayment }
            }
            // calculateNonYakumanPoint(isTsumo = true) 理論上只會回傳上述兩種結果，僅作防呆。
            is RiichiPointResult.Ron, is RiichiPointResult.PaoTsumo, is RiichiPointResult.PaoRon -> emptyMap()
        }
        deltas[achieverId] = (deltas[achieverId] ?: 0) + payments.values.sum()
        payments.forEach { (payerId, amount) -> deltas[payerId] = (deltas[payerId] ?: 0) - amount }
    }
    return NagashiManganResolution(achieverIds, deltas)
}

/** 九種九牌需要公開宣告者手牌作為成立證明，其餘途中流局不公開手牌。 */
internal fun riichiAbortiveDrawRevealedHands(
    tableState: TableState,
    declarerId: Uuid?,
    reason: ExhaustiveDrawReason,
): List<RevealedHandSettlement> {
    if (reason != RiichiExhaustiveDrawReason.KyuushuKyuuhai || declarerId == null) return emptyList()
    if (tableState.players.none { it.id == declarerId }) return emptyList()
    return listOf(RevealedHandSettlement(declarerId, emptySet()))
}

/**
 * 四槓散了：全場槓子數達到 4 個（含）以上，且不是同一位玩家獨得全部槓子時成立。
 */
internal fun riichiSuukanNagare(tableState: TableState): ExhaustiveDrawReason? {
    val kanCountsByPlayer = tableState.players.map { player ->
        player.hand.exposedMelds.count { it.type == MeldType.OPEN_KAN || it.type == MeldType.ADDED_KAN || it.type == MeldType.CLOSED_KAN }
    }
    val totalKans = kanCountsByPlayer.sum()
    if (totalKans < 4) return null
    return if (kanCountsByPlayer.any { it == totalKans }) null else RiichiExhaustiveDrawReason.SuukanNagare
}
