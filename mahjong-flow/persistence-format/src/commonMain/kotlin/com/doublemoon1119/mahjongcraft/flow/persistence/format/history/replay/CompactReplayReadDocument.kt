package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayPlayerIdentity
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TilePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.uuid.Uuid

/**
 * 已驗證封套與索引、尚未展開局內投影的唯讀文件。
 *
 * @property dictionary 共用字典檢視。
 * @property identity 文件原本保存的對局與玩家身分。
 * @property tileTypes 跨局牌種字典。
 * @property factTypes 語意事實種類字典。
 * @property actionTypes 動作種類字典。
 * @property paths 差異操作路徑。
 * @property startedAtEpochMillis 文件開始時間。
 * @property rounds 保留字典編碼的局資料。
 */
internal data class CompactReplayReadDocument(
    val dictionary: CompactReplayDictionary.View,
    val identity: HistoryReplayIdentity,
    val tileTypes: List<Tile>,
    val factTypes: List<String>,
    val actionTypes: List<String>,
    val paths: List<List<JsonElement>>,
    val startedAtEpochMillis: Long,
    val rounds: List<EncodedReplayRound>,
) {
    /** 文件讀取的共用驗證入口。 */
    companion object {
        /**
         * 驗證身份與結構，僅還原 header 與局定位欄位。
         * @param document 已解析的原始文件。
         * @param expectedMatchId 呼叫端要求的對局。
         * @param budget 工作預算。
         * @return 未重建任何局桌況的文件檢視。
         */
        fun open(document: JsonObject, expectedMatchId: Uuid, budget: ReplayReadBudget): CompactReplayReadDocument {
            budget.inspectInput(document)
            require(document.keys == setOf(ReplayFormatKeys.FORMAT_VERSION, ReplayFormatKeys.PAYLOAD)) { "Replay envelope contains unexpected fields" }
            checkVersion(document.getValue(ReplayFormatKeys.FORMAT_VERSION))
            val dictionary = CompactReplayDictionary.open(document.getValue(ReplayFormatKeys.PAYLOAD) as? JsonObject ?: error("Replay payload must be an object"), budget)
            val root = dictionary.fields(dictionary.data)
            require(root.keys == setOf(ReplayFormatKeys.VERSION, ReplayFormatKeys.HEADER, ReplayFormatKeys.ROUNDS)) { "Replay content contains unexpected fields" }
            checkVersion(dictionary.expand(root.getValue(ReplayFormatKeys.VERSION)))
            val header = dictionary.expand(root.getValue(ReplayFormatKeys.HEADER)) as? JsonObject ?: error("Replay header must be an object")
            require(header.keys == setOf(ReplayFormatKeys.MATCH, ReplayFormatKeys.VENUE, ReplayFormatKeys.PLAYERS, ReplayFormatKeys.RULE, ReplayFormatKeys.FLOW, ReplayFormatKeys.TIME, ReplayFormatKeys.TYPES, ReplayFormatKeys.FACT_TYPES, ReplayFormatKeys.ACTION_TYPES, ReplayFormatKeys.PATCH_PATHS)) { "Replay header contains missing or unexpected fields" }
            val match = Uuid.parse(string(header.getValue(ReplayFormatKeys.MATCH)))
            require(match == expectedMatchId) { "Replay match ID does not match the request" }
            val playersJson = header.getValue(ReplayFormatKeys.PLAYERS) as? JsonArray ?: error("Replay players must be an array")
            require(playersJson.isNotEmpty()) { "Replay requires players" }
            if (playersJson.size > budget.limits.maxPlayers) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
            val players = playersJson.mapIndexed { index, element ->
                budget.charge()
                val player = element as? JsonObject ?: error("Replay player must be an object")
                require(player.keys == setOf(ReplaySourceKeys.ID, ReplayFormatKeys.PLAYER_AI)) { "Replay player contains unexpected fields" }
                val ai = player.getValue(ReplayFormatKeys.PLAYER_AI).let { if (it == JsonNull) null else string(it) }
                HistoryReplayPlayerIdentity(index, Uuid.parse(string(player.getValue(ReplaySourceKeys.ID))), ai)
            }
            require(players.map { it.playerId }.distinct().size == players.size) { "Replay contains duplicate players" }
            val identity = HistoryReplayIdentity(match, Uuid.parse(string(header.getValue(ReplayFormatKeys.VENUE))), players.toList())
            val typesJson = header.getValue(ReplayFormatKeys.TYPES) as? JsonArray ?: error("Replay tile dictionary must be an array")
            if (typesJson.size > budget.limits.maxTiles) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
            val types = typesJson.map {
                budget.charge()
                Json.decodeFromJsonElement(TilePersistenceDto.serializer(), it).toDomain()
            }
            val pathsJson = header.getValue(ReplayFormatKeys.PATCH_PATHS) as? JsonArray ?: error("Replay patch paths must be an array")
            val paths = pathsJson.map { path ->
                budget.charge()
                val segments = path as? JsonArray ?: error("Replay patch path must be an array")
                if (segments.size > budget.limits.maxDepth) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
                require(segments.all { it is JsonPrimitive }) { "Replay patch segment must be primitive" }
                segments.toList()
            }
            val encodedRounds = root.getValue(ReplayFormatKeys.ROUNDS) as? JsonArray ?: error("Replay rounds must be an array")
            if (encodedRounds.size > budget.limits.maxRounds) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
            val rounds = encodedRounds.map { encoded ->
                val round = dictionary.fields(encoded)
                require(round.keys == setOf(ReplayFormatKeys.ROUND_NUMBER, ReplayFormatKeys.TILES, ReplayFormatKeys.INITIAL, ReplayFormatKeys.TRANSACTIONS)) { "Replay round contains unexpected fields" }
                val number = integer(dictionary.expand(round.getValue(ReplayFormatKeys.ROUND_NUMBER)))
                require(number > 0) { "Replay round number must be positive" }
                val transactions = round.getValue(ReplayFormatKeys.TRANSACTIONS) as? JsonArray ?: error("Replay transactions must be an array")
                if (transactions.size > budget.limits.maxTransactionsPerRound) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
                EncodedReplayRound(number, round.getValue(ReplayFormatKeys.TILES), round.getValue(ReplayFormatKeys.INITIAL), transactions)
            }
            require(rounds.map { it.number }.distinct().size == rounds.size) { "Replay contains duplicate round numbers" }
            val time = (header.getValue(ReplayFormatKeys.TIME) as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: error("Replay header time must be an integer")
            return CompactReplayReadDocument(dictionary, identity, types, strings(header.getValue(ReplayFormatKeys.FACT_TYPES)), strings(header.getValue(ReplayFormatKeys.ACTION_TYPES)), paths, time, rounds)
        }

        /**
         * 驗證唯一字串字典。
         * @param value 字典陣列。
         * @return 已驗證的字串。
         */
        private fun strings(value: JsonElement): List<String> {
            val values = (value as? JsonArray ?: error("Replay type dictionary must be an array")).map(::string)
            require(values.distinct().size == values.size) { "Replay type dictionary contains duplicates" }
            return values
        }

        /**
         * 驗證文件版本，不將未知內容當作目前格式。
         * @param value 版本值。
         */
        private fun checkVersion(value: JsonElement) {
            if (integer(value) != CompactReplayCodec.FORMAT_VERSION) throw ReplayReadException(ReplayReadError.UNSUPPORTED_CONTENT)
        }

        /**
         * 讀取嚴格字串。
         * @param value 待讀取節點。
         * @return 字串內容。
         */
        private fun string(value: JsonElement): String = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("Replay value must be a string")

        /**
         * 讀取嚴格整數。
         * @param value 待讀取節點。
         * @return 整數內容。
         */
        internal fun integer(value: JsonElement): Int = (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: error("Replay value must be an integer")
    }
}

/**
 * 尚未還原字典的單局資料。
 * @property number 保存局序號。
 * @property tileTypes 初始牌種對照的編碼值。
 * @property initial 初始桌況的編碼值。
 * @property transactions 有序且未展開的交易。
 */
internal data class EncodedReplayRound(
    val number: Int,
    val tileTypes: JsonElement,
    val initial: JsonElement,
    val transactions: JsonArray,
)
