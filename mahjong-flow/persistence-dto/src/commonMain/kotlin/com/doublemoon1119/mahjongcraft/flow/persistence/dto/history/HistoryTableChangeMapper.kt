package com.doublemoon1119.mahjongcraft.flow.persistence.dto.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryChangedValue
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryPlayerChange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableChange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWallChange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWallLayoutChange
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.TileWallPhysicalLayoutPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * 將桌況交易結果與可持久化的差異 DTO 相互轉換。
 *
 * @property registries 解碼玩家與規則專屬資料所需的已凍結 registry。
 * @property json 擴充規則 payload 使用的 JSON 設定。
 */
internal class HistoryTableChangeMapper(
    private val registries: PersistenceRegistries,
    private val json: Json,
) {
    /** 將規則中立差異或完整檢查點轉為持久化 DTO。 */
    fun encode(result: HistoryTableResult): HistoryTableResultPersistenceDto = when (result) {
        is HistoryTableResult.Change -> HistoryTableResultPersistenceDto.Change(encodeChange(result.change))
        is HistoryTableResult.Checkpoint -> HistoryTableResultPersistenceDto.Checkpoint(
            result.reasonId,
            result.tableState.toPersistenceDto(
                registries.ruleConfigs,
                registries.discardPiles,
                registries.playerRuleStates,
                registries.dynamicRuleStates,
                registries.exhaustiveDrawReasons,
                registries.extensionGameActions,
                json,
            ),
        )
    }

    /** 從持久化 DTO 還原規則中立差異或完整檢查點。 */
    fun decode(dto: HistoryTableResultPersistenceDto): HistoryTableResult = when (dto) {
        is HistoryTableResultPersistenceDto.Change -> HistoryTableResult.Change(decodeChange(dto.change))
        is HistoryTableResultPersistenceDto.Checkpoint -> HistoryTableResult.Checkpoint(
            dto.reasonId,
            dto.state.toDomain(
                registries.ruleConfigs,
                registries.discardPiles,
                registries.playerRuleStates,
                registries.dynamicRuleStates,
                registries.exhaustiveDrawReasons,
                registries.extensionGameActions,
                json,
            ),
        )
    }

    /** 只編碼本次交易實際改變的欄位。 */
    private fun encodeChange(change: HistoryTableChange): HistoryTableChangePersistenceDto = HistoryTableChangePersistenceDto(
        changedPlayers = change.changedPlayers.map { playerChange ->
            HistoryPlayerChangePersistenceDto(
                playerWithoutHistory = playerChange.playerWithoutHistory.toPersistenceDto(
                    registries.discardPiles,
                    registries.playerRuleStates,
                    registries.exhaustiveDrawReasons,
                    registries.extensionGameActions,
                    json,
                ),
                retainedActionCount = playerChange.retainedActionCount,
                appendedActions = playerChange.appendedActions.map {
                    it.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions, json)
                },
            )
        },
        wall = change.wall?.let { HistoryWallChangePersistenceDto(it.removedFront, it.removedBack) },
        currentPlayerIndex = change.currentPlayerIndex,
        dynamicRuleState = change.dynamicRuleState?.let { changeValue ->
            HistoryChangedValuePersistenceDto(changeValue.value?.let { registries.dynamicRuleStates.encode(it, json) })
        },
        pendingReaction = change.pendingReaction?.let { changeValue ->
            HistoryChangedValuePersistenceDto(
                changeValue.value?.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions, json),
            )
        },
        pendingKanReaction = change.pendingKanReaction?.let { changeValue ->
            HistoryChangedValuePersistenceDto(
                changeValue.value?.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions, json),
            )
        },
        reservedWallTiles = change.reservedWallTiles?.map { it.toPersistenceDto() },
        physicalWallLayout = change.physicalWallLayout?.let { layout ->
            HistoryWallLayoutChangePersistenceDto(
                layout.removedTileIds.map(Uuid::toString).toSet(),
                TileWallPhysicalLayout(layout.updatedPlacements).toPersistenceDto().placements,
            )
        },
        finishedPlayerIds = change.finishedPlayerIds?.map(Uuid::toString)?.toSet(),
    )

    /** 將局部 DTO 還原成可套用於前一桌況的差異。 */
    private fun decodeChange(dto: HistoryTableChangePersistenceDto): HistoryTableChange = HistoryTableChange(
        changedPlayers = dto.changedPlayers.map { playerChange ->
            HistoryPlayerChange(
                playerWithoutHistory = playerChange.playerWithoutHistory.toDomain(
                    registries.discardPiles,
                    registries.playerRuleStates,
                    registries.exhaustiveDrawReasons,
                    registries.extensionGameActions,
                    json,
                ),
                retainedActionCount = playerChange.retainedActionCount,
                appendedActions = playerChange.appendedActions.map {
                    it.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions, json)
                },
            )
        },
        wall = dto.wall?.let { HistoryWallChange(it.removedFront, it.removedBack) },
        currentPlayerIndex = dto.currentPlayerIndex,
        dynamicRuleState = dto.dynamicRuleState?.let { changeValue ->
            HistoryChangedValue(changeValue.value?.let { registries.dynamicRuleStates.decode(it, json) })
        },
        pendingReaction = dto.pendingReaction?.let { changeValue ->
            HistoryChangedValue(
                changeValue.value?.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions, json),
            )
        },
        pendingKanReaction = dto.pendingKanReaction?.let { changeValue ->
            HistoryChangedValue(
                changeValue.value?.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions, json),
            )
        },
        reservedWallTiles = dto.reservedWallTiles?.map { it.toDomain() },
        physicalWallLayout = dto.physicalWallLayout?.let { layout ->
            HistoryWallLayoutChange(
                layout.removedTileIds.map(Uuid::parse).toSet(),
                TileWallPhysicalLayoutPersistenceDto(
                    layout.updatedPlacements,
                ).toDomain().placements,
            )
        },
        finishedPlayerIds = dto.finishedPlayerIds?.map(Uuid::parse)?.toSet(),
    )
}
