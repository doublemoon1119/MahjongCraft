package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.PositionRules
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy
import kotlin.uuid.Uuid

/**
 * 在規則自己的對手模型之上，套用所有規則共用的讀牌。
 *
 * - 規則確定對手不能榮和的牌，危險度為 0：他自己打過的牌，以及他宣告聽牌後別人打出、他沒有胡的牌。
 * - 其他牌的危險度乘上 [reading] 的倍率，倍率先限制在 [factorRange]，結果再限制在 0 到 1。
 *
 * 預估打點與聽牌可能性直接使用 [delegate] 的估計。
 *
 * @property delegate 規則自己的對手模型。
 * @property rules 本局規則的規則查詢。
 * @property interpretation 規則的牌面正規化，用於比對不能榮和的牌。
 * @property reading 所有規則共用的讀牌。
 * @property factorRange 讀牌倍率允許的範圍。
 */
internal class ReadingOpponentModel(
    private val delegate: OpponentModel,
    private val rules: PositionRules,
    private val interpretation: TileInterpretationPolicy,
    private val reading: OpponentReading,
    private val factorRange: ClosedFloatingPointRange<Double>,
) : OpponentModel by delegate {
    override fun discardDanger(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double {
        val canonical = interpretation.canonicalize(tile)
        val exclusions = rules.ronExclusions(view, opponentId)
        if (canonical in exclusions.ownDiscards || canonical in exclusions.passedAfterDeclaration) return 0.0
        val factor = reading.dangerFactor(view, opponentId, tile).coerceIn(factorRange)
        return (delegate.discardDanger(view, opponentId, tile) * factor).coerceIn(0.0, 1.0)
    }
}
