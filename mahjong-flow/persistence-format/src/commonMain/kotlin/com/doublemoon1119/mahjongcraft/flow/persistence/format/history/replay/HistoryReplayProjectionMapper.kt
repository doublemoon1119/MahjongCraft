package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFactTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryActionTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryDetailValueTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayDiscard
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayMeld
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayPlayerState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayTileArea
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayTileRemoval
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
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentViewPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.persistenceJson
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.MatchRoundPositionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.MeldTypePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RelativeDirectionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TileFaceViewPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TurnPhaseSerialNames
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.MeldTypeId
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import kotlinx.serialization.builtins.ListSerializer
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
     * @param componentViews 牌面特徵的保存轉換。
     * @param poses 依牌索引保存的共同姿態，決定副露哪些牌朝下。
     * @return 歷史桌況。
     */
    fun mapState(
        projection: JsonElement,
        identity: HistoryReplayIdentity,
        roundNumber: Int,
        position: HistoryRoundPosition,
        tileCatalog: HistoryRoundTileCatalog,
        budget: ReplayReadBudget,
        componentViews: ComponentViewPersistenceRegistry,
        poses: Map<Int, TilePose>,
    ): HistoryRoundState {
        val root = obj(projection)
        val context = HistoryReplayProjectionContext(budget, tileCatalog.tiles.size, identity.players.size, componentViews, poses)
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
        val held = buildList {
            addAll(wall)
            addAll(reserved)
            players.forEach { player ->
                addAll(player.handTiles)
                player.lastDrawn?.let(::add)
                player.melds.flatMapTo(this) { it.tiles }
                player.discards.filterNot { it.isTaken }.mapTo(this) { it.tile }
            }
        }
        require(held.map { it.tileIndex }.distinct().size == held.size) { "Replay tile appears in multiple holding areas" }
        // 牌河的牌只會因為副露被取走，所以每張取走的牌都是某組副露的來源牌；副露牌不重複，對應的副露只有一組。
        val meldSources = players.flatMap { player -> player.melds.mapNotNull { it.sourceTile } }.toSet()
        require(players.all { player -> player.discards.all { !it.isTaken || it.tile in meldSources } }) { "Replay taken discard must be the source of a meld" }
        val phaseType = string(obj(root.getValue(ReplaySourceKeys.TURN_PHASE)).getValue(ReplaySourceKeys.TYPE))
        return HistoryRoundState(
            identity = identity, roundNumber = roundNumber, position = position, tileCatalog = tileCatalog, players = players, wallTiles = wall, reservedTiles = reserved,
            currentPlayerSeat = players.getOrNull(current)?.initialSeatIndex ?: invalid(), dealerSeat = dealer, prevalentWind = wind, roundPosition = roundPosition,
            comboCount = integer(root.getValue(ReplaySourceKeys.COMBO_COUNT)), finishedPlayerSeats = root[ReplaySourceKeys.FINISHED_PLAYER_IDS]?.let { array(it).map { value -> context.seat(integer(value)) }.toSet() } ?: emptySet(),
            hasPendingReaction = phaseType == TurnPhaseSerialNames.AWAITING_DISCARD_REACTION, hasPendingRobbingReaction = phaseType == TurnPhaseSerialNames.AWAITING_ROBBING_REACTION, outcome = null,
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
     * @param componentViews 牌面特徵的保存轉換。
     * @return 有序歷史事實。
     */
    fun mapFacts(
        facts: List<JsonObject>,
        actorSeats: List<Int?>,
        identity: HistoryReplayIdentity,
        roundNumber: Int,
        tileCatalog: HistoryRoundTileCatalog,
        budget: ReplayReadBudget,
        componentViews: ComponentViewPersistenceRegistry,
    ): List<HistoryReplayFact> {
        require(roundNumber > 0 && facts.size == actorSeats.size) { "Replay fact context is inconsistent" }
        val context = HistoryReplayProjectionContext(budget, tileCatalog.tiles.size, identity.players.size, componentViews)
        return facts.mapIndexedNotNull { index, fact ->
            budget.charge()
            val type = string(fact.getValue(ReplaySourceKeys.TYPE))
            if (type == HistoryFactTypeKeys.TILE_PRESENTATION_CHANGED) return@mapIndexedNotNull null
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
                HistoryFactTypeKeys.RULE_DECISION_STARTED, HistoryFactTypeKeys.RULE_DECISION_SUBMITTED, HistoryFactTypeKeys.RULE_DECISION_AUTOMATIC_RESOLVED ->
                    HistoryReplayFact.RuleDecision(
                        typeKey = type,
                        timing = RuleDecisionTiming.valueOf(string(fact.getValue(ReplaySourceKeys.TIMING))),
                        stepId = string(fact.getValue(ReplaySourceKeys.STEP_ID)),
                        stepIndex = integer(fact.getValue(ReplaySourceKeys.STEP_INDEX)).also { require(it >= 0) { "Replay rule decision index must not be negative" } },
                        nextStepId = fact[ReplaySourceKeys.NEXT_STEP_ID]?.takeUnless { it == JsonNull }?.let(::string),
                        actorSeat = actor,
                        optionId = fact[ReplaySourceKeys.SUBMISSION]?.let(::obj)?.get(ReplaySourceKeys.OPTION_ID)?.let(::string),
                        passed = fact[ReplaySourceKeys.PASSED]?.let(::boolean) ?: false,
                    )
                HistoryFactTypeKeys.TILE_REPLACEMENT_ROUND_RESOLVED -> HistoryReplayFact.TileReplacementRound(
                    typeKey = type,
                    timing = RuleDecisionTiming.valueOf(string(fact.getValue(ReplaySourceKeys.TIMING))),
                    round = integer(fact.getValue(ReplaySourceKeys.REPLACEMENT_ROUND)).also { require(it >= 1) { "Replay replacement round must start at one" } },
                    removals = array(fact.getValue(ReplaySourceKeys.REMOVALS)).map { raw ->
                        val removal = obj(raw)
                        HistoryReplayTileRemoval(
                            seat = context.seat(integer(removal.getValue(ReplaySourceKeys.PLAYER_ID))),
                            tiles = refs(removal.getValue(ReplaySourceKeys.TILE_IDS), context),
                        )
                    },
                )
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
                        typeKey = type,
                        rollIndex = integer(roll.getValue(ReplaySourceKeys.ROLL_INDEX)).also { require(it >= 0) { "Replay dice roll index must not be negative" } },
                        actorSeat = actorSeat,
                        dice = values,
                    )
                }
                HistoryFactTypeKeys.ROUND_COMPLETED -> HistoryReplayFact.Completion(type, outcome(obj(fact.getValue(ReplaySourceKeys.SUMMARY)), context))
                HistoryFactTypeKeys.WIN_SETTLED -> HistoryReplayFact.Completion(type, winSettledOutcome(fact, context))
                HistoryFactTypeKeys.RULE_EFFECT_RESOLVED -> HistoryReplayFact.RuleEffect(
                    typeKey = type,
                    reasonId = string(fact.getValue(ReplaySourceKeys.REASON_ID)),
                    outcome = fact.getValue(ReplaySourceKeys.ROUND_COMPLETION).takeUnless { it == JsonNull }?.let {
                        outcome(obj(it), context, winnerDetails(fact[ReplaySourceKeys.WIN_DETAILS], context))
                    },
                )
                HistoryFactTypeKeys.MATCH_COMPLETED -> HistoryReplayFact.Completion(type, HistoryRoundOutcome(string(fact.getValue(ReplaySourceKeys.REASON_ID)), emptyList(), scores(fact.getValue(ReplaySourceKeys.FINAL_SCORES_BY_PLAYER_ID), context)))
                HistoryFactTypeKeys.MATCH_ABORTED -> HistoryReplayFact.Completion(
                    typeKey = type,
                    outcome = HistoryRoundOutcome(string(fact.getValue(ReplaySourceKeys.REASON_ID)), emptyList(), emptyMap()),
                )
                else -> HistoryReplayFact.Opaque(type, actor, direct, revealed)
            }
        }
    }

    /**
     * 讀取牌張呈現變化事實，驗證牌索引屬於本局已宣告的牌。
     *
     * @param fact 已解碼的呈現變化事實，呈現結果為字典代碼。
     * @param tileCount 目前已宣告的牌數。
     * @param budget 走訪預算。
     * @param presentation 依字典代碼取得呈現結果；代碼不存在時拋出例外。
     * @return 依牌索引排列的呈現結果。
     */
    fun tilePresentations(
        fact: JsonObject,
        tileCount: Int,
        budget: ReplayReadBudget,
        presentation: (Int) -> ReplayTilePresentation,
    ): Map<Int, ReplayTilePresentation> = obj(fact.getValue(ReplaySourceKeys.PRESENTATIONS)).entries.associate { (key, raw) ->
        budget.charge()
        budget.tile(key.toIntOrNull() ?: invalid(), tileCount) to presentation(integer(raw))
    }

    /**
     * 解碼一個牌張呈現字典項目，依項目大小記帳。
     *
     * @param value 保存的呈現字典項目。
     * @param budget 走訪預算。
     * @param componentViews 牌面特徵的保存轉換。
     * @return 呈現結果。
     */
    fun tilePresentation(
        value: JsonObject,
        budget: ReplayReadBudget,
        componentViews: ComponentViewPersistenceRegistry,
    ): ReplayTilePresentation {
        budget.inspectProjection(value)
        val views = persistenceJson.decodeFromJsonElement(ListSerializer(TypedPersistenceDto.serializer()), value.getValue(ReplaySourceKeys.VIEWS))
        return ReplayTilePresentation(
            appearanceId = value.getValue(ReplaySourceKeys.APPEARANCE_ID).takeUnless { it == JsonNull }?.let(::string),
            views = componentViews.decode(views),
            pose = TilePose.valueOf(string(value.getValue(ReplaySourceKeys.POSE))),
        )
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
        action[ReplaySourceKeys.TILE_ID]?.let { ref(it, context) }
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
        val lastDrawn = hand.getValue(ReplaySourceKeys.LAST_DRAWN).takeUnless { it == JsonNull }?.let { ref(it, context) }
        require(lastDrawn == null || lastDrawn !in tiles) { "Replay last drawn tile must be separate from standing hand tiles" }
        val discards = array(value.getValue(ReplayFormatKeys.DISCARDS)).map { discard(obj(it), context) }
        val tileAreas = array(value.getValue(ReplaySourceKeys.TILE_AREAS)).map { raw ->
            context.charge()
            val area = obj(raw)
            HistoryReplayTileArea(
                areaId = string(area.getValue(ReplaySourceKeys.ID)).also { require(NamespacedId.isValid(it)) { "Replay tile area ID must be namespaced" } },
                tiles = refs(area.getValue(ReplaySourceKeys.TILE_AREA_TILES), context),
            )
        }
        require(tileAreas.map { it.areaId }.distinct().size == tileAreas.size) { "Replay tile areas must be distinct" }
        return HistoryReplayPlayerState(
            initialSeatIndex = context.seat(integer(value.getValue(ReplaySourceKeys.INITIAL_SEAT_INDEX))),
            handTiles = tiles,
            melds = array(hand.getValue(ReplaySourceKeys.MELDS)).map { meld(obj(it), context) },
            lastDrawn = lastDrawn,
            discards = discards,
            score = integer(value.getValue(ReplaySourceKeys.SCORE)),
            seatWind = Wind.valueOf(string(value.getValue(ReplaySourceKeys.SEAT_WIND))),
            tileAreas = tileAreas,
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
        val tile = ref(value.getValue(ReplayFormatKeys.DISCARD_TILE), context)
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
            MeldTypePersistenceDto.Chi -> MeldType.Chi
            MeldTypePersistenceDto.Pon -> MeldType.Pon
            MeldTypePersistenceDto.OpenKan -> MeldType.OpenKan
            MeldTypePersistenceDto.ClosedKan -> MeldType.ClosedKan
            MeldTypePersistenceDto.AddedKan -> MeldType.AddedKan
            is MeldTypePersistenceDto.Extension -> MeldType.Extension(MeldTypeId.parse(saved.typeId))
        }
        val tiles = refs(value.getValue(ReplaySourceKeys.MELD_TILES), context)
        val source = value.getValue(ReplaySourceKeys.SOURCE_TILE).takeUnless { it == JsonNull }?.let { ref(it, context) }
        val savedFaceDown = refs(value.getValue(ReplaySourceKeys.FACE_DOWN_TILE_IDS), context).toSet()
        require(savedFaceDown.all { it in tiles }) { "Replay meld face-down tile must belong to the meld" }
        // 保存了姿態的牌依姿態決定朝向，沒有姿態的牌沿用副露保存的朝下牌。
        val faceDown = tiles.filterTo(linkedSetOf()) { tile -> context.poses[tile.tileIndex]?.let { it == TilePose.FACE_DOWN } ?: (tile in savedFaceDown) }
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
        reasonId = string(value.getValue(ReplaySourceKeys.OUTCOME_ID)),
        beneficiarySeats = array(value.getValue(ReplaySourceKeys.BENEFICIARY_PLAYER_IDS)).map { context.seat(integer(it)) },
        scoresBySeat = scores(value.getValue(ReplaySourceKeys.SETTLED_SCORES_BY_PLAYER_ID), context),
        classification = RoundCompletionClassification.valueOf(string(value.getValue(ReplaySourceKeys.CLASSIFICATION))),
        responsibleSeats = array(value.getValue(ReplaySourceKeys.RESPONSIBLE_PLAYER_IDS)).map { context.seat(integer(it)) },
        transitionDirective = transitionDirective(obj(value.getValue(ReplaySourceKeys.TRANSITION_DIRECTIVE)), context),
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
                    val winning = hand.getValue(ReplaySourceKeys.WINNING_TILE).takeUnless { it == JsonNull }?.let { ref(it, context) }
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
     * @return 有單位數值、公開牌面或有序條目。
     */
    private fun detailValue(value: JsonObject, context: HistoryReplayProjectionContext): HistoryWinDetailValue = when (string(value.getValue(ReplaySourceKeys.TYPE))) {
        HistoryDetailValueTypeKeys.QUANTITIES -> HistoryWinDetailValue.Quantities(array(value.getValue(ReplaySourceKeys.QUANTITIES)).map { quantity(obj(it)) })
        HistoryDetailValueTypeKeys.TILE_FACES -> HistoryWinDetailValue.TileFaces(
            faces = array(value.getValue(ReplaySourceKeys.TILE_FACES)).map { face ->
                context.charge()
                persistenceJson.decodeFromJsonElement(TileFaceViewPersistenceDto.serializer(), face).toDomain(context.componentViews)
            },
        )
        HistoryDetailValueTypeKeys.ENTRIES -> HistoryWinDetailValue.Entries(
            entries = array(value.getValue(ReplaySourceKeys.ENTRIES)).map { raw ->
                val entry = obj(raw)
                WinSettlementDetailEntry(
                    id = string(entry.getValue(ReplaySourceKeys.ID)),
                    quantity = entry[ReplaySourceKeys.QUANTITY]?.takeUnless { it == JsonNull }?.let { quantity(obj(it)) },
                    nameQuantity = entry[ReplaySourceKeys.NAME_QUANTITY]?.takeUnless { it == JsonNull }?.let { quantity(obj(it)) },
                    appliesToSomePayersOnly = boolean(entry.getValue(ReplaySourceKeys.APPLIES_TO_SOME_PAYERS_ONLY)),
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
    private fun refs(value: JsonElement, context: HistoryReplayProjectionContext): List<HistoryTileReference> = array(value).map { ref(it, context) }

    /**
     * 讀取牌張參照，保存在精簡文件中的權威組件不直接交付公開讀模型。
     *
     * @param value 局內索引，或帶有該次組件版本的索引封套。
     * @param context 局內牌目錄及讀取預算。
     * @return 只含已驗證局內身分的牌張參照。
     */
    private fun ref(value: JsonElement, context: HistoryReplayProjectionContext): HistoryTileReference {
        if (value !is JsonObject) return context.tile(integer(value))
        require(value.keys == TILE_VERSION_KEYS) { "Replay tile version contains unexpected fields" }
        array(value.getValue(ReplayFormatKeys.TILE_COMPONENTS))
        context.charge()
        return context.tile(integer(value.getValue(ReplayFormatKeys.TILE_REFERENCE)))
    }

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

    /** 牌張版本封套只允許身分與內部保存組件。 */
    private val TILE_VERSION_KEYS = setOf(ReplayFormatKeys.TILE_REFERENCE, ReplayFormatKeys.TILE_COMPONENTS)

    /** 牌河項目允許的欄位。 */
    private val DISCARD_KEYS = setOf(ReplayFormatKeys.DISCARD_TILE, ReplayFormatKeys.DISCARD_TAKEN, ReplayFormatKeys.DISCARD_MARKERS)
}
