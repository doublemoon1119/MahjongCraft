package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryActionTypeKeys
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [GameAction] 的完整 persistence DTO。 */
@Serializable
sealed interface GameActionPersistenceDto {
    /** [GameAction.GameStarted] 的 persistence DTO。 */
    @Serializable
    @SerialName(HistoryActionTypeKeys.GAME_STARTED)
    data object GameStarted : GameActionPersistenceDto

    /** [GameAction.RoundStarted] 的 persistence DTO。 */
    @Serializable
    @SerialName(HistoryActionTypeKeys.ROUND_STARTED)
    data object RoundStarted : GameActionPersistenceDto

    /** [GameAction.MatchEnded] 的 persistence DTO。 */
    @Serializable
    @SerialName(HistoryActionTypeKeys.MATCH_ENDED)
    data object MatchEnded : GameActionPersistenceDto

    /**
     * [GameAction.DiceRolled] 的 persistence DTO。
     *
     * @property dice 擲出的骰子結果。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.DICE_ROLLED)
    data class DiceRolled(val dice: List<Int>) : GameActionPersistenceDto

    /** [GameAction.Draw] 的 persistence DTO。 */
    @Serializable
    @SerialName(HistoryActionTypeKeys.DRAW)
    data object Draw : GameActionPersistenceDto

    /**
     * [GameAction.Discard] 的 persistence DTO。
     *
     * @property tileId 相關牌張的識別碼。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.DISCARD)
    data class Discard(val tileId: String) : GameActionPersistenceDto

    /**
     * [GameAction.Chi] 的 persistence DTO。
     *
     * @property tileId 相關牌張的識別碼。
     * @property withTileIds 動作同時使用的其他牌張識別碼。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.CHI)
    data class Chi(val tileId: String, val withTileIds: List<String>) : GameActionPersistenceDto

    /**
     * [GameAction.Pon] 的 persistence DTO。
     *
     * @property tileId 相關牌張的識別碼。
     * @property withTileIds 動作同時使用的其他牌張識別碼。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.PON)
    data class Pon(val tileId: String, val withTileIds: List<String>) : GameActionPersistenceDto

    /**
     * [GameAction.Kan] 的 persistence DTO。
     *
     * @property kanType 槓的種類。
     * @property tileId 相關牌張的識別碼。
     * @property withTileIds 動作同時使用的其他牌張識別碼。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.KAN)
    data class Kan(
        val kanType: KanTypePersistenceDto,
        val tileId: String,
        val withTileIds: List<String>,
    ) : GameActionPersistenceDto

    /**
     * [GameAction.Ron] 的 persistence DTO。
     *
     * @property tileId 相關牌張的識別碼。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.RON)
    data class Ron(val tileId: String) : GameActionPersistenceDto

    /** [GameAction.Tsumo] 的 persistence DTO。 */
    @Serializable
    @SerialName(HistoryActionTypeKeys.TSUMO)
    data object Tsumo : GameActionPersistenceDto

    /**
     * [GameAction.Extension] 的 persistence DTO。
     *
     * @property value 保存的型別化值。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.EXTENSION)
    data class Extension(val value: TypedPersistenceDto) : GameActionPersistenceDto

    /** [GameAction.Pass] 的 persistence DTO。 */
    @Serializable
    @SerialName(HistoryActionTypeKeys.PASS)
    data object Pass : GameActionPersistenceDto

    /**
     * [GameAction.ExhaustiveDraw] 的 persistence DTO。
     *
     * @property reason 流局或動作的原因資料。
     */
    @Serializable
    @SerialName(HistoryActionTypeKeys.EXHAUSTIVE_DRAW)
    data class ExhaustiveDraw(val reason: TypedPersistenceDto) : GameActionPersistenceDto
}
