package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTileAudience
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTilePresentation
import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentViewPersistenceRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import kotlin.uuid.Uuid

/**
 * 將牌張呈現結果轉成保存格式。
 *
 * @param componentViews 牌面特徵的保存轉換。
 * @return 保存格式。
 */
internal fun HistoryTilePresentation.toPersistenceDto(componentViews: ComponentViewPersistenceRegistry): HistoryTilePresentationPersistenceDto = HistoryTilePresentationPersistenceDto(
    appearanceId = appearanceId,
    views = componentViews.encode(views),
    pose = pose.name,
    audience = when (val current = audience) {
        HistoryTileAudience.Public -> HistoryTileAudiencePersistenceDto.Public
        is HistoryTileAudience.Players -> HistoryTileAudiencePersistenceDto.Players(current.playerIds.map(Uuid::toString).sorted())
    },
)

/**
 * 還原保存的牌張呈現結果，不重新解算規則。
 *
 * @param componentViews 牌面特徵的保存轉換。
 * @return 保存時的呈現結果。
 */
internal fun HistoryTilePresentationPersistenceDto.toDomain(componentViews: ComponentViewPersistenceRegistry): HistoryTilePresentation = HistoryTilePresentation(
    appearanceId = appearanceId,
    views = componentViews.decode(views),
    pose = TilePose.valueOf(pose),
    audience = when (val saved = audience) {
        HistoryTileAudiencePersistenceDto.Public -> HistoryTileAudience.Public
        is HistoryTileAudiencePersistenceDto.Players -> HistoryTileAudience.Players(saved.playerIds.map(Uuid::parse).toSet())
    },
)
