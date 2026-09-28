package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/**
 * 以評估者本人為觀察者的局面視角。
 *
 * [snapshot] 必須是以 [evaluatorId] 為觀察者產生的桌況快照，因此評估只能使用這位玩家實際看得到的資訊。
 *
 * @property snapshot 以評估者為觀察者的桌況快照。
 * @property evaluatorId 進行評估的玩家。
 */
data class PositionView(
    val snapshot: TableStateSnapshot,
    val evaluatorId: Uuid,
) {
    /** 評估者本人。 */
    val evaluator: MahjongPlayerSnapshot
        get() = player(evaluatorId)

    /** 取得桌上指定玩家；不存在時拋出例外。 */
    fun player(playerId: Uuid): MahjongPlayerSnapshot = snapshot.players.first { it.id == playerId }
}

/** 一種和牌方式的價值評估結果。 */
sealed interface WinValue {
    /**
     * 可以和牌。
     *
     * @property points 和牌可得的點數。
     */
    data class Points(val points: Int) : WinValue {
        init {
            require(points >= 0) { "Win value must not be negative" }
        }
    }

    /** 依規則不能以這種方式和牌，例如沒有役或未達起胡條件。 */
    data object NotWinnable : WinValue

    /** 規則無法從這個視角判斷。 */
    data object Unknown : WinValue
}

/**
 * 從公開資訊估計的對手威脅。
 *
 * @property readyProbability 對手已經聽牌的可能性，介於 0 到 1。
 * @property expectedWinValue 對手和牌時預估可得的點數；放銃給他時的損失以此估計。
 */
data class ThreatEstimate(
    val readyProbability: Double,
    val expectedWinValue: Int,
) {
    init {
        require(readyProbability in 0.0..1.0) { "Ready probability must be between 0 and 1" }
        require(expectedWinValue >= 0) { "Expected win value must not be negative" }
    }
}

/**
 * 宣告一個規則擴充動作的效果。
 *
 * 宣告對和牌打點的影響不在這裡表示，而是由 [PositionEvaluator.winValue] 的 `declarations` 參數計算，
 * 因為加番對點數的影響取決於整手牌。
 *
 * @property cost 宣告當下立即付出的點數。
 * @property locksHand 宣告後是否不能再改變手牌，只能打出摸到的牌。
 */
data class DeclarationEffect(
    val cost: Int = 0,
    val locksHand: Boolean = false,
) {
    init {
        require(cost >= 0) { "Declaration cost must not be negative" }
    }

    /** [DeclarationEffect] 的固定值。 */
    companion object {
        /** 沒有任何效果。 */
        val NONE: DeclarationEffect = DeclarationEffect()
    }
}

/**
 * 規則對一個局面的評估。
 *
 * 所有方法只讀取 [PositionView]，不接觸權威桌況，因此不可能使用評估者看不到的資訊。回傳值是估計而非判定：
 * 真正的合法性、和牌與計分仍由規則模組的正式流程決定。
 */
interface PositionEvaluator {
    /**
     * 評估者以 [winningTile] 完成 [hand] 時可得的點數。
     *
     * @param hand 不含和牌張的手牌。
     * @param isTsumo 是否為自摸。
     * @param declarations 假設和牌前已經宣告的擴充動作；評估者本人已宣告的動作不需要重複傳入。
     */
    fun winValue(
        view: PositionView,
        hand: Hand,
        winningTile: Tile,
        isTsumo: Boolean,
        declarations: Set<GameAction.Extension> = emptySet(),
    ): WinValue

    /** [playerId] 一手和牌的預估基準點數，用於無法具體估算手牌價值時。 */
    fun baselineWinValue(view: PositionView, playerId: Uuid): Int

    /** 評估者打出 [tile] 時，被已經聽牌的 [opponentId] 榮和的可能性，介於 0 到 1。 */
    fun discardDanger(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double

    /** 從公開資訊估計 [opponentId] 的威脅。 */
    fun threat(view: PositionView, opponentId: Uuid): ThreatEstimate

    /** 評估者宣告 [action] 的效果。 */
    fun declarationEffect(view: PositionView, action: GameAction.Extension): DeclarationEffect
}

/**
 * 不具任何規則知識的局面評估。
 *
 * 和牌價值一律回報無法判斷、所有和牌與放銃都以相同的單位點數計算、捨牌危險度不區分牌張、宣告沒有效果；
 * 對手的聽牌可能性只依副露數與捨牌數粗估。沒有提供專屬評估的規則因此仍能讓使用評估的決策正常運作，
 * 只是判斷較為保守。
 */
object NeutralPositionEvaluator : PositionEvaluator {
    /** 所有和牌與放銃共用的單位點數。 */
    const val UNIT_WIN_VALUE: Int = 1

    /** 不區分牌張時使用的捨牌危險度。 */
    const val UNIFORM_DISCARD_DANGER: Double = 0.1

    /** 沒有副露也沒有捨牌時的聽牌可能性。 */
    private const val BASE_READY_PROBABILITY: Double = 0.05

    /** 每一張捨牌增加的聽牌可能性。 */
    private const val READY_PROBABILITY_PER_DISCARD: Double = 0.03

    /** 每一組副露增加的聽牌可能性。 */
    private const val READY_PROBABILITY_PER_MELD: Double = 0.15

    /** 粗估時聽牌可能性的上限。 */
    private const val MAX_READY_PROBABILITY: Double = 0.9

    override fun winValue(
        view: PositionView,
        hand: Hand,
        winningTile: Tile,
        isTsumo: Boolean,
        declarations: Set<GameAction.Extension>,
    ): WinValue = WinValue.Unknown

    override fun baselineWinValue(view: PositionView, playerId: Uuid): Int = UNIT_WIN_VALUE

    override fun discardDanger(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double = UNIFORM_DISCARD_DANGER

    override fun threat(view: PositionView, opponentId: Uuid): ThreatEstimate {
        val opponent = view.player(opponentId)
        val openMelds = opponent.hand.melds.count { it.type != MeldType.CLOSED_KAN }
        val readyProbability = BASE_READY_PROBABILITY +
            READY_PROBABILITY_PER_DISCARD * opponent.discardPile.entries.size +
            READY_PROBABILITY_PER_MELD * openMelds
        return ThreatEstimate(
            readyProbability = readyProbability.coerceAtMost(MAX_READY_PROBABILITY),
            expectedWinValue = UNIT_WIN_VALUE,
        )
    }

    override fun declarationEffect(view: PositionView, action: GameAction.Extension): DeclarationEffect = DeclarationEffect.NONE
}
