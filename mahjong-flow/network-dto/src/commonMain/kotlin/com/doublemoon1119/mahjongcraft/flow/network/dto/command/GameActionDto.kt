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
    /** 對局已開始。 */
    @Serializable data object GameStarted : GameActionDto

    /** 新的一局已開始。 */
    @Serializable data object RoundStarted : GameActionDto

    /** 對局已結束。 */
    @Serializable data object MatchEnded : GameActionDto

    /**
     * DiceRolled 網路 DTO。
     *
     * @property rollIndex 本局中的零基底擲骰索引。
     * @property playerId 實際擲骰玩家 UUID 字串。
     * @property dice 逐顆骰子結果。
     */
    @Serializable data class DiceRolled(val rollIndex: Int, val playerId: String, val dice: List<Int>) : GameActionDto

    /** 玩家摸牌。 */
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

    /** 玩家以自摸胡牌。 */
    @Serializable data object Tsumo : GameActionDto

    /**
     * Extension 網路 DTO。
     *
     * @property value 由 registry 註冊的擴充遊戲動作 DTO。
     */
    @Serializable data class Extension(@Polymorphic val value: ExtensionGameActionDto) : GameActionDto

    /** 玩家放棄本次可選反應。 */
    @Serializable data object Pass : GameActionDto

    /**
     * ExhaustiveDraw 網路 DTO。
     *
     * @property reason 本次流局的具體原因，由規則模組提供並透過註冊表轉換。
     */
    @Serializable data class ExhaustiveDraw(val reason: ExhaustiveDrawReasonDto) : GameActionDto
}
