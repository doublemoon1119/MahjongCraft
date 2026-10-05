package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryDetailValueTypeKeys
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.MeldTypeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RelativeDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 單局事件頁的 C2S 請求。
 * @property requestId 配對非同步回覆的請求識別碼。
 * @property matchId 對局識別碼。
 * @property scope 查詢範圍。
 * @property roundNumber 局序號。
 * @property startTransactionIndex 事件頁起始交易索引。
 * @property limit 要求的交易數量。
 */
@Serializable
data class HistoryRoundEventsRequestDto(
    val requestId: String,
    val matchId: String,
    val scope: HistoryQueryScopeDto = HistoryQueryScopeDto.OWN,
    val roundNumber: Int,
    val startTransactionIndex: Int = 0,
    val limit: Int = 20,
)

/** 單局桌況的 C2S 請求。
 * @property requestId 配對非同步回覆的請求識別碼。
 * @property matchId 對局識別碼。
 * @property scope 查詢範圍。
 * @property roundNumber 局序號。
 * @property position 局內位置；開局或指定交易後。
 */
@Serializable
data class HistoryRoundStateRequestDto(
    val requestId: String,
    val matchId: String,
    val scope: HistoryQueryScopeDto = HistoryQueryScopeDto.OWN,
    val roundNumber: Int,
    val position: HistoryRoundPositionDto = HistoryRoundPositionDto.Initial,
)

/** 網路傳輸的局內位置。 */
@Serializable
sealed interface HistoryRoundPositionDto {
    /** 開局位置。 */
    @SerialName("initial")
    @Serializable
    data object Initial : HistoryRoundPositionDto

    /** 指定交易後的位置。
     * @property index 交易索引。
     */
    @SerialName("after_transaction")
    @Serializable
    data class AfterTransaction(val index: Int) : HistoryRoundPositionDto
}

/** 單局事件頁的 S2C 回覆。
 * @property requestId 對應請求的識別碼。
 * @property matchId 回應所屬對局識別碼。
 * @property roundNumber 回應所屬局序號。
 * @property startTransactionIndex 回應起始交易索引。
 * @property events 事件頁；內容已通過查詢上限。
 * @property errorCode 安全的查詢錯誤代碼。
 */
@Serializable
data class HistoryRoundEventsResponseDto(
    val requestId: String,
    val matchId: String,
    val roundNumber: Int,
    val startTransactionIndex: Int,
    val events: HistoryRoundEventsDto? = null,
    val errorCode: HistoryQueryErrorCodeDto? = null,
)

/** 單局桌況的 S2C 回覆。
 * @property requestId 對應請求的識別碼。
 * @property matchId 回應所屬對局識別碼。
 * @property roundNumber 回應所屬局序號。
 * @property position 回應所屬局內位置。
 * @property state 完整桌況；不可提供時為 null。
 * @property errorCode 安全的查詢錯誤代碼。
 */
@Serializable
data class HistoryRoundStateResponseDto(
    val requestId: String,
    val matchId: String,
    val roundNumber: Int,
    val position: HistoryRoundPositionDto,
    val state: HistoryRoundStateDto? = null,
    val errorCode: HistoryQueryErrorCodeDto? = null,
)

/** 單局事件資料 DTO。
 * @property identity 對局與玩家識別資料。
 * @property roundNumber 局序號。
 * @property transactions 有界交易序列。
 * @property nextTransactionIndex 下一頁起點。
 * @property tileCatalog 回覆末端已宣告的牌目錄。
 */
@Serializable
data class HistoryRoundEventsDto(
    val identity: HistoryReplayIdentityDto,
    val roundNumber: Int,
    val transactions: List<HistoryReplayTransactionDto>,
    val nextTransactionIndex: Int?,
    val tileCatalog: List<TileDto>,
)

/** Replay 對局識別資料 DTO。
 * @property matchId 對局識別碼。
 * @property tableId 牌桌識別碼。
 * @property players 依初始座位排序的玩家識別資料。
 */
