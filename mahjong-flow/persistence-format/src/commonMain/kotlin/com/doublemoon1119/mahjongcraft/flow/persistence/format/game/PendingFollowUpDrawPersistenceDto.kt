package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import com.doublemoon1119.mahjongcraft.logic.table.PendingFollowUpDraw
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * [PendingFollowUpDraw] 的完整 persistence DTO。
 *
 * @property tileId 還沒詢問過規則的牌 UUID 字串。
 * @property source 摸到這張牌的來源。
 */
@Serializable
data class PendingFollowUpDrawPersistenceDto(
    val tileId: String,
    val source: DrawSourcePersistenceDto,
)

/**
 * 將 [PendingFollowUpDraw] 轉換成 persistence DTO。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @return 包含原模型資料的持久化 DTO。
 */
fun PendingFollowUpDraw.toPersistenceDto(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
): PendingFollowUpDrawPersistenceDto = PendingFollowUpDrawPersistenceDto(
    tileId = tileId.toString(),
    source = when (val drawSource = source) {
        DrawSource.LiveWall -> DrawSourcePersistenceDto.LiveWall
        is DrawSource.Supplemental -> DrawSourcePersistenceDto.Supplemental(
            drawSource.action.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry),
        )
    },
)

/**
 * 將 [PendingFollowUpDrawPersistenceDto] 還原成 [PendingFollowUpDraw]。
 *
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @return 還原的領域模型。
 */
fun PendingFollowUpDrawPersistenceDto.toDomain(
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
): PendingFollowUpDraw = PendingFollowUpDraw(
    tileId = Uuid.parse(tileId),
    source = when (val drawSource = source) {
        DrawSourcePersistenceDto.LiveWall -> DrawSource.LiveWall
        is DrawSourcePersistenceDto.Supplemental -> DrawSource.Supplemental(
            drawSource.action.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry),
        )
    },
)
