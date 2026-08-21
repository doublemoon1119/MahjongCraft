package com.doublemoon1119.mahjongcraft.platform.fabric.network

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTimerUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameCommandEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdatePayloadDto
import kotlinx.serialization.builtins.serializer

/** `mahjongcraft:` 命名空間下實際使用的命令、事件更新與主動快照同步頻道。 */
object MahjongChannels {
    val gameCommand = C2SChannel("game_command", GameCommandEnvelopeDto.serializer())

    /** 設定編輯畫面送出的原始 JSON 字串，見 `MahjongTableRoomService.updateConfig`。 */
    val updateGameConfig = C2SChannel("update_game_config", String.serializer())

    /**
     * 玩家（重新）加入世界後，主動向伺服器要求補送一份目前歸屬的房間／對局快照，見
     * `PlayerConnectionLifecycleService.onSnapshotRequested`——由客戶端自己決定「我剛加入、還沒有任何
     * 快照」這件事並主動詢問，不依賴伺服器猜測何時該推送，理由見該方法 KDoc。沒有實際內容，用
     * `Unit` 表示純粹的請求信號。
     */
    val requestSnapshot = C2SChannel("request_snapshot", Unit.serializer())
    val decisionTimerUpdate = S2CChannel("decision_timer_update", DecisionTimerUpdatePayloadDto.serializer())
    val gameUpdate = S2CChannel("game_update", GameUpdatePayloadDto.serializer())
    val roomUpdate = S2CChannel("room_update", RoomUpdatePayloadDto.serializer())
    val gameSnapshot = S2CChannel("game_snapshot", GameSnapshotSyncPayloadDto.serializer())
    val roomSnapshot = S2CChannel("room_snapshot", RoomSnapshotSyncPayloadDto.serializer())
}