@Serializable
data class HistoryReplayIdentityDto(
    val matchId: String,
    val tableId: String,
    val players: List<HistoryReplayPlayerIdentityDto>,
)

/** Replay 玩家識別資料 DTO。
 * @property initialSeatIndex 初始座位索引。
 * @property playerId 玩家識別碼。
 * @property aiStrategyId AI 策略識別碼。
 */
@Serializable
data class HistoryReplayPlayerIdentityDto(
    val initialSeatIndex: Int,
    val playerId: String?,
    val aiStrategyId: String?,
)

/** Replay 交易 DTO。
 * @property index 交易索引。
 * @property occurredAtEpochMillis 發生時間。
 * @property isOpening 是否為開局交易。
 * @property declaredTileCountAfter 交易後已宣告牌數量。
 * @property facts 有序語意事實。
 */
@Serializable
data class HistoryReplayTransactionDto(
    val index: Int,
    val occurredAtEpochMillis: Long,
    val isOpening: Boolean,
    val declaredTileCountAfter: Int,
    val facts: List<HistoryReplayFactDto>,
)

/** Replay 語意事實 DTO；各子類別只傳輸公開欄位。
 * @property typeKey 穩定事實種類識別碼。
 */
@Serializable
sealed interface HistoryReplayFactDto {
    val typeKey: String

    /** 已知動作的安全事實。
     * @property typeKey 事實種類識別碼。
     * @property actorSeat 發起者座位。
     * @property actionType 動作種類。
     * @property directTiles 直接涉及的牌索引。
     * @property revealedTiles 新公開的牌索引。
     * @property extensionTypeId 擴充動作種類。
     */
    @SerialName("known_action")
    @Serializable
    data class KnownAction(
        override val typeKey: String,
        val actorSeat: Int?,
        val actionType: String,
        val directTiles: List<Int>,
        val revealedTiles: List<Int>,
        val extensionTypeId: String?,
    ) : HistoryReplayFactDto

    /** 已裁定回應的安全事實。
     * @property typeKey 事實種類識別碼。
     * @property actionType 動作種類。
     * @property resolvedActorSeat 裁定玩家座位。
     */
    @SerialName("reaction")
    @Serializable
    data class Reaction(
        override val typeKey: String,
        val actionType: String?,
        val resolvedActorSeat: Int?,
    ) : HistoryReplayFactDto

    /** 局準備步驟的安全事實。
     * @property typeKey 事實種類識別碼。
     * @property stepId 步驟識別碼。
     * @property stepIndex 步驟索引。
     * @property nextStepId 下一步識別碼。
     */
    @SerialName("preparation")
    @Serializable
    data class Preparation(
        override val typeKey: String,
        val stepId: String,
        val stepIndex: Int,
        val nextStepId: String?,
    ) : HistoryReplayFactDto

    /** 局完成結果的安全事實。
     * @property typeKey 事實種類識別碼。
     * @property outcome 局結算結果。
     */
    @SerialName("completion")
    @Serializable
    data class Completion(
        override val typeKey: String,
        val outcome: HistoryRoundOutcomeDto?,
    ) : HistoryReplayFactDto

    /** 規則效果的安全事實。
     * @property typeKey 事實種類識別碼。
     * @property reasonId 規則效果識別碼。
     * @property outcome 局結算結果。
     */
    @SerialName("rule_effect")
    @Serializable
    data class RuleEffect(
        override val typeKey: String,
        val reasonId: String,
        val outcome: HistoryRoundOutcomeDto?,
    ) : HistoryReplayFactDto

    /** 未解讀事實的識別及安全牌參照。
     * @property typeKey 事實種類識別碼。
     * @property actorSeat 已知行為者座位。
     * @property directTiles 已知直接涉及的牌索引。
     * @property revealedTiles 已知新公開的牌索引。
     */
    @SerialName("opaque")
    @Serializable
    data class Opaque(
        override val typeKey: String,
        val actorSeat: Int?,
        val directTiles: List<Int>,
        val revealedTiles: List<Int>,
    ) : HistoryReplayFactDto
}

