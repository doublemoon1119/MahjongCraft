package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFactTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryActionTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryDetailValueTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayDiscard
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayMeld
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayPlayerState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayWinningHand
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundTileCatalog
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryTileReference
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryTransitionDirective
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryWinDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryWinDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryWinnerDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.persistenceJson
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.MatchRoundPositionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.MeldTypePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RelativeDirectionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.MeldTypeId
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** 將已驗證的索引投影轉為歷史讀模型，不還原權威規則命令。 */
internal object HistoryReplayProjectionMapper {
    /**
     * 映射選取位置的唯一桌況。
     *
     * @param projection 已還原桌況。
     * @param identity header 玩家身分。
     * @param roundNumber 這一局在整場對局中的順序號；連莊時與 [HistoryRoundState.roundPosition] 的賽程局數不同。
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
        val dealer = context.seat(integer(root.getValue(ReplaySourceKeys.DEALER_PLAYER_ID)))
        val current = integer(root.getValue(ReplaySourceKeys.CURRENT_PLAYER_INDEX))
        val roundPosition = persistenceJson.decodeFromJsonElement(MatchRoundPositionPersistenceDto.serializer(), root.getValue(ReplaySourceKeys.ROUND_POSITION)).toDomain()
        val wind = Wind.valueOf(string(root.getValue(ReplaySourceKeys.PREVALENT_WIND)))
        require(roundPosition.prevalentWind == wind) { "Replay round position is inconsistent" }
        val wall = refs(obj(root.getValue(ReplaySourceKeys.TILE_WALL)).getValue(ReplaySourceKeys.WALL_TILES), context)
        val reserved = refs(root.getValue(ReplaySourceKeys.RESERVED_WALL_TILES), context)
        val held = wall + reserved + players.flatMap { player ->
            player.handTiles + listOfNotNull(player.lastDrawn) + player.melds.flatMap { it.tiles } + player.discards.filterNot { it.isTaken }.map { it.tile }
        }
        require(held.map { it.tileIndex }.distinct().size == held.size) { "Replay tile appears in multiple holding areas" }
        return HistoryRoundState(
            identity, roundNumber, position, tileCatalog, players, wall, reserved,
            players.getOrNull(current)?.initialSeatIndex ?: invalid(), dealer, wind, roundPosition,
            integer(root.getValue(ReplaySourceKeys.COMBO_COUNT)), root[ReplaySourceKeys.FINISHED_PLAYER_IDS]?.let { array(it).map { value -> context.seat(integer(value)) }.toSet() } ?: emptySet(),
            root.getValue(ReplaySourceKeys.PENDING_REACTION) != JsonNull, root.getValue(ReplaySourceKeys.PENDING_ROBBING_REACTION) != JsonNull, null,
        )
    }

    /**
     * 依序映射事實，不公開擴充的私有 payload。
     *
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
                HistoryFactTypeKeys.ACTION_ACCEPTED -> {
                    val (actionType, extensionType) = actionTypes(obj(fact.getValue(ReplaySourceKeys.ACTION)), context)
                    HistoryReplayFact.KnownAction(type, actor, actionType, direct, revealed, extensionType)
                }
                HistoryFactTypeKeys.ACTION_SCORED -> {
                    val (actionType, extensionType) = actionTypes(obj(fact.getValue(ReplaySourceKeys.ACTION)), context)
                    HistoryReplayFact.ActionScored(
                        typeKey = type,
                        actorSeat = actor,
                        actionType = actionType,
                        extensionTypeId = extensionType,
                        scoreDeltasBySeat = scores(fact.getValue(ReplaySourceKeys.SCORE_DELTAS_BY_PLAYER_ID), context),
                        potDeposit = integer(fact.getValue(ReplaySourceKeys.POT_DEPOSIT)),
                    )
                }
                HistoryFactTypeKeys.REACTION_RESOLVED -> {
                    val action = fact.getValue(ReplaySourceKeys.RESOLVED_ACTION).takeUnless { it == JsonNull }?.let(::obj)
                    val resolved = fact.getValue(ReplaySourceKeys.ACTOR_PLAYER_ID).takeUnless { it == JsonNull }?.let { context.seat(integer(it)) }
                    HistoryReplayFact.Reaction(type, action?.getValue(ReplaySourceKeys.TYPE)?.let(::string), resolved)
                }
                HistoryFactTypeKeys.ROUND_PREPARATION_STARTED, HistoryFactTypeKeys.ROUND_PREPARATION_SUBMITTED, HistoryFactTypeKeys.ROUND_PREPARATION_AUTOMATIC_RESOLVED ->
                    HistoryReplayFact.Preparation(type, string(fact.getValue(ReplaySourceKeys.STEP_ID)), integer(fact.getValue(ReplaySourceKeys.STEP_INDEX)).also { require(it >= 0) { "Replay preparation index must not be negative" } }, fact[ReplaySourceKeys.NEXT_STEP_ID]?.takeUnless { it == JsonNull }?.let(::string))
                HistoryFactTypeKeys.DICE_ROLLED -> {
                    val roll = obj(fact.getValue(ReplaySourceKeys.ROLL))
                    val values = array(roll.getValue(ReplaySourceKeys.DICE)).map(::integer)
                    require(values.isNotEmpty() && values.all { it in 1..6 }) {
                        "Replay dice roll must contain one or more dice in the range 1..6"
                    }
                    val actorSeat = actor ?: invalid()
                    val recordedActorSeat = context.seat(integer(roll.getValue(ReplaySourceKeys.PLAYER_ID)))
                    require(recordedActorSeat == actorSeat) {
                        "Replay dice roll actor does not match its recorded player"
                    }
                    HistoryReplayFact.DiceRolled(
                        type,
                        integer(roll.getValue(ReplaySourceKeys.ROLL_INDEX)).also { require(it >= 0) { "Replay dice roll index must not be negative" } },
                        actorSeat,
                        values,
                    )
                }
                HistoryFactTypeKeys.ROUND_COMPLETED -> HistoryReplayFact.Completion(type, outcome(obj(fact.getValue(ReplaySourceKeys.SUMMARY)), context))
                HistoryFactTypeKeys.WIN_SETTLED -> HistoryReplayFact.Completion(type, winSettledOutcome(fact, context))
                HistoryFactTypeKeys.RULE_EFFECT_RESOLVED -> HistoryReplayFact.RuleEffect(
                    type,
                    string(fact.getValue(ReplaySourceKeys.REASON_ID)),
                    fact.getValue(ReplaySourceKeys.ROUND_COMPLETION).takeUnless { it == JsonNull }?.let {
                        outcome(obj(it), context, winnerDetails(fact[ReplaySourceKeys.WIN_DETAILS], context))
                    },
                )
                HistoryFactTypeKeys.MATCH_COMPLETED -> HistoryReplayFact.Completion(type, HistoryRoundOutcome(string(fact.getValue(ReplaySourceKeys.REASON_ID)), emptyList(), scores(fact.getValue(ReplaySourceKeys.FINAL_SCORES_BY_PLAYER_ID), context)))
                HistoryFactTypeKeys.MATCH_ABORTED -> HistoryReplayFact.Completion(
                    type,
                    HistoryRoundOutcome(string(fact.getValue(ReplaySourceKeys.REASON_ID)), emptyList(), emptyMap()),
                )
                else -> HistoryReplayFact.Opaque(type, actor, direct, revealed)
            }
        }
    }

    /**
     * 讀取保存的動作種類，並驗證動作引用的牌都屬於本局。
     *
     * @param action 保存的動作物件。
     * @param context 索引驗證上下文。
     * @return 動作種類與擴充動作種類；內建動作的擴充種類為 null。
     */
    private fun actionTypes(action: JsonObject, context: HistoryReplayProjectionContext): Pair<String, String?> {
        val actionType = string(action.getValue(ReplaySourceKeys.TYPE))
        action[ReplaySourceKeys.TILE_ID]?.let { context.tile(integer(it)) }
        action[ReplaySourceKeys.WITH_TILE_IDS]?.let { refs(it, context) }
        val extensionType = if (actionType == HistoryActionTypeKeys.EXTENSION) {
            string(obj(action.getValue(ReplaySourceKeys.VALUE)).getValue(ReplaySourceKeys.TYPE_KEY))
        } else {
            null
        }
        return actionType to extensionType
    }

