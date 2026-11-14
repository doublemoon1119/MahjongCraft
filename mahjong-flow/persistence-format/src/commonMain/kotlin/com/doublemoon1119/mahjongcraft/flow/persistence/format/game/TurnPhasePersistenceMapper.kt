package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.table.RoundConclusion
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import kotlin.uuid.Uuid

/**
 * 將 [TurnPhase] 轉換成 persistence DTO。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @param componentRegistry 組件的持久化轉換註冊表。
 * @return 包含原模型資料的持久化 DTO。
 */
fun TurnPhase.toPersistenceDto(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    componentRegistry: ComponentPersistenceRegistry,
): TurnPhasePersistenceDto = when (this) {
    TurnPhase.AwaitingDraw -> TurnPhasePersistenceDto.AwaitingDraw
    TurnPhase.AwaitingAction -> TurnPhasePersistenceDto.AwaitingAction
    TurnPhase.AwaitingDiscard -> TurnPhasePersistenceDto.AwaitingDiscard
    is TurnPhase.AwaitingDiscardReaction -> TurnPhasePersistenceDto.AwaitingDiscardReaction(
        reaction = reaction.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry),
    )
    is TurnPhase.AwaitingRobbingReaction -> TurnPhasePersistenceDto.AwaitingRobbingReaction(
        reaction = reaction.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, componentRegistry),
    )
    is TurnPhase.RoundOver -> TurnPhasePersistenceDto.RoundOver(
        conclusion = when (val ending = conclusion) {
            is RoundConclusion.Win -> RoundConclusionPersistenceDto.Win(ending.winnerPlayerIds.mapTo(mutableSetOf(), Uuid::toString))
            is RoundConclusion.ExhaustiveDraw -> RoundConclusionPersistenceDto.ExhaustiveDraw(
                reason = exhaustiveDrawReasonRegistry.encode(ending.reason),
                tenpaiPlayerIds = ending.tenpaiPlayerIds.mapTo(mutableSetOf(), Uuid::toString),
            )
            is RoundConclusion.AbortiveDraw -> RoundConclusionPersistenceDto.AbortiveDraw(exhaustiveDrawReasonRegistry.encode(ending.reason))
        },
    )
}

/**
 * 將 persistence DTO 還原成 [TurnPhase]。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @param componentRegistry 組件的持久化轉換註冊表。
 * @return 還原後的回合階段。
 */
fun TurnPhasePersistenceDto.toDomain(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    componentRegistry: ComponentPersistenceRegistry,
): TurnPhase = when (this) {
    TurnPhasePersistenceDto.AwaitingDraw -> TurnPhase.AwaitingDraw
    TurnPhasePersistenceDto.AwaitingAction -> TurnPhase.AwaitingAction
    TurnPhasePersistenceDto.AwaitingDiscard -> TurnPhase.AwaitingDiscard
    is TurnPhasePersistenceDto.AwaitingDiscardReaction -> TurnPhase.AwaitingDiscardReaction(
        reaction = reaction.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry),
    )
    is TurnPhasePersistenceDto.AwaitingRobbingReaction -> TurnPhase.AwaitingRobbingReaction(
        reaction = reaction.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, componentRegistry),
    )
    is TurnPhasePersistenceDto.RoundOver -> TurnPhase.RoundOver(
        conclusion = when (val ending = conclusion) {
            is RoundConclusionPersistenceDto.Win -> RoundConclusion.Win(ending.winnerPlayerIds.mapTo(mutableSetOf(), Uuid::parse))
            is RoundConclusionPersistenceDto.ExhaustiveDraw -> RoundConclusion.ExhaustiveDraw(
                reason = exhaustiveDrawReasonRegistry.decode(ending.reason),
                tenpaiPlayerIds = ending.tenpaiPlayerIds.mapTo(mutableSetOf(), Uuid::parse),
            )
            is RoundConclusionPersistenceDto.AbortiveDraw -> RoundConclusion.AbortiveDraw(exhaustiveDrawReasonRegistry.decode(ending.reason))
        },
    )
}
