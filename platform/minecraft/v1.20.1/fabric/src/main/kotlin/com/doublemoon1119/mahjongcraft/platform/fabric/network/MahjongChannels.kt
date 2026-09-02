package com.doublemoon1119.mahjongcraft.platform.fabric.network

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTimerUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameCommandEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSelectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomScreenActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.TableLobbyPayloadDto
import kotlinx.serialization.builtins.serializer

/** `mahjongcraft:` 命名空間下實際使用的命令、事件更新與主動快照同步頻道。 */
object MahjongChannels {
    val gameCommand = C2SChannel("game_command", GameCommandEnvelopeDto.serializer())
    val decisionSelection = C2SChannel("decision_selection", PlayerDecisionSelectionDto.serializer())

    val roomScreenAction = C2SChannel("room_screen_action", RoomScreenActionDto.serializer())

    /**
     * 玩家（重新）加入世界後，主動向伺服器要求補送一份目前歸屬的房間／對局快照，見
     * `PlayerConnectionLifecycleService.onSnapshotRequested`——由客戶端自己決定「我剛加入、還沒有任何
     * 快照」這件事並主動詢問，不依賴伺服器猜測何時該推送，理由見該方法 KDoc。沒有實際內容，用
     * `Unit` 表示純粹的請求信號。
     */
    val requestSnapshot = C2SChannel("request_snapshot", Unit.serializer())

    /**
     * 玩家重新加入世界時恢復伺服器記憶體中的自動理牌偏好；這個同步不得移動或翻起任何手牌。
     */
    val restoreAutoSortHand = C2SChannel("restore_auto_sort_hand", Boolean.serializer())

    /**
     * 玩家實際切換「自動整理手牌」偏好時送出，見 `MahjongCraftMod.registerSetAutoSortHandReceiver`／
     * `SetHandSortPreferenceUseCase`——手牌 tile entity 是伺服器端共用的實體，這個偏好必須讓伺服器
     * 知道才能實際重新排列座標，不像牌角標籤那種純客戶端疊加。
     */
    val setAutoSortHand = C2SChannel("set_auto_sort_hand", Boolean.serializer())
    val decisionTimerUpdate = S2CChannel("decision_timer_update", DecisionTimerUpdatePayloadDto.serializer())
    val gameUpdate = S2CChannel("game_update", GameUpdatePayloadDto.serializer())
    val roomUpdate = S2CChannel("room_update", RoomUpdatePayloadDto.serializer())
    val gameSnapshot = S2CChannel("game_snapshot", GameSnapshotSyncPayloadDto.serializer())
    val roomSnapshot = S2CChannel("room_snapshot", RoomSnapshotSyncPayloadDto.serializer())
    val tableLobby = S2CChannel("table_lobby", TableLobbyPayloadDto.serializer())
}