    /**
     * 映射具有明確初始座位的玩家。
     *
     * @param value 玩家投影。
     * @param context 索引驗證上下文。
     * @return 玩家歷史桌況。
     */
    private fun mapPlayer(value: JsonObject, context: HistoryReplayProjectionContext): HistoryReplayPlayerState {
        context.charge()
        val hand = obj(value.getValue(ReplaySourceKeys.HAND))
        val tiles = refs(hand.getValue(ReplaySourceKeys.HAND_TILES), context)
        val lastDrawn = hand.getValue(ReplaySourceKeys.LAST_DRAWN).takeUnless { it == JsonNull }?.let { context.tile(integer(it)) }
        require(lastDrawn == null || lastDrawn !in tiles) { "Replay last drawn tile must be separate from standing hand tiles" }
        val discards = array(value.getValue(ReplayFormatKeys.DISCARDS)).map { discard(obj(it), context) }
        val setAside = refs(value.getValue(ReplayFormatKeys.SET_ASIDE_TILES), context)
        return HistoryReplayPlayerState(
            context.seat(integer(value.getValue(ReplaySourceKeys.INITIAL_SEAT_INDEX))),
            tiles,
            array(hand.getValue(ReplaySourceKeys.MELDS)).map { meld(obj(it), context) },
            lastDrawn,
            discards,
            integer(value.getValue(ReplaySourceKeys.SCORE)),
            Wind.valueOf(string(value.getValue(ReplaySourceKeys.SEAT_WIND))),
            setAside,
        )
    }

