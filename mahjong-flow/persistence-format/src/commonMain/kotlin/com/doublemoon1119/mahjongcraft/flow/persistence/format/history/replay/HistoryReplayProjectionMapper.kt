package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayMeld
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayPlayerState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayRuleInformation
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundTileCatalog
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryTileReference
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.MatchRoundPositionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.MeldTypePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RelativeDirectionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.MeldTypeId
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 將已驗證的索引投影轉為歷史讀模型，不還原權威規則命令。
 * @property registry 規則專屬公開資料的明確解碼器。
 */
internal class HistoryReplayProjectionMapper(private val registry: HistoryReplayProjectionRegistry) {
    /**
     * 映射選取位置的唯一桌況。
     * @param projection 已還原桌況。
     * @param identity header 玩家身分。
     * @param roundNumber 保存局序號。
     * @param position 選取位置。
     * @param tileCatalog 已宣告的局內實體牌。
     * @param budget 走訪預算。
     * @return 歷史桌況。
     */
    fun mapState(projection: JsonElement, identity: HistoryReplayIdentity, roundNumber: Int, position: HistoryRoundPosition, tileCatalog: HistoryRoundTileCatalog, budget: ReplayReadBudget): HistoryRoundState {
        val root = obj(projection)
        val context = HistoryReplayProjectionContext(budget, tileCatalog.tiles.size, identity.players.size)
        val encodedPlayers = array(root.getValue(ReplayFormatKeys.PLAYERS))
        require(encodedPlayers.size == identity.players.size) { "Replay player count does not match header" }
        val players = encodedPlayers.map { mapPlayer(obj(it), context) }
        require(players.map { it.initialSeatIndex }.toSet() == identity.players.indices.toSet()) { "Replay player seats must form a complete sequence" }
        val dealer = context.seat(integer(root.getValue("dealerPlayerId")))
        val current = integer(root.getValue("currentPlayerIndex"))
        val roundPosition = Json.decodeFromJsonElement(MatchRoundPositionPersistenceDto.serializer(), root.getValue("roundPosition")).toDomain()
        val wind = Wind.valueOf(string(root.getValue("prevalentWind")))
        require(roundPosition.prevalentWind == wind && roundPosition.roundNumber == roundNumber) { "Replay round position is inconsistent" }
        val wall = refs(obj(root.getValue("tileWall")).getValue("tiles"), context)
        val reserved = refs(root.getValue("initialDeadWall"), context)
        val held = wall + reserved + players.flatMap { player -> player.handTiles + player.melds.flatMap { it.tiles } + player.discards.filterNot { it.isTaken }.map { it.tile } }
        require(held.map { it.tileIndex }.distinct().size == held.size) { "Replay tile appears in multiple holding areas" }
        return HistoryRoundState(
            identity, roundNumber, position, tileCatalog, players, wall, reserved,
            players.getOrNull(current)?.initialSeatIndex ?: invalid(), dealer, wind, roundPosition,
            integer(root.getValue("comboCount")), root["finishedPlayerIds"]?.let { array(it).map { value -> context.seat(integer(value)) }.toSet() } ?: emptySet(),
            optionalRule(root["dynamicRuleState"], context), root.getValue("pendingReaction") != JsonNull, root.getValue("pendingKanReaction") != JsonNull, null,
        )
    }

