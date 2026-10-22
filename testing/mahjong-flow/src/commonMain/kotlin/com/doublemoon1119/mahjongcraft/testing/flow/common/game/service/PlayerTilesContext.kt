package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.DrawFollowUpPresentationStep
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
import kotlin.uuid.Uuid

/**
 * [FakeGamePresentationPublisher] 紀錄的 [GamePresentationPublisher.publishPlayerTilesUpdated] 資料。
 *
 * @property seatIndex 玩家座位索引。
 * @property standingTileIds 手牌中的牌 UUID。
 * @property drawnTileId 摸牌 UUID，沒有摸牌時為 null。
 * @property melds 副露呈現資料。
 * @property setAsideTileIds 被保留在一旁的牌 UUID。
 * @property isNewlyDrawn 是否為新摸入的牌。
 * @property newlyClaimedMeldTileIds 本次新取得副露中的牌 UUID。
 * @property newlySetAsideTileIds 本次新保留在一旁的牌 UUID。
 * @property followUpSteps 摸牌後依序完成的自動移出與補牌。
 */
data class PlayerTilesContext(
    val seatIndex: Int,
    val standingTileIds: List<Uuid>,
    val drawnTileId: Uuid?,
    val melds: List<MeldPresentation>,
    val setAsideTileIds: List<Uuid>,
    val isNewlyDrawn: Boolean,
    val newlyClaimedMeldTileIds: Set<Uuid>,
    val newlySetAsideTileIds: Set<Uuid>,
    val followUpSteps: List<DrawFollowUpPresentationStep>,
)
