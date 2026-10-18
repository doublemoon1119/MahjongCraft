package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout

/** 正常終局與提前中止共用的房間還原資料與呈現，不決定何時結束對局。 */
internal object GameRoomRestoration {
    /**
     * 依開局時保留的房間資訊還原參與者，真人重新準備、AI 維持就緒。
     *
     * @param game 要返回房間的對局。
     * @return 尚未寫入權威狀態的房間。
     */
    fun roomFor(game: Game): Room = Room(
        id = game.id,
        hostId = game.hostId,
        gameConfig = GameConfig(ruleConfig = game.tableState.config, flowConfig = game.flowConfig),
        playerIds = game.roomPlayerIds,
        readyPlayerIds = game.aiPlayerIds.toList() - game.hostId,
        aiPlayerStrategyKeys = game.aiPlayerStrategyKeys,
    )

    /**
     * 在權威轉移後清除舊呈現並同步房間；中止使用自己的加入原因，不冒充正常打完。
     *
     * @param room 已提交的房間。
     * @param reason 正常終局或提前中止的回房原因。
     * @param snapshots 房間觀察者快照來源。
     * @param events 房間通知出口。
     * @param presentation 清除舊對局呈現的出口。
     */
    suspend fun publish(
        room: Room,
        reason: JoinReason,
        snapshots: RoomSnapshotRepository,
        events: RoomEventPublisher,
        presentation: GamePresentationPublisher,
    ) {
        presentation.publishWallStructure(
            gameId = room.id,
            assemblyStructure = emptyMap(),
            layout = TileWallPhysicalLayout(emptyMap()),
            dealerSeatIndex = 0,
            deadWallTileIds = emptySet(),
            openingDiceCounts = emptyList(),
            isNewOpening = false,
        )
        presentation.clearPlayerTiles(room.id)
        room.playerIds.forEach { playerId -> snapshots.setSnapshot(playerId, room.toSnapshot(playerId)) }
        room.playerIds.forEach { playerId -> events.publishJoin(room.id, playerId, playerId, reason) }
    }
}
