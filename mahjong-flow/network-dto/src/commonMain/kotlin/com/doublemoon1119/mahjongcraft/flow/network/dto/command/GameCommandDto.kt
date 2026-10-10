package com.doublemoon1119.mahjongcraft.flow.network.dto.command

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RoundPreparationSubmissionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExhaustiveDrawReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameCommandDto
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

/**
 * [GameCommand] 的網路 DTO。跟 [GameActionDto] 一樣獨立鏡射成一份 DTO，而不是直接讓
 * `GameCommand` 本身標 `@Serializable`——這個型別樹裡凡是會上線的型別風格一律統一走顯式 DTO +
 * mapper，不要「有些欄位是 DTO、有些欄位是領域型別」的混搭（即使 `:mahjong-flow-common` 本身已經
 * 依賴 Koin、不是「純」模組，這裡仍刻意不直接標註）。
 */
@Serializable
sealed interface GameCommandDto {
    /**
     * SubmitRoundPreparation 網路 DTO。
     *
     * @property submission 玩家提交的開局準備資料。
     */
    @Serializable data class SubmitRoundPreparation(val submission: RoundPreparationSubmissionDto) : GameCommandDto

    /**
     * Extension 網路 DTO。
     *
     * @property value 擴充值或牌面數值。
     */
    @Serializable data class Extension(@Polymorphic val value: ExtensionGameCommandDto) : GameCommandDto

    @Serializable data object Draw : GameCommandDto

    /**
     * Discard 網路 DTO。
     *
     * @property tileId 牌的識別碼。
     */
    @Serializable data class Discard(val tileId: String) : GameCommandDto

    @Serializable data object Tsumo : GameCommandDto

    /**
     * Kan 網路 DTO。
     *
     * @property kanType 槓牌種類。
     * @property tileId 牌的識別碼。
     */
    @Serializable data class Kan(val kanType: KanTypeDto, val tileId: String) : GameCommandDto

    /**
     * RespondToDiscard 網路 DTO。
     *
     * @property action 玩家選擇的遊戲動作。
     */
    @Serializable data class RespondToDiscard(val action: GameActionDto) : GameCommandDto

    /**
     * RespondToRobbing 網路 DTO。
     *
     * @property action 玩家選擇的遊戲動作。
     */
    @Serializable data class RespondToRobbing(val action: GameActionDto) : GameCommandDto

    /**
     * DeclareExhaustiveDraw 網路 DTO。
     *
     * @property reason 相關的結果或操作原因。
     */
    @Serializable data class DeclareExhaustiveDraw(val reason: ExhaustiveDrawReasonDto) : GameCommandDto
}
