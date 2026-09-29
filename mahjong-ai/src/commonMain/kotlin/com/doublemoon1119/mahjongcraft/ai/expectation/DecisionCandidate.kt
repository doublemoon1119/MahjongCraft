package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import kotlin.uuid.Uuid

/** 期望值計算比較的一個選項；每個選項都換算成動作完成後的手牌評估。 */
internal sealed interface DecisionCandidate {
    /** 選中時送出的命令。 */
    val command: GameCommand

    /**
     * 不鳴牌，保留目前的手牌。
     *
     * @property command 選中時送出的命令。
     */
    data class Pass(override val command: GameCommand) : DecisionCandidate

    /**
     * 打出一張牌，可同時宣告一個擴充動作。
     *
     * @property tile 打出的牌。
     * @property declaration 同時宣告的擴充動作；沒有宣告時為 null。
     * @property command 選中時送出的命令。
     */
    data class Discard(
        val tile: IdentifiedTile,
        val declaration: GameAction.Extension?,
        override val command: GameCommand,
    ) : DecisionCandidate

    /**
     * 鳴他家的捨牌組成副露；非槓的副露之後還要打出一張牌。
     *
     * @property meldType 組成的副露種類。
     * @property claimedTile 鳴進的他家捨牌。
     * @property handTileIds 從自己手牌中拿出來組成副露的牌。
     * @property command 選中時送出的命令。
     */
    data class Claim(
        val meldType: MeldType,
        val claimedTile: IdentifiedTile,
        val handTileIds: List<Uuid>,
        override val command: GameCommand,
    ) : DecisionCandidate

    /**
     * 自己回合的暗槓或加槓。
     *
     * @property type 槓的種類。
     * @property tileIds 從自己手牌中拿出來的牌。
     * @property command 選中時送出的命令。
     */
    data class SelfKan(
        val type: GameAction.KanType,
        val tileIds: List<Uuid>,
        override val command: GameCommand,
    ) : DecisionCandidate
}

/**
 * 一個選項的評估結果。
 *
 * @property candidate 被評估的選項。
 * @property expectedValue 選項的期望值；只在同一次決策內互相比較才有意義。
 * @property winProbability 選項完成後本局和牌的估計機率。
 */
internal data class CandidateScore(
    val candidate: DecisionCandidate,
    val expectedValue: Double,
    val winProbability: Double,
)
