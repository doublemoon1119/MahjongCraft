package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/**
 * 以評估者本人為觀察者的局面視角。
 *
 * [snapshot] 必須是以 [evaluatorId] 為觀察者產生的桌況快照，因此查詢只能使用這位玩家實際看得到的資訊。
 *
 * @property snapshot 以評估者為觀察者的桌況快照。
 * @property evaluatorId 進行查詢的玩家。
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

/** 一種和牌方式的價值。 */
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
 * 宣告一個規則擴充動作的效果。
 *
 * 宣告對和牌打點的影響不在這裡表示，而是由 [PositionRules.winValue] 的 `declarations` 參數計算，
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
 * 對手依規則已經不能榮和的牌，牌面以規則的牌面正規化表示。
 *
 * @property ownDiscards 對手自己打過、因此不能榮和的牌。
 * @property passedAfterDeclaration 對手宣告後由其他玩家打出、對手沒有榮和，因此之後也不能榮和的牌。
 */
data class RonExclusions(
    val ownDiscards: Set<Tile> = emptySet(),
    val passedAfterDeclaration: Set<Tile> = emptySet(),
) {
    /** [RonExclusions] 的固定值。 */
    companion object {
        /** 沒有任何不能榮和的牌。 */
        val NONE: RonExclusions = RonExclusions()
    }
}

/**
 * 從一個局面視角查詢規則事實。
 *
 * 所有方法只讀取 [PositionView]，不接觸權威桌況，因此不可能使用評估者看不到的資訊。這裡只回答規則怎麼規定，
 * 不包含任何估計；估計對手的打點、聽牌可能性與捨牌危險度屬於 AI。真正的合法性、和牌與計分仍由規則模組的正式流程決定。
 */
interface PositionRules {
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

    /** 評估者宣告 [action] 的效果。 */
    fun declarationEffect(view: PositionView, action: GameAction.Extension): DeclarationEffect

    /**
     * 評估者的 [hand] 之後聽牌時可以選擇宣告的擴充動作，用於估計尚未聽牌的手牌聽牌後的打點。
     *
     * 只依手牌型態與評估者目前的狀態判斷，不保證聽牌當下一定合法；預設沒有任何動作。
     *
     * @param hand 尚未聽牌的手牌。
     */
    fun prospectiveDeclarations(view: PositionView, hand: Hand): Set<GameAction.Extension> = emptySet()

    /** 從公開資訊可以確定 [opponentId] 依規則不能榮和的牌；預設沒有。 */
    fun ronExclusions(view: PositionView, opponentId: Uuid): RonExclusions = RonExclusions.NONE

    /** [tile] 目前算作幾張寶牌；沒有寶牌制度的規則恆為 0。 */
    fun bonusTileCount(view: PositionView, tile: Tile): Int = 0
}

/**
 * 不具任何規則知識的規則查詢：和牌價值一律回報無法判斷，宣告沒有效果，沒有不能榮和的牌，也沒有寶牌。
 */
object NeutralPositionRules : PositionRules {
    override fun winValue(
        view: PositionView,
        hand: Hand,
        winningTile: Tile,
        isTsumo: Boolean,
        declarations: Set<GameAction.Extension>,
    ): WinValue = WinValue.Unknown

    override fun declarationEffect(view: PositionView, action: GameAction.Extension): DeclarationEffect = DeclarationEffect.NONE
}
