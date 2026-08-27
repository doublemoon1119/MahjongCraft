package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationBusyGate
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** [GamePresentationBusyGate] 的 Fabric 實作，直接查詢 [TablePresentationBusyTracker]。 */
@Single(binds = [GamePresentationBusyGate::class])
class FabricGamePresentationBusyGate(private val tracker: TablePresentationBusyTracker) : GamePresentationBusyGate {
    override fun isBusy(gameId: Uuid): Boolean = tracker.isBusy(gameId)

    override fun isPresentingContinuingWin(gameId: Uuid): Boolean = tracker.isPresentingContinuingWin(gameId)
}
