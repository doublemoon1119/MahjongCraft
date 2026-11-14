package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryPlayerChange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableChange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWallChange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWallLayoutChange
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RevealedHandSettlementPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TileWallPhysicalLayoutPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import kotlin.uuid.Uuid

/**
 * 將桌況交易結果與可持久化的差異 DTO 相互轉換。
 *
 * @property registries 解碼玩家與規則專屬資料所需的已凍結 registry。
 */
internal class HistoryTableChangeMapper(
    private val registries: PersistenceRegistries,
) {
    /**
     * 將規則中立差異或完整檢查點轉為持久化 DTO。
     *
     * @param result 本次權威交易的桌況結果。
     * @return 轉換後的結果。
     */
    fun encode(result: HistoryTableResult): HistoryTableResultPersistenceDto = when (result) {
        is HistoryTableResult.Change -> HistoryTableResultPersistenceDto.Change(encodeChange(result.change))
        is HistoryTableResult.Checkpoint -> HistoryTableResultPersistenceDto.Checkpoint(
            reasonId = result.reasonId,
            state = result.tableState.toPersistenceDto(
                ruleConfigRegistry = registries.ruleConfigs,
                discardPileRegistry = registries.discardPiles,
                exhaustiveDrawReasonRegistry = registries.exhaustiveDrawReasons,
                extensionGameActionRegistry = registries.extensionGameActions,
                componentRegistry = registries.components,
            ),
        )
    }

    /**
     * 從持久化 DTO 還原規則中立差異或完整檢查點。
     *
     * @param dto 待還原的桌況結果 DTO。
     * @return 還原的桌況差異或完整檢查點。
     */
    fun decode(dto: HistoryTableResultPersistenceDto): HistoryTableResult = when (dto) {
        is HistoryTableResultPersistenceDto.Change -> HistoryTableResult.Change(decodeChange(dto.change))
        is HistoryTableResultPersistenceDto.Checkpoint -> HistoryTableResult.Checkpoint(
            reasonId = dto.reasonId,
            tableState = dto.state.toDomain(
                ruleConfigRegistry = registries.ruleConfigs,
                discardPileRegistry = registries.discardPiles,
                exhaustiveDrawReasonRegistry = registries.exhaustiveDrawReasons,
                extensionGameActionRegistry = registries.extensionGameActions,
                componentRegistry = registries.components,
            ),
        )
    }

    /**
     * 只編碼本次交易實際改變的欄位。
     *
     * @param change 相對於前一桌況的結構差異。
     * @return 保存該差異的 DTO。
     */
    private fun encodeChange(change: HistoryTableChange): HistoryTableChangePersistenceDto = HistoryTableChangePersistenceDto(
        components = change.components?.let(registries.components::encode),
        changedPlayers = change.changedPlayers.map { playerChange ->
            HistoryPlayerChangePersistenceDto(
                playerWithoutHistory = playerChange.playerWithoutHistory.toPersistenceDto(
                    discardPileRegistry = registries.discardPiles,
                    exhaustiveDrawReasonRegistry = registries.exhaustiveDrawReasons,
                    extensionGameActionRegistry = registries.extensionGameActions,
                    componentRegistry = registries.components,
                ),
                retainedActionCount = playerChange.retainedActionCount,
                appendedActions = playerChange.appendedActions.map {
                    it.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions)
                },
            )
        },
        wall = change.wall?.let {
            HistoryWallChangePersistenceDto(
                removedFront = it.removedFront,
                removedBack = it.removedBack,
                updatedTiles = it.updatedTiles.map { tile -> tile.toPersistenceDto(registries.components) },
            )
        },
        currentPlayerIndex = change.currentPlayerIndex,
        turnPhase = change.turnPhase?.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions, registries.components),
        reservedWallTiles = change.reservedWallTiles?.map { it.toPersistenceDto(registries.components) },
        physicalWallLayout = change.physicalWallLayout?.let { layout ->
            HistoryWallLayoutChangePersistenceDto(
                removedTileIds = layout.removedTileIds.map(Uuid::toString).toSet(),
                updatedPlacements = TileWallPhysicalLayout(layout.updatedPlacements).toPersistenceDto().placements,
            )
        },
        finishedPlayerIds = change.finishedPlayerIds?.map(Uuid::toString)?.toSet(),
        publicTileEvidenceIds = change.publicTileEvidenceIds?.map(Uuid::toString)?.toSet(),
        revealedHandSettlements = change.revealedHandSettlements?.map { settlement ->
            RevealedHandSettlementPersistenceDto(
                playerId = settlement.playerId.toString(),
                waitingTiles = settlement.waitingTiles.map { it.toPersistenceDto() },
            )
        },
        openingDiceRolls = change.openingDiceRolls?.map { it.toPersistenceDto() },
        appendedDiscards = change.appendedDiscards.map(Uuid::toString),
    )

    /**
     * 將局部 DTO 還原成可套用於前一桌況的差異。
     *
     * @param dto 已保存的桌況差異。
     * @return 可套用於前一桌況的結構差異。
     */
    private fun decodeChange(dto: HistoryTableChangePersistenceDto): HistoryTableChange = HistoryTableChange(
        components = dto.components?.let(registries.components::decode),
        changedPlayers = dto.changedPlayers.map { playerChange ->
            HistoryPlayerChange(
                playerWithoutHistory = playerChange.playerWithoutHistory.toDomain(
                    discardPileRegistry = registries.discardPiles,
                    exhaustiveDrawReasonRegistry = registries.exhaustiveDrawReasons,
                    extensionGameActionRegistry = registries.extensionGameActions,
                    componentRegistry = registries.components,
                ),
                retainedActionCount = playerChange.retainedActionCount,
                appendedActions = playerChange.appendedActions.map {
                    it.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions)
                },
            )
        },
        wall = dto.wall?.let {
            HistoryWallChange(
                removedFront = it.removedFront,
                removedBack = it.removedBack,
                updatedTiles = it.updatedTiles.map { tile -> tile.toDomain(registries.components) },
            )
        },
        currentPlayerIndex = dto.currentPlayerIndex,
        turnPhase = dto.turnPhase?.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions, registries.components),
        reservedWallTiles = dto.reservedWallTiles?.map { it.toDomain(registries.components) },
        physicalWallLayout = dto.physicalWallLayout?.let { layout ->
            HistoryWallLayoutChange(
                removedTileIds = layout.removedTileIds.map(Uuid::parse).toSet(),
                updatedPlacements = TileWallPhysicalLayoutPersistenceDto(
                    placements = layout.updatedPlacements,
                ).toDomain().placements,
            )
        },
        finishedPlayerIds = dto.finishedPlayerIds?.map(Uuid::parse)?.toSet(),
        publicTileEvidenceIds = dto.publicTileEvidenceIds?.map(Uuid::parse)?.toSet(),
        revealedHandSettlements = dto.revealedHandSettlements?.map { settlement ->
            RevealedHandSettlement(
                playerId = Uuid.parse(settlement.playerId),
                waitingTiles = settlement.waitingTiles.map { it.toDomain() }.toSet(),
            )
        },
        openingDiceRolls = dto.openingDiceRolls?.map { it.toDomain() },
        appendedDiscards = dto.appendedDiscards.map(Uuid::parse),
    )
}