    /**
     * 依序映射事實，不公開擴充的私有 payload。
     * @param facts 解碼後的語意事實。
     * @param actorSeats 與事實一對一的行為者。
     * @param identity 玩家身分。
     * @param roundNumber 局序號。
     * @param tileCatalog 已宣告的牌。
     * @param budget 走訪預算。
     * @return 有序歷史事實。
     */
    fun mapFacts(facts: List<JsonObject>, actorSeats: List<Int?>, identity: HistoryReplayIdentity, roundNumber: Int, tileCatalog: HistoryRoundTileCatalog, budget: ReplayReadBudget): List<HistoryReplayFact> {
        require(roundNumber > 0 && facts.size == actorSeats.size) { "Replay fact context is inconsistent" }
        val context = HistoryReplayProjectionContext(budget, tileCatalog.tiles.size, identity.players.size)
        return facts.mapIndexed { index, fact ->
            budget.charge()
            val type = string(fact.getValue(ReplaySourceKeys.TYPE))
            val actor = actorSeats[index]?.let(context::seat)
            val direct = fact[ReplaySourceKeys.DIRECT_TILES]?.let { refs(it, context) } ?: emptyList()
            val revealed = fact[ReplaySourceKeys.REVEALED_TILES]?.let { refs(it, context) } ?: emptyList()
            when (type) {
                ReplaySourceKeys.ACTION_ACCEPTED -> {
                    val action = obj(fact.getValue(ReplaySourceKeys.ACTION))
                    val actionType = string(action.getValue(ReplaySourceKeys.TYPE))
                    action["tileId"]?.let { context.tile(integer(it)) }
                    action["withTileIds"]?.let { refs(it, context) }
                    val extension = if (actionType == "extension") obj(action.getValue("value")) else null
                    val extensionType = extension?.getValue(ReplaySourceKeys.TYPE_KEY)?.let(::string)
                    extensionType?.let { registry.decodeAction(it, checkNotNull(extension).getValue(ReplayFormatKeys.PAYLOAD), context) }
                        ?: HistoryReplayFact.KnownAction(type, actor, actionType, direct, revealed, extensionType)
                }
                "reaction_resolved" -> {
                    val action = fact.getValue("resolvedAction").takeUnless { it == JsonNull }?.let(::obj)
                    val resolved = fact.getValue("actorPlayerId").takeUnless { it == JsonNull }?.let { context.seat(integer(it)) }
                    HistoryReplayFact.Reaction(type, action?.getValue(ReplaySourceKeys.TYPE)?.let(::string), resolved)
                }
                "round_preparation_started", "round_preparation_submitted", "round_preparation_automatic_resolved" ->
                    HistoryReplayFact.Preparation(type, string(fact.getValue("stepId")), integer(fact.getValue("stepIndex")).also { require(it >= 0) { "Replay preparation index must not be negative" } }, fact["nextStepId"]?.takeUnless { it == JsonNull }?.let(::string))
                "round_completed" -> HistoryReplayFact.Completion(type, outcome(obj(fact.getValue("summary")), context))
                "rule_effect_resolved" -> HistoryReplayFact.RuleEffect(type, string(fact.getValue("reasonId")), fact.getValue("roundCompletion").takeUnless { it == JsonNull }?.let { outcome(obj(it), context) })
                "match_completed" -> HistoryReplayFact.Completion(type, HistoryRoundOutcome(string(fact.getValue("reasonId")), emptyList(), scores(fact.getValue("finalScoresByPlayerId"), context)))
                else -> registry.decodeFact(type, fact, context) ?: HistoryReplayFact.Opaque(type, actor, direct, revealed)
            }
        }
    }

    /**
     * 映射具有明確初始座位的玩家。
     * @param value 玩家投影。
     * @param context 索引驗證上下文。
     * @return 玩家歷史桌況。
     */
    private fun mapPlayer(value: JsonObject, context: HistoryReplayProjectionContext): HistoryReplayPlayerState {
        context.charge()
        val hand = obj(value.getValue("hand"))
        val tiles = refs(hand.getValue("tiles"), context)
        val lastDrawn = hand.getValue("lastDrawn").takeUnless { it == JsonNull }?.let { context.tile(integer(it)) }
        require(lastDrawn == null || lastDrawn in tiles) { "Replay last drawn tile must belong to the hand" }
        val pile = obj(value.getValue("discardPile"))
        val typeKey = string(pile.getValue(ReplaySourceKeys.TYPE_KEY))
        val discards = registry.decodeDiscard(typeKey, pile.getValue(ReplayFormatKeys.PAYLOAD), context) ?: throw ReplayReadException(ReplayReadError.UNSUPPORTED_CONTENT)
        return HistoryReplayPlayerState(
            context.seat(integer(value.getValue(ReplaySourceKeys.INITIAL_SEAT_INDEX))),
            tiles,
            array(hand.getValue("melds")).map { meld(obj(it), context) },
            lastDrawn,
            discards,
            integer(value.getValue("score")),
            Wind.valueOf(string(value.getValue("seatWind"))),
            optionalRule(value["playerRuleState"], context),
        )
    }

