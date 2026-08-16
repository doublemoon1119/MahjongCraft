package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlin.uuid.Uuid

/**
 * 對局 in-process 呈現觸發器。
 *
 * `:mahjong-flow` 對外傳遞「只有平台呈現層需要、不該進入 `TableState`／persistence／network DTO」
 * 一次性資料的出口——目前是開局／連莊重新擲骰開門時的權威骰子結果與牌牆結構座標。這些資料只在牌局
 * 剛初始化的那個當下存在，呼叫端用完即可丟棄，不需要另外保存。
 *
 * 與 [GameEventPublisher] 分工明確：[GameEventPublisher] 負責通知玩家（跨網路、需要序列化）；此介面
 * 負責觸發 server 端本地呈現邏輯（不跨網路、不需要序列化）。實作方必須是 best-effort——沒有平台
 * 實作、該桌不是對應平台的桌子、或呈現觸發本身失敗時，都不能拋例外，呼叫端的權威狀態變更不因此
 * 受影響。
 */
interface GamePresentationPublisher {
    /**
     * 通知平台呈現層本局權威擲骰結果。
     *
     * [dealerSeatIndex]／[roundNumber]／[comboCount] 是呼叫端已經持有的通用桌況資料，一併帶過去讓
     * 平台呈現層自行決定怎麼用（例如換算成畫面呈現用的「這是第幾次擲骰」序號、決定擲骰者的座位）——
     * 不在這裡先算好任何 Minecraft 專屬概念，維持這個介面本身跟平台無關。
     *
     * @param gameId 對局 Uuid。
     * @param dice 本次開門使用的權威擲骰個別點數。
     * @param dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index（自風輪轉不會改變
     *   index，只改變該 index 玩家的風位）。
     * @param roundNumber 本次擲骰發生當下的局數。
     * @param comboCount 本次擲骰發生當下的本場數（連莊次數）。
     */
    fun publishDiceRoll(gameId: Uuid, dice: DiceRollResult, dealerSeatIndex: Int, roundNumber: Int, comboCount: Int)

    /**
     * 通知平台呈現層本局牌牆結構座標。
     *
     * [dealerSeatIndex] 跟 [publishDiceRoll] 同理，是呼叫端已經持有的通用桌況資料，一併帶過去讓平台
     * 呈現層自行決定怎麼把牌牆面／墩／層結構換算成以莊家座位為基準的世界座標。
     *
     * @param gameId 對局 Uuid。
     * @param structure 本局牌牆所有牌（含活牌與王牌）的面／墩／層結構座標，鍵為
     * [com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile.id]；空 map 代表這局結束，只需要清除
     * 舊牌。
     * @param dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index。
     */
    fun publishWallStructure(gameId: Uuid, structure: Map<Uuid, TileWallPosition>, dealerSeatIndex: Int)

    /**
     * 通知平台呈現層本局開局座位傳送。只在開局時呼叫一次，之後連莊/過莊開新局不會再次呼叫——風位
     * 輪轉純粹是規則概念，玩家在平台世界裡的物理位置整場對局固定不變。
     *
     * @param gameId 對局 Uuid。
     * @param seatedPlayerIds 依 `TableState.players` 固定座位順序排列的玩家 Uuid 清單。
     */
    fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>)
}
