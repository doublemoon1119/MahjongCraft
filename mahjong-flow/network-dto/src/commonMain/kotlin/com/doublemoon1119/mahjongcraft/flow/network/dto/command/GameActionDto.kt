package com.doublemoon1119.mahjongcraft.flow.network.dto.command

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExhaustiveDrawReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameActionDto
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

/**
 * [GameAction] 的網路 DTO。[ExhaustiveDraw.reason] 是開放型別
 * （見 [ExhaustiveDrawReasonDto]），透過 registry 轉換，不是窮舉 `when`。
 */
@Serializable
sealed interface GameActionDto {
    @Serializable data object GameStarted : GameActionDto

    @Serializable data object RoundStarted : GameActionDto

    @Serializable data object MatchEnded : GameActionDto

    /**
     * DiceRolled 網路 DTO。
     *
     * @property dice 骰子結果。
     */
    @Serializable data class DiceRolled(val dice: List<Int>) : GameActionDto

    @Serializable data object Draw : GameActionDto

    /**
     * Discard 網路 DTO。
     *
     * @property tileId 牌的識別碼。
     */
    @Serializable data class Discard(val tileId: String) : GameActionDto

    /**
     * Chi 網路 DTO。
     *
     * @property tileId 牌的識別碼。
     * @property withTiles 鳴牌所使用的牌識別碼。
     */
    @Serializable data class Chi(val tileId: String, val withTiles: List<String>) : GameActionDto

    /**
     * Pon 網路 DTO。
     *
     * @property tileId 牌的識別碼。
     * @property withTiles 鳴牌所使用的牌識別碼。
     */
    @Serializable data class Pon(val tileId: String, val withTiles: List<String>) : GameActionDto

    /**
     * Kan 網路 DTO。
     *
     * @property kanType 槓牌種類。
     * @property tileId 牌的識別碼。
     * @property withTiles 鳴牌所使用的牌識別碼。
     */
    @Serializable data class Kan(val kanType: KanTypeDto, val tileId: String, val withTiles: List<String>) : GameActionDto

    /**
     * Ron 網路 DTO。
     *
     * @property tileId 牌的識別碼。
     */
    @Serializable data class Ron(val tileId: String) : GameActionDto

    @Serializable data object Tsumo : GameActionDto

    /**
     * Extension 網路 DTO。
     *
     * @property value 擴充值或牌面數值。
     */
    @Serializable data class Extension(@Polymorphic val value: ExtensionGameActionDto) : GameActionDto

    @Serializable data object Pass : GameActionDto

    /**
     * ExhaustiveDraw 網路 DTO。
     *
     * @property reason 相關的結果或操作原因。
     */
    @Serializable data class ExhaustiveDraw(val reason: ExhaustiveDrawReasonDto) : GameActionDto
}