    /**
     * 保留副露牌順序、來源牌與來源方向。
     * @param value 副露投影。
     * @param context 索引驗證上下文。
     * @return 型別化副露。
     */
    private fun meld(value: JsonObject, context: HistoryReplayProjectionContext): HistoryReplayMeld {
        context.charge()
        val type = when (val saved = Json.decodeFromJsonElement(MeldTypePersistenceDto.serializer(), value.getValue(ReplaySourceKeys.TYPE))) {
            MeldTypePersistenceDto.Chi -> MeldType.CHI
            MeldTypePersistenceDto.Pon -> MeldType.PON
            MeldTypePersistenceDto.OpenKan -> MeldType.OPEN_KAN
            MeldTypePersistenceDto.ClosedKan -> MeldType.CLOSED_KAN
            MeldTypePersistenceDto.AddedKan -> MeldType.ADDED_KAN
            is MeldTypePersistenceDto.Extension -> MeldType.Extension(MeldTypeId.parse(saved.typeId))
        }
        val tiles = refs(value.getValue("tiles"), context)
        val source = value.getValue("sourceTile").takeUnless { it == JsonNull }?.let { context.tile(integer(it)) }
        require(source == null || source in tiles) { "Replay meld source must belong to the meld" }
        val direction = when (RelativeDirectionPersistenceDto.valueOf(string(value.getValue("sourceDirection")))) {
            RelativeDirectionPersistenceDto.LEFT -> RelativeDirection.Left
            RelativeDirectionPersistenceDto.ACROSS -> RelativeDirection.Across
            RelativeDirectionPersistenceDto.RIGHT -> RelativeDirection.Right
            RelativeDirectionPersistenceDto.SELF -> RelativeDirection.Self
        }
        return HistoryReplayMeld(type, tiles, source, direction)
    }

    /**
     * 只經註冊 codec 公開規則資料，未註冊資料不可取得。
     * @param value 可為 null 的 typed envelope。
     * @param context 解碼預算與索引。
     * @return 可用公開資料或 null。
     */
    private fun optionalRule(value: JsonElement?, context: HistoryReplayProjectionContext): HistoryReplayRuleInformation? {
        if (value == null || value == JsonNull) return null
        val typed = obj(value)
        val type = string(typed.getValue(ReplaySourceKeys.TYPE_KEY))
        return registry.decodeOptionalRule(type, typed.getValue(ReplayFormatKeys.PAYLOAD), context) ?: HistoryReplayRuleInformation(type, null)
    }

    /**
     * 讀取保存的結算摘要，不重新計分。
     * @param value 完成摘要。
     * @param context 座位驗證上下文。
     * @return 座位索引形式結果。
     */
    private fun outcome(value: JsonObject, context: HistoryReplayProjectionContext): HistoryRoundOutcome = HistoryRoundOutcome(
        string(value.getValue("outcomeId")),
        array(value.getValue("beneficiaryPlayerIds")).map { context.seat(integer(it)) },
        scores(value.getValue("settledScoresByPlayerId"), context),
        RoundCompletionClassification.valueOf(string(value.getValue("classification"))),
        array(value.getValue("responsiblePlayerIds")).map { context.seat(integer(it)) },
        RoundTransitionDirective.valueOf(string(value.getValue("transitionDirective"))),
    )

    /**
     * 將索引字串鍵分數表轉為座位分數。
     * @param value 結算分數表。
     * @param context 座位驗證上下文。
     * @return 不含 UUID 猜測的分數表。
     */
    private fun scores(value: JsonElement, context: HistoryReplayProjectionContext): Map<Int, Int> {
        val result = linkedMapOf<Int, Int>()
        obj(value).forEach { (key, score) ->
            val seat = context.seat(key.toIntOrNull() ?: invalid())
            require(key == seat.toString() && result.put(seat, integer(score)) == null) { "Replay score contains an invalid seat key" }
        }
        return result
    }

    /** 驗證有序牌參照。
     * @param value 索引陣列。
     * @param context 局內牌目錄。
     * @return 原順序牌參照。
     */
    private fun refs(value: JsonElement, context: HistoryReplayProjectionContext): List<HistoryTileReference> = array(value).map { context.tile(integer(it)) }

    /** 讀取嚴格物件。
     * @param value 待驗證值。
     * @return 物件。
     */
    private fun obj(value: JsonElement): JsonObject = value as? JsonObject ?: invalid()

    /** 讀取嚴格陣列。
     * @param value 待驗證值。
     * @return 陣列。
     */
    private fun array(value: JsonElement): JsonArray = value as? JsonArray ?: invalid()

    /** 讀取嚴格字串。
     * @param value 待驗證值。
     * @return 字串。
     */
    private fun string(value: JsonElement): String = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()

    /** 讀取嚴格整數。
     * @param value 待驗證值。
     * @return 整數。
     */
    private fun integer(value: JsonElement): Int = CompactReplayReadDocument.integer(value)

    /** 拒絕破損投影，不以預設資料替代。 */
    private fun invalid(): Nothing = throw ReplayReadException(ReplayReadError.INVALID_DOCUMENT)
}