/** Replay 局結算結果 DTO。
 * @property reasonId 結算原因識別碼。
 * @property beneficiarySeats 受益玩家座位。
 * @property scoresBySeat 依座位索引排列的分數。
 * @property classification 局結算分類。
 * @property responsibleSeats 責任玩家座位。
 * @property transitionDirective 莊家推進決策。
 * @property scoreChangesBySeat 該次結算相對於交易前的分數變化。
 * @property winnerDetails 各贏家的規則專屬詳情。
 * @property hasEarlierWinSettlement 是否已有較早的胡牌結算事實。
 */
@Serializable
data class HistoryRoundOutcomeDto(
    val reasonId: String,
    val beneficiarySeats: List<Int>,
    val scoresBySeat: Map<Int, Int>,
    val classification: String?,
    val responsibleSeats: List<Int>,
    val transitionDirective: String?,
    val scoreChangesBySeat: Map<Int, Int> = emptyMap(),
    val winnerDetails: List<HistoryWinnerDetailsDto> = emptyList(),
    val hasEarlierWinSettlement: Boolean = false,
)

/** 歷史贏家詳情 DTO。
 * @property seatIndex 贏家座位。
 * @property detailFields 規則專屬詳情欄位。
 * @property hand 保存結算順序的立牌與獨立和牌張；未記錄時為 null。
 */
@Serializable
data class HistoryWinnerDetailsDto(
    val seatIndex: Int,
    val detailFields: List<HistoryWinDetailFieldDto>,
    val hand: HistoryReplayWinningHandDto? = null,
)

/** 歷史胡牌手牌 DTO。
 * @property standingTiles 不含和牌張的有序立牌索引。
 * @property winningTile 獨立和牌索引；特殊結算時為 null。
 */
@Serializable
data class HistoryReplayWinningHandDto(
    val standingTiles: List<Int>,
    val winningTile: Int? = null,
)

/** 歷史胡牌詳情欄位 DTO。
 * @property id 規則專屬欄位識別碼。
 * @property value 欄位值。
 */
@Serializable
data class HistoryWinDetailFieldDto(val id: String, val value: HistoryWinDetailValueDto)

/** 歷史胡牌詳情值 DTO；只傳輸規則算出的語意資料，顯示文字由接收端依 ID 決定。 */
@Serializable
sealed interface HistoryWinDetailValueDto {
    /** 有單位的數值。
     * @property quantities 依規則順序排列的數值。
     */
    @Serializable
    @SerialName(HistoryDetailValueTypeKeys.QUANTITIES)
    data class Quantities(val quantities: List<HistoryWinDetailQuantityDto>) : HistoryWinDetailValueDto

    /** 依規則順序排列的條目。
     * @property entries 條目列表。
     */
    @Serializable
    @SerialName(HistoryDetailValueTypeKeys.ENTRIES)
    data class Entries(val entries: List<EntryDto>) : HistoryWinDetailValueDto {
        /** 單一條目。
         * @property id 規則定義的條目 ID。
         * @property quantity 條目附帶的數值；沒有數值時為 null。
         */
        @Serializable
        data class EntryDto(
            val id: String,
            val quantity: HistoryWinDetailQuantityDto? = null,
        )
    }

    /** 局內牌參照集合。
     * @property tiles 牌索引列表。
     */
    @Serializable
    @SerialName(HistoryDetailValueTypeKeys.TILES)
    data class Tiles(val tiles: List<Int>) : HistoryWinDetailValueDto
}

/** 歷史胡牌詳情的有單位數值 DTO。
 * @property unitId 規則定義的單位 ID。
 * @property amount 數值。
 */
@Serializable
data class HistoryWinDetailQuantityDto(val unitId: String, val amount: Int)