    /**
     * 讀取不分規則的牌河項目；省略的鳴走與標記欄位分別代表未鳴走與沒有標記。
     *
     * @param value 牌河項目投影。
     * @param context 索引驗證上下文。
     * @return 回放捨牌。
     */
    private fun discard(value: JsonObject, context: HistoryReplayProjectionContext): HistoryReplayDiscard {
        context.charge()
        require(value.keys.all { it in DISCARD_KEYS }) { "Replay discard contains unexpected fields" }
        val tile = context.tile(integer(value.getValue(ReplayFormatKeys.DISCARD_TILE)))
        val taken = value[ReplayFormatKeys.DISCARD_TAKEN]?.let { if (boolean(it)) true else invalid() } ?: false
        val markers = value[ReplayFormatKeys.DISCARD_MARKERS]?.let { markers ->
            array(markers).map {
                context.charge()
                string(it)
            }.toSet()
        } ?: emptySet()
        return HistoryReplayDiscard(tile, taken, markers)
    }

    /**
     * 保留副露牌順序、來源牌、來源方向、公開牌面的牌與朝下擺放的牌。
     *
     * @param value 副露投影。
     * @param context 索引驗證上下文。
     * @return 型別化副露。
     */
    private fun meld(value: JsonObject, context: HistoryReplayProjectionContext): HistoryReplayMeld {
        context.charge()
        val type = when (val saved = persistenceJson.decodeFromJsonElement(MeldTypePersistenceDto.serializer(), value.getValue(ReplaySourceKeys.TYPE))) {
            MeldTypePersistenceDto.Chi -> MeldType.CHI
            MeldTypePersistenceDto.Pon -> MeldType.PON
            MeldTypePersistenceDto.OpenKan -> MeldType.OPEN_KAN
            MeldTypePersistenceDto.ClosedKan -> MeldType.CLOSED_KAN
            MeldTypePersistenceDto.AddedKan -> MeldType.ADDED_KAN
            is MeldTypePersistenceDto.Extension -> MeldType.Extension(MeldTypeId.parse(saved.typeId))
        }
        val tiles = refs(value.getValue(ReplaySourceKeys.MELD_TILES), context)
        val source = value.getValue(ReplaySourceKeys.SOURCE_TILE).takeUnless { it == JsonNull }?.let { context.tile(integer(it)) }
        val faceDown = refs(value.getValue(ReplaySourceKeys.FACE_DOWN_TILE_IDS), context).toSet()
        require(faceDown.all { it in tiles }) { "Replay meld face-down tile must belong to the meld" }
        val publiclyRevealed = refs(value.getValue(ReplaySourceKeys.PUBLICLY_REVEALED_TILE_IDS), context).toSet()
        require(publiclyRevealed.all { it in tiles }) { "Replay meld publicly revealed tile must belong to the meld" }
        require(source == null || source in tiles) { "Replay meld source must belong to the meld" }
        val direction = when (RelativeDirectionPersistenceDto.valueOf(string(value.getValue(ReplaySourceKeys.SOURCE_DIRECTION)))) {
            RelativeDirectionPersistenceDto.LEFT -> RelativeDirection.Left
            RelativeDirectionPersistenceDto.ACROSS -> RelativeDirection.Across
            RelativeDirectionPersistenceDto.RIGHT -> RelativeDirection.Right
            RelativeDirectionPersistenceDto.SELF -> RelativeDirection.Self
        }
        return HistoryReplayMeld(type, tiles, source, direction, faceDown, publiclyRevealed)
    }

