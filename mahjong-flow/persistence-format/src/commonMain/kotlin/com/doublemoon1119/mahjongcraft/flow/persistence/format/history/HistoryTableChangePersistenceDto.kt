package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.GameActionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.IdentifiedTilePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.MahjongPlayerPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.PendingReactionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.PendingRobbingReactionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TableStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TileWallPlacementPersistenceDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 區分「欄位改為 null」與「欄位沒有變動」。
 *
 * @property value 欄位的新值；可空欄位的值也可以為 null。
 */
@Serializable
data class HistoryChangedValuePersistenceDto<T>(
    val value: T,
)

/** 活牌牆兩端的移除張數。
 *
 * @property removedFront 從摸牌端移除的張數。
 * @property removedBack 從另一端移除的張數。
 */
@Serializable
data class HistoryWallChangePersistenceDto(
    val removedFront: Int,
    val removedBack: Int,
)

/** 實體牌牆格位的局部變化。
 *
 * @property removedTileIds 離開牌牆的牌 UUID 字串。
 * @property updatedPlacements 位置或方向改變的牌 UUID 與新格位。
 */
@Serializable
data class HistoryWallLayoutChangePersistenceDto(
    val removedTileIds: Set<String>,
    val updatedPlacements: Map<String, TileWallPlacementPersistenceDto>,
)

/** 一筆權威交易中可重建的桌況差異；未列出的欄位保持原值。
 *
 * @property changedPlayers 實際變動的玩家狀態及其動作紀錄增量。
 * @property wall 活牌牆兩端移除數量；未改變時為 null。
 * @property currentPlayerIndex 新的行動玩家索引；未改變時為 null。
 * @property dynamicRuleState 規則動態狀態的新值；外層 null 代表未改變。
 * @property pendingReaction 捨牌反應視窗的新值；外層 null 代表未改變。
 * @property pendingRobbingReaction 搶和反應視窗的新值；外層 null 代表未改變。
 * @property reservedWallTiles 規則保留牌的新順序；未改變時為 null。
 * @property physicalWallLayout 實體牌牆格位的局部變化；未改變時為 null。
 * @property finishedPlayerIds 本局已完成玩家的 UUID 字串集合；未改變時為 null。
 * @property revealedHandTileIds 本局已公開手牌的 UUID 字串集合；未改變時為 null。
 */
@Serializable
data class HistoryTableChangePersistenceDto(
    val changedPlayers: List<HistoryPlayerChangePersistenceDto> = emptyList(),
    val wall: HistoryWallChangePersistenceDto? = null,
    val currentPlayerIndex: Int? = null,
    val dynamicRuleState: HistoryChangedValuePersistenceDto<TypedPersistenceDto?>? = null,
    val pendingReaction: HistoryChangedValuePersistenceDto<PendingReactionPersistenceDto?>? = null,
    val pendingRobbingReaction: HistoryChangedValuePersistenceDto<PendingRobbingReactionPersistenceDto?>? = null,
    val reservedWallTiles: List<IdentifiedTilePersistenceDto>? = null,
    val physicalWallLayout: HistoryWallLayoutChangePersistenceDto? = null,
    val finishedPlayerIds: Set<String>? = null,
    val revealedHandTileIds: Set<String>? = null,
)

/** 玩家狀態與動作紀錄的局部變化。
 *
 * @property playerWithoutHistory 交易後的玩家狀態；累積動作紀錄固定為空。
 * @property retainedActionCount 從交易前動作紀錄保留的前綴數量。
 * @property appendedActions 本次交易新增的動作。
 */
@Serializable
data class HistoryPlayerChangePersistenceDto(
    val playerWithoutHistory: MahjongPlayerPersistenceDto,
    val retainedActionCount: Int,
    val appendedActions: List<GameActionPersistenceDto>,
)

/** 同一筆權威交易唯一的桌況結果。 */
@Serializable
sealed interface HistoryTableResultPersistenceDto {
    /** 可從前一桌況還原的局部變化。
     *
     * @property change 本次交易的結構差異。
     */
    @Serializable
    @SerialName("change")
    data class Change(
        val change: HistoryTableChangePersistenceDto,
    ) : HistoryTableResultPersistenceDto

    /** 無法安全表示差異時保存的完整桌況。
     *
     * @property reasonId 採用完整檢查點的穩定原因 ID。
     * @property state 交易提交後的完整桌況。
     */
    @Serializable
    @SerialName("checkpoint")
    data class Checkpoint(
        val reasonId: String,
        val state: TableStatePersistenceDto,
    ) : HistoryTableResultPersistenceDto
}