/** Replay 桌況 DTO。
 * @property identity 對局與玩家識別資料。
 * @property roundNumber 局序號。
 * @property position 局內位置。
 * @property tileCatalog 牌目錄。
 * @property players 玩家桌況。
 * @property wallTiles 活牌牆牌索引。
 * @property reservedTiles 保留牌索引。
 * @property currentPlayerSeat 行動玩家座位。
 * @property dealerSeat 莊家座位。
 * @property prevalentWind 場風。
 * @property roundPosition 保存的局位。
 * @property comboCount 連莊次數。
 * @property finishedPlayerSeats 已完成玩家座位。
 * @property dynamicRuleState 規則公開資訊。
 * @property hasPendingReaction 是否有待處理反應。
 * @property hasPendingKanReaction 是否有待處理槓牌反應。
 * @property outcome 結算結果。
 */
@Serializable
data class HistoryRoundStateDto(
    val identity: HistoryReplayIdentityDto,
    val roundNumber: Int,
    val position: HistoryRoundPositionDto,
    val tileCatalog: List<TileDto>,
    val players: List<HistoryReplayPlayerStateDto>,
    val wallTiles: List<Int>,
    val reservedTiles: List<Int>,
    val currentPlayerSeat: Int,
    val dealerSeat: Int,
    val prevalentWind: WindDto,
    val roundPosition: MatchRoundPositionDto,
    val comboCount: Int,
    val finishedPlayerSeats: Set<Int>,
    val dynamicRuleState: HistoryReplayRuleInformationDto?,
    val hasPendingReaction: Boolean,
    val hasPendingKanReaction: Boolean,
    val outcome: HistoryRoundOutcomeDto?,
)

/** Replay 玩家桌況 DTO。
 * @property initialSeatIndex 初始座位索引。
 * @property handTiles 手牌牌索引。
 * @property melds 副露。
 * @property lastDrawn 最近摸牌索引。
 * @property discards 捨牌。
 * @property score 分數。
 * @property seatWind 座風。
 * @property playerRuleState 規則公開資訊。
 */
@Serializable
data class HistoryReplayPlayerStateDto(
    val initialSeatIndex: Int,
    val handTiles: List<Int>,
    val melds: List<HistoryReplayMeldDto>,
    val lastDrawn: Int?,
    val discards: List<HistoryReplayDiscardDto>,
    val score: Int,
    val seatWind: WindDto,
    val playerRuleState: HistoryReplayRuleInformationDto?,
)

/** Replay 副露 DTO。
 * @property type 副露種類。
 * @property tiles 副露牌索引。
 * @property sourceTile 來源牌索引。
 * @property sourceDirection 來源方向。
 */
@Serializable
data class HistoryReplayMeldDto(
    val type: MeldTypeDto,
    val tiles: List<Int>,
    val sourceTile: Int?,
    val sourceDirection: RelativeDirectionDto?,
)

/** Replay 捨牌 DTO。
 * @property tile 捨牌索引。
 * @property isTaken 是否已被副露取走。
 * @property markers 規則提供的公開標記。
 */
@Serializable
data class HistoryReplayDiscardDto(val tile: Int, val isTaken: Boolean, val markers: Set<String>)

/** Replay 規則公開資訊 DTO。
 * @property typeKey 穩定資訊種類識別碼。
 * @property summary 公開摘要；不可公開時為 null。
 */
@Serializable
data class HistoryReplayRuleInformationDto(val typeKey: String, val summary: String?)

/** 單場歷史摘要查詢的 S2C 回覆。
 *
 * @property requestId 對應請求的識別碼。
 * @property detail 對局摘要及局級索引；不可提供時為 null。
 * @property errorCode 安全的查詢錯誤代碼。
 */
@Serializable
data class HistorySummaryResponseDto(
    val requestId: String,
    val detail: HistoryMatchDetailDto? = null,
    val errorCode: HistoryQueryErrorCodeDto? = null,
)

/** 單場歷史規則設定查詢的 S2C 回覆。
 *
 * @property requestId 對應請求的識別碼。
 * @property config 對局開局時採用的完整遊戲設定；不可提供時為 null。
 * @property errorCode 安全的查詢錯誤代碼。
 */
@Serializable
data class HistoryRuleSettingsResponseDto(
    val requestId: String,
    val config: GameConfigDto? = null,
    val errorCode: HistoryQueryErrorCodeDto? = null,
)
