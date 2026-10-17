package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 一般流局原子結算成功後交給流程協調器的結果。
 *
 * @property tableState 結算後的權威桌況。
 * @property settlement 本次規則計算出的結算與公開手牌資訊；呈現流程必須使用這份結果，不能重新計算。
 */
data class DeclaredExhaustiveDraw(
    val tableState: TableState,
    val settlement: ExhaustiveDrawSettlementResult,
)
