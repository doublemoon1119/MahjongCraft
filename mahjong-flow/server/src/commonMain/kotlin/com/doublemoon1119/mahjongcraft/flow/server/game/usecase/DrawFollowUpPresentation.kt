package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import kotlin.uuid.Uuid

/**
 * 摸牌或補牌之後，呈現摸牌玩家的牌區與後續的自動處理。
 *
 * 自動處理暫停在搶和視窗時，呈現最後一步的牌已移出、尚未補牌的樣子；權威桌況要等反應結束才套用。
 * 每一步的實體牌牆移動與收付隨步驟交給平台，牌牆新公開的牌在整串之後一次發布。規則狀態有改變時只在這裡通知一次，
 * 呼叫端不另外通知，避免桌上物件在牌區讓開之前就被同步。
 *
 * @param gameId 對局識別碼。
 * @param playerId 摸牌的玩家。
 * @param followUp 摸牌之後的自動處理結果；它的桌況是這次交易提交的權威桌況。
 * @param module 對局採用的規則模組。
 * @param newlyClaimedMeldTileIds 這次新移入副露的牌，例如槓的牌。
 * @param newlySetAsideTileIds 這次玩家宣告移出的牌，不含自動處理移出的牌。
 * @param ruleStateChanged 呼叫端自己的動作是否已改變規則狀態，例如宣告移出手牌；自動處理改變的規則狀態另外判斷。
 */
internal fun GamePresentationPublisher.publishPlayerAreaAfterDraw(
    gameId: Uuid,
    playerId: Uuid,
    followUp: DrawFollowUpApplier.Result,
    module: MahjongRuleModule<*>,
    newlyClaimedMeldTileIds: Set<Uuid> = emptySet(),
    newlySetAsideTileIds: Set<Uuid> = emptySet(),
    ruleStateChanged: Boolean = false,
) {
    val presentedState = (followUp.ending as? DrawFollowUpApplier.Ending.RobbingWindowOpened)?.declaredState
        ?: followUp.tableState
    val seatIndex = presentedState.players.indexOfFirst { it.id == playerId }
    val player = presentedState.players[seatIndex]
    publishPlayerTilesUpdated(
        gameId = gameId,
        seatIndex = seatIndex,
        standingTileIds = player.hand.tiles.map { it.id },
        drawnTileId = player.hand.lastDrawn?.id,
        melds = player.hand.melds.map { it.toPresentation(presentedState.config.revealsClosedKanTiles) },
        setAsideTileIds = module.setAsideTiles(player).map { it.id },
        isNewlyDrawn = true,
        newlyClaimedMeldTileIds = newlyClaimedMeldTileIds,
        newlySetAsideTileIds = newlySetAsideTileIds,
        followUpSteps = followUp.presentationSteps,
    )
    if (ruleStateChanged || followUp.steps.isNotEmpty() || followUp.ending is DrawFollowUpApplier.Ending.RobbingWindowOpened) {
        publishRuleStateUpdated(gameId)
    }
    followUp.steps.flatMap { it.applied.wallRevealBatches }.forEach { revealedTileIds ->
        publishWallTilesRevealed(gameId, revealedTileIds)
    }
}