    /**
     * 讀取保存的結算摘要，不重新計分。
     *
     * @param value 完成摘要。
     * @param context 座位驗證上下文。
     * @param savedWinnerDetails 同一結算已保存的逐位和牌明細；未提供時不補造內容。
     * @return 座位索引形式結果。
     */
    private fun outcome(
        value: JsonObject,
        context: HistoryReplayProjectionContext,
        savedWinnerDetails: List<HistoryWinnerDetails>? = null,
    ): HistoryRoundOutcome = HistoryRoundOutcome(
        string(value.getValue(ReplaySourceKeys.OUTCOME_ID)),
        array(value.getValue(ReplaySourceKeys.BENEFICIARY_PLAYER_IDS)).map { context.seat(integer(it)) },
        scores(value.getValue(ReplaySourceKeys.SETTLED_SCORES_BY_PLAYER_ID), context),
        RoundCompletionClassification.valueOf(string(value.getValue(ReplaySourceKeys.CLASSIFICATION))),
        array(value.getValue(ReplaySourceKeys.RESPONSIBLE_PLAYER_IDS)).map { context.seat(integer(it)) },
        transitionDirective(obj(value.getValue(ReplaySourceKeys.TRANSITION_DIRECTIVE)), context),
        winnerDetails = savedWinnerDetails ?: emptyList(),
    )

    /**
     * 讀取保存的下一任莊家；指定的玩家已轉成牌譜座位索引。
     *
     * @param value 保存的下一任莊家。
     * @param context 座位驗證上下文。
     * @return 牌譜形式的下一任莊家。
     */
    private fun transitionDirective(value: JsonObject, context: HistoryReplayProjectionContext): HistoryTransitionDirective {
        val type = string(value.getValue(ReplaySourceKeys.TYPE))
        val expectedKeys = if (type == HistoryTransitionDirective.PASS_TO_TYPE_ID) {
            setOf(ReplaySourceKeys.TYPE, ReplaySourceKeys.NEXT_DEALER_PLAYER_ID)
        } else {
            setOf(ReplaySourceKeys.TYPE)
        }
        require(value.keys == expectedKeys) { "Replay transition directive contains missing or unexpected fields" }
        return when (type) {
            HistoryTransitionDirective.REPEAT_DEALER_TYPE_ID -> HistoryTransitionDirective.RepeatDealer
            HistoryTransitionDirective.PASS_TO_NEXT_SEAT_TYPE_ID -> HistoryTransitionDirective.PassToNextSeat
            HistoryTransitionDirective.PASS_TO_TYPE_ID -> HistoryTransitionDirective.PassTo(context.seat(integer(value.getValue(ReplaySourceKeys.NEXT_DEALER_PLAYER_ID))))
            else -> invalid()
        }
    }

    /**
     * 將保存的和牌欄位轉成局內座位與牌參照；缺少欄位時回傳空清單。
     *
     * @param value 已保存的逐位明細陣列。
     * @param context 座位與牌參照驗證上下文。
     * @return 保留原順序的和牌明細。
     */
    private fun winnerDetails(value: JsonElement?, context: HistoryReplayProjectionContext): List<HistoryWinnerDetails> {
        if (value == null || value == JsonNull) return emptyList()
        return array(value).map { raw ->
            val item = obj(raw)
            HistoryWinnerDetails(
                seatIndex = context.seat(integer(item.getValue(ReplaySourceKeys.PLAYER_ID))),
                detailFields = array(item.getValue(ReplaySourceKeys.DETAIL_FIELDS)).map { fieldValue ->
                    val field = obj(fieldValue)
                    HistoryWinDetailField(
                        id = string(field.getValue(ReplaySourceKeys.ID)),
                        value = detailValue(obj(field.getValue(ReplaySourceKeys.VALUE)), context),
                    )
                },
                hand = item[ReplaySourceKeys.WINNING_HAND]?.takeUnless { it == JsonNull }?.let { handValue ->
                    val hand = obj(handValue)
                    val standing = refs(hand.getValue(ReplaySourceKeys.WINNING_STANDING_TILES), context)
                    val winning = hand.getValue(ReplaySourceKeys.WINNING_TILE).takeUnless { it == JsonNull }?.let { context.tile(integer(it)) }
                    require(standing.map { it.tileIndex }.distinct().size == standing.size) { "Replay winning hand contains duplicate standing tiles" }
                    require(winning == null || winning !in standing) { "Replay winning tile must be separate from standing tiles" }
                    HistoryReplayWinningHand(standing, winning)
                },
            )
        }
    }

