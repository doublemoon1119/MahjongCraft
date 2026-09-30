package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 榮和時每一本場的點數。 */
const val RIICHI_COMBO_BONUS_RON_POINTS: Int = 300

/** 自摸時每一本場由其他每位玩家各付的點數。 */
const val RIICHI_COMBO_BONUS_TSUMO_POINTS_PER_PAYER: Int = 100

/**
 * 日麻本場的付款分攤。
 *
 * - 一般榮和：放銃者支付 本場數 × [RIICHI_COMBO_BONUS_RON_POINTS]。
 * - 一般自摸（含流局滿貫）：其他仍在局中的每位玩家各付 本場數 × [RIICHI_COMBO_BONUS_TSUMO_POINTS_PER_PAYER]。
 * - 包牌自摸，或包牌責任者本人放銃：責任者一人支付 本場數 × [RIICHI_COMBO_BONUS_RON_POINTS]。
 * - 第三方放銃給被包者：放銃者與責任者各付 本場數 × [RIICHI_COMBO_BONUS_RON_POINTS] 的一半。
 *
 * @param tableState 目前的桌況（尚未套用本次胡牌結算）。
 * @param winnerId 收取本場的贏家 Uuid。
 * @param discarderId 放銃者 Uuid；自摸或流局滿貫為 null。
 * @param paoPlayerId 本次胡牌成立包牌時的責任者 Uuid；沒有包牌時為 null。
 * @return 每位付款者應支付的本場點數；本場數為 0 時回傳空 map。
 */
internal fun riichiComboBonusPayments(
    tableState: TableState,
    winnerId: Uuid,
    discarderId: Uuid?,
    paoPlayerId: Uuid?,
): Map<Uuid, Int> {
    val comboCount = tableState.comboCount
    if (comboCount <= 0) return emptyMap()
    val fullBonus = comboCount * RIICHI_COMBO_BONUS_RON_POINTS
    return when {
        paoPlayerId != null && (discarderId == null || discarderId == paoPlayerId) -> mapOf(paoPlayerId to fullBonus)
        paoPlayerId != null && discarderId != null -> mapOf(discarderId to fullBonus / 2, paoPlayerId to fullBonus / 2)
        discarderId != null -> mapOf(discarderId to fullBonus)
        else ->
            tableState.activePlayers
                .filter { it.id != winnerId }
                .associate { it.id to comboCount * RIICHI_COMBO_BONUS_TSUMO_POINTS_PER_PAYER }
    }
}

/**
 * 贏家本次胡牌的包牌責任者：依 [PaoLiability.direction] 找出相對於 [winnerId] 位於該方向的玩家。
 *
 * @param winnerId 被包的贏家 Uuid。
 * @param paoLiability 贏家身上成立的包牌責任。
 * @return 包牌責任者 Uuid。
 */
internal fun TableState.riichiPaoPlayerId(winnerId: Uuid, paoLiability: PaoLiability): Uuid = players
    .first { relativeDirectionOf(winnerId, it.id) == paoLiability.direction }
    .id