    /**
     * 將和牌交易事實投影成沒有重複分數表的結算結果。
     *
     * @param value 已保存的和牌交易事實。
     * @param context 座位與牌參照驗證上下文。
     * @return 分數留待交易投影補齊的和牌結果。
     */
    private fun winSettledOutcome(value: JsonObject, context: HistoryReplayProjectionContext): HistoryRoundOutcome {
        val details = winnerDetails(value[ReplaySourceKeys.WIN_DETAILS], context)
        return HistoryRoundOutcome(
            reasonId = string(value.getValue(ReplaySourceKeys.OUTCOME_ID)),
            beneficiarySeats = details.map { it.seatIndex },
            scoresBySeat = emptyMap(),
            classification = RoundCompletionClassification.WIN,
            responsibleSeats = array(value[ReplaySourceKeys.RESPONSIBLE_PLAYER_IDS] ?: JsonArray(emptyList())).map { context.seat(integer(it)) },
            winnerDetails = details,
        )
    }

    /**
     * 將持久化和牌值轉成不含 UUID 的歷史明細值。
     *
     * @param value 已保存的明細值。
     * @param context 座位與牌參照驗證上下文。
     * @return 有單位數值、牌參照或有序條目。
     */
    private fun detailValue(value: JsonObject, context: HistoryReplayProjectionContext): HistoryWinDetailValue = when (string(value.getValue(ReplaySourceKeys.TYPE))) {
        HistoryDetailValueTypeKeys.QUANTITIES -> HistoryWinDetailValue.Quantities(array(value.getValue(ReplaySourceKeys.QUANTITIES)).map { quantity(obj(it)) })
        HistoryDetailValueTypeKeys.TILES -> HistoryWinDetailValue.Tiles(refs(value.getValue(ReplaySourceKeys.TILE_IDS), context))
        HistoryDetailValueTypeKeys.ENTRIES -> HistoryWinDetailValue.Entries(
            array(value.getValue(ReplaySourceKeys.ENTRIES)).map { raw ->
                val entry = obj(raw)
                WinSettlementDetailEntry(
                    id = string(entry.getValue(ReplaySourceKeys.ID)),
                    quantity = entry[ReplaySourceKeys.QUANTITY]?.takeUnless { it == JsonNull }?.let { quantity(obj(it)) },
                )
            },
        )
        else -> invalid()
    }

    /**
     * 讀取有單位數值。
     *
     * @param value 已保存的有單位數值。
     * @return 單位與數值。
     */
    private fun quantity(value: JsonObject): WinSettlementQuantity = WinSettlementQuantity(
        unitId = string(value.getValue(ReplaySourceKeys.UNIT_ID)),
        amount = integer(value.getValue(ReplaySourceKeys.AMOUNT)),
    )

    /**
     * 將索引字串鍵分數表轉為座位分數。
     *
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

    /**
     * 驗證有序牌參照。
     *
     * @param value 索引陣列。
     * @param context 局內牌目錄。
     * @return 原順序牌參照。
     */
    private fun refs(value: JsonElement, context: HistoryReplayProjectionContext): List<HistoryTileReference> = array(value).map { context.tile(integer(it)) }

    /**
     * 讀取嚴格物件。
     *
     * @param value 待驗證值。
     * @return 物件。
     */
    private fun obj(value: JsonElement): JsonObject = value as? JsonObject ?: invalid()

    /**
     * 讀取嚴格陣列。
     *
     * @param value 待驗證值。
     * @return 陣列。
     */
    private fun array(value: JsonElement): JsonArray = value as? JsonArray ?: invalid()

    /**
     * 讀取嚴格字串。
     *
     * @param value 待驗證值。
     * @return 字串。
     */
    private fun string(value: JsonElement): String = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()

    /**
     * 讀取嚴格布林值。
     *
     * @param value 待驗證值。
     * @return 布林值。
     */
    private fun boolean(value: JsonElement): Boolean = (value as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: invalid()

    /**
     * 讀取嚴格整數。
     *
     * @param value 待驗證值。
     * @return 整數。
     */
    private fun integer(value: JsonElement): Int = CompactReplayReadDocument.integer(value)

    /** 拒絕破損投影，不以預設資料替代。 */
    private fun invalid(): Nothing = throw ReplayReadException(ReplayReadError.INVALID_DOCUMENT)

    /** 牌河項目允許的欄位。 */
    private val DISCARD_KEYS = setOf(ReplayFormatKeys.DISCARD_TILE, ReplayFormatKeys.DISCARD_TAKEN, ReplayFormatKeys.DISCARD_MARKERS)
}
